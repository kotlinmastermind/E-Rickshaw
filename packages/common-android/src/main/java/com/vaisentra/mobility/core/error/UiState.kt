package com.vaisentra.mobility.core.error

/**
 * Enterprise UI State representation across Compose screens and ViewModels.
 * Explicitly distinguishes Loading, Empty, Success, and structured Error states.
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data class Loading(val message: String = "Loading...") : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Empty(val message: String = "No items available") : UiState<Nothing>
    data class Error(
        val appError: AppError,
        val canRetry: Boolean = appError.isRetryEligible,
        val onRetry: (() -> Unit)? = null
    ) : UiState<Nothing> {
        val isOffline: Boolean get() = appError is AppError.NetworkError || appError is AppError.ConnectionError
        val isAuthRequired: Boolean get() = appError is AppError.AuthenticationError
        val isPermissionRequired: Boolean get() = appError is AppError.PermissionError
        val isServiceUnavailable: Boolean get() = (appError as? AppError.HttpError)?.statusCode in 500..599
        val userDisplayMessage: String get() = appError.userMessage
    }
}

