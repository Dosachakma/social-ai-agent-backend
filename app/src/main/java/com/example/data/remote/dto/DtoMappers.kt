package com.example.data.remote.dto

import com.example.data.model.*
import java.util.UUID

object DtoMappers {

    fun mapPlatformType(platformStr: String?): PlatformType {
        return when (platformStr?.uppercase()?.trim()) {
            "FACEBOOK" -> PlatformType.FACEBOOK
            "INSTAGRAM" -> PlatformType.INSTAGRAM
            "TWITTER", "X", "X_TWITTER" -> PlatformType.TWITTER
            "LINKEDIN" -> PlatformType.LINKEDIN
            "TIKTOK" -> PlatformType.TIKTOK
            else -> PlatformType.FACEBOOK
        }
    }

    fun mapPlatformTypeToString(platform: PlatformType): String {
        return when (platform) {
            PlatformType.FACEBOOK -> "FACEBOOK"
            PlatformType.INSTAGRAM -> "INSTAGRAM"
            PlatformType.TWITTER -> "TWITTER"
            PlatformType.LINKEDIN -> "LINKEDIN"
            PlatformType.TIKTOK -> "TIKTOK"
        }
    }

    fun mapPostStatus(statusStr: String?): PostStatus {
        return when (statusStr?.uppercase()?.trim()) {
            "SCHEDULED" -> PostStatus.SCHEDULED
            "DRAFT" -> PostStatus.DRAFT
            "PUBLISHED" -> PostStatus.PUBLISHED
            "FAILED" -> PostStatus.FAILED
            "GENERATING" -> PostStatus.GENERATING
            else -> PostStatus.DRAFT
        }
    }

    fun mapPostStatusToString(status: PostStatus): String {
        return when (status) {
            PostStatus.SCHEDULED -> "SCHEDULED"
            PostStatus.DRAFT -> "DRAFT"
            PostStatus.PUBLISHED -> "PUBLISHED"
            PostStatus.FAILED -> "FAILED"
            PostStatus.GENERATING -> "GENERATING"
        }
    }

    fun mapApprovalState(stateStr: String?): ActionApprovalState {
        return when (stateStr?.uppercase()?.trim()) {
            "PROPOSED" -> ActionApprovalState.PROPOSED
            "AWAITING_APPROVAL" -> ActionApprovalState.AWAITING_APPROVAL
            "APPROVED" -> ActionApprovalState.APPROVED
            "EXECUTING" -> ActionApprovalState.EXECUTING
            "SUCCESS" -> ActionApprovalState.SUCCESS
            "FAILED" -> ActionApprovalState.FAILED
            "CANCELLED" -> ActionApprovalState.CANCELLED
            else -> ActionApprovalState.PROPOSED
        }
    }

    fun mapApprovalStateToString(state: ActionApprovalState): String {
        return when (state) {
            ActionApprovalState.PROPOSED -> "PROPOSED"
            ActionApprovalState.AWAITING_APPROVAL -> "AWAITING_APPROVAL"
            ActionApprovalState.APPROVED -> "APPROVED"
            ActionApprovalState.EXECUTING -> "EXECUTING"
            ActionApprovalState.SUCCESS -> "SUCCESS"
            ActionApprovalState.FAILED -> "FAILED"
            ActionApprovalState.CANCELLED -> "CANCELLED"
        }
    }

    fun mapAccountType(typeStr: String?): AccountType {
        return when (typeStr?.uppercase()?.trim()) {
            "PERSONAL" -> AccountType.PERSONAL
            "PAGE" -> AccountType.PAGE
            "BUSINESS" -> AccountType.BUSINESS
            "CREATOR" -> AccountType.CREATOR
            else -> AccountType.PAGE
        }
    }

    fun mapConnectionStatus(statusStr: String?): ConnectionStatus {
        return when (statusStr?.uppercase()?.trim()) {
            "CONNECTED" -> ConnectionStatus.CONNECTED
            "DISCONNECTED" -> ConnectionStatus.DISCONNECTED
            "CONNECTING" -> ConnectionStatus.CONNECTING
            "EXPIRED" -> ConnectionStatus.EXPIRED
            "REAUTH_REQUIRED" -> ConnectionStatus.REAUTH_REQUIRED
            "ERROR" -> ConnectionStatus.ERROR
            else -> ConnectionStatus.DISCONNECTED
        }
    }

    fun mapTokenStatus(statusStr: String?): TokenStatus {
        return when (statusStr?.uppercase()?.trim()) {
            "VALID" -> TokenStatus.VALID
            "EXPIRING" -> TokenStatus.EXPIRING
            "EXPIRED" -> TokenStatus.EXPIRED
            "REVOKED" -> TokenStatus.REVOKED
            else -> TokenStatus.UNKNOWN
        }
    }

    fun mapCapabilities(capabilitiesList: List<String>?): List<SocialCapability> {
        if (capabilitiesList.isNullOrEmpty()) {
            return listOf(
                SocialCapability.CREATE_POST,
                SocialCapability.PUBLISH_POST,
                SocialCapability.READ_ANALYTICS
            )
        }
        return capabilitiesList.mapNotNull { cap ->
            when (cap.uppercase().trim()) {
                "CREATE_POST" -> SocialCapability.CREATE_POST
                "PUBLISH_POST" -> SocialCapability.PUBLISH_POST
                "READ_COMMENTS" -> SocialCapability.READ_COMMENTS
                "REPLY_COMMENT" -> SocialCapability.REPLY_COMMENT
                "READ_MESSAGES" -> SocialCapability.READ_MESSAGES
                "SEND_MESSAGE" -> SocialCapability.SEND_MESSAGE
                "READ_ANALYTICS" -> SocialCapability.READ_ANALYTICS
                "MEDIA_UPLOAD" -> SocialCapability.MEDIA_UPLOAD
                "STORY_PUBLISH" -> SocialCapability.STORY_PUBLISH
                "REEL_PUBLISH" -> SocialCapability.REEL_PUBLISH
                else -> null
            }
        }
    }

    fun mapBrandTone(toneStr: String?): BrandTone {
        return when (toneStr?.uppercase()?.trim()) {
            "PROFESSIONAL" -> BrandTone.PROFESSIONAL
            "FRIENDLY" -> BrandTone.FRIENDLY
            "FUNNY" -> BrandTone.FUNNY
            "EDUCATIONAL" -> BrandTone.EDUCATIONAL
            "LUXURY" -> BrandTone.LUXURY
            "CASUAL" -> BrandTone.CASUAL
            "INSPIRATIONAL" -> BrandTone.INSPIRATIONAL
            "SALES_FOCUSED" -> BrandTone.SALES_FOCUSED
            else -> BrandTone.PROFESSIONAL
        }
    }

    fun mapBrandLanguage(langStr: String?): BrandLanguage {
        return when (langStr?.uppercase()?.trim()) {
            "ENGLISH" -> BrandLanguage.ENGLISH
            "BANGLA" -> BrandLanguage.BANGLA
            "BANGLISH" -> BrandLanguage.BANGLISH
            else -> BrandLanguage.ENGLISH
        }
    }

    fun mapRecurrenceOption(optStr: String?): RecurrenceOption {
        return when (optStr?.uppercase()?.trim()) {
            "DAILY" -> RecurrenceOption.DAILY
            "WEEKLY" -> RecurrenceOption.WEEKLY
            "MONTHLY" -> RecurrenceOption.MONTHLY
            else -> RecurrenceOption.NONE
        }
    }

    // --- Domain Mappers ---

    fun toSocialAccount(dto: SocialAccountDto): SocialAccount {
        val platform = mapPlatformType(dto.platform)
        val connStatus = mapConnectionStatus(dto.connectionStatus)
        val tokStatus = mapTokenStatus(dto.tokenStatus)
        val isConn = connStatus == ConnectionStatus.CONNECTED

        return SocialAccount(
            id = dto.id ?: UUID.randomUUID().toString(),
            workspaceId = dto.workspaceId ?: "ws_default",
            platform = platform,
            platformUserId = dto.platformUserId ?: "",
            accountName = dto.accountName ?: "${platform.displayName} Account",
            handle = dto.handle ?: "@account",
            profileImageUrl = dto.avatarUrl ?: "",
            avatarUrl = dto.avatarUrl ?: "",
            accountType = mapAccountType(dto.accountType),
            connectionStatus = connStatus,
            tokenStatus = tokStatus,
            scopes = dto.scopes ?: emptyList(),
            followerCount = dto.followerCount ?: 0,
            postsTodayCount = dto.postsTodayCount ?: 0,
            availableCapabilities = mapCapabilities(dto.capabilities),
            isConnected = isConn,
            lastSyncedTime = if (isConn) "Synced" else "Disconnected",
            isDemoData = false
        )
    }

    fun toPlatformPublishResult(dto: PlatformPublishResultDto): PlatformPublishResult {
        return PlatformPublishResult(
            id = dto.id ?: UUID.randomUUID().toString(),
            platform = mapPlatformType(dto.platform),
            status = mapApprovalState(dto.status),
            externalPostId = dto.externalPostId,
            timestamp = dto.createdAt ?: "Just now",
            errorMessage = dto.errorMessage,
            executionEnvironment = if (dto.executionEnvironment == "PRODUCTION" || dto.executionEnvironment == "REAL") ExecutionEnvironment.PRODUCTION else ExecutionEnvironment.MOCK,
            idempotencyKey = dto.idempotencyKey ?: ""
        )
    }

    fun toSocialPost(dto: SocialPostDto): SocialPost {
        val platforms = dto.targetPlatforms?.map { mapPlatformType(it) } ?: listOf(PlatformType.FACEBOOK)
        val postStatus = mapPostStatus(dto.status)
        val approvalState = mapApprovalState(dto.approvalState)
        val publishResults = dto.publishResults?.map { toPlatformPublishResult(it) } ?: emptyList()

        return SocialPost(
            id = dto.id ?: UUID.randomUUID().toString(),
            workspaceId = dto.workspaceId ?: "ws_default",
            title = dto.title ?: "Untitled Post",
            content = dto.content ?: "",
            targetPlatforms = platforms,
            scheduledTime = dto.scheduledAt ?: "Scheduled",
            scheduledAt = dto.scheduledAt,
            publishedAt = dto.publishedAt,
            timezone = dto.timezone ?: "America/New_York",
            repeatOption = mapRecurrenceOption(dto.repeatOption),
            requireApproval = dto.requireApproval ?: true,
            status = postStatus,
            mediaUrl = dto.mediaUrls?.firstOrNull(),
            media = dto.mediaUrls ?: emptyList(),
            hashtags = dto.hashtags ?: "",
            cta = dto.cta ?: "",
            isAiGenerated = dto.isAiGenerated ?: true,
            approvalState = approvalState,
            errorMessage = dto.errorMessage,
            retryCount = dto.retryCount ?: 0,
            maxRetries = dto.maxRetries ?: 3,
            platformPublishResults = publishResults,
            engagementScore = dto.engagementScore ?: 88,
            engagementCount = dto.engagementCount ?: 0,
            createdAt = dto.createdAt ?: "Just now",
            updatedAt = dto.updatedAt ?: "Just now",
            isDemoData = false
        )
    }

    fun toBrandProfile(dto: BrandProfileDto): BrandProfile {
        val keywordsList = when (val kw = dto.keywords) {
            is List<*> -> kw.filterIsInstance<String>()
            is String -> kw.split(",").map { it.trim() }
            else -> emptyList()
        }
        val keywordsStr = keywordsList.joinToString(", ")

        val wordsAvoidList = when (val wa = dto.wordsToAvoid) {
            is List<*> -> wa.filterIsInstance<String>()
            is String -> wa.split(",").map { it.trim() }
            else -> emptyList()
        }
        val wordsAvoidStr = wordsAvoidList.joinToString(", ")

        return BrandProfile(
            id = dto.id ?: UUID.randomUUID().toString(),
            brandName = dto.name ?: "Brand Profile",
            businessDescription = dto.businessDescription ?: "",
            industry = dto.industry ?: "",
            targetAudience = dto.targetAudience ?: "",
            primaryLanguage = mapBrandLanguage(dto.primaryLanguage),
            secondaryLanguage = dto.secondaryLanguage?.let { mapBrandLanguage(it) },
            brandTone = mapBrandTone(dto.toneOfVoice),
            writingStyle = dto.writingStyle ?: "",
            preferredCta = dto.preferredCta ?: "",
            preferredHashtags = dto.preferredHashtags ?: "",
            wordsToAvoid = wordsAvoidStr,
            brandKeywords = keywordsStr,
            productsServices = dto.productsServices ?: "",
            website = dto.website ?: "",
            contactInfo = dto.contactInfo ?: ""
        )
    }

    fun toActivityLog(dto: AgentLogDto): ActivityLog {
        return ActivityLog(
            id = dto.id ?: UUID.randomUUID().toString(),
            title = dto.action.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            detail = dto.error ?: "Action ${dto.action} executed with status ${dto.status}.",
            timestamp = dto.createdAt ?: "Just now",
            platform = dto.platform?.let { mapPlatformType(it) },
            actionType = dto.action,
            executionEnvironment = if (dto.executionEnvironment == "PRODUCTION") ExecutionEnvironment.PRODUCTION else ExecutionEnvironment.MOCK
        )
    }

    fun toAnalyticsData(dto: AnalyticsDataDto): AnalyticsData {
        val breakdown = dto.platformBreakdown?.map { pm ->
            PlatformMetric(
                platform = mapPlatformType(pm.platform),
                reach = pm.reach ?: 0,
                engagementRate = pm.engagementRate ?: 0.0,
                followersGained = pm.followersGained ?: 0
            )
        } ?: emptyList()

        return AnalyticsData(
            totalReach = dto.totalReach ?: 0,
            totalEngagement = dto.totalEngagement ?: 0,
            followerGrowthPercent = dto.followerGrowthPercent ?: 0.0,
            totalScheduledPosts = dto.totalScheduledPosts ?: 0,
            platformBreakdown = breakdown,
            isDemoData = false
        )
    }

    // --- Request Builders ---

    fun toCreatePostRequest(post: SocialPost): CreateSocialPostRequest {
        return CreateSocialPostRequest(
            title = post.title,
            content = post.content,
            targetPlatforms = post.targetPlatforms.map { mapPlatformTypeToString(it) },
            status = mapPostStatusToString(post.status),
            approvalState = mapApprovalStateToString(post.approvalState),
            scheduledAt = post.scheduledAt,
            timezone = post.timezone,
            repeatOption = post.repeatOption.name,
            requireApproval = post.requireApproval,
            mediaUrls = if (post.media.isNotEmpty()) post.media else (post.mediaUrl?.let { listOf(it) }),
            hashtags = post.hashtags.ifBlank { null },
            cta = post.cta.ifBlank { null },
            isAiGenerated = post.isAiGenerated
        )
    }

    fun toUpdatePostRequest(post: SocialPost): UpdateSocialPostRequest {
        return UpdateSocialPostRequest(
            title = post.title,
            content = post.content,
            targetPlatforms = post.targetPlatforms.map { mapPlatformTypeToString(it) },
            status = mapPostStatusToString(post.status),
            approvalState = mapApprovalStateToString(post.approvalState),
            scheduledAt = post.scheduledAt,
            publishedAt = post.publishedAt,
            timezone = post.timezone,
            repeatOption = post.repeatOption.name,
            requireApproval = post.requireApproval,
            mediaUrls = if (post.media.isNotEmpty()) post.media else (post.mediaUrl?.let { listOf(it) }),
            hashtags = post.hashtags.ifBlank { null },
            cta = post.cta.ifBlank { null },
            errorMessage = post.errorMessage
        )
    }

    fun toCreateBrandProfileRequest(profile: BrandProfile): CreateOrUpdateBrandProfileRequest {
        val keywordsList = profile.brandKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return CreateOrUpdateBrandProfileRequest(
            name = profile.brandName,
            businessDescription = profile.businessDescription,
            industry = profile.industry,
            targetAudience = profile.targetAudience,
            primaryLanguage = profile.primaryLanguage.name,
            secondaryLanguage = profile.secondaryLanguage?.name,
            toneOfVoice = profile.brandTone.name,
            writingStyle = profile.writingStyle,
            preferredCta = profile.preferredCta,
            preferredHashtags = profile.preferredHashtags,
            wordsToAvoid = profile.wordsToAvoid,
            keywords = keywordsList,
            productsServices = profile.productsServices,
            website = profile.website,
            contactInfo = profile.contactInfo
        )
    }
}
