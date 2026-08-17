package com.example.data.remote.mappers

import com.example.data.model.*
import com.example.data.remote.api.*
import java.util.UUID

/**
 * Clean round-trip mappers between API DTOs and Android Domain Models.
 */
object DomainMappers {

    // --- BrandProfile Mappers ---
    fun BrandProfileDto.toDomain(): BrandProfile {
        val tone = when (toneOfVoice?.uppercase()) {
            "FRIENDLY" -> BrandTone.FRIENDLY
            "FUNNY" -> BrandTone.FUNNY
            "EDUCATIONAL" -> BrandTone.EDUCATIONAL
            "LUXURY" -> BrandTone.LUXURY
            "CASUAL" -> BrandTone.CASUAL
            "INSPIRATIONAL" -> BrandTone.INSPIRATIONAL
            "SALES_FOCUSED" -> BrandTone.SALES_FOCUSED
            else -> BrandTone.PROFESSIONAL
        }

        val primaryLang = when (primaryLanguage?.uppercase()) {
            "BANGLA" -> BrandLanguage.BANGLA
            "BANGLISH" -> BrandLanguage.BANGLISH
            else -> BrandLanguage.ENGLISH
        }

        val secondaryLang = when (secondaryLanguage?.uppercase()) {
            "BANGLA" -> BrandLanguage.BANGLA
            "BANGLISH" -> BrandLanguage.BANGLISH
            "ENGLISH" -> BrandLanguage.ENGLISH
            else -> null
        }

        return BrandProfile(
            id = id,
            brandName = brandName ?: name,
            businessDescription = description ?: "",
            industry = industry ?: "",
            targetAudience = targetAudience ?: "",
            primaryLanguage = primaryLang,
            secondaryLanguage = secondaryLang,
            brandTone = tone,
            writingStyle = writingStyle ?: "",
            preferredCta = preferredCta ?: "",
            preferredHashtags = preferredHashtags?.joinToString(" ") ?: "",
            wordsToAvoid = wordsToAvoid?.joinToString(", ") ?: "",
            brandKeywords = brandKeywords?.joinToString(", ") ?: "",
            productsServices = productsServices ?: "",
            website = website ?: "",
            contactInfo = contactInfo ?: ""
        )
    }

    fun BrandProfile.toMutationRequest(): BrandProfileMutationRequest {
        return BrandProfileMutationRequest(
            name = brandName,
            brandName = brandName,
            industry = industry,
            tagline = null,
            description = businessDescription,
            targetAudience = targetAudience,
            primaryLanguage = primaryLanguage.displayName,
            secondaryLanguage = secondaryLanguage?.displayName,
            toneOfVoice = brandTone.displayName,
            writingStyle = writingStyle,
            preferredCta = preferredCta,
            preferredHashtags = preferredHashtags.split(" ", ",").map { it.trim() }.filter { it.isNotBlank() },
            wordsToAvoid = wordsToAvoid.split(",").map { it.trim() }.filter { it.isNotBlank() },
            brandKeywords = brandKeywords.split(",").map { it.trim() }.filter { it.isNotBlank() },
            productsServices = productsServices,
            website = website,
            contactInfo = contactInfo,
            isDefault = true
        )
    }

    // --- SocialAccount Mappers ---
    fun SocialAccountDto.toDomain(isDemo: Boolean = false): SocialAccount {
        val platformType = when (platform.uppercase()) {
            "INSTAGRAM" -> PlatformType.INSTAGRAM
            "TWITTER", "X" -> PlatformType.TWITTER
            "LINKEDIN" -> PlatformType.LINKEDIN
            "TIKTOK" -> PlatformType.TIKTOK
            else -> PlatformType.FACEBOOK
        }

        val type = when (accountType?.uppercase()) {
            "PERSONAL" -> AccountType.PERSONAL
            "BUSINESS" -> AccountType.BUSINESS
            "CREATOR" -> AccountType.CREATOR
            else -> AccountType.PAGE
        }

        val connStatus = when (connectionStatus?.uppercase()) {
            "DISCONNECTED" -> ConnectionStatus.DISCONNECTED
            "CONNECTING" -> ConnectionStatus.CONNECTING
            "EXPIRED" -> ConnectionStatus.EXPIRED
            "REAUTH_REQUIRED" -> ConnectionStatus.REAUTH_REQUIRED
            "ERROR" -> ConnectionStatus.ERROR
            else -> ConnectionStatus.CONNECTED
        }

        val tokStatus = when (tokenStatus?.uppercase()) {
            "EXPIRING" -> TokenStatus.EXPIRING
            "EXPIRED" -> TokenStatus.EXPIRED
            "REVOKED" -> TokenStatus.REVOKED
            "UNKNOWN" -> TokenStatus.UNKNOWN
            else -> TokenStatus.VALID
        }

        val isConnectedBool = connStatus == ConnectionStatus.CONNECTED

        return SocialAccount(
            id = id,
            userId = "current_user",
            workspaceId = workspaceId ?: "ws_default",
            platform = platformType,
            platformUserId = platformUserId ?: id,
            accountName = accountName,
            handle = handle ?: "@${accountName.lowercase().replace(" ", "")}",
            profileImageUrl = avatarUrl ?: "",
            accountType = type,
            connectionStatus = connStatus,
            scopes = scopes ?: emptyList(),
            connectedAt = System.currentTimeMillis(),
            lastSyncedAt = System.currentTimeMillis(),
            tokenStatus = tokStatus,
            availableCapabilities = listOf(
                SocialCapability.CREATE_POST,
                SocialCapability.PUBLISH_POST,
                SocialCapability.READ_COMMENTS,
                SocialCapability.REPLY_COMMENT,
                SocialCapability.READ_ANALYTICS,
                SocialCapability.MEDIA_UPLOAD
            ),
            avatarUrl = avatarUrl ?: "",
            followerCount = followerCount ?: 0,
            postsTodayCount = 0,
            isConnected = isConnectedBool,
            lastSyncedTime = lastSyncedAt ?: "Recently",
            isDemoData = isDemo
        )
    }

    fun SocialAccount.toConnectRequest(): ConnectAccountRequest {
        return ConnectAccountRequest(
            platform = platform.name,
            accountName = accountName,
            name = accountName,
            handle = handle,
            platformUserId = platformUserId,
            accountType = accountType.name,
            avatarUrl = avatarUrl.ifBlank { null },
            scopes = scopes
        )
    }

    fun SocialAccount.toUpdateRequest(): UpdateAccountRequest {
        return UpdateAccountRequest(
            accountName = accountName,
            handle = handle,
            avatarUrl = avatarUrl.ifBlank { null },
            connectionStatus = connectionStatus.name,
            tokenStatus = tokenStatus.name,
            followerCount = followerCount
        )
    }

    // --- SocialPost Mappers ---
    fun SocialPostDto.toDomain(isDemo: Boolean = false): SocialPost {
        val pStatus = when (status?.uppercase()) {
            "SCHEDULED" -> PostStatus.SCHEDULED
            "PUBLISHED" -> PostStatus.PUBLISHED
            "FAILED" -> PostStatus.FAILED
            "GENERATING" -> PostStatus.GENERATING
            else -> PostStatus.DRAFT
        }

        val aState = when (approvalState?.uppercase()) {
            "AWAITING_APPROVAL" -> ActionApprovalState.AWAITING_APPROVAL
            "APPROVED" -> ActionApprovalState.APPROVED
            "EXECUTING" -> ActionApprovalState.EXECUTING
            "SUCCESS" -> ActionApprovalState.SUCCESS
            "FAILED" -> ActionApprovalState.FAILED
            "CANCELLED" -> ActionApprovalState.CANCELLED
            else -> ActionApprovalState.PROPOSED
        }

        val platforms = targetPlatforms?.mapNotNull { pStr ->
            when (pStr.uppercase()) {
                "FACEBOOK" -> PlatformType.FACEBOOK
                "INSTAGRAM" -> PlatformType.INSTAGRAM
                "TWITTER", "X" -> PlatformType.TWITTER
                "LINKEDIN" -> PlatformType.LINKEDIN
                "TIKTOK" -> PlatformType.TIKTOK
                else -> null
            }
        } ?: emptyList()

        val results = publishResults?.map { it.toDomain() } ?: emptyList()

        return SocialPost(
            id = id,
            workspaceId = workspaceId ?: "ws_default",
            title = title,
            content = content,
            targetPlatforms = platforms,
            scheduledTime = scheduledTime ?: (scheduledAt ?: "Unscheduled"),
            scheduledAt = scheduledAt,
            timezone = timezone ?: "America/New_York",
            repeatOption = when (repeatOption?.uppercase()) {
                "DAILY" -> RecurrenceOption.DAILY
                "WEEKLY" -> RecurrenceOption.WEEKLY
                "MONTHLY" -> RecurrenceOption.MONTHLY
                else -> RecurrenceOption.NONE
            },
            requireApproval = requireApproval ?: true,
            publishedAt = publishedAt,
            status = pStatus,
            mediaUrl = mediaUrl ?: mediaUrls?.firstOrNull(),
            media = mediaUrls ?: (mediaUrl?.let { listOf(it) } ?: emptyList()),
            hashtags = hashtags ?: "",
            cta = cta ?: "",
            isAiGenerated = isAiGenerated ?: false,
            approvalState = aState,
            errorMessage = errorMessage,
            retryCount = retryCount ?: 0,
            platformPublishResults = results,
            createdAt = createdAt ?: "Recently",
            updatedAt = updatedAt ?: "Recently",
            engagementScore = engagementScore ?: 85,
            isDemoData = isDemo
        )
    }

    fun SocialPost.toCreateRequest(): CreatePostRequest {
        return CreatePostRequest(
            title = title,
            content = content,
            caption = content,
            targetPlatforms = targetPlatforms.map { it.name },
            status = status.name,
            approvalState = approvalState.name,
            scheduledTime = scheduledTime,
            scheduledAt = scheduledAt,
            timezone = timezone,
            repeatOption = repeatOption.name,
            requireApproval = requireApproval,
            mediaUrls = if (media.isNotEmpty()) media else (mediaUrl?.let { listOf(it) }),
            mediaUrl = mediaUrl,
            hashtags = hashtags.ifBlank { null },
            cta = cta.ifBlank { null },
            isAiGenerated = isAiGenerated,
            engagementScore = engagementScore
        )
    }

    fun SocialPost.toUpdateRequest(): UpdatePostRequest {
        return UpdatePostRequest(
            title = title,
            content = content,
            targetPlatforms = targetPlatforms.map { it.name },
            status = status.name,
            approvalState = approvalState.name,
            scheduledTime = scheduledTime,
            scheduledAt = scheduledAt,
            publishedAt = publishedAt,
            timezone = timezone,
            repeatOption = repeatOption.name,
            requireApproval = requireApproval,
            mediaUrls = if (media.isNotEmpty()) media else (mediaUrl?.let { listOf(it) }),
            hashtags = hashtags.ifBlank { null },
            cta = cta.ifBlank { null },
            errorMessage = errorMessage,
            retryCount = retryCount
        )
    }

    // --- PublishResult Mappers ---
    fun PublishResultDto.toDomain(): PlatformPublishResult {
        val platformType = when (platform.uppercase()) {
            "INSTAGRAM" -> PlatformType.INSTAGRAM
            "TWITTER", "X" -> PlatformType.TWITTER
            "LINKEDIN" -> PlatformType.LINKEDIN
            "TIKTOK" -> PlatformType.TIKTOK
            else -> PlatformType.FACEBOOK
        }

        val approval = when (status.uppercase()) {
            "SUCCESS", "PUBLISHED" -> ActionApprovalState.SUCCESS
            "FAILED" -> ActionApprovalState.FAILED
            "EXECUTING" -> ActionApprovalState.EXECUTING
            else -> ActionApprovalState.PROPOSED
        }

        val env = when (executionEnvironment?.uppercase()) {
            "REAL", "PRODUCTION" -> ExecutionEnvironment.REAL
            "DEVELOPMENT" -> ExecutionEnvironment.DEVELOPMENT
            else -> ExecutionEnvironment.MOCK
        }

        return PlatformPublishResult(
            id = id,
            platform = platformType,
            status = approval,
            externalPostId = externalPostId,
            timestamp = publishedAt ?: createdAt ?: "Just now",
            errorMessage = errorMessage,
            executionEnvironment = env,
            idempotencyKey = idempotencyKey ?: ""
        )
    }

    fun PlatformPublishResult.toSaveRequest(): SavePublishResultRequest {
        return SavePublishResultRequest(
            platform = platform.name,
            status = status.name,
            externalPostId = externalPostId,
            errorMessage = errorMessage,
            idempotencyKey = idempotencyKey.ifBlank { null },
            executionEnvironment = executionEnvironment.name
        )
    }

    // --- AgentLog Mappers ---
    fun AgentLogDto.toActivityLog(): ActivityLog {
        val platformType = platform?.let { p ->
            when (p.uppercase()) {
                "FACEBOOK" -> PlatformType.FACEBOOK
                "INSTAGRAM" -> PlatformType.INSTAGRAM
                "TWITTER", "X" -> PlatformType.TWITTER
                "LINKEDIN" -> PlatformType.LINKEDIN
                "TIKTOK" -> PlatformType.TIKTOK
                else -> null
            }
        }

        val env = when (executionEnvironment?.uppercase()) {
            "REAL", "PRODUCTION" -> ExecutionEnvironment.REAL
            "DEVELOPMENT" -> ExecutionEnvironment.DEVELOPMENT
            else -> ExecutionEnvironment.MOCK
        }

        return ActivityLog(
            id = id,
            title = title ?: action,
            detail = detail ?: (errorMessage ?: "Executed action $action"),
            timestamp = createdAt ?: "Just now",
            platform = platformType,
            actionType = action,
            executionEnvironment = env
        )
    }

    // --- Analytics Mappers ---
    fun AnalyticsDto.toDomain(): AnalyticsData {
        val metrics = platformBreakdown?.map { dto ->
            val platformType = when (dto.platform.uppercase()) {
                "INSTAGRAM" -> PlatformType.INSTAGRAM
                "TWITTER", "X" -> PlatformType.TWITTER
                "LINKEDIN" -> PlatformType.LINKEDIN
                "TIKTOK" -> PlatformType.TIKTOK
                else -> PlatformType.FACEBOOK
            }
            PlatformMetric(
                platform = platformType,
                reach = dto.reach,
                engagementRate = dto.engagementRate,
                followersGained = dto.followersGained
            )
        } ?: emptyList()

        return AnalyticsData(
            totalReach = totalReach,
            totalEngagement = totalEngagement,
            followerGrowthPercent = followerGrowthPercent,
            totalScheduledPosts = totalScheduledPosts,
            platformBreakdown = metrics,
            isDemoData = isDemoData
        )
    }
}
