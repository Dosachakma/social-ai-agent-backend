package com.example

import com.example.data.model.*
import com.example.data.remote.MockOAuthService
import com.example.data.remote.MockServerTokenStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FacebookInstagramOAuthTest {

    private lateinit var tokenStore: MockServerTokenStore
    private lateinit var oauthService: MockOAuthService

    @Before
    fun setUp() {
        tokenStore = MockServerTokenStore()
        oauthService = MockOAuthService(tokenStore)
    }

    @Test
    fun testFacebookOAuthConnectionFlow() = runBlocking {
        val sessionRes = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        assertTrue(sessionRes is AppResult.Success)
        val session = (sessionRes as AppResult.Success).data

        val exchangeRes = oauthService.exchangeAuthorizationCode(
            session = session,
            code = "code_facebook_mock",
            state = session.state,
            redirectUri = session.redirectUri
        )

        assertTrue(exchangeRes is AppResult.Success)
        val success = (exchangeRes as AppResult.Success).data
        val account = success.account

        assertEquals(PlatformType.FACEBOOK, account.platform)
        assertEquals(AccountType.PAGE, account.accountType)
        assertEquals(ConnectionStatus.CONNECTED, account.connectionStatus)
        assertEquals(TokenStatus.VALID, account.tokenStatus)

        // Verify capabilities for Facebook Page
        assertTrue(account.availableCapabilities.contains(SocialCapability.CREATE_POST))
        assertTrue(account.availableCapabilities.contains(SocialCapability.PUBLISH_POST))
        assertTrue(account.availableCapabilities.contains(SocialCapability.READ_COMMENTS))
        assertTrue(account.availableCapabilities.contains(SocialCapability.REPLY_COMMENT))
        assertTrue(account.availableCapabilities.contains(SocialCapability.READ_MESSAGES))

        // Check token store has stored the token securely
        val storedTokenRes = tokenStore.getToken("workspace_user_1", OAuthProvider.FACEBOOK)
        assertTrue(storedTokenRes is AppResult.Success)
        assertNotNull((storedTokenRes as AppResult.Success).data)
    }

    @Test
    fun testInstagramBusinessOAuthConnectionFlow() = runBlocking {
        val sessionRes = oauthService.createOAuthSession(OAuthProvider.INSTAGRAM)
        val session = (sessionRes as AppResult.Success).data

        val exchangeRes = oauthService.exchangeAuthorizationCode(
            session = session,
            code = "code_instagram_business",
            state = session.state,
            redirectUri = session.redirectUri
        )

        assertTrue(exchangeRes is AppResult.Success)
        val account = (exchangeRes as AppResult.Success).data.account

        assertEquals(PlatformType.INSTAGRAM, account.platform)
        assertEquals(AccountType.BUSINESS, account.accountType)
        assertTrue(account.availableCapabilities.contains(SocialCapability.PUBLISH_POST))
        assertTrue(account.availableCapabilities.contains(SocialCapability.STORY_PUBLISH))
        assertTrue(account.availableCapabilities.contains(SocialCapability.REEL_PUBLISH))
    }

    @Test
    fun testInstagramPersonalAccount_LacksPublishCapability() = runBlocking {
        val sessionRes = oauthService.createOAuthSession(OAuthProvider.INSTAGRAM)
        val session = (sessionRes as AppResult.Success).data

        val exchangeRes = oauthService.exchangeAuthorizationCode(
            session = session,
            code = "code_instagram_personal",
            state = session.state,
            redirectUri = session.redirectUri
        )

        assertTrue(exchangeRes is AppResult.Success)
        val account = (exchangeRes as AppResult.Success).data.account

        assertEquals(PlatformType.INSTAGRAM, account.platform)
        assertEquals(AccountType.PERSONAL, account.accountType)

        // Personal Instagram lacks automated publishing per Meta API restrictions
        assertTrue(account.availableCapabilities.contains(SocialCapability.CREATE_POST))
        assertFalse(account.availableCapabilities.contains(SocialCapability.PUBLISH_POST))
        assertFalse(account.availableCapabilities.contains(SocialCapability.STORY_PUBLISH))
    }
}
