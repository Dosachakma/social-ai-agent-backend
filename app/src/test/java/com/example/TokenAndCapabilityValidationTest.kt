package com.example

import com.example.data.ai.*
import com.example.data.model.*
import com.example.data.remote.MockOAuthService
import com.example.data.remote.MockServerTokenStore
import com.example.data.repository.MockScheduledPostRepository
import com.example.data.repository.MockSocialMediaRepository
import com.example.data.scheduler.DefaultSchedulerService
import com.example.data.security.AccountValidationEngine
import com.example.data.security.AccountValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TokenAndCapabilityValidationTest {

    private lateinit var validationEngine: AccountValidationEngine
    private lateinit var socialMediaRepository: MockSocialMediaRepository
    private lateinit var scheduledPostRepository: MockScheduledPostRepository
    private lateinit var schedulerService: DefaultSchedulerService

    @Before
    fun setUp() {
        validationEngine = AccountValidationEngine()
        val tokenStore = MockServerTokenStore()
        val oauthService = MockOAuthService(tokenStore)
        socialMediaRepository = MockSocialMediaRepository(tokenStore, oauthService)
        scheduledPostRepository = MockScheduledPostRepository()
        schedulerService = DefaultSchedulerService(
            repository = scheduledPostRepository,
            socialMediaRepository = socialMediaRepository,
            validationEngine = validationEngine
        )
    }

    @Test
    fun testValidationEngine_ValidAccount_Allowed() {
        val account = SocialAccount(
            accountName = "Connected Facebook",
            handle = "@facebook_page",
            platform = PlatformType.FACEBOOK,
            connectionStatus = ConnectionStatus.CONNECTED,
            tokenStatus = TokenStatus.VALID,
            availableCapabilities = listOf(SocialCapability.PUBLISH_POST),
            isConnected = true
        )

        val result = validationEngine.validateActionExecution(
            account = account,
            requiredCapability = SocialCapability.PUBLISH_POST,
            hasUserPermission = true,
            isApproved = true
        )

        assertTrue(result is AccountValidationResult.Allowed)
    }

    @Test
    fun testValidationEngine_DisconnectedAccount_Blocked() {
        val account = SocialAccount(
            accountName = "Disconnected Facebook",
            handle = "@facebook_page",
            platform = PlatformType.FACEBOOK,
            connectionStatus = ConnectionStatus.DISCONNECTED,
            tokenStatus = TokenStatus.UNKNOWN,
            availableCapabilities = listOf(SocialCapability.PUBLISH_POST),
            isConnected = false
        )

        val result = validationEngine.validateActionExecution(
            account = account,
            requiredCapability = SocialCapability.PUBLISH_POST,
            hasUserPermission = true,
            isApproved = true
        )

        assertTrue(result is AccountValidationResult.Blocked)
        assertEquals("DISCONNECTED", (result as AccountValidationResult.Blocked).code)
    }

    @Test
    fun testValidationEngine_ExpiredToken_Blocked() {
        val account = SocialAccount(
            accountName = "Expired Facebook",
            handle = "@facebook_page",
            platform = PlatformType.FACEBOOK,
            connectionStatus = ConnectionStatus.REAUTH_REQUIRED,
            tokenStatus = TokenStatus.EXPIRED,
            availableCapabilities = listOf(SocialCapability.PUBLISH_POST),
            isConnected = true
        )

        val result = validationEngine.validateActionExecution(
            account = account,
            requiredCapability = SocialCapability.PUBLISH_POST,
            hasUserPermission = true,
            isApproved = true
        )

        assertTrue(result is AccountValidationResult.Blocked)
        assertEquals("EXPIRED_TOKEN", (result as AccountValidationResult.Blocked).code)
    }

    @Test
    fun testValidationEngine_MissingCapability_Blocked() {
        val account = SocialAccount(
            accountName = "Personal Instagram",
            handle = "@personal_insta",
            platform = PlatformType.INSTAGRAM,
            accountType = AccountType.PERSONAL,
            connectionStatus = ConnectionStatus.CONNECTED,
            tokenStatus = TokenStatus.VALID,
            availableCapabilities = listOf(SocialCapability.CREATE_POST, SocialCapability.READ_ANALYTICS),
            isConnected = true
        )

        val result = validationEngine.validateActionExecution(
            account = account,
            requiredCapability = SocialCapability.PUBLISH_POST,
            hasUserPermission = true,
            isApproved = true
        )

        assertTrue(result is AccountValidationResult.Blocked)
        assertEquals("MISSING_CAPABILITY", (result as AccountValidationResult.Blocked).code)
    }

    @Test
    fun testSchedulerSafety_BlocksExecutionWhenTokenExpired() = runBlocking {
        // Disconnect Facebook
        val fbAccount = socialMediaRepository.getAccountByPlatform(PlatformType.FACEBOOK)
        assertNotNull(fbAccount)
        socialMediaRepository.updateTokenStatus(fbAccount!!.id, TokenStatus.EXPIRED)

        val post = SocialPost(
            id = "sched_expired_test",
            title = "Test Scheduled Post",
            content = "Hello World",
            targetPlatforms = listOf(PlatformType.FACEBOOK),
            scheduledTime = "2026-08-15 12:00",
            status = PostStatus.SCHEDULED,
            requireApproval = false,
            approvalState = ActionApprovalState.APPROVED
        )
        scheduledPostRepository.create(post)

        val execResult = schedulerService.executeScheduledPost("sched_expired_test")
        assertTrue(execResult is AppResult.Success)

        val publishResults = (execResult as AppResult.Success).data
        val fbResult = publishResults[PlatformType.FACEBOOK]
        assertNotNull(fbResult)
        assertEquals(ActionApprovalState.FAILED, fbResult!!.status)
        assertTrue(fbResult.errorMessage!!.contains("REAUTH_REQUIRED"))
    }

    @Test
    fun testAgentToolExecution_BlocksWhenDisconnectedOrMissingCapability() = runBlocking {
        val publishTool = PublishPostTool()

        val disconnectedAccount = SocialAccount(
            accountName = "Offline Account",
            handle = "@offline",
            platform = PlatformType.FACEBOOK,
            connectionStatus = ConnectionStatus.DISCONNECTED,
            tokenStatus = TokenStatus.UNKNOWN,
            availableCapabilities = listOf(SocialCapability.PUBLISH_POST),
            isConnected = false
        )

        publishTool.targetAccountOverride = disconnectedAccount
        val resultRes = publishTool.execute(mapOf("content" to "Test draft"))
        assertTrue(resultRes is AppResult.Success)

        val toolResult = (resultRes as AppResult.Success).data
        assertFalse(toolResult.success)
        assertEquals("VALIDATION_FAILED", toolResult.status)
        assertEquals("DISCONNECTED", toolResult.error)
    }
}
