package com.example

import com.example.data.config.LinkedInOAuthConfig
import com.example.data.config.TwitterOAuthConfig
import com.example.data.model.*
import com.example.data.remote.*
import com.example.data.repository.MockSocialMediaRepository
import com.example.ui.screens.accounts.AccountsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwitterLinkedInOAuthAndCapabilityTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeBackend: FakePlatformTokenExchangeBackend
    private lateinit var twitterOAuthService: RealTwitterOAuthService
    private lateinit var linkedInOAuthService: RealLinkedInOAuthService
    private lateinit var repository: MockSocialMediaRepository
    private lateinit var viewModel: AccountsViewModel

    class FakePlatformTokenExchangeBackend : TwitterTokenExchangeBackend, LinkedInTokenExchangeBackend {
        var shouldFail: Boolean = false
        var failureCode: String = "TICKET_EXPIRED"
        var failureMessage: String = "The authorization ticket has expired."
        var lastExchangedTicket: String? = null
        var lastExchangedState: String? = null
        var lastExchangedPlatform: String? = null

        override suspend fun exchangeTwitterTicket(
            ticket: String,
            state: String?
        ): AppResult<TwitterExchangeMetadata> {
            lastExchangedPlatform = "TWITTER"
            lastExchangedTicket = ticket
            lastExchangedState = state
            if (shouldFail) {
                return AppResult.Error(AgentError(failureCode, failureMessage))
            }
            return AppResult.Success(
                TwitterExchangeMetadata(
                    id = "tw_user_998877",
                    name = "Social AI Studio",
                    handle = "@socialaistudio",
                    profileImageUrl = "https://pbs.twimg.com/profile.png",
                    followerCount = 14200,
                    capabilities = listOf("CREATE_POST", "PUBLISH_POST", "MEDIA_UPLOAD", "READ_ANALYTICS")
                )
            )
        }

        override suspend fun exchangeLinkedInTicket(
            ticket: String,
            state: String?
        ): AppResult<LinkedInExchangeMetadata> {
            lastExchangedPlatform = "LINKEDIN"
            lastExchangedTicket = ticket
            lastExchangedState = state
            if (shouldFail) {
                return AppResult.Error(AgentError(failureCode, failureMessage))
            }
            return AppResult.Success(
                LinkedInExchangeMetadata(
                    id = "urn:li:person:abcdef123",
                    authorUrn = "urn:li:person:abcdef123",
                    name = "Alex Rivera",
                    handle = "@alexrivera",
                    email = "alex@socialagent.app",
                    profileImageUrl = "https://media.licdn.com/dms/image/profile.jpg",
                    capabilities = listOf("CREATE_POST", "PUBLISH_POST", "MEDIA_UPLOAD", "READ_ANALYTICS")
                )
            )
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeBackend = FakePlatformTokenExchangeBackend()
        
        val twitterConfig = TwitterOAuthConfig(
            clientId = "test_twitter_client_id_123",
            redirectUri = "https://social-ai-agent-backend.onrender.com/auth/twitter/callback",
            environment = ExecutionEnvironment.REAL
        )
        twitterOAuthService = RealTwitterOAuthService(
            config = twitterConfig,
            tokenStore = MockServerTokenStore(),
            tokenExchangeBackend = fakeBackend
        )

        val linkedInConfig = LinkedInOAuthConfig(
            clientId = "test_linkedin_client_id_456",
            redirectUri = "https://social-ai-agent-backend.onrender.com/auth/linkedin/callback",
            environment = ExecutionEnvironment.REAL
        )
        linkedInOAuthService = RealLinkedInOAuthService(
            config = linkedInConfig,
            tokenStore = MockServerTokenStore(),
            tokenExchangeBackend = fakeBackend
        )

        repository = MockSocialMediaRepository(oauthService = twitterOAuthService)
        viewModel = AccountsViewModel(
            repository = repository,
            realTwitterOAuthService = twitterOAuthService,
            realLinkedInOAuthService = linkedInOAuthService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. Twitter OAuth State & URL Generation
    @Test
    fun test01_TwitterOAuthStateAndUrlGeneration() = runTest {
        val sessionRes = twitterOAuthService.createOAuthSession(OAuthProvider.TWITTER)
        assertTrue(sessionRes is AppResult.Success)
        val session = (sessionRes as AppResult.Success).data

        assertNotNull(session.state)
        assertTrue(session.state.startsWith("tw_state_"))

        val authUrl = twitterOAuthService.config.generateAuthorizationUrl(session.state, "mock_code_challenge")
        assertTrue(authUrl.contains("state=${session.state}"))
        assertTrue(authUrl.contains("client_id=test_twitter_client_id_123"))
        assertTrue(authUrl.contains("code_challenge=mock_code_challenge"))
        assertTrue(authUrl.contains("code_challenge_method=S256"))
    }

    // 2. Twitter Ticket Exchange Success
    @Test
    fun test02_TwitterTicketExchangeSuccess() = runTest {
        val sessionRes = twitterOAuthService.createOAuthSession(OAuthProvider.TWITTER)
        val session = (sessionRes as AppResult.Success).data

        val payload = OAuthCallbackPayload(
            status = "success",
            ticket = "tw_ticket_xyz789",
            state = session.state
        )

        val result = twitterOAuthService.handleDeepLinkCallback(payload)
        assertTrue(result is AppResult.Success)
        val account = (result as AppResult.Success).data

        assertEquals("TWITTER", fakeBackend.lastExchangedPlatform)
        assertEquals("tw_ticket_xyz789", fakeBackend.lastExchangedTicket)
        assertEquals(session.state, fakeBackend.lastExchangedState)
        assertEquals("Social AI Studio", account.accountName)
        assertEquals("tw_user_998877", account.platformUserId)
        assertEquals(PlatformType.TWITTER, account.platform)
        assertEquals(ConnectionStatus.CONNECTED, account.connectionStatus)
    }

    // 3. Twitter Ticket Exchange Error (TICKET_EXPIRED)
    @Test
    fun test03_TwitterTicketExpiredError() = runTest {
        fakeBackend.shouldFail = true
        fakeBackend.failureCode = "TICKET_EXPIRED"
        fakeBackend.failureMessage = "Twitter authorization ticket expired."

        val sessionRes = twitterOAuthService.createOAuthSession(OAuthProvider.TWITTER)
        val session = (sessionRes as AppResult.Success).data

        val payload = OAuthCallbackPayload(
            status = "success",
            ticket = "tw_ticket_expired_1",
            state = session.state
        )

        val result = twitterOAuthService.handleDeepLinkCallback(payload)
        assertTrue(result is AppResult.Error)
        assertEquals("TICKET_EXPIRED", (result as AppResult.Error).error.code)
    }

    // 4. LinkedIn OAuth State & URL Generation
    @Test
    fun test04_LinkedInOAuthStateAndUrlGeneration() = runTest {
        val sessionRes = linkedInOAuthService.createOAuthSession(OAuthProvider.LINKEDIN)
        assertTrue(sessionRes is AppResult.Success)
        val session = (sessionRes as AppResult.Success).data

        assertNotNull(session.state)
        assertTrue(session.state.startsWith("li_state_"))

        val authUrl = linkedInOAuthService.config.generateAuthorizationUrl(session.state)
        val decodedAuthUrl = java.net.URLDecoder.decode(authUrl, "UTF-8")
        assertTrue(authUrl.contains("state=${session.state}"))
        assertTrue(authUrl.contains("client_id=test_linkedin_client_id_456"))
        assertTrue(decodedAuthUrl.contains("redirect_uri=https://social-ai-agent-backend.onrender.com/auth/linkedin/callback"))
        assertTrue(decodedAuthUrl.contains("w_member_social"))
    }

    // 5. LinkedIn Ticket Exchange Success
    @Test
    fun test05_LinkedInTicketExchangeSuccess() = runTest {
        val sessionRes = linkedInOAuthService.createOAuthSession(OAuthProvider.LINKEDIN)
        val session = (sessionRes as AppResult.Success).data

        val payload = OAuthCallbackPayload(
            status = "success",
            ticket = "li_ticket_abc456",
            state = session.state
        )

        val result = linkedInOAuthService.handleDeepLinkCallback(payload)
        assertTrue(result is AppResult.Success)
        val account = (result as AppResult.Success).data

        assertEquals("LINKEDIN", fakeBackend.lastExchangedPlatform)
        assertEquals("li_ticket_abc456", fakeBackend.lastExchangedTicket)
        assertEquals(session.state, fakeBackend.lastExchangedState)
        assertEquals("Alex Rivera", account.accountName)
        assertEquals("urn:li:person:abcdef123", account.platformUserId)
        assertEquals(PlatformType.LINKEDIN, account.platform)
        assertEquals(ConnectionStatus.CONNECTED, account.connectionStatus)
    }

    // 6. LinkedIn Ticket Exchange Error (INSUFFICIENT_SCOPE)
    @Test
    fun test06_LinkedInInsufficientScopeError() = runTest {
        fakeBackend.shouldFail = true
        fakeBackend.failureCode = "INSUFFICIENT_SCOPE"
        fakeBackend.failureMessage = "Member did not grant w_member_social permissions."

        val sessionRes = linkedInOAuthService.createOAuthSession(OAuthProvider.LINKEDIN)
        val session = (sessionRes as AppResult.Success).data

        val payload = OAuthCallbackPayload(
            status = "success",
            ticket = "li_ticket_insufficient_scope",
            state = session.state
        )

        val result = linkedInOAuthService.handleDeepLinkCallback(payload)
        assertTrue(result is AppResult.Error)
        assertEquals("INSUFFICIENT_SCOPE", (result as AppResult.Error).error.code)
    }

    // 7. ViewModel Deep Link Callback Routing for Twitter
    @Test
    fun test07_ViewModelRoutingForTwitter() = runTest {
        val sessionRes = twitterOAuthService.createOAuthSession(OAuthProvider.TWITTER)
        val session = (sessionRes as AppResult.Success).data

        viewModel.handleOAuthCallback(
            OAuthCallbackPayload(
                status = "success",
                ticket = "tw_ticket_vm_route",
                state = session.state
            )
        )
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertFalse(uiState.isConnecting)
        assertEquals("TWITTER", fakeBackend.lastExchangedPlatform)
        val twAccount = repository.getAccountByPlatform(PlatformType.TWITTER)
        assertNotNull(twAccount)
        assertEquals("Social AI Studio", twAccount?.accountName)
        assertEquals(ConnectionStatus.CONNECTED, twAccount?.connectionStatus)
    }

    // 8. ViewModel Deep Link Callback Routing for LinkedIn
    @Test
    fun test08_ViewModelRoutingForLinkedIn() = runTest {
        val sessionRes = linkedInOAuthService.createOAuthSession(OAuthProvider.LINKEDIN)
        val session = (sessionRes as AppResult.Success).data

        viewModel.handleOAuthCallback(
            OAuthCallbackPayload(
                status = "success",
                ticket = "li_ticket_vm_route",
                state = session.state
            )
        )
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertFalse(uiState.isConnecting)
        assertEquals("LINKEDIN", fakeBackend.lastExchangedPlatform)
        val liAccount = repository.getAccountByPlatform(PlatformType.LINKEDIN)
        assertNotNull(liAccount)
        assertEquals("Alex Rivera", liAccount?.accountName)
        assertEquals(ConnectionStatus.CONNECTED, liAccount?.connectionStatus)
    }

    // 9. Zero Token Leakage Check in Metadata
    @Test
    fun test09_ZeroTokenLeakageInMetadata() {
        val twMetadata = TwitterExchangeMetadata(
            id = "tw_123",
            name = "Test Account",
            handle = "@test",
            capabilities = listOf("PUBLISH_POST")
        )
        val twJson = twMetadata.toString()
        assertFalse(twJson.contains("access_token"))
        assertFalse(twJson.contains("refresh_token"))
        assertFalse(twJson.contains("client_secret"))

        val liMetadata = LinkedInExchangeMetadata(
            id = "urn:li:person:123",
            authorUrn = "urn:li:person:123",
            name = "Test Member",
            handle = "@member",
            capabilities = listOf("PUBLISH_POST")
        )
        val liJson = liMetadata.toString()
        assertFalse(liJson.contains("access_token"))
        assertFalse(liJson.contains("refresh_token"))
        assertFalse(liJson.contains("client_secret"))
    }
}
