package com.vaisentra.mobility.core.error

import retrofit2.Response

/**
 * Converts a Retrofit [Response] to a typed [AppResult].
 * Encapsulates status code checking and parses safe error states.
 * Rejects empty bodies for non-Unit expected types with [AppError.EmptyResponseError].
 */
inline fun <reified T> Response<T>.toAppResult(): AppResult<T> {
    return if (this.isSuccessful) {
        val body = this.body()
        if (body != null) {
            AppResult.Success(body)
        } else if (T::class == Unit::class) {
            @Suppress("UNCHECKED_CAST")
            AppResult.Success(Unit as T)
        } else {
            AppResult.Failure(
                AppError.EmptyResponseError(
                    httpCode = this.code(),
                    diagnosticMessage = "API returned HTTP ${this.code()} with null response body"
                )
            )
        }
    } else {
        val code = this.code()
        val retryAfter = this.headers()["Retry-After"]?.toLongOrNull()
        val error = when (code) {
            401 -> AppError.AuthenticationError(
                diagnosticMessage = "API returned 401 Unauthorized"
            )
            403 -> AppError.AuthorizationError(
                diagnosticMessage = "API returned 403 Forbidden"
            )
            404 -> AppError.HttpError(
                statusCode = 404,
                diagnosticMessage = "API returned 404 Not Found"
            )
            422 -> AppError.ValidationError(
                code = "ERR_VAL_422",
                userMessage = "The request could not be processed due to invalid parameters.",
                diagnosticMessage = "API returned 422 Unprocessable Entity"
            )
            429 -> AppError.HttpError(
                statusCode = 429,
                retryAfterSeconds = retryAfter,
                diagnosticMessage = "API returned 429 Too Many Requests"
            )
            502 -> AppError.HttpError(
                statusCode = 502,
                diagnosticMessage = "API returned 502 Bad Gateway"
            )
            503 -> AppError.HttpError(
                statusCode = 503,
                retryAfterSeconds = retryAfter,
                diagnosticMessage = "API returned 503 Service Unavailable"
            )
            else -> AppError.HttpError(
                statusCode = code,
                retryAfterSeconds = retryAfter,
                diagnosticMessage = "API returned HTTP $code"
            )
        }
        AppResult.Failure(error)
    }
}
