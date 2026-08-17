package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiModelConfig
import com.example.data.model.AutonomousLevel
import com.example.data.remote.session.SessionState
import com.example.data.remote.session.WorkspaceSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val config: AiModelConfig = AiModelConfig(),
    val isDarkMode: Boolean = true,
    val apiKeyInput: String = "••••••••••••••••",
    val workspaceId: String = SessionState.DEFAULT_WORKSPACE_ID,
    val backendUrl: String = "",
    val isDemoMode: Boolean = false,
    val syncStatus: String = "Ready"
)

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            WorkspaceSessionManager.sessionState.collect { session ->
                _uiState.update {
                    it.copy(
                        workspaceId = session.workspaceId,
                        backendUrl = session.backendUrl,
                        isDemoMode = session.isDemoMode,
                        syncStatus = session.lastSyncStatus
                    )
                }
            }
        }
    }

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

    fun toggleDemoMode(enabled: Boolean) {
        WorkspaceSessionManager.setDemoMode(enabled)
    }

    fun updateWorkspaceId(workspaceId: String) {
        WorkspaceSessionManager.setWorkspaceId(workspaceId)
    }

    fun updateBackendUrl(url: String) {
        WorkspaceSessionManager.setBackendUrl(url)
    }
}
