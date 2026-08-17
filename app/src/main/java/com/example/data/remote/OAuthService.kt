package com.example.data.remote

import com.example.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Payload extracted from OAuth callback deep link:
 * socialai://auth/callback?status=success&ticket=...&state=...
 *
 * Security: ticket is redacted from toString() to prevent leaking in logs.
 */
data class OAuthCallbackPayload(
    val status: String,
    val ticket: String? = null,
    val state: String? = null,
    val error: String? = null,
    val errorCode: String? = null
) {
    val isSuccess: Boolean
        get() = status.equals("success", ignoreCase = true) && !ticket.isNullOrBlank()

    override fun toString(): String {
        val ticketMask = if (ticket.isNullOrBlank()) "null" else "[REDACTED_TICKET]"
        return "OAuthCallbackPayload(status='$status', ticket=$ticketMask, state='$state', error='$error', errorCode='$errorCode')"
    }
}

/**
 * Event bus for routing OAuth deep-link callbacks from MainActivity to the active ViewModel / OAuth service.
 */
object OAuthCallbackManager {
    private val _callbacks = MutableSharedFlow<OAuthCallbackPayload>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val callbacks: SharedFlow<OAuthCallbackPayload> = _callbacks.asSharedFlow()

    fun dispatchCallback(
        status: String,
        ticket: String? = null,
        state: String? = null,
        error: String? = null,
        errorCode: String? = null
    ) {
        val payload = OAuthCallbackPayload(
            status = status,
            ticket = ticket,
            state = state,
            error = error,
            errorCode = errorCode
        )
        _callbacks.tryEmit(payload)
    }

    fun dispatchPayload(payload: OAuthCallbackPayload) {
        _callbacks.tryEmit(payload)
    }

    fun clear() {
        _callbacks.resetReplayCache()
    }
}

interface OAuthService {
    fun initiateOAuthFlow(provider: OAuthProvider): Flow<OAuthResult>
    suspend fun refreshToken(provider: OAuthProvider, currentToken: SocialAccessToken): AppResult<SocialAccessToken>
    suspend fun revokeToken(provider: OAuthProvider, token: SocialAccessToken): AppResult<Boolean>
}

interface ServerTokenStore {
    suspend fun storeToken(userId: String, provider: OAuthProvider, token: SocialAccessToken): AppResult<Boolean>
    suspend fun getToken(userId: String, provider: OAuthProvider): AppResult<SocialAccessToken?>
    suspend fun clearToken(userId: String, provider: OAuthProvider): AppResult<Boolean>
}

/**
 * Requirement 3 & 4: Backend OAuth service abstraction with CSRF state protection & code exchange
 */
interface BackendOAuthService : OAuthService {
    suspend fun createOAuthSession(
        provider: OAuthProvider,
        redirectUri: String = "https://socialagent.app/oauth/callback"
    ): AppResult<OAuthSession>

    suspend fun exchangeAuthorizationCode(
        session: OAuthSession,
        code: String,
        state: String,
        redirectUri: String = "https://socialagent.app/oauth/callback"
    ): AppResult<OAuthResult.Success>

    suspend fun fetchManagedPages(
        userId: String,
        provider: OAuthProvider
    ): AppResult<List<SocialPage>>
}

interface BackendTokenStore : ServerTokenStore {
    suspend fun isTokenValid(userId: String, provider: OAuthProvider): AppResult<Boolean>
}

object OAuthSessionValidator {
    fun validateCallback(
        session: OAuthSession?,
        receivedState: String?,
        receivedProvider: OAuthProvider,
        receivedRedirectUri: String
    ): AppResult<Boolean> {
        if (session == null) {
            return AppResult.Error(AgentError("INVALID_SESSION", "OAuth session does not exist."))
        }
        if (receivedState.isNullOrBlank() || receivedState != session.state) {
            return AppResult.Error(AgentError("CSRF_ERROR", "Invalid OAuth state parameter (CSRF attempt rejected)."))
        }
        if (receivedProvider != session.provider) {
            return AppResult.Error(AgentError("PROVIDER_MISMATCH", "OAuth provider mismatch."))
        }
        if (session.isExpired) {
            return AppResult.Error(AgentError("SESSION_EXPIRED", "OAuth authorization session expired."))
        }
        if (receivedRedirectUri != session.redirectUri) {
            return AppResult.Error(AgentError("REDIRECT_URI_MISMATCH", "OAuth redirect URI mismatch."))
        }
        return AppResult.Success(true)
    }
}

class MockOAuthService(
    private val tokenStore: ServerTokenStore = MockServerTokenStore()
) : BackendOAuthService {

    private val activeSessions = mutableMapOf<String, OAuthSession>()

    override suspend fun createOAuthSession(
        provider: OAuthProvider,
        redirectUri: String
    ): AppResult<OAuthSession> {
        delay(100)
        val session = OAuthSession(
            state = "state_${UUID.randomUUID().toString().take(8)}",
            provider = provider,
            redirectUri = redirectUri
        )
        activeSessions[session.state] = session
        return AppResult.Success(session)
    }

    override suspend fun exchangeAuthorizationCode(
        session: OAuthSession,
        code: String,
        state: String,
        redirectUri: String
    ): AppResult<OAuthResult.Success> {
        delay(300)
        val validationResult = OAuthSessionValidator.validateCallback(session, state, session.provider, redirectUri)
        if (validationResult is AppResult.Error) {
            return AppResult.Error(validationResult.error)
        }

        if (code.isBlank() || code.startsWith("invalid_")) {
            return AppResult.Error(AgentError("INVALID_CODE", "Failed to exchange authorization code."))
        }

        val platform = when (session.provider) {
            OAuthProvider.FACEBOOK -> PlatformType.FACEBOOK
            OAuthProvider.INSTAGRAM -> PlatformType.INSTAGRAM
            OAuthProvider.TWITTER -> PlatformType.TWITTER
            OAuthProvider.LINKEDIN -> PlatformType.LINKEDIN
            OAuthProvider.TIKTOK -> PlatformType.TIKTOK
        }

        val accountType = when (session.provider) {
            OAuthProvider.FACEBOOK -> AccountType.PAGE
            OAuthProvider.INSTAGRAM -> when {
                code.contains("personal") -> AccountType.PERSONAL
                code.contains("creator") -> AccountType.CREATOR
                else -> AccountType.BUSINESS
            }
            OAuthProvider.TWITTER -> AccountType.PERSONAL
            OAuthProvider.LINKEDIN -> AccountType.BUSINESS
            OAuthProvider.TIKTOK -> AccountType.CREATOR
        }

        val capabilities = when (session.provider) {
            OAuthProvider.FACEBOOK -> listOf(
                SocialCapability.CREATE_POST,
                SocialCapability.PUBLISH_POST,
                SocialCapability.READ_COMMENTS,
                SocialCapability.REPLY_COMMENT,
                SocialCapability.READ_MESSAGES,
                SocialCapability.SEND_MESSAGE,
                SocialCapability.READ_ANALYTICS,
                SocialCapability.MEDIA_UPLOAD
            )
            OAuthProvider.INSTAGRAM -> if (accountType == AccountType.PERSONAL) {
                listOf(
                    SocialCapability.CREATE_POST,
                    SocialCapability.READ_ANALYTICS
                )
            } else {
                listOf(
                    SocialCapability.CREATE_POST,
                    SocialCapability.PUBLISH_POST,
                    SocialCapability.READ_COMMENTS,
                    SocialCapability.REPLY_COMMENT,
                    SocialCapability.READ_ANALYTICS,
                    SocialCapability.MEDIA_UPLOAD,
                    SocialCapability.STORY_PUBLISH,
                    SocialCapability.REEL_PUBLISH
                )
            }
            else -> listOf(
                SocialCapability.CREATE_POST,
                SocialCapability.PUBLISH_POST,
                SocialCapability.READ_ANALYTICS
            )
        }

        val mockToken = SocialAccessToken(
            accessToken = "mock_access_token_${session.provider.name.lowercase()}_${UUID.randomUUID().toString().take(6)}",
            refreshToken = "mock_refresh_token_${session.provider.name.lowercase()}_${UUID.randomUUID().toString().take(6)}",
            expiresInSeconds = 7200,
            scope = listOf("public_profile", "pages_manage_posts", "instagram_content_publish")
        )

        val userId = "workspace_user_1"
        // Tokens are stored ONLY inside ServerTokenStore, never in the UI account model!
        tokenStore.storeToken(userId, session.provider, mockToken)

        val account = SocialAccount(
            userId = userId,
            platform = platform,
            accountName = "Connected ${session.provider.displayName}",
            handle = "@connected_${platform.name.lowercase()}",
            accountType = accountType,
            connectionStatus = ConnectionStatus.CONNECTED,
            tokenStatus = TokenStatus.VALID,
            availableCapabilities = capabilities,
            isConnected = true,
            isDemoData = true
        )

        activeSessions.remove(session.state)
        return AppResult.Success(OAuthResult.Success(session.provider, mockToken, account))
    }

    override suspend fun fetchManagedPages(
        userId: String,
        provider: OAuthProvider
    ): AppResult<List<SocialPage>> {
        delay(200)
        return if (provider == OAuthProvider.FACEBOOK) {
            AppResult.Success(
                listOf(
                    SocialPage(
                        platform = PlatformType.FACEBOOK,
                        platformAccountId = "fb_page_101",
                        name = "TechPulse Official Page",
                        category = "Technology Company",
                        accountType = AccountType.PAGE,
                        availableCapabilities = listOf(
                            SocialCapability.CREATE_POST,
                            SocialCapability.PUBLISH_POST,
                            SocialCapability.READ_COMMENTS,
                            SocialCapability.REPLY_COMMENT,
                            SocialCapability.READ_ANALYTICS
                        )
                    ),
                    SocialPage(
                        platform = PlatformType.FACEBOOK,
                        platformAccountId = "fb_page_102",
                        name = "TechPulse Community Hub",
                        category = "Community",
                        accountType = AccountType.PAGE,
                        availableCapabilities = listOf(
                            SocialCapability.CREATE_POST,
                            SocialCapability.READ_COMMENTS,
                            SocialCapability.REPLY_COMMENT
                        )
                    )
                )
            )
        } else {
            AppResult.Success(emptyList())
        }
    }

    override fun initiateOAuthFlow(provider: OAuthProvider): Flow<OAuthResult> = flow {
        delay(600)
        val sessionRes = createOAuthSession(provider)
        if (sessionRes is AppResult.Success) {
            val session = sessionRes.data
            val exchangeRes = exchangeAuthorizationCode(session, "code_mock_auth_123", session.state, session.redirectUri)
            if (exchangeRes is AppResult.Success) {
                emit(exchangeRes.data)
            } else if (exchangeRes is AppResult.Error) {
                emit(OAuthResult.Error(provider, exchangeRes.error.message))
            }
        }
    }

    override suspend fun refreshToken(
        provider: OAuthProvider,
        currentToken: SocialAccessToken
    ): AppResult<SocialAccessToken> {
        delay(300)
        val newToken = currentToken.copy(
            accessToken = "refreshed_access_token_${provider.name.lowercase()}_${UUID.randomUUID().toString().take(6)}",
            obtainedAtTimestamp = System.currentTimeMillis()
        )
        tokenStore.storeToken("workspace_user_1", provider, newToken)
        return AppResult.Success(newToken)
    }

    override suspend fun revokeToken(
        provider: OAuthProvider,
        token: SocialAccessToken
    ): AppResult<Boolean> {
        delay(200)
        tokenStore.clearToken("workspace_user_1", provider)
        return AppResult.Success(true)
    }
}

class MockServerTokenStore : BackendTokenStore {
    private val tokenMap = mutableMapOf<Pair<String, OAuthProvider>, SocialAccessToken>()

    override suspend fun storeToken(
        userId: String,
        provider: OAuthProvider,
        token: SocialAccessToken
    ): AppResult<Boolean> {
        tokenMap[Pair(userId, provider)] = token
        return AppResult.Success(true)
    }

    override suspend fun getToken(
        userId: String,
        provider: OAuthProvider
    ): AppResult<SocialAccessToken?> {
        return AppResult.Success(tokenMap[Pair(userId, provider)])
    }

    override suspend fun clearToken(
        userId: String,
        provider: OAuthProvider
    ): AppResult<Boolean> {
        tokenMap.remove(Pair(userId, provider))
        return AppResult.Success(true)
    }

    override suspend fun isTokenValid(userId: String, provider: OAuthProvider): AppResult<Boolean> {
        val token = tokenMap[Pair(userId, provider)]
        return AppResult.Success(token != null && !token.isExpired)
    }
}
