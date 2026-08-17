package com.example.data.session

import com.example.data.model.ExecutionEnvironment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UserSession(
    val userId: String = "user_default_1",
    val email: String = "founder@techpulse.app",
    val fullName: String = "Alex Founder",
    val avatarUrl: String? = null
)

data class SessionState(
    val isAuthenticated: Boolean = true,
    val token: String? = "dev_token_default_session",
    val user: UserSession = UserSession(),
    val activeWorkspaceId: String = "ws_default",
    val activeWorkspaceName: String = "TechPulse Main Workspace",
    val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment.PRODUCTION
) {
    val isDemoWorkspace: Boolean
        get() = executionEnvironment == ExecutionEnvironment.MOCK
}

/**
 * Singleton / In-Memory Session Manager.
 * Handles authenticated JWT token, active tenant workspace, and Demo vs Production mode.
 */
class SessionManager(
    initialState: SessionState = SessionState()
) {
    private val _sessionState = MutableStateFlow(initialState)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    val currentToken: String?
        get() = _sessionState.value.token

    val currentWorkspaceId: String
        get() = _sessionState.value.activeWorkspaceId

    val currentEnvironment: ExecutionEnvironment
        get() = _sessionState.value.executionEnvironment

    fun setAuthToken(token: String, user: UserSession? = null) {
        _sessionState.update { current ->
            current.copy(
                isAuthenticated = true,
                token = token,
                user = user ?: current.user
            )
        }
    }

    fun setActiveWorkspace(workspaceId: String, workspaceName: String? = null) {
        _sessionState.update { current ->
            current.copy(
                activeWorkspaceId = workspaceId,
                activeWorkspaceName = workspaceName ?: current.activeWorkspaceName
            )
        }
    }

    fun setExecutionEnvironment(environment: ExecutionEnvironment) {
        _sessionState.update { current ->
            current.copy(executionEnvironment = environment)
        }
    }

    fun logout() {
        _sessionState.update { current ->
            current.copy(
                isAuthenticated = false,
                token = null
            )
        }
    }

    companion object {
        private var instance: SessionManager? = null

        fun getInstance(): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager().also { instance = it }
            }
        }

        fun resetForTesting(customSession: SessionState = SessionState()): SessionManager {
            val newInstance = SessionManager(customSession)
            instance = newInstance
            return newInstance
        }
    }
}
