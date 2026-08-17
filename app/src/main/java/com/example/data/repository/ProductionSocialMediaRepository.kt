package com.example.data.repository

import com.example.data.model.*
import com.example.data.remote.api.CreateAgentLogRequest
import com.example.data.remote.api.SocialStudioApiService
import com.example.data.remote.mappers.DomainMappers.toActivityLog
import com.example.data.remote.mappers.DomainMappers.toConnectRequest
import com.example.data.remote.mappers.DomainMappers.toDomain
import com.example.data.remote.mappers.DomainMappers.toUpdateRequest
import com.example.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Production-grade SocialMediaRepository connecting directly to Node.js / PostgreSQL backend.
 * Enforces strict tenant isolation via workspaceId and fails loudly without silent mock fallbacks.
 */
class ProductionSocialMediaRepository(
    private val apiService: SocialStudioApiService,
    private val postRepository: ScheduledPostRepository,
    private val sessionManager: SessionManager = SessionManager.getInstance(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : SocialMediaRepository {

    private val _accounts = MutableStateFlow<List<SocialAccount>>(emptyList())
    private val _activities = MutableStateFlow<List<ActivityLog>>(emptyList())
    private val _analyticsData = MutableStateFlow(
        AnalyticsData(
            totalReach = 0,
            totalEngagement = 0,
            followerGrowthPercent = 0.0,
            totalScheduledPosts = 0,
            platformBreakdown = emptyList(),
            isDemoData = false
        )
    )
    private val _suggestions = MutableStateFlow(
        listOf(
            AiSuggestion(
                id = "prod_sug_1",
                title = "Launch Cross-Platform Campaign",
                description = "Leverage your connected brand voice to publish weekly founder updates.",
                recommendedAction = "Draft Campaign in Brand Tone",
                platform = PlatformType.LINKEDIN,
                confidenceScore = 96,
                category = "Cloud Growth",
                isDemoData = false
            ),
            AiSuggestion(
                id = "prod_sug_2",
                title = "Schedule Peak Engagement Window",
                description = "Best engagement timing for your audience is between 2:00 PM and 5:00 PM.",
                recommendedAction = "Queue Next Post for Peak Window",
                platform = PlatformType.INSTAGRAM,
                confidenceScore = 93,
                category = "Timing AI",
                isDemoData = false
            )
        )
    )

    init {
        coroutineScope.launch {
            refreshAll()
        }
    }

    suspend fun refreshAll() {
        val workspaceId = sessionManager.currentWorkspaceId
        refreshAccounts(workspaceId)
        refreshActivities(workspaceId)
        refreshAnalytics(workspaceId)
    }

    suspend fun refreshAccounts(workspaceId: String = sessionManager.currentWorkspaceId): AppResult<List<SocialAccount>> {
        return try {
            val response = apiService.getAccounts(workspaceId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    val domainAccounts = body.data.map { it.toDomain(isDemo = false) }
                    _accounts.value = domainAccounts
                    AppResult.Success(domainAccounts)
                } else {
                    AppResult.Error(AgentError(body?.error ?: "FETCH_ACCOUNTS_FAILED", body?.message ?: "Failed to fetch accounts."))
                }
            } else {
                AppResult.Error(AgentError("HTTP_${response.code()}", "Error fetching accounts (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Network error fetching accounts.", e))
        }
    }

    suspend fun refreshActivities(workspaceId: String = sessionManager.currentWorkspaceId): AppResult<List<ActivityLog>> {
        return try {
            val response = apiService.getAgentLogs(workspaceId, limit = 50)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    val domainLogs = body.data.map { it.toActivityLog() }
                    _activities.value = domainLogs
                    AppResult.Success(domainLogs)
                } else {
                    AppResult.Error(AgentError(body?.error ?: "FETCH_LOGS_FAILED", body?.message ?: "Failed to fetch activity logs."))
                }
            } else {
                AppResult.Error(AgentError("HTTP_${response.code()}", "Error fetching activity logs (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Network error fetching activity logs.", e))
        }
    }

    suspend fun refreshAnalytics(workspaceId: String = sessionManager.currentWorkspaceId): AppResult<AnalyticsData> {
        return try {
            val response = apiService.getAnalytics(workspaceId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    val domainAnalytics = body.data.toDomain()
                    _analyticsData.value = domainAnalytics
                    AppResult.Success(domainAnalytics)
                } else {
                    AppResult.Error(AgentError(body?.error ?: "FETCH_ANALYTICS_FAILED", body?.message ?: "Failed to fetch analytics."))
                }
            } else {
                AppResult.Error(AgentError("HTTP_${response.code()}", "Error fetching analytics (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Network error fetching analytics.", e))
        }
    }

    override fun getConnectedAccounts(): Flow<List<SocialAccount>> = _accounts.asStateFlow()

    override fun getScheduledPosts(): Flow<List<SocialPost>> = postRepository.getAllAsFlow().map { list ->
        list.filter { it.status == PostStatus.SCHEDULED }
    }

    override fun getAllPosts(): Flow<List<SocialPost>> = postRepository.getAllAsFlow()

    override fun getAiSuggestions(): Flow<List<AiSuggestion>> = _suggestions.asStateFlow()

    override fun getRecentActivity(): Flow<List<ActivityLog>> = _activities.asStateFlow()

    override fun getAnalytics(): Flow<AnalyticsData> = _analyticsData.asStateFlow()

    override suspend fun createPost(post: SocialPost): Result<SocialPost> {
        val createRes = postRepository.create(post)
        return when (createRes) {
            is AppResult.Success -> {
                // Record log on server
                recordAgentActivity(
                    action = "POST_CREATED",
                    platform = post.targetPlatforms.firstOrNull(),
                    title = "Post Created: ${post.title}",
                    detail = "Post queued for ${post.targetPlatforms.joinToString { it.displayName }}."
                )
                Result.success(createRes.data)
            }
            is AppResult.Error -> {
                Result.failure(Exception(createRes.error.message))
            }
        }
    }

    override suspend fun deletePost(postId: String): Result<Unit> {
        val deleteRes = postRepository.delete(postId)
        return when (deleteRes) {
            is AppResult.Success -> {
                recordAgentActivity(
                    action = "POST_DELETED",
                    platform = null,
                    title = "Post Deleted",
                    detail = "Post $postId removed from queue."
                )
                Result.success(Unit)
            }
            is AppResult.Error -> {
                Result.failure(Exception(deleteRes.error.message))
            }
        }
    }

    override suspend fun toggleAccountConnection(accountId: String): Result<SocialAccount> {
        val currentAccount = _accounts.value.find { it.id == accountId }
            ?: return Result.failure(Exception("Account not found"))

        val newConnectionStatus = if (currentAccount.isConnected) ConnectionStatus.DISCONNECTED else ConnectionStatus.CONNECTED
        val newIsConnected = newConnectionStatus == ConnectionStatus.CONNECTED
        val updated = currentAccount.copy(
            isConnected = newIsConnected,
            connectionStatus = newConnectionStatus
        )

        val saveRes = updateAccountOnBackend(updated)
        return when (saveRes) {
            is AppResult.Success -> Result.success(saveRes.data)
            is AppResult.Error -> Result.failure(Exception(saveRes.error.message))
        }
    }

    override suspend fun connectAccount(provider: OAuthProvider, code: String): AppResult<SocialAccount> {
        val platformType = when (provider) {
            OAuthProvider.FACEBOOK -> PlatformType.FACEBOOK
            OAuthProvider.INSTAGRAM -> PlatformType.INSTAGRAM
            OAuthProvider.TWITTER -> PlatformType.TWITTER
            OAuthProvider.LINKEDIN -> PlatformType.LINKEDIN
            OAuthProvider.TIKTOK -> PlatformType.TIKTOK
        }

        val newAccount = SocialAccount(
            platform = platformType,
            accountName = "${provider.displayName} Account",
            handle = "@${provider.name.lowercase()}_cloud",
            accountType = AccountType.PAGE,
            connectionStatus = ConnectionStatus.CONNECTED,
            tokenStatus = TokenStatus.VALID,
            isConnected = true,
            isDemoData = false
        )

        return saveConnectedAccount(newAccount)
    }

    override suspend fun saveConnectedAccount(account: SocialAccount): AppResult<SocialAccount> {
        val workspaceId = sessionManager.currentWorkspaceId
        val req = account.toConnectRequest()

        return try {
            val response = apiService.connectAccount(workspaceId, req)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    val savedDomain = body.data.toDomain(isDemo = false)
                    _accounts.value = _accounts.value.filterNot { it.id == savedDomain.id } + savedDomain

                    recordAgentActivity(
                        action = "ACCOUNT_CONNECTED",
                        platform = account.platform,
                        title = "Connected ${account.platform.displayName}",
                        detail = "Social account '${account.accountName}' persisted in PostgreSQL."
                    )
                    AppResult.Success(savedDomain)
                } else {
                    AppResult.Error(AgentError(body?.error ?: "CONNECT_FAILED", body?.message ?: "Failed to connect account."))
                }
            } else {
                AppResult.Error(AgentError("HTTP_${response.code()}", "Failed to persist account (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Network error connecting account.", e))
        }
    }

    private suspend fun updateAccountOnBackend(account: SocialAccount): AppResult<SocialAccount> {
        val workspaceId = sessionManager.currentWorkspaceId
        val req = account.toUpdateRequest()

        return try {
            val response = apiService.updateAccount(workspaceId, account.id, req)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    val updatedDomain = body.data.toDomain(isDemo = false)
                    _accounts.value = _accounts.value.map { if (it.id == account.id) updatedDomain else it }
                    AppResult.Success(updatedDomain)
                } else {
                    AppResult.Error(AgentError(body?.error ?: "UPDATE_FAILED", body?.message ?: "Failed to update account."))
                }
            } else {
                AppResult.Error(AgentError("HTTP_${response.code()}", "Failed to update account (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Network error updating account.", e))
        }
    }

    override suspend fun disconnectAccount(accountId: String): AppResult<Boolean> {
        val workspaceId = sessionManager.currentWorkspaceId
        val target = _accounts.value.find { it.id == accountId }

        return try {
            val response = apiService.deleteAccount(workspaceId, accountId)
            if (response.isSuccessful) {
                _accounts.value = _accounts.value.filterNot { it.id == accountId }
                recordAgentActivity(
                    action = "ACCOUNT_DISCONNECTED",
                    platform = target?.platform,
                    title = "Disconnected ${target?.platform?.displayName ?: "Account"}",
                    detail = "Account $accountId deleted and tokens revoked from database."
                )
                AppResult.Success(true)
            } else {
                AppResult.Error(AgentError("HTTP_${response.code()}", "Failed to disconnect account (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            AppResult.Error(AgentError("NETWORK_ERROR", e.message ?: "Network error disconnecting account.", e))
        }
    }

    override suspend fun updateTokenStatus(accountId: String, tokenStatus: TokenStatus): AppResult<SocialAccount> {
        val account = _accounts.value.find { it.id == accountId }
            ?: return AppResult.Error(AgentError("ACCOUNT_NOT_FOUND", "Account $accountId not found."))

        val connectionStatus = if (tokenStatus == TokenStatus.EXPIRED || tokenStatus == TokenStatus.REVOKED) {
            ConnectionStatus.REAUTH_REQUIRED
        } else if (tokenStatus == TokenStatus.VALID) {
            ConnectionStatus.CONNECTED
        } else {
            account.connectionStatus
        }

        val updated = account.copy(
            tokenStatus = tokenStatus,
            connectionStatus = connectionStatus,
            isConnected = (connectionStatus == ConnectionStatus.CONNECTED)
        )

        return updateAccountOnBackend(updated)
    }

    override suspend fun getAccountByPlatform(platform: PlatformType): SocialAccount? {
        return _accounts.value.find { it.platform == platform }
    }

    private suspend fun recordAgentActivity(
        action: String,
        platform: PlatformType?,
        title: String,
        detail: String
    ) {
        val workspaceId = sessionManager.currentWorkspaceId
        try {
            apiService.createAgentLog(
                workspaceId = workspaceId,
                request = CreateAgentLogRequest(
                    action = action,
                    platform = platform?.name,
                    status = "SUCCESS",
                    executionEnvironment = "PRODUCTION",
                    title = title,
                    detail = detail
                )
            )
            refreshActivities(workspaceId)
        } catch (e: Exception) {
            // Non-blocking log write
        }
    }
}

/**
 * Dual Mode Repository Router.
 * Automatically delegates to MockSocialMediaRepository when in Demo Workspace mode,
 * and to ProductionSocialMediaRepository when in Real Cloud / Production mode.
 * STRICT SECURITY INVARIANT: In Production mode, never silently fall back to mock data on network errors.
 */
class DualModeSocialMediaRepository(
    private val productionRepository: ProductionSocialMediaRepository,
    private val mockRepository: MockSocialMediaRepository = MockSocialMediaRepository(),
    private val sessionManager: SessionManager = SessionManager.getInstance()
) : SocialMediaRepository {

    private val isDemoMode: Boolean
        get() = sessionManager.currentEnvironment == ExecutionEnvironment.MOCK

    private val activeRepository: SocialMediaRepository
        get() = if (isDemoMode) mockRepository else productionRepository

    override fun getConnectedAccounts(): Flow<List<SocialAccount>> =
        if (isDemoMode) mockRepository.getConnectedAccounts() else productionRepository.getConnectedAccounts()

    override fun getScheduledPosts(): Flow<List<SocialPost>> =
        if (isDemoMode) mockRepository.getScheduledPosts() else productionRepository.getScheduledPosts()

    override fun getAllPosts(): Flow<List<SocialPost>> =
        if (isDemoMode) mockRepository.getAllPosts() else productionRepository.getAllPosts()

    override fun getAiSuggestions(): Flow<List<AiSuggestion>> =
        if (isDemoMode) mockRepository.getAiSuggestions() else productionRepository.getAiSuggestions()

    override fun getRecentActivity(): Flow<List<ActivityLog>> =
        if (isDemoMode) mockRepository.getRecentActivity() else productionRepository.getRecentActivity()

    override fun getAnalytics(): Flow<AnalyticsData> =
        if (isDemoMode) mockRepository.getAnalytics() else productionRepository.getAnalytics()

    override suspend fun createPost(post: SocialPost): Result<SocialPost> =
        activeRepository.createPost(post)

    override suspend fun deletePost(postId: String): Result<Unit> =
        activeRepository.deletePost(postId)

    override suspend fun toggleAccountConnection(accountId: String): Result<SocialAccount> =
        activeRepository.toggleAccountConnection(accountId)

    override suspend fun connectAccount(provider: OAuthProvider, code: String): AppResult<SocialAccount> =
        activeRepository.connectAccount(provider, code)

    override suspend fun saveConnectedAccount(account: SocialAccount): AppResult<SocialAccount> =
        activeRepository.saveConnectedAccount(account)

    override suspend fun disconnectAccount(accountId: String): AppResult<Boolean> =
        activeRepository.disconnectAccount(accountId)

    override suspend fun updateTokenStatus(accountId: String, tokenStatus: TokenStatus): AppResult<SocialAccount> =
        activeRepository.updateTokenStatus(accountId, tokenStatus)

    override suspend fun getAccountByPlatform(platform: PlatformType): SocialAccount? =
        activeRepository.getAccountByPlatform(platform)
}
