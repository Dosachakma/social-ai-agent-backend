package com.example.data.repository

import com.example.data.model.*
import com.example.data.remote.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

class MockSocialMediaRepository(
    private val tokenStore: ServerTokenStore = MockServerTokenStore(),
    private val oauthService: BackendOAuthService = MockOAuthService(tokenStore)
) : SocialMediaRepository {

    private val accounts = MutableStateFlow(
        listOf(
            SocialAccount(
                id = "acc_1",
                platform = PlatformType.FACEBOOK,
                accountName = "TechPulse Media",
                handle = "@techpulse_official",
                accountType = AccountType.PAGE,
                connectionStatus = ConnectionStatus.CONNECTED,
                tokenStatus = TokenStatus.VALID,
                availableCapabilities = listOf(
                    SocialCapability.CREATE_POST,
                    SocialCapability.PUBLISH_POST,
                    SocialCapability.READ_COMMENTS,
                    SocialCapability.REPLY_COMMENT,
                    SocialCapability.READ_MESSAGES,
                    SocialCapability.SEND_MESSAGE,
                    SocialCapability.READ_ANALYTICS,
                    SocialCapability.MEDIA_UPLOAD
                ),
                followerCount = 42800,
                postsTodayCount = 2,
                isConnected = true,
                lastSyncedTime = "2 mins ago"
            ),
            SocialAccount(
                id = "acc_2",
                platform = PlatformType.INSTAGRAM,
                accountName = "TechPulse Visuals",
                handle = "@techpulse.app",
                accountType = AccountType.BUSINESS,
                connectionStatus = ConnectionStatus.CONNECTED,
                tokenStatus = TokenStatus.VALID,
                availableCapabilities = listOf(
                    SocialCapability.CREATE_POST,
                    SocialCapability.PUBLISH_POST,
                    SocialCapability.READ_COMMENTS,
                    SocialCapability.REPLY_COMMENT,
                    SocialCapability.READ_ANALYTICS,
                    SocialCapability.MEDIA_UPLOAD,
                    SocialCapability.STORY_PUBLISH,
                    SocialCapability.REEL_PUBLISH
                ),
                followerCount = 89200,
                postsTodayCount = 3,
                isConnected = true,
                lastSyncedTime = "Just now"
            ),
            SocialAccount(
                id = "acc_3",
                platform = PlatformType.TWITTER,
                accountName = "TechPulse X",
                handle = "@techpulse_x",
                accountType = AccountType.PERSONAL,
                connectionStatus = ConnectionStatus.CONNECTED,
                tokenStatus = TokenStatus.VALID,
                availableCapabilities = listOf(
                    SocialCapability.CREATE_POST,
                    SocialCapability.PUBLISH_POST,
                    SocialCapability.READ_ANALYTICS
                ),
                followerCount = 31500,
                postsTodayCount = 4,
                isConnected = true,
                lastSyncedTime = "5 mins ago"
            ),
            SocialAccount(
                id = "acc_4",
                platform = PlatformType.LINKEDIN,
                accountName = "TechPulse Inc.",
                handle = "company/techpulse",
                accountType = AccountType.BUSINESS,
                connectionStatus = ConnectionStatus.CONNECTED,
                tokenStatus = TokenStatus.VALID,
                availableCapabilities = listOf(
                    SocialCapability.CREATE_POST,
                    SocialCapability.PUBLISH_POST,
                    SocialCapability.READ_ANALYTICS
                ),
                followerCount = 18400,
                postsTodayCount = 1,
                isConnected = true,
                lastSyncedTime = "10 mins ago"
            ),
            SocialAccount(
                id = "acc_5",
                platform = PlatformType.TIKTOK,
                accountName = "TechPulse Clips",
                handle = "@techpulse_tok",
                accountType = AccountType.CREATOR,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                tokenStatus = TokenStatus.UNKNOWN,
                availableCapabilities = listOf(
                    SocialCapability.CREATE_POST,
                    SocialCapability.PUBLISH_POST,
                    SocialCapability.READ_ANALYTICS
                ),
                followerCount = 64100,
                postsTodayCount = 0,
                isConnected = false,
                lastSyncedTime = "Disconnected"
            )
        )
    )

    private val posts = MutableStateFlow(
        listOf(
            SocialPost(
                id = "post_1",
                title = "AI Automation Trends 2026",
                content = "🚀 Autonomous AI agents are reshaping how modern teams scale social presence! Here are 3 key takeaways for founders this quarter. What's your top strategy?",
                targetPlatforms = listOf(PlatformType.TWITTER, PlatformType.LINKEDIN),
                scheduledTime = "Today at 2:30 PM",
                status = PostStatus.SCHEDULED,
                isAiGenerated = true,
                engagementScore = 95
            ),
            SocialPost(
                id = "post_2",
                title = "Behind the Scenes Reel",
                content = "✨ High energy product demo showing off real-time sentiment analysis and auto-reply filters. Swipe to see the magic in action!",
                targetPlatforms = listOf(PlatformType.INSTAGRAM, PlatformType.TIKTOK),
                scheduledTime = "Today at 6:00 PM",
                status = PostStatus.SCHEDULED,
                isAiGenerated = true,
                engagementScore = 91
            ),
            SocialPost(
                id = "post_3",
                title = "Weekly Tech Digest #42",
                content = "Read our comprehensive breakdown of multi-agent architectures and local AI models for privacy-conscious applications.",
                targetPlatforms = listOf(PlatformType.FACEBOOK, PlatformType.LINKEDIN),
                scheduledTime = "Tomorrow at 9:00 AM",
                status = PostStatus.SCHEDULED,
                isAiGenerated = false,
                engagementScore = 84
            ),
            SocialPost(
                id = "post_4",
                title = "Product Launch Teaser Draft",
                content = "Something huge is dropping next Tuesday. AI copilot for your entire social workflow. Stay tuned!",
                targetPlatforms = listOf(PlatformType.TWITTER, PlatformType.INSTAGRAM, PlatformType.FACEBOOK),
                scheduledTime = "Draft",
                status = PostStatus.DRAFT,
                isAiGenerated = true,
                engagementScore = 89
            ),
            SocialPost(
                id = "post_5",
                title = "Customer Story Spotlight",
                content = "How NovaLabs boosted cross-platform reach by 320% in 30 days using autonomous AI scheduling.",
                targetPlatforms = listOf(PlatformType.LINKEDIN),
                scheduledTime = "Yesterday at 4:15 PM",
                status = PostStatus.PUBLISHED,
                isAiGenerated = true,
                engagementScore = 97,
                engagementCount = 1420
            )
        )
    )

    private val suggestions = MutableStateFlow(
        listOf(
            AiSuggestion(
                id = "sug_1",
                title = "Create a post about your product",
                description = "Feature highlight post on new automated workflows will boost product trial conversions.",
                recommendedAction = "Draft Product Feature Spotlight",
                platform = PlatformType.LINKEDIN,
                confidenceScore = 98,
                category = "Product Growth"
            ),
            AiSuggestion(
                id = "sug_2",
                title = "Your engagement is higher this week",
                description = "Total impressions jumped +24.8% on Instagram & X with 8.4k new audience interactions.",
                recommendedAction = "Schedule double post window today at 6:00 PM",
                platform = PlatformType.INSTAGRAM,
                confidenceScore = 95,
                category = "Engagement AI"
            ),
            AiSuggestion(
                id = "sug_3",
                title = "You have 8 unanswered comments",
                description = "8 incoming high-intent questions across Facebook and LinkedIn need your review.",
                recommendedAction = "Launch Smart AI Auto-Reply",
                platform = PlatformType.FACEBOOK,
                confidenceScore = 99,
                category = "Community"
            )
        )
    )

    private val activities = MutableStateFlow(
        listOf(
            ActivityLog(
                id = "act_1",
                title = "Autonomous Post Queued",
                detail = "AI Agent generated and scheduled 'AI Automation Trends 2026' for Twitter & LinkedIn.",
                timestamp = "12 mins ago",
                platform = PlatformType.TWITTER,
                actionType = "Auto Queue"
            ),
            ActivityLog(
                id = "act_2",
                title = "Spam Comment Auto-Hidden",
                detail = "Blocked suspicious crypto phishing link on Instagram page.",
                timestamp = "45 mins ago",
                platform = PlatformType.INSTAGRAM,
                actionType = "Security Shield"
            ),
            ActivityLog(
                id = "act_3",
                title = "Smart Reply Sent",
                detail = "AI Agent answered pricing question on LinkedIn message inbox.",
                timestamp = "2 hours ago",
                platform = PlatformType.LINKEDIN,
                actionType = "Auto Reply"
            ),
            ActivityLog(
                id = "act_4",
                title = "Analytics Insight Generated",
                detail = "Weekly growth report calculated: Reach up 24.8% across all channels.",
                timestamp = "4 hours ago",
                platform = null,
                actionType = "Report"
            )
        )
    )

    private val analyticsData = MutableStateFlow(
        AnalyticsData(
            totalReach = 246500,
            totalEngagement = 38400,
            followerGrowthPercent = 21.6,
            totalScheduledPosts = 8,
            platformBreakdown = listOf(
                PlatformMetric(PlatformType.INSTAGRAM, reach = 98000, engagementRate = 6.4, followersGained = 1240),
                PlatformMetric(PlatformType.TIKTOK, reach = 64000, engagementRate = 8.1, followersGained = 2100),
                PlatformMetric(PlatformType.FACEBOOK, reach = 42000, engagementRate = 3.8, followersGained = 480),
                PlatformMetric(PlatformType.TWITTER, reach = 28500, engagementRate = 5.2, followersGained = 890),
                PlatformMetric(PlatformType.LINKEDIN, reach = 14000, engagementRate = 7.3, followersGained = 610)
            )
        )
    )

    override fun getConnectedAccounts(): Flow<List<SocialAccount>> = accounts

    override fun getScheduledPosts(): Flow<List<SocialPost>> = posts.map { list ->
        list.filter { it.status == PostStatus.SCHEDULED }
    }

    override fun getAllPosts(): Flow<List<SocialPost>> = posts

    override fun getAiSuggestions(): Flow<List<AiSuggestion>> = suggestions

    override fun getRecentActivity(): Flow<List<ActivityLog>> = activities

    override fun getAnalytics(): Flow<AnalyticsData> = analyticsData

    override suspend fun createPost(post: SocialPost): Result<SocialPost> {
        val current = posts.value.toMutableList()
        current.add(0, post)
        posts.value = current
        
        // Log activity
        val newLog = ActivityLog(
            title = "New Post Created",
            detail = "Post '${post.title}' added to queue for ${post.targetPlatforms.joinToString { it.displayName }}.",
            timestamp = "Just now",
            platform = post.targetPlatforms.firstOrNull(),
            actionType = "User Action"
        )
        activities.value = listOf(newLog) + activities.value
        return Result.success(post)
    }

    override suspend fun deletePost(postId: String): Result<Unit> {
        val updated = posts.value.filterNot { it.id == postId }
        posts.value = updated
        return Result.success(Unit)
    }

    override suspend fun toggleAccountConnection(accountId: String): Result<SocialAccount> {
        val currentList = accounts.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == accountId }
        if (index != -1) {
            val item = currentList[index]
            val updatedIsConnected = !item.isConnected
            val updatedStatus = if (updatedIsConnected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
            val updatedToken = if (updatedIsConnected) TokenStatus.VALID else TokenStatus.UNKNOWN
            val updatedItem = item.copy(
                isConnected = updatedIsConnected,
                connectionStatus = updatedStatus,
                tokenStatus = updatedToken,
                lastSyncedTime = if (updatedIsConnected) "Just now" else "Disconnected"
            )
            currentList[index] = updatedItem
            accounts.value = currentList
            return Result.success(updatedItem)
        }
        return Result.failure(Exception("Account not found"))
    }

    override suspend fun connectAccount(provider: OAuthProvider, code: String): AppResult<SocialAccount> {
        val sessionRes = oauthService.createOAuthSession(provider)
        if (sessionRes is AppResult.Error) return AppResult.Error(sessionRes.error)
        val session = (sessionRes as AppResult.Success).data

        val exchangeRes = oauthService.exchangeAuthorizationCode(session, code, session.state, session.redirectUri)
        if (exchangeRes is AppResult.Error) return AppResult.Error(exchangeRes.error)

        val success = (exchangeRes as AppResult.Success).data
        return saveConnectedAccount(success.account)
    }

    override suspend fun saveConnectedAccount(account: SocialAccount): AppResult<SocialAccount> {
        val currentList = accounts.value.toMutableList()
        val indexByPlatformUser = currentList.indexOfFirst {
            it.platform == account.platform && (it.platformUserId == account.platformUserId || it.platformUserId.isNullOrBlank())
        }
        val indexToReplace = if (indexByPlatformUser != -1) {
            indexByPlatformUser
        } else {
            currentList.indexOfFirst { it.platform == account.platform }
        }

        val finalAccount = if (indexToReplace != -1) {
            account.copy(id = currentList[indexToReplace].id)
        } else {
            account
        }

        if (indexToReplace != -1) {
            currentList[indexToReplace] = finalAccount
        } else {
            currentList.add(finalAccount)
        }
        accounts.value = currentList

        // Audit Log
        val environmentLabel = if (finalAccount.isDemoData) "MOCK MODE" else "LIVE OAUTH"
        val newLog = ActivityLog(
            title = "OAuth Account Connected",
            detail = "Connected ${finalAccount.platform.displayName} in $environmentLabel for account '${finalAccount.accountName}'.",
            timestamp = "Just now",
            platform = finalAccount.platform,
            actionType = "OAuth Connect"
        )
        activities.value = listOf(newLog) + activities.value

        return AppResult.Success(finalAccount)
    }

    override suspend fun disconnectAccount(accountId: String): AppResult<Boolean> {
        val currentList = accounts.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == accountId }
        if (index == -1) {
            return AppResult.Error(AgentError("ACCOUNT_NOT_FOUND", "Account $accountId not found."))
        }

        val account = currentList[index]
        val provider = when (account.platform) {
            PlatformType.FACEBOOK -> OAuthProvider.FACEBOOK
            PlatformType.INSTAGRAM -> OAuthProvider.INSTAGRAM
            PlatformType.TWITTER -> OAuthProvider.TWITTER
            PlatformType.LINKEDIN -> OAuthProvider.LINKEDIN
            PlatformType.TIKTOK -> OAuthProvider.TIKTOK
        }

        // Revoke token via OAuth abstraction
        val tokenRes = tokenStore.getToken("workspace_user_1", provider)
        if (tokenRes is AppResult.Success && tokenRes.data != null) {
            oauthService.revokeToken(provider, tokenRes.data!!)
        }
        tokenStore.clearToken("workspace_user_1", provider)

        // Update account status to DISCONNECTED
        val disconnectedAccount = account.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            tokenStatus = TokenStatus.UNKNOWN,
            isConnected = false,
            lastSyncedTime = "Disconnected"
        )
        currentList[index] = disconnectedAccount
        accounts.value = currentList

        // Pause / fail scheduled posts targeting this platform
        val updatedPosts = posts.value.map { post ->
            if (post.targetPlatforms.contains(account.platform) && post.status == PostStatus.SCHEDULED) {
                post.copy(
                    status = PostStatus.FAILED,
                    approvalState = ActionApprovalState.CANCELLED,
                    errorMessage = "REAUTH_REQUIRED: Targeted platform account was disconnected."
                )
            } else {
                post
            }
        }
        posts.value = updatedPosts

        // Audit Log
        val newLog = ActivityLog(
            title = "Account Disconnected",
            detail = "Disconnected ${account.platform.displayName} account '${account.accountName}' (${account.handle}). Tokens revoked.",
            timestamp = "Just now",
            platform = account.platform,
            actionType = "OAuth Disconnect"
        )
        activities.value = listOf(newLog) + activities.value

        return AppResult.Success(true)
    }

    override suspend fun updateTokenStatus(accountId: String, tokenStatus: TokenStatus): AppResult<SocialAccount> {
        val currentList = accounts.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == accountId }
        if (index == -1) {
            return AppResult.Error(AgentError("ACCOUNT_NOT_FOUND", "Account $accountId not found."))
        }

        val account = currentList[index]
        val connectionStatus = if (tokenStatus == TokenStatus.EXPIRED || tokenStatus == TokenStatus.REVOKED) {
            ConnectionStatus.REAUTH_REQUIRED
        } else if (tokenStatus == TokenStatus.VALID) {
            ConnectionStatus.CONNECTED
        } else {
            account.connectionStatus
        }

        val updatedAccount = account.copy(
            tokenStatus = tokenStatus,
            connectionStatus = connectionStatus,
            isConnected = (connectionStatus == ConnectionStatus.CONNECTED)
        )
        currentList[index] = updatedAccount
        accounts.value = currentList

        if (tokenStatus == TokenStatus.EXPIRED || tokenStatus == TokenStatus.REVOKED) {
            val updatedPosts = posts.value.map { post ->
                if (post.targetPlatforms.contains(account.platform) && post.status == PostStatus.SCHEDULED) {
                    post.copy(
                        status = PostStatus.FAILED,
                        approvalState = ActionApprovalState.FAILED,
                        errorMessage = "REAUTH_REQUIRED: Account token is ${tokenStatus.displayName}."
                    )
                } else {
                    post
                }
            }
            posts.value = updatedPosts
        }

        val newLog = ActivityLog(
            title = "Token Status Updated",
            detail = "Token for ${account.platform.displayName} account '${account.accountName}' set to ${tokenStatus.displayName}.",
            timestamp = "Just now",
            platform = account.platform,
            actionType = "Security Audit"
        )
        activities.value = listOf(newLog) + activities.value

        return AppResult.Success(updatedAccount)
    }

    override suspend fun getAccountByPlatform(platform: PlatformType): SocialAccount? {
        return accounts.value.find { it.platform == platform }
    }
}
