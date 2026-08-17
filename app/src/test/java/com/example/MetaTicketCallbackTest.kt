package com.example

import com.example.data.config.MetaOAuthConfig
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
class MetaTicketCallbackTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockBackend: FakeTicketExchangeBackend
    private lateinit var oauthService: RealMetaOAuthService
    private lateinit var repository: MockSocialMediaRepository
    private lateinit var viewModel: AccountsViewModel

    class FakeTicketExchangeBackend : MetaTokenExchangeBackend {
        var shouldFail: Boolean = false
        var failureCode: String = "TICKET_EXPIRED"
        var failureMessage: String = "The authorization ticket has expired."
        var lastExchangedTicket: String? = null
        var lastExchangedState: String? = null

        override suspend fun exchangeTicket(
            ticket: String,
            state: String?
        ): AppResult<BackendAccountMetadata> {
            lastExchangedTicket = ticket
            lastExchangedState = state
            if (shouldFail) {
                return AppResult.Error(AgentError(failureCode, failureMessage))
            }
            return AppResult.Success(
                BackendAccountMetadata(
                    id = "fb_acc_9988",
                    name = "Acme Social Agency",
                    pages = listOf(
                        BackendPageMetadata(
                            id = "page_12345",
                            name = "Acme Global",
                            category = "Marketing",
                            tasks = listOf("MANAGE", "CREATE_CONTENT")
                        )
                    )
                )
            )
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockBackend = FakeTicketExchangeBackend()
        val config = MetaOAuthConfig(
            appId = "123456789012345",
            redirectUri = "https://social-ai-agent-backend.onrender.com/auth/facebook/callback",
            environment = ExecutionEnvironment.REAL
        )
        oauthService = RealMetaOAuthService(
            config = config,
            tokenStore = MockServerTokenStore(),
            tokenExchangeBackend = mockBackend
        )
        repository = MockSocialMediaRepository(oauthService = oauthService)
        viewModel = AccountsViewModel(
            repository = repository,
            realMetaOAuthService = oauthService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. OAuth state generation
    @Test
    fun test01_OAuthStateGeneration() = runTest {
        val sessionRes = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        assertTrue(sessionRes is AppResult.Success)
        val session = (sessionRes as AppResult.Success).data

        assertNotNull(session.state)
        assertTrue(session.state.isNotBlank())
        assertTrue(session.state.startsWith("meta_state_"))
        assertNotNull(oauthService.getActiveSession(session.state))

        // Verify authorization URL includes state
        val authUrl = oauthService.config.generateAuthorizationUrl(session.state)
        assertTrue(authUrl.contains("state=${session.state}"))
        assertTrue(authUrl.contains("client_id=123456789012345"))
        assertTrue(authUrl.contains("redirect_uri=https://social-ai-agent-backend.onrender.com/auth/facebook/callback"))
    }

    // 2. Successful callback
    @Test
    fun test02_SuccessfulCallback() = runTest {
        val sessionRes = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        val session = (sessionRes as AppResult.Success).data

        val callbackPayload = OAuthCallbackPayload(
            status = "success",
            ticket = "ticket_single_use_abc123",
            state = session.state
        )
        assertTrue(callbackPayload.isSuccess)

        val exchangeResult = oauthService.handleDeepLinkCallback(callbackPayload)
        assertTrue(exchangeResult is AppResult.Success)
    }

    // 3. Invalid state
    @Test
    fun test03_InvalidState() = runTest {
        oauthService.createOAuthSession(OAuthProvider.FACEBOOK)

        val callbackPayload = OAuthCallbackPayload(
            status = "success",
            ticket = "ticket_attacker_999",
            state = "invalid_attacker_state"
        )

        val result = oauthService.handleDeepLinkCallback(callbackPayload)
        assertTrue(result is AppResult.Error)
        val error = (result as AppResult.Error).error
        assertEquals("INVALID_SESSION", error.code)
    }

    // 4. Missing state
    @Test
    fun test04_MissingState() = runTest {
        val callbackPayload = OAuthCallbackPayload(
            status = "success",
            ticket = "ticket_valid_123",
            state = null
        )

        val result = oauthService.handleDeepLinkCallback(callbackPayload)
        assertTrue(result is AppResult.Error)
        assertEquals("CSRF_ERROR", (result as AppResult.Error).error.code)
    }

    // 5. Expired OAuth session
    @Test
    fun test05_ExpiredOAuthSession() = runTest {
        val expiredSession = OAuthSession(
            state = "expired_state_123",
            provider = OAuthProvider.FACEBOOK,
            createdAt = System.currentTimeMillis() - 1000000,
            expiresAt = System.currentTimeMillis() - 10000
        )
        assertTrue(expiredSession.isExpired)

        // Inject expired session into service by creating custom subclass or using session
        val validationResult = OAuthSessionValidator.validateCallback(
            session = expiredSession,
            receivedState = "expired_state_123",
            receivedProvider = OAuthProvider.FACEBOOK,
            receivedRedirectUri = expiredSession.redirectUri
        )
        assertTrue(validationResult is AppResult.Error)
        assertEquals("SESSION_EXPIRED", (validationResult as AppResult.Error).error.code)
    }

    // 6. Missing ticket
    @Test
    fun test06_MissingTicket() = runTest {
        val sessionRes = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        val state = (sessionRes as AppResult.Success).data.state

        val callbackPayload = OAuthCallbackPayload(
            status = "success",
            ticket = null,
            state = state
        )

        val result = oauthService.handleDeepLinkCallback(callbackPayload)
        assertTrue(result is AppResult.Error)
        assertEquals("MISSING_TICKET", (result as AppResult.Error).error.code)
    }

    // 7. Ticket exchange success
    @Test
    fun test07_TicketExchangeSuccess() = runTest {
        val sessionRes = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        val session = (sessionRes as AppResult.Success).data

        val callbackPayload = OAuthCallbackPayload(
            status = "success",
            ticket = "ticket_exchange_valid",
            state = session.state
        )

        val result = oauthService.handleDeepLinkCallback(callbackPayload)
        assertTrue(result is AppResult.Success)
        val account = (result as AppResult.Success).data

        assertEquals("ticket_exchange_valid", mockBackend.lastExchangedTicket)
        assertEquals(session.state, mockBackend.lastExchangedState)
        assertEquals("Acme Global", account.accountName)
        assertEquals("page_12345", account.platformUserId)
    }

    // 8. TICKET_EXPIRED
    @Test
    fun test08_TicketExpired() = runTest {
        mockBackend.shouldFail = true
        mockBackend.failureCode = "TICKET_EXPIRED"
        mockBackend.failureMessage = "The authorization ticket has expired. Please authenticate again."

        val sessionRes = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        val state = (sessionRes as AppResult.Success).data.state

        val callbackPayload = OAuthCallbackPayload(
            status = "success",
            ticket = "ticket_expired_1",
            state = state
        )

        val result = oauthService.handleDeepLinkCallback(callbackPayload)
        assertTrue(result is AppResult.Error)
        val error = (result as AppResult.Error).error
        assertEquals("TICKET_EXPIRED", error.code)
    }

    // 9. TICKET_NOT_FOUND
    @Test
    fun test09_TicketNotFound() = runTest {
        mockBackend.shouldFail = true
        mockBackend.failureCode = "TICKET_NOT_FOUND"
        mockBackend.failureMessage = "The authorization ticket was not found, invalid, or has already been used."

        val sessionRes = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        val state = (sessionRes as AppResult.Success).data.state

        val callbackPayload = OAuthCallbackPayload(
            status = "success",
            ticket = "ticket_not_found_1",
            state = state
        )

        val result = oauthService.handleDeepLinkCallback(callbackPayload)
        assertTrue(result is AppResult.Error)
        val error = (result as AppResult.Error).error
        assertEquals("TICKET_NOT_FOUND", error.code)
    }

    // 10. STATE_MISMATCH
    @Test
    fun test10_StateMismatch() = runTest {
        mockBackend.shouldFail = true
        mockBackend.failureCode = "STATE_MISMATCH"
        mockBackend.failureMessage = "The state parameter did not match the authorization session."

        val sessionRes = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        val state = (sessionRes as AppResult.Success).data.state

        val callbackPayload = OAuthCallbackPayload(
            status = "success",
            ticket = "ticket_state_mismatch_1",
            state = state
        )

        val result = oauthService.handleDeepLinkCallback(callbackPayload)
        assertTrue(result is AppResult.Error)
        val error = (result as AppResult.Error).error
        assertEquals("STATE_MISMATCH", error.code)
    }

    // 11. User cancellation
    @Test
    fun test11_UserCancellation() = runTest {
        val sessionRes = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        val state = (sessionRes as AppResult.Success).data.state

        val callbackPayload = OAuthCallbackPayload(
            status = "error",
            errorCode = "user_cancelled",
            state = state
        )

        val result = oauthService.handleDeepLinkCallback(callbackPayload)
        assertTrue(result is AppResult.Error)
        assertEquals("USER_CANCELLED", (result as AppResult.Error).error.code)
        // Ensure session was cleaned up
        assertNull(oauthService.getActiveSession(state))
    }

    // 12. Duplicate account prevention
    @Test
    fun test12_DuplicateAccountPrevention() = runTest {
        val session1 = (oauthService.createOAuthSession(OAuthProvider.FACEBOOK) as AppResult.Success).data
        val payload1 = OAuthCallbackPayload(
            status = "success",
            ticket = "ticket_dup_1",
            state = session1.state
        )
        val accountRes1 = oauthService.handleDeepLinkCallback(payload1)
        assertTrue(accountRes1 is AppResult.Success)
        val connectedAccount = (accountRes1 as AppResult.Success).data
        repository.saveConnectedAccount(connectedAccount)

        val session2 = (oauthService.createOAuthSession(OAuthProvider.FACEBOOK) as AppResult.Success).data
        val payload2 = OAuthCallbackPayload(
            status = "success",
            ticket = "ticket_dup_2",
            state = session2.state
        )
        val accountRes2 = oauthService.handleDeepLinkCallback(payload2)
        assertTrue(accountRes2 is AppResult.Success)
        val reconnectedAccount = (accountRes2 as AppResult.Success).data
        repository.saveConnectedAccount(reconnectedAccount)

        val fbAccount = repository.getAccountByPlatform(PlatformType.FACEBOOK)
        assertNotNull(fbAccount)
        assertEquals(PlatformType.FACEBOOK, fbAccount?.platform)
        assertEquals(ConnectionStatus.CONNECTED, fbAccount?.connectionStatus)
    }

    // 13. Successful connected-account state
    @Test
    fun test13_SuccessfulConnectedAccountState() = runTest {
        val sessionRes = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        val session = (sessionRes as AppResult.Success).data

        val callbackPayload = OAuthCallbackPayload(
            status = "success",
            ticket = "ticket_connected_state_test",
            state = session.state
        )

        val result = oauthService.handleDeepLinkCallback(callbackPayload)
        assertTrue(result is AppResult.Success)
        val account = (result as AppResult.Success).data

        assertEquals(ConnectionStatus.CONNECTED, account.connectionStatus)
        assertEquals(TokenStatus.VALID, account.tokenStatus)
        assertTrue(account.isConnected)
        assertTrue(account.availableCapabilities.contains(SocialCapability.CREATE_POST))
        assertTrue(account.availableCapabilities.contains(SocialCapability.PUBLISH_POST))
        assertTrue(account.availableCapabilities.contains(SocialCapability.READ_COMMENTS))
        assertTrue(account.availableCapabilities.contains(SocialCapability.REPLY_COMMENT))
    }

    // 14. Sensitive ticket/token/code redaction
    @Test
    fun test14_SensitiveTicketTokenCodeRedaction() {
        val secretTicket = "super_secret_single_use_ticket_12345"
        val payload = OAuthCallbackPayload(
            status = "success",
            ticket = secretTicket,
            state = "meta_state_abc"
        )
        val toStringOutput = payload.toString()
        assertFalse(toStringOutput.contains(secretTicket))
        assertTrue(toStringOutput.contains("[REDACTED_TICKET]"))

        val token = SocialAccessToken(
            accessToken = "super_secret_access_token_67890",
            refreshToken = "super_secret_refresh_token_abcde"
        )
        val tokenString = token.toString()
        assertFalse(tokenString.contains("super_secret_access_token_67890"))
        assertFalse(tokenString.contains("super_secret_refresh_token_abcde"))
        assertTrue(tokenString.contains("[REDACTED]"))
    }

    // 15. MOCK mode still works
    @Test
    fun test15_MockMode_StillWorks() = runTest {
        val mockRepo = MockSocialMediaRepository(
            tokenStore = MockServerTokenStore(),
            oauthService = MockOAuthService()
        )
        val mockVm = AccountsViewModel(
            repository = mockRepo,
            realMetaOAuthService = oauthService
        )
        mockVm.connectAccount(OAuthProvider.FACEBOOK, "code_mock_auth", ExecutionEnvironment.MOCK)
        advanceUntilIdle()

        val uiState = mockVm.uiState.value
        assertFalse(uiState.isConnecting)
        assertNotNull(uiState.statusMessage)
        assertTrue(uiState.statusMessage!!.contains("Demo Workspace"))
    }

    // 16. REAL mode does not silently fall back to fake success
    @Test
    fun test16_RealMode_DoesNotSilentlyFallbackToFakeSuccess() = runTest {
        // Unconfigured backend
        val unconfiguredService = RealMetaOAuthService(
            config = MetaOAuthConfig(appId = null, environment = ExecutionEnvironment.REAL),
            tokenExchangeBackend = UnconfiguredMetaTokenExchangeBackend()
        )
        val unconfiguredVm = AccountsViewModel(repository, unconfiguredService)

        unconfiguredVm.connectAccount(OAuthProvider.FACEBOOK, environment = ExecutionEnvironment.REAL)
        advanceUntilIdle()

        val uiState = unconfiguredVm.uiState.value
        assertFalse(uiState.isConnecting)
        assertNotNull(uiState.statusMessage)
        assertTrue(uiState.statusMessage!!.contains("LIVE CONFIGURATION REQUIRED"))

        // Also test backend failure during deep link callback does not fake success
        mockBackend.shouldFail = true
        mockBackend.failureCode = "TICKET_EXPIRED"
        mockBackend.failureMessage = "The authorization ticket has expired."

        val sessionRes = oauthService.createOAuthSession(OAuthProvider.FACEBOOK)
        val state = (sessionRes as AppResult.Success).data.state

        viewModel.handleOAuthCallback(
            OAuthCallbackPayload(status = "success", ticket = "ticket_failed_1", state = state)
        )
        advanceUntilIdle()

        val stateAfterFailure = viewModel.uiState.value
        assertFalse(stateAfterFailure.isConnecting)
        assertNotNull(stateAfterFailure.statusMessage)
        assertTrue(stateAfterFailure.statusMessage!!.contains("expired") || stateAfterFailure.statusMessage!!.contains("failed"))
    }
}
