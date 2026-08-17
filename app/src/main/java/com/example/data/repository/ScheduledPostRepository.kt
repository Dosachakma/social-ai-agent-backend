package com.example.data.repository

import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
        postsFlow.value = postsFlow.value + created
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
        else AppResult.Error(AgentError("POST_NOT_FOUND", "Post $post not found"))
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
            ),
            SocialPost(
                id = "post_seed_3",
                title = "TikTok Behind The Scenes",
                content = "A quick look at how our AI agent manages multi-channel queues simultaneously! 📱⚡",
                targetPlatforms = listOf(PlatformType.TIKTOK, PlatformType.INSTAGRAM),
                scheduledTime = "Wed 14 at 6:00 PM",
                scheduledAt = "2026-08-14T18:00:00",
                timezone = "America/New_York",
                repeatOption = RecurrenceOption.NONE,
                requireApproval = false,
                status = PostStatus.SCHEDULED,
                approvalState = ActionApprovalState.APPROVED,
                hashtags = "#TikTokTech #BehindTheScenes"
            ),
            SocialPost(
                id = "post_seed_4",
                title = "Published Customer Spotlight",
                content = "See how Acme Corp boosted engagement by 24% using brand memory automation.",
                targetPlatforms = listOf(PlatformType.FACEBOOK, PlatformType.TWITTER),
                scheduledTime = "Sun 10 at 12:00 PM",
                scheduledAt = "2026-08-10T12:00:00",
                timezone = "America/New_York",
                publishedAt = "2026-08-10T12:00:00",
                status = PostStatus.PUBLISHED,
                approvalState = ActionApprovalState.SUCCESS,
                platformPublishResults = listOf(
                    PlatformPublishResult(platform = PlatformType.FACEBOOK, status = ActionApprovalState.SUCCESS, externalPostId = "fb_ext_882"),
                    PlatformPublishResult(platform = PlatformType.TWITTER, status = ActionApprovalState.SUCCESS, externalPostId = "x_ext_991")
                )
            ),
            SocialPost(
                id = "post_seed_5",
                title = "Draft Q&A Announcement",
                content = "Got questions about social AI scheduling? Drop them in the comments below!",
                targetPlatforms = listOf(PlatformType.INSTAGRAM),
                scheduledTime = "Thu 15 at 4:00 PM",
                scheduledAt = "2026-08-15T16:00:00",
                timezone = "America/New_York",
                status = PostStatus.DRAFT,
                approvalState = ActionApprovalState.PROPOSED
            )
        )
    }
}
