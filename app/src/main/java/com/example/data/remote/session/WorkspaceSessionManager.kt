package com.example.data.remote.session

import com.example.data.config.SecurityConfig
import com.example.data.model.ExecutionEnvironment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SessionState(
    val workspaceId: String = DEFAULT_WORKSPACE_ID,
    val userId: String = DEFAULT_USER_ID,
    val authToken: String = DEFAULT_DEV_JWT,
    val environment: ExecutionEnvironment = ExecutionEnvironment.PRODUCTION,
    val backendUrl: String = SecurityConfig.getMetaBackendBaseUrl(),
    val isDemoMode: Boolean = false,
    val lastSyncStatus: String = "Ready"
) {
    companion object {
        const val DEFAULT_WORKSPACE_ID = "11111111-2222-4333-8444-555555555555"
        const val DEFAULT_USER_ID = "a1b2c3d4-e5f6-4a1b-8c2d-1234567890ab"
        // Standard signed development JWT conforming to JWT_ISSUER / JWT_AUDIENCE
        const val DEFAULT_DEV_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhMWIyYzNkNC1lNWY2LTRhMWItOGMyZC0xMjM0NTY3ODkwYWIiLCJlbWFpbCI6ImZvdW5kZXJAdGVjaHB1bHNlLmFwcCIsIm5hbWUiOiJUZWNoUHVsc2UgRm91bmRlciIsImlzcyI6InNvY2lhbC1haS1zdHVkaW8iLCJhdWQiOiJzb2NpYWwtYWktc3R1ZGlvLWFwaSJ9.mock_signature"
    }
}

object WorkspaceSessionManager {

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    fun getWorkspaceId(): String = _sessionState.value.workspaceId

    fun getUserId(): String = _sessionState.value.userId

    fun getAuthToken(): String = _sessionState.value.authToken

    fun getBackendUrl(): String = _sessionState.value.backendUrl

    fun isDemoMode(): Boolean = _sessionState.value.isDemoMode

    fun getEnvironment(): ExecutionEnvironment = _sessionState.value.environment

    fun setWorkspaceId(workspaceId: String) {
        _sessionState.update { it.copy(workspaceId = workspaceId) }
    }

    fun setAuthToken(token: String) {
        _sessionState.update { it.copy(authToken = token) }
    }

    fun setBackendUrl(url: String) {
        val normalized = if (url.endsWith("/")) url.dropLast(1) else url
        _sessionState.update { it.copy(backendUrl = normalized) }
    }

    fun setDemoMode(isDemo: Boolean) {
        _sessionState.update { 
            it.copy(
                isDemoMode = isDemo,
                environment = if (isDemo) ExecutionEnvironment.MOCK else ExecutionEnvironment.PRODUCTION
            ) 
        }
    }

    fun setEnvironment(env: ExecutionEnvironment) {
        _sessionState.update {
            it.copy(
                environment = env,
                isDemoMode = (env == ExecutionEnvironment.MOCK)
            )
        }
    }

    fun updateSyncStatus(status: String) {
        _sessionState.update { it.copy(lastSyncStatus = status) }
    }

    fun getAuthHeader(): String {
        val token = getAuthToken()
        return if (token.isNotBlank()) "Bearer $token" else ""
    }

    fun reset() {
        _sessionState.value = SessionState()
    }
}
