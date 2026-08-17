package com.example.data.repository

import com.example.data.model.AgentError
import com.example.data.model.AppResult
import com.example.data.remote.api.SocialStudioApiService
import com.example.data.remote.api.WorkspaceDto
import com.example.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface WorkspaceRepository {
    val workspaces: StateFlow<List<WorkspaceDto>>
    val isLoading: StateFlow<Boolean>
    suspend fun fetchUserWorkspaces(): AppResult<List<WorkspaceDto>>
    fun selectWorkspace(workspaceId: String, workspaceName: String? = null)
}

class ProductionWorkspaceRepository(
    private val apiService: SocialStudioApiService,
    private val sessionManager: SessionManager = SessionManager.getInstance()
) : WorkspaceRepository {

    private val _workspaces = MutableStateFlow<List<WorkspaceDto>>(
        listOf(
            WorkspaceDto(
                id = "ws_default",
                name = "TechPulse Main Workspace",
                slug = "techpulse-main",
                userRole = "owner"
            )
        )
    )
    override val workspaces: StateFlow<List<WorkspaceDto>> = _workspaces.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    override suspend fun fetchUserWorkspaces(): AppResult<List<WorkspaceDto>> {
        _isLoading.value = true
        return try {
            val response = apiService.getWorkspaces()
            _isLoading.value = false
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    _workspaces.value = body.data
                    // If current active workspace is not in list, auto-select first
                    if (body.data.isNotEmpty() && body.data.none { it.id == sessionManager.currentWorkspaceId }) {
                        sessionManager.setActiveWorkspace(body.data.first().id, body.data.first().name)
                    }
                    AppResult.Success(body.data)
                } else {
                    AppResult.Error(
                        AgentError(
                            code = body?.error ?: "FETCH_WORKSPACES_FAILED",
                            message = body?.message ?: "Failed to retrieve user workspaces."
                        )
                    )
                }
            } else {
                AppResult.Error(
                    AgentError(
                        code = "HTTP_${response.code()}",
                        message = "Error fetching workspaces: HTTP ${response.code()}"
                    )
                )
            }
        } catch (e: Exception) {
            _isLoading.value = false
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error fetching workspaces.",
                    cause = e
                )
            )
        }
    }

    override fun selectWorkspace(workspaceId: String, workspaceName: String?) {
        val foundName = workspaceName ?: _workspaces.value.find { it.id == workspaceId }?.name
        sessionManager.setActiveWorkspace(workspaceId, foundName)
    }
}
