package com.example.appcolegios.auth

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val type: AuthErrorType, val message: String) : AuthState()
}

enum class AuthErrorType {
    INVALID_EMAIL,
    WRONG_CREDENTIALS,
    USER_NOT_FOUND,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    NETWORK,
    UNKNOWN
}

