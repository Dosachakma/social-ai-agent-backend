package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.example.data.model.AiModelConfig
import com.example.data.model.AutonomousLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val config: AiModelConfig = AiModelConfig(),
    val isDarkMode: Boolean = true,
    val apiKeyInput: String = "••••••••••••••••"
)

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateModelName(model: String) {
        _uiState.update { it.copy(config = it.config.copy(modelName = model)) }
    }

    fun updateAutonomousLevel(level: AutonomousLevel) {
        _uiState.update { it.copy(config = it.config.copy(autonomousLevel = level)) }
    }

    fun toggleDarkMode(enabled: Boolean) {
        _uiState.update { it.copy(isDarkMode = enabled) }
    }

    fun togglePhishingProtection(enabled: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(enablePhishingDetection = enabled)) }
    }

    fun toggleAutoReply(enabled: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(enableAutoCommentReply = enabled)) }
    }
}
