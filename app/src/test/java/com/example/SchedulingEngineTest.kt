package com.example

import com.example.data.model.*
import com.example.data.notification.MockNotificationService
import com.example.data.repository.MockScheduledPostRepository
import com.example.data.scheduler.DefaultSchedulerService
import com.example.data.scheduler.RetryPolicy
import com.example.data.scheduler.SchedulingErrorType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SchedulingEngineTest {

    private lateinit var repository: MockScheduledPostRepository
    private lateinit var notificationService: MockNotificationService
    private lateinit var schedulerService: DefaultSchedulerService

    @Before
    fun setup() {
        repository = MockScheduledPostRepository()
        notificationService = MockNotificationService()
        schedulerService = DefaultSchedulerService(
            repository = repository,
            notificationService = notificationService
        )
    }

    @Test
    fun testSchedulePostRequiringApproval() = runTest {
        val post = SocialPost(
            title = "Test Post",
            content = "Testing scheduling with approval",
            targetPlatforms = listOf(PlatformType.FACEBOOK, PlatformType.INSTAGRAM),
            scheduledTime = "Tomorrow at 10:00 AM",
            scheduledAt = "2026-08-13T10:00:00",
            timezone = "America/New_York",
            requireApproval = true
        )

        val result = schedulerService.schedulePost(post)
        assertTrue(result.isSuccess)

        val scheduledPost = result.getOrNull()
        assertNotNull(scheduledPost)
        assertEquals(ActionApprovalState.AWAITING_APPROVAL, scheduledPost?.approvalState)
        assertEquals(PostStatus.DRAFT, scheduledPost?.status)

        val notifications = notificationService.getSentNotifications()
        assertTrue(notifications.any { it.title == "Approval Required" })
    }

    @Test
    fun testApprovalSafetyBlocksUnapprovedExecution() = runTest {
        val post = SocialPost(
            id = "unapproved_1",
            title = "Unapproved Post",
            content = "This should not run automatically",
            targetPlatforms = listOf(PlatformType.TWITTER),
            scheduledTime = "Tomorrow at 10:00 AM",
            requireApproval = true,
            approvalState = ActionApprovalState.AWAITING_APPROVAL,
            status = PostStatus.DRAFT
        )

        repository.create(post)

        val execResult = schedulerService.executeScheduledPost("unapproved_1")
        assertTrue(execResult.isError)

        val error = (execResult as AppResult.Error).error
        assertEquals("APPROVAL_REQUIRED", error.code)
    }

    @Test
    fun testApprovedPostExecutesSuccessfully() = runTest {
        val post = SocialPost(
            id = "approved_1",
            title = "Approved Post",
            content = "Ready for mock publishing",
            targetPlatforms = listOf(PlatformType.FACEBOOK, PlatformType.LINKEDIN),
            scheduledTime = "Tomorrow at 10:00 AM",
            requireApproval = true,
            approvalState = ActionApprovalState.APPROVED,
            status = PostStatus.SCHEDULED
        )

        repository.create(post)

        val execResult = schedulerService.executeScheduledPost("approved_1")
        assertTrue(execResult.isSuccess)

        val resultMap = execResult.getOrNull()
        assertNotNull(resultMap)
        assertEquals(2, resultMap?.size)
        assertTrue(resultMap?.values?.all { it.status == ActionApprovalState.SUCCESS } == true)

        val updatedPost = repository.getById("approved_1").getOrNull()
        assertEquals(PostStatus.PUBLISHED, updatedPost?.status)
        assertEquals(ActionApprovalState.SUCCESS, updatedPost?.approvalState)

        val notifications = notificationService.getSentNotifications()
        assertTrue(notifications.any { it.title == "Post Published (Mock)" })
    }

    @Test
    fun testIdempotencyPreventsDuplicateExecution() = runTest {
        val post = SocialPost(
            id = "idempotent_1",
            title = "Idempotency Test",
            content = "Should execute once and skip on retry",
            targetPlatforms = listOf(PlatformType.INSTAGRAM),
            scheduledTime = "Today at 2:00 PM",
            requireApproval = false,
            approvalState = ActionApprovalState.APPROVED,
            status = PostStatus.SCHEDULED
        )

        repository.create(post)

        // First execution
        schedulerService.executeScheduledPost("idempotent_1")

        // Second execution
        val secondExecResult = schedulerService.executeScheduledPost("idempotent_1")
        assertTrue(secondExecResult.isSuccess)

        val results = secondExecResult.getOrNull()
        val igResult = results?.get(PlatformType.INSTAGRAM)
        assertNotNull(igResult)
        assertTrue(igResult?.errorMessage?.contains("ALREADY_COMPLETED") == true)
    }

    @Test
    fun testRescheduleAndCancel() = runTest {
        val post = SocialPost(
            id = "reschedule_1",
            title = "Reschedule Test",
            content = "Content to reschedule",
            targetPlatforms = listOf(PlatformType.TIKTOK),
            scheduledTime = "Tomorrow at 5:00 PM",
            timezone = "America/New_York",
            requireApproval = false,
            approvalState = ActionApprovalState.APPROVED,
            status = PostStatus.SCHEDULED
        )

        repository.create(post)

        // Reschedule
        val reschedRes = schedulerService.reschedulePost("reschedule_1", "2026-08-20T18:00:00", "UTC")
        assertTrue(reschedRes.isSuccess)
        val updated = reschedRes.getOrNull()
        assertEquals("2026-08-20T18:00:00", updated?.scheduledAt)
        assertEquals("UTC", updated?.timezone)

        // Cancel
        val cancelRes = schedulerService.cancelScheduledPost("reschedule_1")
        assertTrue(cancelRes.isSuccess)

        val cancelledPost = repository.getById("reschedule_1").getOrNull()
        assertEquals(ActionApprovalState.CANCELLED, cancelledPost?.approvalState)
    }

    @Test
    fun testPermanentErrorClassification() {
        val retryPolicy = RetryPolicy()
        val isPermAuth = retryPolicy.isPermanentError("Invalid authentication token", "AUTH_ERROR")
        val isPermPerm = retryPolicy.isPermanentError("Permission denied for target channel", "PERMISSION_DENIED")
        val isTransient = retryPolicy.isPermanentError("Network timeout connecting to endpoint", "TIMEOUT")

        assertTrue(isPermAuth)
        assertTrue(isPermPerm)
        assertFalse(isTransient)

        assertEquals(SchedulingErrorType.PERMANENT_ERROR, retryPolicy.classifyError("Invalid auth token"))
        assertEquals(SchedulingErrorType.TRANSIENT_ERROR, retryPolicy.classifyError("Socket timeout"))
    }

    @Test
    fun testAuditLogsGenerated() = runTest {
        val post = SocialPost(
            id = "audit_1",
            title = "Audit Post",
            content = "Generating audit logs",
            targetPlatforms = listOf(PlatformType.TWITTER),
            scheduledTime = "Tomorrow at 1:00 PM",
            requireApproval = false,
            approvalState = ActionApprovalState.APPROVED,
            status = PostStatus.SCHEDULED
        )

        schedulerService.schedulePost(post)
        schedulerService.executeScheduledPost("audit_1")

        val logs = schedulerService.getActionLogs().first()
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.any { it.metadata["event"] == "POST_SCHEDULED" })
        assertTrue(logs.any { it.metadata["event"] == "POST_EXECUTION_SUCCESS" })
    }
}
