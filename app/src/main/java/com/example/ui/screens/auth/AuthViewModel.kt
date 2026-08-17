package com.example.ui.screens.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AuthUiState(
    val email: String = "founder@techpulse.app",
    val isAuthenticated: Boolean = true,
    val isLoading: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(emailInput: String) {
        _uiState.update { it.copy(email = emailInput, isAuthenticated = true) }
    }

    fun logout() {
        _uiState.update { it.copy(isAuthenticated = false) }
    }
}
