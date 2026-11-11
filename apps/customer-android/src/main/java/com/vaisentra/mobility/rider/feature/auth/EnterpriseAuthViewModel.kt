package com.vaisentra.mobility.rider.feature.auth

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaisentra.mobility.rider.core.network.*
import com.vaisentra.mobility.core.security.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.regex.Pattern

sealed class AuthUiState {
    object Idle : AuthUiState()
    data class Loading(val message: String) : AuthUiState()
    data class RedirectSignUp(
        val initialIdentifier: String,
        val isEmail: Boolean
    ) : AuthUiState()
    data class Success(val token: String, val message: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class EnterpriseAuthViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "EnterpriseAuthVM"
        private val PHONE_PATTERN = Pattern.compile("^(\\+91[\\-\\s]?)?[0]?(91)?[6789]\\d{9}$")
        private val EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")

        fun isValidPhone(input: String): Boolean {
            val clean = input.replace(" ", "").replace("-", "")
            return PHONE_PATTERN.matcher(clean).matches()
        }

        fun isValidEmail(input: String): Boolean {
            return EMAIL_PATTERN.matcher(input.trim()).matches()
        }

        fun normalizePhone(input: String): String {
            val clean = input.replace(" ", "").replace("-", "")
            return when {
                clean.startsWith("+91") -> clean
                clean.startsWith("91") && clean.length == 12 -> "+$clean"
                clean.length == 10 -> "+91$clean"
                else -> clean
            }
        }

        fun formatErrorMessage(e: Throwable, defaultMsg: String): String {
            val em = e.message.orEmpty()
            return when {
                e is java.net.UnknownHostException || em.contains("Unable to resolve host", ignoreCase = true) || em.contains("No address associated", ignoreCase = true) ->
                    "Server connection failed. Please check your internet or Wi-Fi connection and tap Continue."
                e is java.net.ConnectException || em.contains("Failed to connect", ignoreCase = true) || em.contains("ENETUNREACH", ignoreCase = true) ->
                    "Unable to reach server. Please check your network connection."
                e is java.net.SocketTimeoutException || em.contains("timeout", ignoreCase = true) ->
                    "Server is taking longer to respond. Please tap Continue again in a few seconds."
                else -> e.localizedMessage ?: defaultMsg
            }
        }
    }

    fun signInWithPassword(identifier: String, password: String) {
        val trimmed = identifier.trim()
        if (trimmed.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your email/phone and password.")
            return
        }

        _uiState.value = AuthUiState.Loading("Authenticating...")
        viewModelScope.launch {
            try {
                val ident = if (trimmed.contains("@")) trimmed else normalizePhone(trimmed)
                val response = ApiClient.getService().unifiedLogin(
                    UnifiedLoginRequestPayload(
                        loginType = "PASSWORD",
                        identifier = ident,
                        password = password
                    )
                )

                if (response.isSuccessful && response.body()?.accessToken != null) {
                    val body = response.body()!!
                    val token = body.accessToken!!
                    val uid = body.user?.id ?: "usr_customer"
                    tokenManager.saveAuth(token, uid, ident, "customer")
                    _uiState.value = AuthUiState.Success(token, "Sign in successful!")
                } else {
                    val err = response.errorBody()?.string() ?: "Invalid email, phone number, or password."
                    _uiState.value = AuthUiState.Error(err)
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(formatErrorMessage(e, "Sign in failed."))
            }
        }
    }

    fun signUpCustomer(fullName: String, phoneOrEmail: String, password: String, email: String? = null) {
        val trimmedIdent = phoneOrEmail.trim()
        if (fullName.isBlank() || trimmedIdent.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all required fields.")
            return
        }

        _uiState.value = AuthUiState.Loading("Creating your account...")
        viewModelScope.launch {
            try {
                val isEmail = trimmedIdent.contains("@")
                val phone = if (!isEmail) normalizePhone(trimmedIdent) else null
                val userEmail = if (isEmail) trimmedIdent else email?.trim()

                val response = ApiClient.getService().signupCustomer(
                    CustomerSignupRequestPayload(
                        fullName = fullName.trim(),
                        phoneNumber = phone,
                        email = userEmail,
                        username = userEmail?.substringBefore("@") ?: ("cust_" + (phone?.takeLast(4) ?: "user")),
                        password = password
                    )
                )

                if (response.isSuccessful && response.body()?.accessToken != null) {
                    val body = response.body()!!
                    val token = body.accessToken!!
                    val uid = body.user?.id ?: "usr_customer"
                    tokenManager.saveAuth(token, uid, phone ?: userEmail ?: "customer", "customer")
                    _uiState.value = AuthUiState.Success(token, "Account created successfully!")
                } else {
                    val err = response.errorBody()?.string() ?: "Sign up failed. Account may already exist."
                    _uiState.value = AuthUiState.Error(err)
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(formatErrorMessage(e, "Registration failed."))
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
