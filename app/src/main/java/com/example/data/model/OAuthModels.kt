package com.example.data.model

import java.util.UUID

enum class OAuthProvider(val displayName: String) {
    FACEBOOK("Facebook OAuth"),
    INSTAGRAM("Instagram Graph OAuth"),
    TWITTER("X / Twitter OAuth 2.0"),
    LINKEDIN("LinkedIn OAuth 2.0"),
    TIKTOK("TikTok for Developers OAuth")
}

data class OAuthSession(
    val state: String = UUID.randomUUID().toString(),
    val provider: OAuthProvider,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + 600000, // 10 minutes session expiration
    val redirectUri: String = "https://socialagent.app/oauth/callback"
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt
}

data class SocialAccessToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresInSeconds: Long = 3600,
    val tokenType: String = "Bearer",
    val scope: List<String> = emptyList(),
    val obtainedAtTimestamp: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = (System.currentTimeMillis() - obtainedAtTimestamp) > (expiresInSeconds * 1000)

    // Requirement 17: Never log or print raw OAuth access tokens or refresh tokens
    override fun toString(): String {
        return "SocialAccessToken(accessToken='[REDACTED]', refreshToken='[REDACTED]', expiresInSeconds=$expiresInSeconds, tokenType='$tokenType', scope=$scope, isExpired=$isExpired)"
    }
}

sealed class OAuthResult {
    data class Success(
        val provider: OAuthProvider,
        val token: SocialAccessToken,
        val account: SocialAccount
    ) : OAuthResult()

    data class Cancelled(val provider: OAuthProvider) : OAuthResult()

    data class Error(
        val provider: OAuthProvider,
        val message: String,
        val cause: Throwable? = null
    ) : OAuthResult()
}
