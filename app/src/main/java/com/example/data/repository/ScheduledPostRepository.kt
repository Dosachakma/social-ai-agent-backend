package com.example.data.repository

import com.example.data.model.*
import com.example.data.remote.api.SocialStudioApiService
import com.example.data.remote.mappers.DomainMappers.toCreateRequest
import com.example.data.remote.mappers.DomainMappers.toDomain
import com.example.data.remote.mappers.DomainMappers.toSaveRequest
import com.example.data.remote.mappers.DomainMappers.toUpdateRequest
import com.example.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

interface ScheduledPostRepository {
    suspend fun create(post: SocialPost): AppResult<SocialPost>
    suspend fun getById(id: String): AppResult<SocialPost?>
    suspend fun getUpcoming(): AppResult<List<SocialPost>>
    suspend fun getForDate(dateIso: String): AppResult<List<SocialPost>>
    suspend fun updateStatus(id: String, status: PostStatus, approvalState: ActionApprovalState): AppResult<SocialPost>
    suspend fun updatePost(post: SocialPost): AppResult<SocialPost>
    suspend fun cancel(id: String): AppResult<Boolean>
    suspend fun delete(id: String): AppResult<Boolean>
    suspend fun saveExecutionResult(postId: String, platformResult: PlatformPublishResult): AppResult<SocialPost>
    fun getAllAsFlow(): Flow<List<SocialPost>>
}

class MockScheduledPostRepository : ScheduledPostRepository {

    private val postsFlow = MutableStateFlow<List<SocialPost>>(seedPosts())

    override suspend fun create(post: SocialPost): AppResult<SocialPost> {
        val created = post.copy(
            id = if (post.id.isBlank()) UUID.randomUUID().toString() else post.id
        )
        postsFlow.value = listOf(created) + postsFlow.value
        return AppResult.Success(created)
    }

    override suspend fun getById(id: String): AppResult<SocialPost?> {
        val found = postsFlow.value.find { it.id == id }
        return AppResult.Success(found)
    }

    override suspend fun getUpcoming(): AppResult<List<SocialPost>> {
        val upcoming = postsFlow.value.filter { 
            it.status == PostStatus.SCHEDULED || it.status == PostStatus.DRAFT 
        }
        return AppResult.Success(upcoming)
    }

    override suspend fun getForDate(dateIso: String): AppResult<List<SocialPost>> {
        val matches = postsFlow.value.filter { post ->
            post.scheduledTime.contains(dateIso, ignoreCase = true) ||
            (post.scheduledAt != null && post.scheduledAt.contains(dateIso))
        }
        return AppResult.Success(matches)
    }

    override suspend fun updateStatus(
        id: String,
        status: PostStatus,
        approvalState: ActionApprovalState
    ): AppResult<SocialPost> {
        var updatedPost: SocialPost? = null
        postsFlow.value = postsFlow.value.map { post ->
            if (post.id == id) {
                val updated = post.copy(status = status, approvalState = approvalState)
                updatedPost = updated
                updated
            } else {
                post
            }
        }
        return updatedPost?.let { AppResult.Success(it) } 
            ?: AppResult.Error(AgentError("POST_NOT_FOUND", "Post with id $id not found"))
    }

    override suspend fun updatePost(post: SocialPost): AppResult<SocialPost> {
        var found = false
        postsFlow.value = postsFlow.value.map {
            if (it.id == post.id) {
                found = true
                post
            } else {
                it
            }
        }
        return if (found) AppResult.Success(post) 
        else AppResult.Error(AgentError("POST_NOT_FOUND", "Post ${post.id} not found"))
    }

    override suspend fun cancel(id: String): AppResult<Boolean> {
        var found = false
        postsFlow.value = postsFlow.value.map { post ->
            if (post.id == id) {
                found = true
                post.copy(
                    status = PostStatus.FAILED,
                    approvalState = ActionApprovalState.CANCELLED,
                    errorMessage = "Post cancelled by user"
                )
            } else {
                post
            }
        }
        return AppResult.Success(found)
    }

    override suspend fun delete(id: String): AppResult<Boolean> {
        val initialSize = postsFlow.value.size
        postsFlow.value = postsFlow.value.filterNot { it.id == id }
        return AppResult.Success(postsFlow.value.size < initialSize)
    }

    override suspend fun saveExecutionResult(
        postId: String,
        platformResult: PlatformPublishResult
    ): AppResult<SocialPost> {
        var updatedPost: SocialPost? = null
        postsFlow.value = postsFlow.value.map { post ->
            if (post.id == postId) {
                val existingResults = post.platformPublishResults.filterNot { it.platform == platformResult.platform }
                val newResults = existingResults + platformResult
                val updatedKeys = post.idempotencyKeys + (platformResult.platform to platformResult.idempotencyKey)
                
                val allSucceeded = post.targetPlatforms.all { p ->
                    newResults.any { res -> res.platform == p && res.status == ActionApprovalState.SUCCESS }
                }

                val updated = post.copy(
                    platformPublishResults = newResults,
                    idempotencyKeys = updatedKeys,
                    status = if (allSucceeded) PostStatus.PUBLISHED else post.status,
                    approvalState = if (allSucceeded) ActionApprovalState.SUCCESS else post.approvalState
                )
                updatedPost = updated
                updated
            } else {
                post
            }
        }
        return updatedPost?.let { AppResult.Success(it) }
            ?: AppResult.Error(AgentError("POST_NOT_FOUND", "Post $postId not found"))
    }

    override fun getAllAsFlow(): Flow<List<SocialPost>> = postsFlow.asStateFlow()

    private fun seedPosts(): List<SocialPost> {
        return listOf(
            SocialPost(
                id = "post_seed_1",
                title = "AI Automation Launch Teaser",
                content = "🚀 Autonomous social agent capabilities are changing content management forever. Stay tuned!",
                targetPlatforms = listOf(PlatformType.FACEBOOK, PlatformType.INSTAGRAM, PlatformType.LINKEDIN),
                scheduledTime = "Mon 12 at 3:00 PM",
                scheduledAt = "2026-08-12T15:00:00",
                timezone = "America/New_York",
                repeatOption = RecurrenceOption.NONE,
                requireApproval = true,
                status = PostStatus.SCHEDULED,
                approvalState = ActionApprovalState.APPROVED,
                mediaUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800",
                hashtags = "#AI #Automation #Productivity"
            ),
            SocialPost(
                id = "post_seed_2",
                title = "Weekly Founder Thought Leadership",
                content = "Building software in 2026 requires continuous feedback loops and autonomous safety guardrails.",
                targetPlatforms = listOf(PlatformType.LINKEDIN, PlatformType.TWITTER),
                scheduledTime = "Tue 13 at 9:00 AM",
                scheduledAt = "2026-08-13T09:00:00",
                timezone = "America/New_York",
                repeatOption = RecurrenceOption.WEEKLY,
                requireApproval = true,
                status = PostStatus.SCHEDULED,
                approvalState = ActionApprovalState.AWAITING_APPROVAL,
                hashtags = "#TechLeadership #FounderLog"
            )
        )
    }
}

/**
 * Production PostgreSQL-backed Scheduled Post Repository communicating with Node.js API.
 */
class ProductionScheduledPostRepository(
    private val apiService: SocialStudioApiService,
    private val sessionManager: SessionManager = SessionManager.getInstance(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ScheduledPostRepository {

    private val _postsFlow = MutableStateFlow<List<SocialPost>>(emptyList())

    init {
        coroutineScope.launch {
            refreshPosts()
        }
    }

    suspend fun refreshPosts(): AppResult<List<SocialPost>> {
        val workspaceId = sessionManager.currentWorkspaceId
        return try {
            val response = apiService.getPosts(workspaceId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    val domainPosts = body.data.map { it.toDomain(isDemo = false) }
                    _postsFlow.value = domainPosts
                    AppResult.Success(domainPosts)
                } else {
                    AppResult.Error(
                        AgentError(
                            code = body?.error ?: "FETCH_POSTS_FAILED",
                            message = body?.message ?: "Failed to retrieve posts."
                        )
                    )
                }
            } else {
                AppResult.Error(
                    AgentError(
                        code = "HTTP_${response.code()}",
                        message = "Error retrieving posts from backend (HTTP ${response.code()})"
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error communicating with posts service.",
                    cause = e
                )
            )
        }
    }

    override suspend fun create(post: SocialPost): AppResult<SocialPost> {
        val workspaceId = sessionManager.currentWorkspaceId
        val req = post.toCreateRequest()
        return try {
            val response = apiService.createPost(workspaceId, req)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    val createdDomain = body.data.toDomain(isDemo = false)
                    _postsFlow.value = listOf(createdDomain) + _postsFlow.value
                    AppResult.Success(createdDomain)
                } else {
                    AppResult.Error(
                        AgentError(
                            code = body?.error ?: "CREATE_POST_FAILED",
                            message = body?.message ?: "Failed to create post."
                        )
                    )
                }
            } else {
                AppResult.Error(
                    AgentError(
                        code = "HTTP_${response.code()}",
                        message = "Failed to create post on server (HTTP ${response.code()})"
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error creating post.",
                    cause = e
                )
            )
        }
    }

    override suspend fun getById(id: String): AppResult<SocialPost?> {
        val workspaceId = sessionManager.currentWorkspaceId
        return try {
            val response = apiService.getPostById(workspaceId, id)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    AppResult.Success(body.data.toDomain(isDemo = false))
                } else {
                    AppResult.Success(null)
                }
            } else if (response.code() == 404) {
                AppResult.Success(null)
            } else {
                AppResult.Error(
                    AgentError(
                        code = "HTTP_${response.code()}",
                        message = "Failed to fetch post $id (HTTP ${response.code()})"
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error fetching post $id.",
                    cause = e
                )
            )
        }
    }

    override suspend fun getUpcoming(): AppResult<List<SocialPost>> {
        val workspaceId = sessionManager.currentWorkspaceId
        return try {
            val response = apiService.getScheduledPosts(workspaceId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    val upcoming = body.data.map { it.toDomain(isDemo = false) }
                    AppResult.Success(upcoming)
                } else {
                    AppResult.Error(
                        AgentError(
                            code = body?.error ?: "FETCH_SCHEDULED_FAILED",
                            message = body?.message ?: "Failed to fetch scheduled posts."
                        )
                    )
                }
            } else {
                AppResult.Error(
                    AgentError(
                        code = "HTTP_${response.code()}",
                        message = "Failed to fetch scheduled posts (HTTP ${response.code()})"
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error fetching scheduled posts.",
                    cause = e
                )
            )
        }
    }

    override suspend fun getForDate(dateIso: String): AppResult<List<SocialPost>> {
        val matches = _postsFlow.value.filter { post ->
            post.scheduledTime.contains(dateIso, ignoreCase = true) ||
            (post.scheduledAt != null && post.scheduledAt.contains(dateIso))
        }
        return AppResult.Success(matches)
    }

    override suspend fun updateStatus(
        id: String,
        status: PostStatus,
        approvalState: ActionApprovalState
    ): AppResult<SocialPost> {
        val workspaceId = sessionManager.currentWorkspaceId
        val existing = _postsFlow.value.find { it.id == id }
            ?: return AppResult.Error(AgentError("POST_NOT_FOUND", "Post with id $id not found"))

        val updated = existing.copy(status = status, approvalState = approvalState)
        return updatePost(updated)
    }

    override suspend fun updatePost(post: SocialPost): AppResult<SocialPost> {
        val workspaceId = sessionManager.currentWorkspaceId
        val req = post.toUpdateRequest()
        return try {
            val response = apiService.updatePost(workspaceId, post.id, req)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    val domainUpdated = body.data.toDomain(isDemo = false)
                    _postsFlow.value = _postsFlow.value.map { if (it.id == post.id) domainUpdated else it }
                    AppResult.Success(domainUpdated)
                } else {
                    AppResult.Error(
                        AgentError(
                            code = body?.error ?: "UPDATE_POST_FAILED",
                            message = body?.message ?: "Failed to update post."
                        )
                    )
                }
            } else {
                AppResult.Error(
                    AgentError(
                        code = "HTTP_${response.code()}",
                        message = "Failed to update post on server (HTTP ${response.code()})"
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error updating post.",
                    cause = e
                )
            )
        }
    }

    override suspend fun cancel(id: String): AppResult<Boolean> {
        val existing = _postsFlow.value.find { it.id == id }
            ?: return AppResult.Error(AgentError("POST_NOT_FOUND", "Post with id $id not found"))
        val cancelled = existing.copy(
            status = PostStatus.FAILED,
            approvalState = ActionApprovalState.CANCELLED,
            errorMessage = "Post cancelled by user"
        )
        val updateRes = updatePost(cancelled)
        return if (updateRes is AppResult.Success) AppResult.Success(true) else AppResult.Error((updateRes as AppResult.Error).error)
    }

    override suspend fun delete(id: String): AppResult<Boolean> {
        val workspaceId = sessionManager.currentWorkspaceId
        return try {
            val response = apiService.deletePost(workspaceId, id)
            if (response.isSuccessful) {
                _postsFlow.value = _postsFlow.value.filterNot { it.id == id }
                AppResult.Success(true)
            } else {
                AppResult.Error(
                    AgentError(
                        code = "HTTP_${response.code()}",
                        message = "Failed to delete post (HTTP ${response.code()})"
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error deleting post.",
                    cause = e
                )
            )
        }
    }

    override suspend fun saveExecutionResult(
        postId: String,
        platformResult: PlatformPublishResult
    ): AppResult<SocialPost> {
        val workspaceId = sessionManager.currentWorkspaceId
        val req = platformResult.toSaveRequest()
        return try {
            val response = apiService.savePublishResult(workspaceId, postId, req)
            if (response.isSuccessful) {
                // Refresh post to get consolidated publish results
                val refreshed = getById(postId)
                if (refreshed is AppResult.Success && refreshed.data != null) {
                    _postsFlow.value = _postsFlow.value.map { if (it.id == postId) refreshed.data else it }
                    AppResult.Success(refreshed.data)
                } else {
                    AppResult.Error(AgentError("REFRESH_FAILED", "Publish result recorded but post refresh failed."))
                }
            } else {
                AppResult.Error(
                    AgentError(
                        code = "HTTP_${response.code()}",
                        message = "Failed to record publish result (HTTP ${response.code()})"
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error recording publish result.",
                    cause = e
                )
            )
        }
    }

    override fun getAllAsFlow(): Flow<List<SocialPost>> = _postsFlow.asStateFlow()
}
