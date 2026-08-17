package com.example.data.repository

import com.example.data.model.*
import com.example.data.remote.session.WorkspaceSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class HybridScheduledPostRepository(
    private val remoteRepo: RemoteScheduledPostRepository = RemoteScheduledPostRepository(),
    private val mockRepo: MockScheduledPostRepository = MockScheduledPostRepository()
) : ScheduledPostRepository {

    private fun getActiveRepo(): ScheduledPostRepository {
        return if (WorkspaceSessionManager.isDemoMode()) mockRepo else remoteRepo
    }

    override suspend fun create(post: SocialPost): AppResult<SocialPost> {
        return getActiveRepo().create(post)
    }

    override suspend fun getById(id: String): AppResult<SocialPost?> {
        return getActiveRepo().getById(id)
    }

    override suspend fun getUpcoming(): AppResult<List<SocialPost>> {
        return getActiveRepo().getUpcoming()
    }

    override suspend fun getForDate(dateIso: String): AppResult<List<SocialPost>> {
        return getActiveRepo().getForDate(dateIso)
    }

    override suspend fun updateStatus(
        id: String,
        status: PostStatus,
        approvalState: ActionApprovalState
    ): AppResult<SocialPost> {
        return getActiveRepo().updateStatus(id, status, approvalState)
    }

    override suspend fun updatePost(post: SocialPost): AppResult<SocialPost> {
        return getActiveRepo().updatePost(post)
    }

    override suspend fun cancel(id: String): AppResult<Boolean> {
        return getActiveRepo().cancel(id)
    }

    override suspend fun delete(id: String): AppResult<Boolean> {
        return getActiveRepo().delete(id)
    }

    override suspend fun saveExecutionResult(
        postId: String,
        platformResult: PlatformPublishResult
    ): AppResult<SocialPost> {
        return getActiveRepo().saveExecutionResult(postId, platformResult)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAllAsFlow(): Flow<List<SocialPost>> {
        return WorkspaceSessionManager.sessionState.flatMapLatest { state ->
            if (state.isDemoMode) mockRepo.getAllAsFlow() else remoteRepo.getAllAsFlow()
        }
    }
}
