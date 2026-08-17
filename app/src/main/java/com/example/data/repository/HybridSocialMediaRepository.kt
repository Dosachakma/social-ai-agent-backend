package com.example.data.repository

import com.example.data.model.*
import com.example.data.remote.session.WorkspaceSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class HybridSocialMediaRepository(
    private val remoteRepo: RemoteSocialMediaRepository = RemoteSocialMediaRepository(),
    private val mockRepo: MockSocialMediaRepository = MockSocialMediaRepository()
) : SocialMediaRepository {

    private fun getActiveRepo(): SocialMediaRepository {
        return if (WorkspaceSessionManager.isDemoMode()) mockRepo else remoteRepo
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getConnectedAccounts(): Flow<List<SocialAccount>> {
        return WorkspaceSessionManager.sessionState.flatMapLatest { state ->
            if (state.isDemoMode) mockRepo.getConnectedAccounts() else remoteRepo.getConnectedAccounts()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getScheduledPosts(): Flow<List<SocialPost>> {
        return WorkspaceSessionManager.sessionState.flatMapLatest { state ->
            if (state.isDemoMode) mockRepo.getScheduledPosts() else remoteRepo.getScheduledPosts()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAllPosts(): Flow<List<SocialPost>> {
        return WorkspaceSessionManager.sessionState.flatMapLatest { state ->
            if (state.isDemoMode) mockRepo.getAllPosts() else remoteRepo.getAllPosts()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAiSuggestions(): Flow<List<AiSuggestion>> {
        return WorkspaceSessionManager.sessionState.flatMapLatest { state ->
            if (state.isDemoMode) mockRepo.getAiSuggestions() else remoteRepo.getAiSuggestions()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getRecentActivity(): Flow<List<ActivityLog>> {
        return WorkspaceSessionManager.sessionState.flatMapLatest { state ->
            if (state.isDemoMode) mockRepo.getRecentActivity() else remoteRepo.getRecentActivity()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAnalytics(): Flow<AnalyticsData> {
        return WorkspaceSessionManager.sessionState.flatMapLatest { state ->
            if (state.isDemoMode) mockRepo.getAnalytics() else remoteRepo.getAnalytics()
        }
    }

    override suspend fun createPost(post: SocialPost): Result<SocialPost> {
        return getActiveRepo().createPost(post)
    }

    override suspend fun deletePost(postId: String): Result<Unit> {
        return getActiveRepo().deletePost(postId)
    }

    override suspend fun toggleAccountConnection(accountId: String): Result<SocialAccount> {
        return getActiveRepo().toggleAccountConnection(accountId)
    }

    override suspend fun connectAccount(provider: OAuthProvider, code: String): AppResult<SocialAccount> {
        return getActiveRepo().connectAccount(provider, code)
    }

    override suspend fun saveConnectedAccount(account: SocialAccount): AppResult<SocialAccount> {
        return getActiveRepo().saveConnectedAccount(account)
    }

    override suspend fun disconnectAccount(accountId: String): AppResult<Boolean> {
        return getActiveRepo().disconnectAccount(accountId)
    }

    override suspend fun updateTokenStatus(accountId: String, tokenStatus: TokenStatus): AppResult<SocialAccount> {
        return getActiveRepo().updateTokenStatus(accountId, tokenStatus)
    }

    override suspend fun getAccountByPlatform(platform: PlatformType): SocialAccount? {
        return getActiveRepo().getAccountByPlatform(platform)
    }
}
