package com.example.data.model

import java.util.UUID

enum class ExecutionEnvironment(val displayName: String) {
    MOCK("Demo Workspace (Interactive)"),
    DEVELOPMENT("Development API"),
    PRODUCTION("Production API"),
    REAL("Real API Execution")
}

enum class ActionApprovalState(val label: String) {
    PROPOSED("Proposed"),
    AWAITING_APPROVAL("Awaiting Approval"),
    APPROVED("Approved"),
    EXECUTING("Executing"),
    SUCCESS("Success"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

data class AgentError(
    val code: String = "UNKNOWN_ERROR",
    val message: String,
    val cause: Throwable? = null
)

sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Error(val error: AgentError) : AppResult<Nothing>

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

enum class PlatformType(val displayName: String) {
    FACEBOOK("Facebook"),
    INSTAGRAM("Instagram"),
    TWITTER("X / Twitter"),
    LINKEDIN("LinkedIn"),
    TIKTOK("TikTok")
}

enum class PostStatus(val label: String) {
    SCHEDULED("Scheduled"),
    DRAFT("Draft"),
    PUBLISHED("Published"),
    FAILED("Failed"),
    GENERATING("AI Generating")
}

enum class AccountType(val displayName: String) {
    PERSONAL("Personal Account"),
    PAGE("Facebook Page"),
    BUSINESS("Business Account"),
    CREATOR("Creator Account")
}

enum class ConnectionStatus(val displayName: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting..."),
    CONNECTED("Connected"),
    EXPIRED("Expired"),
    REAUTH_REQUIRED("Reconnection Required"),
    ERROR("Connection Error")
}

enum class TokenStatus(val displayName: String) {
    VALID("Valid"),
    EXPIRING("Expiring Soon"),
    EXPIRED("Expired"),
    REVOKED("Revoked"),
    UNKNOWN("Unknown")
}

enum class SocialCapability(val displayName: String, val description: String) {
    CREATE_POST("Create Post", "Draft copy and content"),
    PUBLISH_POST("Publish Post", "Directly publish posts"),
    READ_COMMENTS("Read Comments", "Fetch public post comments"),
    REPLY_COMMENT("Reply to Comment", "Post replies to user comments"),
    READ_MESSAGES("Read Messages", "Fetch direct messages"),
    SEND_MESSAGE("Send Message", "Send private direct messages"),
    READ_ANALYTICS("Read Analytics", "View channel reach & metrics"),
    MEDIA_UPLOAD("Media Upload", "Upload images and videos"),
    STORY_PUBLISH("Story Publish", "Publish story media"),
    REEL_PUBLISH("Reel Publish", "Publish reel videos")
}

data class SocialPage(
    val id: String = UUID.randomUUID().toString(),
    val platform: PlatformType = PlatformType.FACEBOOK,
    val platformAccountId: String,
    val name: String,
    val category: String = "Brand / Business",
    val profileImageUrl: String = "",
    val accountType: AccountType = AccountType.PAGE,
    val availableCapabilities: List<SocialCapability> = listOf(
        SocialCapability.CREATE_POST,
        SocialCapability.PUBLISH_POST,
        SocialCapability.READ_COMMENTS,
        SocialCapability.REPLY_COMMENT,
        SocialCapability.READ_ANALYTICS,
        SocialCapability.MEDIA_UPLOAD
    )
)

data class SocialAccount(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "workspace_user_1",
    val workspaceId: String = "ws_default",
    val platform: PlatformType,
    val platformUserId: String = "platform_user_123",
    val accountName: String,
    val handle: String,
    val profileImageUrl: String = "",
    val accountType: AccountType = AccountType.PAGE,
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
    val scopes: List<String> = listOf("public_profile", "pages_manage_posts", "instagram_basic"),
    val connectedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val tokenStatus: TokenStatus = TokenStatus.VALID,
    val availableCapabilities: List<SocialCapability> = listOf(
        SocialCapability.CREATE_POST,
        SocialCapability.PUBLISH_POST,
        SocialCapability.READ_COMMENTS,
        SocialCapability.REPLY_COMMENT,
        SocialCapability.READ_ANALYTICS,
        SocialCapability.MEDIA_UPLOAD
    ),
    val avatarUrl: String = "",
    val followerCount: Int = 0,
    val postsTodayCount: Int = 2,
    val isConnected: Boolean = true,
    val lastSyncedTime: String = "Just now",
    val isDemoData: Boolean = true
) {
    val username: String get() = handle
    val displayName: String get() = accountName
    val isFullyConnected: Boolean
        get() = isConnected && connectionStatus == ConnectionStatus.CONNECTED && tokenStatus == TokenStatus.VALID
}

enum class RecurrenceOption(val label: String) {
    NONE("None"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

data class PlatformPublishResult(
    val id: String = UUID.randomUUID().toString(),
    val platform: PlatformType,
    val status: ActionApprovalState,
    val externalPostId: String? = null,
    val timestamp: String = "Just now",
    val errorMessage: String? = null,
    val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment.MOCK,
    val idempotencyKey: String = ""
)

data class SocialPost(
    val id: String = UUID.randomUUID().toString(),
    val workspaceId: String = "ws_default",
    val title: String,
    val content: String, // Caption / post body
    val targetPlatforms: List<PlatformType>,
    val scheduledTime: String,
    val scheduledAt: String? = null,
    val timezone: String = "America/New_York",
    val repeatOption: RecurrenceOption = RecurrenceOption.NONE,
    val requireApproval: Boolean = true,
    val publishedAt: String? = null,
    val status: PostStatus = PostStatus.DRAFT,
    val mediaUrl: String? = null,
    val media: List<String> = emptyList(),
    val hashtags: String = "",
    val cta: String = "",
    val isAiGenerated: Boolean = true,
    val approvalState: ActionApprovalState = ActionApprovalState.PROPOSED,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val platformPublishResults: List<PlatformPublishResult> = emptyList(),
    val idempotencyKeys: Map<PlatformType, String> = emptyMap(),
    val createdAt: String = "Just now",
    val updatedAt: String = "Just now",
    val engagementScore: Int = 88,
    val engagementCount: Int = 0,
    val isDemoData: Boolean = true
) {
    val platforms: List<PlatformType> get() = targetPlatforms
    val caption: String get() = content
    val aiGenerated: Boolean get() = isAiGenerated
}

data class SocialComment(
    val id: String = UUID.randomUUID().toString(),
    val postId: String,
    val authorName: String,
    val authorHandle: String,
    val text: String,
    val timestamp: String = "Just now",
    val isSpamOrPhishing: Boolean = false,
    val isReplied: Boolean = false
)

data class SocialMessage(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val senderName: String,
    val text: String,
    val timestamp: String = "Just now",
    val isReplied: Boolean = false
)

data class AiSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val recommendedAction: String,
    val platform: PlatformType?,
    val confidenceScore: Int = 94,
    val category: String = "Growth Strategy",
    val isDemoData: Boolean = true
)

data class ActivityLog(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val detail: String,
    val timestamp: String,
    val platform: PlatformType?,
    val actionType: String = "Auto Action",
    val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment.MOCK
)

data class PlatformMetric(
    val platform: PlatformType,
    val reach: Int,
    val engagementRate: Double,
    val followersGained: Int
)

data class AnalyticsData(
    val totalReach: Int = 142800,
    val totalEngagement: Int = 23900,
    val followerGrowthPercent: Double = 18.4,
    val totalScheduledPosts: Int = 12,
    val platformBreakdown: List<PlatformMetric> = emptyList(),
    val isDemoData: Boolean = true
)

enum class SenderType {
    USER, AGENT, SYSTEM
}

enum class AgentAction(val label: String) {
    CREATE_POST("Create Post"),
    GENERATE_IMAGE("Generate Image"),
    SCHEDULE_POST("Schedule Post"),
    PUBLISH_POST("Publish Post"),
    REPLY_COMMENT("Reply to Comment"),
    REPLY_MESSAGE("Reply to Message"),
    ANALYZE_ACCOUNT("Analyze Account")
}

data class GeneratedContentPreview(
    val platform: PlatformType = PlatformType.INSTAGRAM,
    val tone: String = "Professional",
    val title: String = "",
    val content: String = "",
    val mediaUrl: String? = null,
    val actionType: AgentAction = AgentAction.CREATE_POST,
    val scheduledTime: String = "Today at 6:00 PM",
    val approvalState: ActionApprovalState = ActionApprovalState.AWAITING_APPROVAL,
    val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment.MOCK,
    val executionMessage: String? = null
)

enum class AIProviderType(val displayName: String) {
    MOCK("Mock AI Engine"),
    GEMINI("Google Gemini")
}

data class AgentMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: SenderType,
    val text: String,
    val timestamp: String = "Just now",
    val action: AgentAction? = null,
    val contentPreview: GeneratedContentPreview? = null,
    val agentPlan: com.example.data.ai.AgentPlan? = null,
    val quickActionRecommendation: String? = null,
    val isAutonomousAction: Boolean = false,
    val attachedMediaUrl: String? = null,
    val approvalState: ActionApprovalState = ActionApprovalState.PROPOSED,
    val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment.MOCK
)

data class AgentActionLog(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "workspace_user_1",
    val action: AgentAction,
    val platform: PlatformType?,
    val status: ActionApprovalState,
    val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment.MOCK,
    val timestamp: String = "Just now",
    val error: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class QuickActionType(val title: String, val subtitle: String) {
    CREATE_POST("Create Post", "Draft or AI generate copy"),
    GENERATE_IMAGE("Generate Image", "AI Visual creator"),
    SCHEDULE_POST("Schedule Post", "Pick date & platform queue"),
    ASK_AI_AGENT("Ask AI Agent", "Prompt agent assistant")
}

enum class AutonomousLevel(val label: String, val description: String) {
    MANUAL("Manual Mode", "User manually creates and approves all actions"),
    ASSISTED("Copilot Mode", "AI generates drafts & suggestions, requires user tap approval"),
    AUTONOMOUS("Autonomous Mode", "AI agent posts, replies, and manages campaigns independently")
}

data class AiModelConfig(
    val provider: AIProviderType = AIProviderType.MOCK,
    val modelName: String = "gemini-3.5-flash",
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 2048,
    val isApiKeyConfigured: Boolean = false,
    val autonomousLevel: AutonomousLevel = AutonomousLevel.ASSISTED,
    val enablePhishingDetection: Boolean = true,
    val enableAutoCommentReply: Boolean = true,
    val enableVoiceFeatures: Boolean = true,
    val maxPostsPerDay: Int = 5,
    val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment.MOCK
)
