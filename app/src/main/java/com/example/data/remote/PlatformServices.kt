package com.example.data.remote

import com.example.data.model.*
import kotlinx.coroutines.delay

/**
 * Base mock platform implementation. Keeps clear boundary between mock behavior and real API behavior.
 */
abstract class BaseMockPlatformService(
    override val platform: PlatformType
) : SocialPlatformService {

    override val environment: ExecutionEnvironment = ExecutionEnvironment.MOCK

    override suspend fun connect(account: SocialAccount): AppResult<SocialAccount> {
        delay(300)
        return AppResult.Success(account.copy(isConnected = true, lastSyncedTime = "Just now"))
    }

    override suspend fun disconnect(accountId: String): AppResult<Boolean> {
        delay(200)
        return AppResult.Success(true)
    }

    override suspend fun getAccount(accountId: String): AppResult<SocialAccount> {
        delay(200)
        return AppResult.Success(
            SocialAccount(
                id = accountId,
                platform = platform,
                accountName = "Demo ${platform.displayName} Page",
                handle = "@demo_${platform.name.lowercase()}",
                followerCount = 12500,
                isDemoData = true
            )
        )
    }

    override suspend fun createPost(post: SocialPost): AppResult<SocialPost> {
        delay(400)
        return AppResult.Success(
            post.copy(
                status = PostStatus.DRAFT,
                approvalState = ActionApprovalState.PROPOSED,
                isDemoData = true
            )
        )
    }

    override suspend fun publishPost(postId: String): AppResult<SocialPost> {
        delay(600)
        // Rule: Never fake real publication success when in mock mode.
        return AppResult.Success(
            SocialPost(
                id = postId,
                title = "Mock Execution Post",
                content = "Mock execution completed for ${platform.displayName}.",
                targetPlatforms = listOf(platform),
                scheduledTime = "Instant",
                status = PostStatus.PUBLISHED,
                approvalState = ActionApprovalState.SUCCESS,
                isDemoData = true
            )
        )
    }

    override suspend fun schedulePost(post: SocialPost, scheduledTime: String): AppResult<SocialPost> {
        delay(400)
        return AppResult.Success(
            post.copy(
                status = PostStatus.SCHEDULED,
                scheduledTime = scheduledTime,
                approvalState = ActionApprovalState.APPROVED,
                isDemoData = true
            )
        )
    }

    override suspend fun getComments(postId: String): AppResult<List<SocialComment>> {
        delay(300)
        return AppResult.Success(
            listOf(
                SocialComment(postId = postId, authorName = "Alex", authorHandle = "@alex", text = "Awesome content!"),
                SocialComment(postId = postId, authorName = "Sam", authorHandle = "@sam", text = "How can I sign up?")
            )
        )
    }

    override suspend fun replyToComment(commentId: String, replyText: String): AppResult<Boolean> {
        delay(300)
        return AppResult.Success(true)
    }

    override suspend fun deleteComment(commentId: String): AppResult<Boolean> {
        delay(200)
        return AppResult.Success(true)
    }

    override suspend fun getMessages(accountId: String): AppResult<List<SocialMessage>> {
        delay(300)
        return AppResult.Success(
            listOf(
                SocialMessage(accountId = accountId, senderName = "Jordan", text = "Interested in partnership")
            )
        )
    }

    override suspend fun replyToMessage(messageId: String, replyText: String): AppResult<Boolean> {
        delay(300)
        return AppResult.Success(true)
    }

    override suspend fun getAnalytics(accountId: String): AppResult<AnalyticsData> {
        delay(400)
        return AppResult.Success(
            AnalyticsData(
                totalReach = 45000,
                totalEngagement = 3200,
                isDemoData = true
            )
        )
    }
}

class FacebookPlatformService : BaseMockPlatformService(PlatformType.FACEBOOK)
class InstagramPlatformService : BaseMockPlatformService(PlatformType.INSTAGRAM)
class TwitterPlatformService : BaseMockPlatformService(PlatformType.TWITTER)
class LinkedInPlatformService : BaseMockPlatformService(PlatformType.LINKEDIN)
class TikTokPlatformService : BaseMockPlatformService(PlatformType.TIKTOK)

object SocialPlatformRegistry {
    private val services = mapOf(
        PlatformType.FACEBOOK to FacebookPlatformService(),
        PlatformType.INSTAGRAM to InstagramPlatformService(),
        PlatformType.TWITTER to TwitterPlatformService(),
        PlatformType.LINKEDIN to LinkedInPlatformService(),
        PlatformType.TIKTOK to TikTokPlatformService()
    )

    fun getService(platform: PlatformType): SocialPlatformService {
        return services[platform] ?: FacebookPlatformService()
    }
}
