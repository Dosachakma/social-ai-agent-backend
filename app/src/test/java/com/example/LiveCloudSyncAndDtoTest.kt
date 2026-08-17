package com.example

import com.example.data.model.*
import com.example.data.remote.dto.*
import com.example.data.remote.session.SessionState
import com.example.data.remote.session.WorkspaceSessionManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LiveCloudSyncAndDtoTest {

    @Before
    fun setup() {
        WorkspaceSessionManager.reset()
    }

    @Test
    fun testWorkspaceSessionManagerDefaults() {
        assertEquals(SessionState.DEFAULT_WORKSPACE_ID, WorkspaceSessionManager.getWorkspaceId())
        assertFalse(WorkspaceSessionManager.isDemoMode())
        assertTrue(WorkspaceSessionManager.getAuthHeader().startsWith("Bearer "))

        WorkspaceSessionManager.setDemoMode(true)
        assertTrue(WorkspaceSessionManager.isDemoMode())

        WorkspaceSessionManager.setWorkspaceId("custom-workspace-uuid")
        assertEquals("custom-workspace-uuid", WorkspaceSessionManager.getWorkspaceId())
    }

    @Test
    fun testSocialAccountDtoMapping() {
        val dto = SocialAccountDto(
            id = "acc-uuid-123",
            workspaceId = "ws-1",
            platform = "FACEBOOK",
            platformUserId = "fb_12345",
            accountName = "Acme Corp Page",
            handle = "@acmecorp",
            avatarUrl = "https://example.com/avatar.png",
            accountType = "PAGE",
            connectionStatus = "CONNECTED",
            tokenStatus = "VALID",
            scopes = listOf("pages_manage_posts", "pages_read_engagement"),
            followerCount = 45000,
            lastSyncedAt = "2026-08-16T12:00:00Z"
        )

        val account = DtoMappers.toSocialAccount(dto)
        assertEquals("acc-uuid-123", account.id)
        assertEquals(PlatformType.FACEBOOK, account.platform)
        assertEquals("Acme Corp Page", account.accountName)
        assertEquals("@acmecorp", account.handle)
        assertTrue(account.isConnected)
        assertEquals(TokenStatus.VALID, account.tokenStatus)
        assertEquals(45000, account.followerCount)
        assertEquals(AccountType.PAGE, account.accountType)
    }

    @Test
    fun testSocialPostDtoMapping() {
        val dto = SocialPostDto(
            id = "post-uuid-456",
            workspaceId = "ws-1",
            title = "Product Launch Announcement",
            content = "Excited to unveil our new AI agent capabilities!",
            targetPlatforms = listOf("LINKEDIN", "TWITTER"),
            status = "SCHEDULED",
            approvalState = "APPROVED",
            scheduledAt = "2026-08-20T10:00:00Z",
            timezone = "UTC",
            requireApproval = false,
            mediaUrls = listOf("https://example.com/hero.jpg"),
            publishResults = listOf(
                PlatformPublishResultDto(
                    id = "res-1",
                    platform = "LINKEDIN",
                    status = "SUCCESS",
                    externalPostId = "urn:li:share:123456",
                    executionEnvironment = "PRODUCTION"
                )
            )
        )

        val post = DtoMappers.toSocialPost(dto)
        assertEquals("post-uuid-456", post.id)
        assertEquals("Product Launch Announcement", post.title)
        assertEquals(2, post.targetPlatforms.size)
        assertTrue(post.targetPlatforms.contains(PlatformType.LINKEDIN))
        assertTrue(post.targetPlatforms.contains(PlatformType.TWITTER))
        assertEquals(PostStatus.SCHEDULED, post.status)
        assertEquals(ActionApprovalState.APPROVED, post.approvalState)
        assertEquals(1, post.platformPublishResults.size)
        assertEquals(ExecutionEnvironment.PRODUCTION, post.platformPublishResults.first().executionEnvironment)
    }

    @Test
    fun testBrandProfileDtoMapping() {
        val dto = BrandProfileDto(
            id = "brand-uuid-789",
            workspaceId = "ws-1",
            name = "Nexus AI",
            industry = "SaaS / AI",
            toneOfVoice = "PROFESSIONAL",
            targetAudience = "Tech founders and CMOs",
            keywords = listOf("Autonomous Growth", "Enterprise Security"),
            wordsToAvoid = listOf("guaranteed ROI", "cheap"),
            businessDescription = "Powered by Nexus AI Engine"
        )

        val profile = DtoMappers.toBrandProfile(dto)
        assertEquals("brand-uuid-789", profile.id)
        assertEquals("Nexus AI", profile.brandName)
        assertEquals(BrandTone.PROFESSIONAL, profile.brandTone)
        assertTrue(profile.brandKeywords.contains("Autonomous Growth"))
        assertTrue(profile.wordsToAvoid.contains("guaranteed ROI"))
    }
}
