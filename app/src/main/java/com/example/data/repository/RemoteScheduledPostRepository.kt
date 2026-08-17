package com.example.data.repository

import com.example.data.model.*
import com.example.data.remote.api.SocialMediaApiService
import com.example.data.remote.client.ApiClientProvider
import com.example.data.remote.dto.DtoMappers
import com.example.data.remote.dto.SavePublishResultRequest
import com.example.data.remote.dto.UpdateSocialPostRequest
import com.example.data.remote.session.WorkspaceSessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteScheduledPostRepository(
    private val apiService: SocialMediaApiService = ApiClientProvider.getApiService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ScheduledPostRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val postsFlow = MutableStateFlow<List<SocialPost>>(emptyList())

    init {
        refreshPostsAsync()
        scope.launch {
            WorkspaceSessionManager.sessionState.collect {
                refreshPostsAsync()
            }
        }
    }

    private fun getWorkspaceId(): String = WorkspaceSessionManager.getWorkspaceId()

    private fun refreshPostsAsync() {
        scope.launch {
            try {
                val res = apiService.getPosts(getWorkspaceId())
                if (res.isSuccessful && res.body()?.success == true) {
                    val list = res.body()?.data?.map { DtoMappers.toSocialPost(it) } ?: emptyList()
                    postsFlow.value = list
                }
            } catch (e: Exception) {
                // Silently hold current cache
            }
        }
    }

    override suspend fun create(post: SocialPost): AppResult<SocialPost> = withContext(ioDispatcher) {
        try {
            val req = DtoMappers.toCreatePostRequest(post)
            val res = apiService.createPost(getWorkspaceId(), req)
            val data = res.body()?.data
            if (res.isSuccessful && res.body()?.success == true && data != null) {
                val created = DtoMappers.toSocialPost(data)
                postsFlow.value = listOf(created) + postsFlow.value
                AppResult.Success(created)
            } else {
                AppResult.Error(AgentError("CREATE_POST_FAILED", res.body()?.message ?: "Failed to create post"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Failed to create post", e))
        }
    }

    override suspend fun getById(id: String): AppResult<SocialPost?> = withContext(ioDispatcher) {
        val cached = postsFlow.value.find { it.id == id }
        if (cached != null) return@withContext AppResult.Success(cached)

        try {
            val res = apiService.getPostById(getWorkspaceId(), id)
            val data = res.body()?.data
            if (res.isSuccessful && res.body()?.success == true && data != null) {
                val post = DtoMappers.toSocialPost(data)
                AppResult.Success(post)
            } else {
                AppResult.Success(null)
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Failed to fetch post", e))
        }
    }

    override suspend fun getUpcoming(): AppResult<List<SocialPost>> = withContext(ioDispatcher) {
        try {
            val res = apiService.getScheduledPosts(getWorkspaceId())
            if (res.isSuccessful && res.body()?.success == true) {
                val list = res.body()?.data?.map { DtoMappers.toSocialPost(it) } ?: emptyList()
                AppResult.Success(list)
            } else {
                val fallback = postsFlow.value.filter { it.status == PostStatus.SCHEDULED || it.status == PostStatus.DRAFT }
                AppResult.Success(fallback)
            }
        } catch (e: Exception) {
            val fallback = postsFlow.value.filter { it.status == PostStatus.SCHEDULED || it.status == PostStatus.DRAFT }
            AppResult.Success(fallback)
        }
    }

    override suspend fun getForDate(dateIso: String): AppResult<List<SocialPost>> = withContext(ioDispatcher) {
        val matches = postsFlow.value.filter { post ->
            post.scheduledTime.contains(dateIso, ignoreCase = true) ||
            (post.scheduledAt != null && post.scheduledAt.contains(dateIso))
        }
        AppResult.Success(matches)
    }

    override suspend fun updateStatus(
        id: String,
        status: PostStatus,
        approvalState: ActionApprovalState
    ): AppResult<SocialPost> = withContext(ioDispatcher) {
        try {
            val req = UpdateSocialPostRequest(
                status = DtoMappers.mapPostStatusToString(status),
                approvalState = DtoMappers.mapApprovalStateToString(approvalState)
            )
            val res = apiService.updatePost(getWorkspaceId(), id, req)
            val data = res.body()?.data
            if (res.isSuccessful && res.body()?.success == true && data != null) {
                val updated = DtoMappers.toSocialPost(data)
                postsFlow.value = postsFlow.value.map { if (it.id == id) updated else it }
                AppResult.Success(updated)
            } else {
                AppResult.Error(AgentError("UPDATE_STATUS_FAILED", res.body()?.message ?: "Failed to update status"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Failed to update status", e))
        }
    }

    override suspend fun updatePost(post: SocialPost): AppResult<SocialPost> = withContext(ioDispatcher) {
        try {
            val req = DtoMappers.toUpdatePostRequest(post)
            val res = apiService.updatePost(getWorkspaceId(), post.id, req)
            val data = res.body()?.data
            if (res.isSuccessful && res.body()?.success == true && data != null) {
                val updated = DtoMappers.toSocialPost(data)
                postsFlow.value = postsFlow.value.map { if (it.id == post.id) updated else it }
                AppResult.Success(updated)
            } else {
                AppResult.Error(AgentError("UPDATE_POST_FAILED", res.body()?.message ?: "Failed to update post"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Failed to update post", e))
        }
    }

    override suspend fun cancel(id: String): AppResult<Boolean> = withContext(ioDispatcher) {
        try {
            val req = UpdateSocialPostRequest(
                status = "FAILED",
                approvalState = "CANCELLED",
                errorMessage = "Cancelled by user"
            )
            val res = apiService.updatePost(getWorkspaceId(), id, req)
            if (res.isSuccessful && res.body()?.success == true) {
                postsFlow.value = postsFlow.value.map { post ->
                    if (post.id == id) {
                        post.copy(
                            status = PostStatus.FAILED,
                            approvalState = ActionApprovalState.CANCELLED,
                            errorMessage = "Cancelled by user"
                        )
                    } else {
                        post
                    }
                }
                AppResult.Success(true)
            } else {
                AppResult.Error(AgentError("CANCEL_FAILED", res.body()?.message ?: "Failed to cancel post"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Failed to cancel post", e))
        }
    }

    override suspend fun delete(id: String): AppResult<Boolean> = withContext(ioDispatcher) {
        try {
            val res = apiService.deletePost(getWorkspaceId(), id)
            if (res.isSuccessful && res.body()?.success == true) {
                postsFlow.value = postsFlow.value.filterNot { it.id == id }
                AppResult.Success(true)
            } else {
                AppResult.Error(AgentError("DELETE_FAILED", res.body()?.message ?: "Failed to delete post"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Failed to delete post", e))
        }
    }

    override suspend fun saveExecutionResult(
        postId: String,
        platformResult: PlatformPublishResult
    ): AppResult<SocialPost> = withContext(ioDispatcher) {
        try {
            val req = SavePublishResultRequest(
                platform = DtoMappers.mapPlatformTypeToString(platformResult.platform),
                status = DtoMappers.mapApprovalStateToString(platformResult.status),
                externalPostId = platformResult.externalPostId,
                errorMessage = platformResult.errorMessage,
                executionEnvironment = if (platformResult.executionEnvironment == ExecutionEnvironment.PRODUCTION) "PRODUCTION" else "MOCK",
                idempotencyKey = platformResult.idempotencyKey.ifBlank { null }
            )
            val res = apiService.savePublishResult(getWorkspaceId(), postId, req)
            if (res.isSuccessful && res.body()?.success == true) {
                // Fetch the updated post
                val postRes = apiService.getPostById(getWorkspaceId(), postId)
                val data = postRes.body()?.data
                if (postRes.isSuccessful && data != null) {
                    val updated = DtoMappers.toSocialPost(data)
                    postsFlow.value = postsFlow.value.map { if (it.id == postId) updated else it }
                    AppResult.Success(updated)
                } else {
                    AppResult.Error(AgentError("FETCH_POST_FAILED", "Failed to retrieve post after saving result"))
                }
            } else {
                AppResult.Error(AgentError("SAVE_RESULT_FAILED", res.body()?.message ?: "Failed to save execution result"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Failed to save execution result", e))
        }
    }

    override fun getAllAsFlow(): Flow<List<SocialPost>> = postsFlow.asStateFlow()
}
