package com.example

import com.example.data.model.*
import com.example.data.remote.MockOAuthService
import com.example.data.remote.MockServerTokenStore
import com.example.data.remote.OAuthSessionValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OAuthServiceTest {

    private lateinit var tokenStore: MockServerTokenStore
    private lateinit var oauthService: MockOAuthService

    @Before
    fun setUp() {
        tokenStore = MockServerTokenStore()
        oauthService = MockOAuthService(tokenStore)
    }

    @Test
    fun testCreateOAuthSession_Success() = runBlocking {
        val result = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        assertTrue(result is AppResult.Success)
        val session = (result as AppResult.Success).data
        assertEquals(OAuthProvider.FACEBOOK, session.provider)
        assertNotNull(session.state)
        assertTrue(session.state.isNotBlank())
        assertFalse(session.isExpired)
    }

    @Test
    fun testValidateCallback_ValidSession() {
        val session = OAuthSession(
            provider = OAuthProvider.FACEBOOK,
            state = "valid_state_123",
            redirectUri = "https://app.socialagent.ai/oauth/callback"
        )
        val result = OAuthSessionValidator.validateCallback(
            session = session,
            receivedState = "valid_state_123",
            receivedProvider = OAuthProvider.FACEBOOK,
            receivedRedirectUri = "https://app.socialagent.ai/oauth/callback"
        )
        assertTrue(result is AppResult.Success)
    }

    @Test
    fun testValidateCallback_StateMismatch_CSRFRejected() {
        val session = OAuthSession(
            provider = OAuthProvider.FACEBOOK,
            state = "valid_state_123",
            redirectUri = "https://app.socialagent.ai/oauth/callback"
        )
        val result = OAuthSessionValidator.validateCallback(
            session = session,
            receivedState = "malicious_state_456",
            receivedProvider = OAuthProvider.FACEBOOK,
            receivedRedirectUri = "https://app.socialagent.ai/oauth/callback"
        )
        assertTrue(result is AppResult.Error)
        assertEquals("CSRF_ERROR", (result as AppResult.Error).error.code)
    }

    @Test
    fun testValidateCallback_ProviderMismatch() {
        val session = OAuthSession(
            provider = OAuthProvider.FACEBOOK,
            state = "valid_state_123",
            redirectUri = "https://app.socialagent.ai/oauth/callback"
        )
        val result = OAuthSessionValidator.validateCallback(
            session = session,
            receivedState = "valid_state_123",
            receivedProvider = OAuthProvider.INSTAGRAM,
            receivedRedirectUri = "https://app.socialagent.ai/oauth/callback"
        )
        assertTrue(result is AppResult.Error)
        assertEquals("PROVIDER_MISMATCH", (result as AppResult.Error).error.code)
    }

    @Test
    fun testValidateCallback_ExpiredSession() {
        val expiredSession = OAuthSession(
            provider = OAuthProvider.FACEBOOK,
            state = "valid_state_123",
            createdAt = System.currentTimeMillis() - 700000, // 700 seconds ago > 600s TTL
            redirectUri = "https://app.socialagent.ai/oauth/callback"
        )
        val result = OAuthSessionValidator.validateCallback(
            session = expiredSession,
            receivedState = "valid_state_123",
            receivedProvider = OAuthProvider.FACEBOOK,
            receivedRedirectUri = "https://app.socialagent.ai/oauth/callback"
        )
        assertTrue(result is AppResult.Error)
        assertEquals("SESSION_EXPIRED", (result as AppResult.Error).error.code)
    }

    @Test
    fun testValidateCallback_RedirectUriMismatch() {
        val session = OAuthSession(
            provider = OAuthProvider.FACEBOOK,
            state = "valid_state_123",
            redirectUri = "https://app.socialagent.ai/oauth/callback"
        )
        val result = OAuthSessionValidator.validateCallback(
            session = session,
            receivedState = "valid_state_123",
            receivedProvider = OAuthProvider.FACEBOOK,
            receivedRedirectUri = "https://attacker.com/callback"
        )
        assertTrue(result is AppResult.Error)
        assertEquals("REDIRECT_URI_MISMATCH", (result as AppResult.Error).error.code)
    }
}
