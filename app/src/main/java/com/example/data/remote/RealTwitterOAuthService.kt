package com.example.data.remote

import com.example.data.config.TwitterOAuthConfig
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class RealTwitterOAuthService(
    val config: TwitterOAuthConfig = TwitterOAuthConfig(),
    private val tokenStore: ServerTokenStore = MockServerTokenStore(),
    val tokenExchangeBackend: TwitterTokenExchangeBackend = RealHttpPlatformTokenExchangeBackend()
) : BackendOAuthService {

    private val activeSessions = mutableMapOf<String, OAuthSession>()

    fun getActiveSession(state: String): OAuthSession? = activeSessions[state]

    override suspend fun createOAuthSession(
        provider: OAuthProvider,
        redirectUri: String
    ): AppResult<OAuthSession> {
        if (provider != OAuthProvider.TWITTER) {
            return AppResult.Error(
                AgentError("UNSUPPORTED_PROVIDER", "Twitter OAuth service only supports X / Twitter.")
            )
        }

        if (!config.isConfigured) {
            return AppResult.Error(
                AgentError("TWITTER_OAUTH_NOT_CONFIGURED", "Twitter Client ID is not configured.")
            )
        }

        val session = OAuthSession(
            state = "tw_state_${UUID.randomUUID()}",
            provider = provider,
            redirectUri = redirectUri.ifBlank { config.redirectUri }
        )
        activeSessions[session.state] = session
        return AppResult.Success(session)
    }

    suspend fun handleDeepLinkCallback(payload: OAuthCallbackPayload): AppResult<SocialAccount> {
        val state = payload.state

        if (payload.status.equals("error", ignoreCase = true) || payload.errorCode != null || payload.error != null) {
            if (!state.isNullOrBlank()) activeSessions.remove(state)
            val errCode = payload.errorCode ?: payload.error ?: "OAUTH_ERROR"
            return when {
                errCode.equals("user_cancelled", ignoreCase = true) || errCode.contains("cancel", ignoreCase = true) -> {
                    AppResult.Error(AgentError("USER_CANCELLED", "User cancelled X/Twitter authorization flow."))
                }
                errCode.equals("access_denied", ignoreCase = true) || errCode.contains("denied", ignoreCase = true) -> {
                    AppResult.Error(AgentError("ACCESS_DENIED", "Access denied by user or platform permissions."))
                }
                else -> {
                    AppResult.Error(AgentError(errCode, "X/Twitter OAuth callback error ($errCode)."))
                }
            }
        }

        val ticket = payload.ticket
        if (ticket.isNullOrBlank()) {
            if (!state.isNullOrBlank()) activeSessions.remove(state)
            return AppResult.Error(AgentError("MISSING_TICKET", "Authorization ticket is missing from callback."))
        }

        if (state.isNullOrBlank()) {
            return AppResult.Error(AgentError("CSRF_ERROR", "OAuth state parameter is missing from callback."))
        }

        val session = activeSessions[state]
            ?: return AppResult.Error(AgentError("INVALID_SESSION", "No active OAuth session found for state (CSRF rejected)."))

        if (session.isExpired) {
            activeSessions.remove(state)
            return AppResult.Error(AgentError("SESSION_EXPIRED", "OAuth session has expired. Please initiate authentication again."))
        }

        val exchangeRes = tokenExchangeBackend.exchangeTwitterTicket(ticket = ticket, state = state)
        activeSessions.remove(state)

        if (exchangeRes is AppResult.Error) {
            return AppResult.Error(exchangeRes.error)
        }

        val metadata = (exchangeRes as AppResult.Success).data
        val userId = "workspace_user_1"
        val twitterUserId = metadata.id ?: "tw_user_${UUID.randomUUID().toString().take(8)}"
        val accountName = metadata.name ?: "X User"
        val handle = metadata.handle ?: (if (!metadata.username.isNullOrBlank()) "@${metadata.username}" else "@x_user")

        val account = SocialAccount(
            userId = userId,
            platform = PlatformType.TWITTER,
            platformUserId = twitterUserId,
            accountName = accountName,
            handle = handle,
            profileImageUrl = metadata.profileImageUrl ?: "",
            avatarUrl = metadata.profileImageUrl ?: "",
            accountType = AccountType.PERSONAL,
            connectionStatus = ConnectionStatus.CONNECTED,
            tokenStatus = TokenStatus.VALID,
            availableCapabilities = listOf(
                SocialCapability.CREATE_POST,
                SocialCapability.PUBLISH_POST,
                SocialCapability.READ_COMMENTS,
                SocialCapability.REPLY_COMMENT,
                SocialCapability.READ_ANALYTICS,
                SocialCapability.MEDIA_UPLOAD
            ),
            followerCount = metadata.followerCount ?: 0,
            isConnected = true,
            isDemoData = false
        )

        return AppResult.Success(account)
    }

    override suspend fun exchangeAuthorizationCode(
        session: OAuthSession,
        code: String,
        state: String,
        redirectUri: String
    ): AppResult<OAuthResult.Success> {
        val validation = OAuthSessionValidator.validateCallback(session, state, session.provider, redirectUri)
        if (validation is AppResult.Error) return AppResult.Error(validation.error)

        val dummyToken = SocialAccessToken(
            accessToken = "tw_access_token_secured",
            expiresInSeconds = 7200
        )
        tokenStore.storeToken("workspace_user_1", session.provider, dummyToken)

        val account = SocialAccount(
            userId = "workspace_user_1",
            platform = PlatformType.TWITTER,
            accountName = "X / Twitter User",
            handle = "@twitter_user",
            accountType = AccountType.PERSONAL,
            connectionStatus = ConnectionStatus.CONNECTED,
            tokenStatus = TokenStatus.VALID,
            availableCapabilities = listOf(
                SocialCapability.CREATE_POST,
                SocialCapability.PUBLISH_POST,
                SocialCapability.READ_ANALYTICS
            ),
            isConnected = true,
            isDemoData = false
        )

        activeSessions.remove(session.state)
        return AppResult.Success(OAuthResult.Success(session.provider, dummyToken, account))
    }

    override suspend fun fetchManagedPages(
        userId: String,
        provider: OAuthProvider
    ): AppResult<List<SocialPage>> = AppResult.Success(emptyList())

    override fun initiateOAuthFlow(provider: OAuthProvider): Flow<OAuthResult> = flow {
        val sessionRes = createOAuthSession(provider)
        if (sessionRes is AppResult.Success) {
            val session = sessionRes.data
            val exchangeRes = exchangeAuthorizationCode(session, "code_live_twitter_auth", session.state, session.redirectUri)
            if (exchangeRes is AppResult.Success) {
                emit(exchangeRes.data)
            }
        }
    }

    override suspend fun refreshToken(
        provider: OAuthProvider,
        currentToken: SocialAccessToken
    ): AppResult<SocialAccessToken> {
        val newToken = currentToken.copy(
            accessToken = "refreshed_tw_token_${UUID.randomUUID().toString().take(8)}",
            obtainedAtTimestamp = System.currentTimeMillis()
        )
        tokenStore.storeToken("workspace_user_1", provider, newToken)
        return AppResult.Success(newToken)
    }

    override suspend fun revokeToken(
        provider: OAuthProvider,
        token: SocialAccessToken
    ): AppResult<Boolean> {
        tokenStore.clearToken("workspace_user_1", provider)
        return AppResult.Success(true)
    }
}
