package com.example

import com.example.data.model.*
import com.example.data.remote.MockOAuthService
import com.example.data.remote.MockServerTokenStore
import com.example.data.repository.MockSocialMediaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AccountDisconnectTest {

    private lateinit var tokenStore: MockServerTokenStore
    private lateinit var oauthService: MockOAuthService
    private lateinit var repository: MockSocialMediaRepository

    @Before
    fun setUp() {
        tokenStore = MockServerTokenStore()
        oauthService = MockOAuthService(tokenStore)
        repository = MockSocialMediaRepository(tokenStore, oauthService)
    }

    @Test
    fun testDisconnectAccount_FullFlow() = runBlocking {
        // First connect Facebook account
        val connectRes = repository.connectAccount(OAuthProvider.FACEBOOK)
        assertTrue(connectRes is AppResult.Success)
        val connectedAccount = (connectRes as AppResult.Success).data
        assertTrue(connectedAccount.isConnected)

        // Verify token exists in store
        val tokenBefore = tokenStore.getToken("workspace_user_1", OAuthProvider.FACEBOOK)
        assertTrue(tokenBefore is AppResult.Success)
        assertNotNull((tokenBefore as AppResult.Success).data)

        // Perform disconnect
        val disconnectRes = repository.disconnectAccount(connectedAccount.id)
        assertTrue(disconnectRes is AppResult.Success)

        // Verify token cleared from store
        val tokenAfter = tokenStore.getToken("workspace_user_1", OAuthProvider.FACEBOOK)
        assertTrue(tokenAfter is AppResult.Success)
        assertNull((tokenAfter as AppResult.Success).data)

        // Verify account connection status is updated to DISCONNECTED
        val updatedAccount = repository.getAccountByPlatform(PlatformType.FACEBOOK)
        assertNotNull(updatedAccount)
        assertEquals(ConnectionStatus.DISCONNECTED, updatedAccount!!.connectionStatus)
        assertEquals(TokenStatus.UNKNOWN, updatedAccount.tokenStatus)
        assertFalse(updatedAccount.isConnected)

        // Verify audit log generated
        val logs = repository.getRecentActivity().first()
        val disconnectLog = logs.find { it.actionType == "OAuth Disconnect" }
        assertNotNull(disconnectLog)
        assertTrue(disconnectLog!!.detail.contains("Disconnected"))
    }

    @Test
    fun testDisconnectAccount_PausesScheduledPosts() = runBlocking {
        // Schedule a post targeting Facebook
        val scheduledPost = SocialPost(
            id = "test_sched_1",
            title = "Test Scheduled Post",
            content = "Post content",
            targetPlatforms = listOf(PlatformType.FACEBOOK, PlatformType.TWITTER),
            scheduledTime = "2026-08-20 10:00",
            status = PostStatus.SCHEDULED,
            requireApproval = false,
            approvalState = ActionApprovalState.APPROVED
        )
        repository.createPost(scheduledPost)

        val fbAccount = repository.getAccountByPlatform(PlatformType.FACEBOOK)
        assertNotNull(fbAccount)

        // Disconnect Facebook
        repository.disconnectAccount(fbAccount!!.id)

        // Verify scheduled post targeting Facebook was failed/paused with error message
        val posts = repository.getAllPosts().first()
        val targetPost = posts.find { it.id == "test_sched_1" }
        assertNotNull(targetPost)
        assertEquals(PostStatus.FAILED, targetPost!!.status)
        assertTrue(targetPost.errorMessage!!.contains("REAUTH_REQUIRED"))
    }
}
