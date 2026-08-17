package com.example.data.config

import com.example.data.model.ExecutionEnvironment
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TwitterOAuthConfig(
    val clientId: String = SecurityConfig.getTwitterClientId() ?: SecurityConfig.DEFAULT_TWITTER_CLIENT_ID,
    val redirectUri: String = SecurityConfig.getTwitterRedirectUri(),
    val scopes: List<String> = listOf(
        "tweet.read",
        "tweet.write",
        "users.read",
        "offline.access"
    ),
    val environment: ExecutionEnvironment = ExecutionEnvironment.REAL
) {
    val isConfigured: Boolean
        get() = clientId.isNotBlank()

    fun generateAuthorizationUrl(
        state: String,
        codeChallenge: String = state,
        codeChallengeMethod: String = "S256"
    ): String {
        val encodedScopes = URLEncoder.encode(scopes.joinToString(" "), StandardCharsets.UTF_8.toString())
        val encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString())
        val encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8.toString())
        val encodedChallenge = URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8.toString())

        return "https://twitter.com/i/oauth2/authorize" +
                "?response_type=code" +
                "&client_id=${URLEncoder.encode(clientId, StandardCharsets.UTF_8.toString())}" +
                "&redirect_uri=$encodedRedirect" +
                "&scope=$encodedScopes" +
                "&state=$encodedState" +
                "&code_challenge=$encodedChallenge" +
                "&code_challenge_method=$codeChallengeMethod"
    }
}
