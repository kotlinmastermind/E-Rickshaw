package com.vaisentra.mobility.core.error

/**
 * Functional result type representing either a successful computation [Success]
 * or an application failure [Failure] containing a typed [AppError].
 */
sealed class AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): AppError? = (this as? Failure)?.error

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun <R> flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (AppError) -> Unit): AppResult<T> {
        if (this is Failure) action(error)
        return this
    }

    inline fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (AppError) -> R
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(error)
    }

    companion object {
        fun <T> success(data: T): AppResult<T> = Success(data)
        fun failure(error: AppError): AppResult<Nothing> = Failure(error)
    }
}

/**
 * Returns the encapsulated value if this instance represents [AppResult.Success]
 * or the [default] value if it is [AppResult.Failure].
 */
fun <T> AppResult<T>.getOrDefault(default: T): T = when (this) {
    is AppResult.Success -> data
    is AppResult.Failure -> default
}

