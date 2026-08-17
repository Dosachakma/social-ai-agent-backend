package com.example

import com.example.data.config.MetaOAuthConfig
import com.example.data.config.SecurityConfig
import com.example.data.model.*
import com.example.data.remote.*
import com.example.data.repository.MockSocialMediaRepository
import com.example.data.security.AccountValidationEngine
import com.example.data.security.AccountValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MetaOAuthFoundationTest {

    private lateinit var tokenStore: MockServerTokenStore
    private lateinit var mockOAuthBackend: MockMetaTokenExchangeBackend
    private lateinit var configuredMetaService: RealMetaOAuthService
    private lateinit var unconfiguredMetaService: RealMetaOAuthService

    class MockMetaTokenExchangeBackend : MetaTokenExchangeBackend {
        var shouldFail: Boolean = false
        var failureCode: String = "TOKEN_EXCHANGE_FAILED"

        override suspend fun exchangeCodeForToken(
            code: String,
            redirectUri: String
        ): AppResult<SocialAccessToken> {
            if (shouldFail) {
                return AppResult.Error(AgentError(failureCode, "Mock token exchange failed"))
            }
            return AppResult.Success(
                SocialAccessToken(
                    accessToken = "live_meta_access_token_12345",
                    refreshToken = "live_meta_refresh_token_67890",
                    expiresInSeconds = 5184000,
                    scope = listOf("public_profile", "pages_manage_posts", "instagram_content_publish")
                )
            )
        }

        override suspend fun fetchFacebookPages(
            accessToken: SocialAccessToken
        ): AppResult<List<SocialPage>> {
            return AppResult.Success(
                listOf(
                    SocialPage(
                        platform = PlatformType.FACEBOOK,
                        platformAccountId = "fb_page_live_1001",
                        name = "Live Meta Brand Page",
                        category = "Brand",
                        accountType = AccountType.PAGE
                    )
                )
            )
        }

        override suspend fun fetchInstagramAccounts(
            accessToken: SocialAccessToken
        ): AppResult<List<SocialAccount>> {
            return AppResult.Success(emptyList())
        }
    }

    @Before
    fun setUp() {
        tokenStore = MockServerTokenStore()
        mockOAuthBackend = MockMetaTokenExchangeBackend()

        val liveConfig = MetaOAuthConfig(
            appId = "123456789012345",
            redirectUri = "https://socialagent.app/oauth/callback",
            environment = ExecutionEnvironment.REAL
        )
        configuredMetaService = RealMetaOAuthService(
            config = liveConfig,
            tokenStore = tokenStore,
            tokenExchangeBackend = mockOAuthBackend
        )

        val unconfiguredConfig = MetaOAuthConfig(
            appId = null,
            environment = ExecutionEnvironment.REAL
        )
        unconfiguredMetaService = RealMetaOAuthService(
            config = unconfiguredConfig,
            tokenStore = tokenStore,
            tokenExchangeBackend = UnconfiguredMetaTokenExchangeBackend()
        )
    }

    @Test
    fun testMetaOAuthConfig_NoSecretExposure_GeneratesValidAuthUrl() {
        val config = MetaOAuthConfig(
            appId = "987654321098765",
            redirectUri = "https://socialagent.app/oauth/callback"
        )

        assertTrue(config.isConfigured)
        val authUrl = config.generateAuthorizationUrl("test_state_123")

        assertTrue(authUrl.contains("client_id=987654321098765"))
        assertTrue(authUrl.contains("redirect_uri=https://socialagent.app/oauth/callback"))
        assertTrue(authUrl.contains("state=test_state_123"))
        assertTrue(authUrl.contains("scope="))

        // Security check: Must not contain appSecret or client secret string
        assertFalse(authUrl.contains("secret"))
        assertFalse(config.toString().contains("appSecret"))
    }

    @Test
    fun testFacebookOAuthFlow_ConfiguredBackend_Success() = runBlocking {
        val sessionRes = configuredMetaService.createOAuthSession(OAuthProvider.FACEBOOK)
        assertTrue(sessionRes is AppResult.Success)
        val session = (sessionRes as AppResult.Success).data

        assertEquals(OAuthProvider.FACEBOOK, session.provider)
        assertNotNull(session.state)

        val exchangeRes = configuredMetaService.exchangeAuthorizationCode(
            session = session,
            code = "valid_live_code_fb",
            state = session.state,
            redirectUri = session.redirectUri
        )

        assertTrue(exchangeRes is AppResult.Success)
        val success = (exchangeRes as AppResult.Success).data
        val account = success.account

        assertEquals(PlatformType.FACEBOOK, account.platform)
        assertEquals(AccountType.PAGE, account.accountType)
        assertEquals(ConnectionStatus.CONNECTED, account.connectionStatus)
        assertTrue(account.availableCapabilities.contains(SocialCapability.PUBLISH_POST))

        // Verify token stored in ServerTokenStore safely
        val storedTokenRes = tokenStore.getToken("workspace_user_1", OAuthProvider.FACEBOOK)
        assertTrue(storedTokenRes is AppResult.Success)
        assertNotNull((storedTokenRes as AppResult.Success).data)
    }

    @Test
    fun testFacebookOAuthFlow_UnconfiguredBackend_FailsSafely() = runBlocking {
        val sessionRes = unconfiguredMetaService.createOAuthSession(OAuthProvider.FACEBOOK)
        assertTrue(sessionRes is AppResult.Error)
        val err = (sessionRes as AppResult.Error).error
        assertEquals("META_OAUTH_NOT_CONFIGURED", err.code)
    }

    @Test
    fun testInstagramOAuthFlow_BusinessVsPersonalCapabilities() = runBlocking {
        // Test Business Instagram
        val sessionRes1 = configuredMetaService.createOAuthSession(OAuthProvider.INSTAGRAM)
        val session1 = (sessionRes1 as AppResult.Success).data
        val bizRes = configuredMetaService.exchangeAuthorizationCode(session1, "code_ig_business", session1.state, session1.redirectUri)
        assertTrue(bizRes is AppResult.Success)
        val bizAccount = (bizRes as AppResult.Success).data.account

        assertEquals(AccountType.BUSINESS, bizAccount.accountType)
        assertTrue(bizAccount.availableCapabilities.contains(SocialCapability.PUBLISH_POST))
        assertTrue(bizAccount.availableCapabilities.contains(SocialCapability.STORY_PUBLISH))

        // Test Personal Instagram
        val sessionRes2 = configuredMetaService.createOAuthSession(OAuthProvider.INSTAGRAM)
        val session2 = (sessionRes2 as AppResult.Success).data
        val personalRes = configuredMetaService.exchangeAuthorizationCode(session2, "code_ig_personal", session2.state, session2.redirectUri)
        assertTrue(personalRes is AppResult.Success)
        val personalAccount = (personalRes as AppResult.Success).data.account

        assertEquals(AccountType.PERSONAL, personalAccount.accountType)
        assertTrue(personalAccount.availableCapabilities.contains(SocialCapability.CREATE_POST))
        assertFalse(personalAccount.availableCapabilities.contains(SocialCapability.PUBLISH_POST))
    }

    @Test
    fun testOAuthCallbackValidation_SecurityChecks() {
        val session = OAuthSession(
            provider = OAuthProvider.FACEBOOK,
            state = "secure_state_999",
            redirectUri = "https://socialagent.app/oauth/callback"
        )

        // Valid
        assertTrue(OAuthSessionValidator.validateCallback(session, "secure_state_999", OAuthProvider.FACEBOOK, "https://socialagent.app/oauth/callback").isSuccess)

        // CSRF State mismatch
        val csrfRes = OAuthSessionValidator.validateCallback(session, "attacker_state", OAuthProvider.FACEBOOK, "https://socialagent.app/oauth/callback")
        assertTrue(csrfRes is AppResult.Error)
        assertEquals("CSRF_ERROR", (csrfRes as AppResult.Error).error.code)

        // Provider Mismatch
        val providerRes = OAuthSessionValidator.validateCallback(session, "secure_state_999", OAuthProvider.INSTAGRAM, "https://socialagent.app/oauth/callback")
        assertTrue(providerRes is AppResult.Error)
        assertEquals("PROVIDER_MISMATCH", (providerRes as AppResult.Error).error.code)

        // Redirect URI Mismatch
        val redirectRes = OAuthSessionValidator.validateCallback(session, "secure_state_999", OAuthProvider.FACEBOOK, "https://attacker.com/callback")
        assertTrue(redirectRes is AppResult.Error)
        assertEquals("REDIRECT_URI_MISMATCH", (redirectRes as AppResult.Error).error.code)
    }

    @Test
    fun testAuthorizationCodeSecurity_NoTokenOrCodeLeaksInModels() {
        val token = SocialAccessToken(
            accessToken = "secret_raw_token_value_abc123",
            refreshToken = "secret_refresh_token_xyz789"
        )

        // SocialAccessToken.toString() MUST redact sensitive token strings
        val tokenString = token.toString()
        assertFalse(tokenString.contains("secret_raw_token_value_abc123"))
        assertFalse(tokenString.contains("secret_refresh_token_xyz789"))
        assertTrue(tokenString.contains("[REDACTED]"))

        // SocialAccount does not contain token fields
        val account = SocialAccount(
            accountName = "Test Account",
            handle = "@test",
            platform = PlatformType.FACEBOOK
        )
        assertFalse(account.toString().contains("accessToken"))
        assertFalse(account.toString().contains("code"))
    }

    @Test
    fun testOAuthCancellation_ReturnsUserCancelledError() = runBlocking {
        val sessionRes = configuredMetaService.createOAuthSession(OAuthProvider.FACEBOOK)
        val session = (sessionRes as AppResult.Success).data

        val exchangeRes = configuredMetaService.exchangeAuthorizationCode(
            session = session,
            code = "USER_CANCELLED",
            state = session.state,
            redirectUri = session.redirectUri
        )

        assertTrue(exchangeRes is AppResult.Error)
        assertEquals("USER_CANCELLED", (exchangeRes as AppResult.Error).error.code)
    }

    @Test
    fun testOAuthErrorMapping_StructuredErrors() = runBlocking {
        val sessionRes = configuredMetaService.createOAuthSession(OAuthProvider.FACEBOOK)
        val session = (sessionRes as AppResult.Success).data

        // Missing code
        val missingCodeRes = configuredMetaService.exchangeAuthorizationCode(session, "", session.state, session.redirectUri)
        assertTrue(missingCodeRes is AppResult.Error)
        assertEquals("AUTHORIZATION_CODE_MISSING", (missingCodeRes as AppResult.Error).error.code)

        // Access denied
        val accessDeniedRes = configuredMetaService.exchangeAuthorizationCode(session, "ACCESS_DENIED", session.state, session.redirectUri)
        assertTrue(accessDeniedRes is AppResult.Error)
        assertEquals("ACCESS_DENIED", (accessDeniedRes as AppResult.Error).error.code)
    }

    @Test
    fun testDuplicateAccountPrevention_UpdatesExistingAccount() = runBlocking {
        val repository = MockSocialMediaRepository(tokenStore, configuredMetaService)

        // Connect Facebook once
        val res1 = repository.connectAccount(OAuthProvider.FACEBOOK, "code_live_fb_1")
        assertTrue(res1 is AppResult.Success)
        val acc1 = (res1 as AppResult.Success).data

        val accountsBefore = repository.getAccountByPlatform(PlatformType.FACEBOOK)
        assertNotNull(accountsBefore)

        // Reconnect same account
        val res2 = repository.connectAccount(OAuthProvider.FACEBOOK, "code_live_fb_1")
        assertTrue(res2 is AppResult.Success)
        val acc2 = (res2 as AppResult.Success).data

        // ID should match existing account rather than duplicating
        assertEquals(acc1.id, acc2.id)
    }

    @Test
    fun testCapabilityMapping_AccountValidationEngine() {
        val engine = AccountValidationEngine()

        val bizAccount = SocialAccount(
            accountName = "Business IG",
            handle = "@biz",
            platform = PlatformType.INSTAGRAM,
            accountType = AccountType.BUSINESS,
            connectionStatus = ConnectionStatus.CONNECTED,
            tokenStatus = TokenStatus.VALID,
            availableCapabilities = listOf(SocialCapability.PUBLISH_POST)
        )

        val pubResult = engine.validateActionExecution(
            account = bizAccount,
            requiredCapability = SocialCapability.PUBLISH_POST,
            hasUserPermission = true,
            isApproved = true
        )
        assertTrue(pubResult is AccountValidationResult.Allowed)

        val personalAccount = SocialAccount(
            accountName = "Personal IG",
            handle = "@personal",
            platform = PlatformType.INSTAGRAM,
            accountType = AccountType.PERSONAL,
            connectionStatus = ConnectionStatus.CONNECTED,
            tokenStatus = TokenStatus.VALID,
            availableCapabilities = listOf(SocialCapability.CREATE_POST)
        )

        val blockResult = engine.validateActionExecution(
            account = personalAccount,
            requiredCapability = SocialCapability.PUBLISH_POST,
            hasUserPermission = true,
            isApproved = true
        )
        assertTrue(blockResult is AccountValidationResult.Blocked)
        assertEquals("MISSING_CAPABILITY", (blockResult as AccountValidationResult.Blocked).code)
    }
}
