package com.example.appcolegios.auth

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Authenticated(val userId: String, val role: String) : AuthState
    data class Error(val message: String) : AuthState
}

