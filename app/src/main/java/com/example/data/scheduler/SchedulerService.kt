package com.example.data.scheduler

import com.example.data.model.*
import com.example.data.notification.MockNotificationService
import com.example.data.notification.NotificationService
import com.example.data.remote.SocialPlatformRegistry
import com.example.data.repository.MockScheduledPostRepository
import com.example.data.repository.MockSocialMediaRepository
import com.example.data.repository.ScheduledPostRepository
import com.example.data.repository.SocialMediaRepository
import com.example.data.security.AccountValidationEngine
import com.example.data.security.AccountValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

interface SchedulerService {
    suspend fun schedulePost(post: SocialPost): AppResult<SocialPost>
    suspend fun cancelScheduledPost(postId: String): AppResult<Boolean>
    suspend fun reschedulePost(postId: String, newScheduledAt: String, newTimezone: String): AppResult<SocialPost>
    suspend fun approvePost(postId: String): AppResult<SocialPost>
    suspend fun getScheduledPosts(): AppResult<List<SocialPost>>
    suspend fun executeScheduledPost(postId: String): AppResult<Map<PlatformType, PlatformPublishResult>>
    fun getActionLogs(): Flow<List<AgentActionLog>>
}

class DefaultSchedulerService(
    private val repository: ScheduledPostRepository = MockScheduledPostRepository(),
    private val socialMediaRepository: SocialMediaRepository = MockSocialMediaRepository(),
    private val notificationService: NotificationService = MockNotificationService(),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val validationEngine: AccountValidationEngine = AccountValidationEngine()
) : SchedulerService {

    private val logsFlow = MutableStateFlow<List<AgentActionLog>>(emptyList())

    override suspend fun schedulePost(post: SocialPost): AppResult<SocialPost> {
        val approvalState = if (post.requireApproval) ActionApprovalState.AWAITING_APPROVAL else ActionApprovalState.APPROVED
        val status = if (approvalState == ActionApprovalState.APPROVED) PostStatus.SCHEDULED else PostStatus.DRAFT

        val postToSave = post.copy(
            approvalState = approvalState,
            status = status
        )

        val result = repository.create(postToSave)
        if (result is AppResult.Success) {
            val savedPost = result.data
            addAuditLog(
                action = AgentAction.SCHEDULE_POST,
                platform = savedPost.targetPlatforms.firstOrNull(),
                status = approvalState,
                metadata = mapOf(
                    "postId" to savedPost.id,
                    "event" to "POST_SCHEDULED",
                    "scheduledAt" to (savedPost.scheduledAt ?: savedPost.scheduledTime),
                    "timezone" to savedPost.timezone
                )
            )

            if (post.requireApproval) {
                notificationService.notifyApprovalRequired(savedPost)
            } else {
                notificationService.notifyPostScheduled(savedPost)
            }
        }
        return result
    }

    override suspend fun cancelScheduledPost(postId: String): AppResult<Boolean> {
        val result = repository.cancel(postId)
        if (result is AppResult.Success && result.data) {
            addAuditLog(
                action = AgentAction.SCHEDULE_POST,
                platform = null,
                status = ActionApprovalState.CANCELLED,
                metadata = mapOf("postId" to postId, "event" to "POST_CANCELLED")
            )
        }
        return result
    }

    override suspend fun reschedulePost(
        postId: String,
        newScheduledAt: String,
        newTimezone: String
    ): AppResult<SocialPost> {
        val postRes = repository.getById(postId)
        val existing = postRes.getOrNull()
            ?: return AppResult.Error(AgentError("POST_NOT_FOUND", "Post $postId not found"))

        val updated = existing.copy(
            scheduledAt = newScheduledAt,
            scheduledTime = newScheduledAt,
            timezone = newTimezone,
            status = PostStatus.SCHEDULED
        )

        val saveRes = repository.updatePost(updated)
        if (saveRes is AppResult.Success) {
            addAuditLog(
                action = AgentAction.SCHEDULE_POST,
                platform = updated.targetPlatforms.firstOrNull(),
                status = updated.approvalState,
                metadata = mapOf(
                    "postId" to postId,
                    "event" to "POST_RESCHEDULED",
                    "newScheduledAt" to newScheduledAt,
                    "newTimezone" to newTimezone
                )
            )
        }
        return saveRes
    }

    override suspend fun approvePost(postId: String): AppResult<SocialPost> {
        val postRes = repository.getById(postId)
        val existing = postRes.getOrNull()
            ?: return AppResult.Error(AgentError("POST_NOT_FOUND", "Post $postId not found"))

        val approvedPost = existing.copy(
            approvalState = ActionApprovalState.APPROVED,
            status = PostStatus.SCHEDULED
        )

        val updateRes = repository.updatePost(approvedPost)
        if (updateRes is AppResult.Success) {
            addAuditLog(
                action = AgentAction.SCHEDULE_POST,
                platform = approvedPost.targetPlatforms.firstOrNull(),
                status = ActionApprovalState.APPROVED,
                metadata = mapOf("postId" to postId, "event" to "POST_APPROVED")
            )
            notificationService.notifyPostScheduled(approvedPost)
        }
        return updateRes
    }

    override suspend fun getScheduledPosts(): AppResult<List<SocialPost>> {
        return repository.getUpcoming()
    }

    override suspend fun executeScheduledPost(postId: String): AppResult<Map<PlatformType, PlatformPublishResult>> {
        val postRes = repository.getById(postId)
        val post = postRes.getOrNull()
            ?: return AppResult.Error(AgentError("POST_NOT_FOUND", "Post $postId not found"))

        // 1. APPROVAL SAFETY CHECK
        if (post.requireApproval && post.approvalState != ActionApprovalState.APPROVED) {
            addAuditLog(
                action = AgentAction.PUBLISH_POST,
                platform = post.targetPlatforms.firstOrNull(),
                status = ActionApprovalState.AWAITING_APPROVAL,
                error = "Post requires user approval.",
                metadata = mapOf("postId" to postId, "event" to "POST_APPROVAL_REQUIRED")
            )
            return AppResult.Error(
                AgentError("APPROVAL_REQUIRED", "Post requires user approval.")
            )
        }

        // Audit Log: EXECUTION STARTED
        addAuditLog(
            action = AgentAction.PUBLISH_POST,
            platform = post.targetPlatforms.firstOrNull(),
            status = ActionApprovalState.EXECUTING,
            metadata = mapOf("postId" to postId, "event" to "POST_EXECUTION_STARTED")
        )

        // Update post status to EXECUTING
        repository.updateStatus(postId, PostStatus.SCHEDULED, ActionApprovalState.EXECUTING)

        val resultMap = mutableMapOf<PlatformType, PlatformPublishResult>()

        for (platform in post.targetPlatforms) {
            val idempotencyKey = "exec_${postId}_${platform.name}"

            // 1.5. ACCOUNT STATUS & CAPABILITY VALIDATION
            val targetAccount = socialMediaRepository.getAccountByPlatform(platform)
            val validation = validationEngine.validateActionExecution(
                account = targetAccount,
                requiredCapability = SocialCapability.PUBLISH_POST,
                hasUserPermission = true,
                isApproved = true
            )

            if (validation is AccountValidationResult.Blocked) {
                val blockedResult = PlatformPublishResult(
                    platform = platform,
                    status = ActionApprovalState.FAILED,
                    externalPostId = null,
                    errorMessage = "REAUTH_REQUIRED: ${validation.message}",
                    executionEnvironment = ExecutionEnvironment.MOCK,
                    idempotencyKey = idempotencyKey
                )
                resultMap[platform] = blockedResult
                repository.saveExecutionResult(postId, blockedResult)

                addAuditLog(
                    action = AgentAction.PUBLISH_POST,
                    platform = platform,
                    status = ActionApprovalState.FAILED,
                    error = validation.message,
                    metadata = mapOf("postId" to postId, "event" to "SCHEDULED_POST_ACCOUNT_BLOCKED", "reason" to validation.code)
                )
                continue
            }

            // 2. IDEMPOTENCY / DUPLICATE PROTECTION
            val existingResult = post.platformPublishResults.find { 
                it.platform == platform && it.status == ActionApprovalState.SUCCESS 
            }
            if (existingResult != null) {
                val duplicateResult = existingResult.copy(
                    errorMessage = "ALREADY_COMPLETED: Skip duplicate execution"
                )
                resultMap[platform] = duplicateResult
                continue
            }

            // Execute via Platform Adapter
            val platformService = SocialPlatformRegistry.getService(platform)
            var attempts = post.retryCount
            var lastError: String? = null
            var executionSuccess = false
            var platformResult: PlatformPublishResult? = null

            while (attempts < post.maxRetries && !executionSuccess) {
                attempts++
                val publishResult = platformService.publishPost(postId)

                when (publishResult) {
                    is AppResult.Success -> {
                        executionSuccess = true
                        platformResult = PlatformPublishResult(
                            platform = platform,
                            status = ActionApprovalState.SUCCESS,
                            externalPostId = "mock_ext_${platform.name.lowercase()}_${UUID.randomUUID().toString().take(6)}",
                            errorMessage = null,
                            executionEnvironment = ExecutionEnvironment.MOCK,
                            idempotencyKey = idempotencyKey
                        )
                    }
                    is AppResult.Error -> {
                        lastError = publishResult.error.message
                        val errorType = retryPolicy.classifyError(lastError, publishResult.error.code)

                        if (errorType == SchedulingErrorType.PERMANENT_ERROR) {
                            // Do NOT retry permanent errors
                            break
                        }
                    }
                }
            }

            if (!executionSuccess) {
                platformResult = PlatformPublishResult(
                    platform = platform,
                    status = ActionApprovalState.FAILED,
                    externalPostId = null,
                    errorMessage = lastError ?: "Execution failed after $attempts attempts",
                    executionEnvironment = ExecutionEnvironment.MOCK,
                    idempotencyKey = idempotencyKey
                )
            }

            resultMap[platform] = platformResult!!
            repository.saveExecutionResult(postId, platformResult)
        }

        // Determine overall post success or failure
        val allSuccessful = resultMap.values.all { it.status == ActionApprovalState.SUCCESS }
        val finalStatus = if (allSuccessful) PostStatus.PUBLISHED else PostStatus.FAILED
        val finalApprovalState = if (allSuccessful) ActionApprovalState.SUCCESS else ActionApprovalState.FAILED

        repository.updateStatus(postId, finalStatus, finalApprovalState)

        val summaryPlatformMap = resultMap.mapValues { entry ->
            if (entry.value.status == ActionApprovalState.SUCCESS) "Mock execution completed." else (entry.value.errorMessage ?: "Failed")
        }

        if (allSuccessful) {
            addAuditLog(
                action = AgentAction.PUBLISH_POST,
                platform = post.targetPlatforms.firstOrNull(),
                status = ActionApprovalState.SUCCESS,
                metadata = mapOf("postId" to postId, "event" to "POST_EXECUTION_SUCCESS")
            )
            notificationService.notifyPostPublished(post, summaryPlatformMap)
        } else {
            addAuditLog(
                action = AgentAction.PUBLISH_POST,
                platform = post.targetPlatforms.firstOrNull(),
                status = ActionApprovalState.FAILED,
                error = "Partial or complete failure during publishing",
                metadata = mapOf("postId" to postId, "event" to "POST_EXECUTION_FAILED")
            )
            notificationService.notifyPostFailed(post, "Partial or complete platform publishing failure")
        }

        return AppResult.Success(resultMap)
    }

    override fun getActionLogs(): Flow<List<AgentActionLog>> = logsFlow.asStateFlow()

    private fun addAuditLog(
        action: AgentAction,
        platform: PlatformType?,
        status: ActionApprovalState,
        error: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val log = AgentActionLog(
            action = action,
            platform = platform,
            status = status,
            error = error,
            executionEnvironment = ExecutionEnvironment.MOCK,
            metadata = metadata
        )
        logsFlow.value = logsFlow.value + log
    }
}
