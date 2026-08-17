package com.example.data.config

import com.example.data.model.ExecutionEnvironment

/**
 * Requirement 2: Production Meta OAuth Configuration model.
 * Exposes App ID, redirect URI, scopes, environment.
 * CRITICAL SECURITY RULE: MUST NOT contain Meta App Secret or OAuth client secret.
 */
data class MetaOAuthConfig(
    val appId: String? = SecurityConfig.getMetaAppId(),
    val redirectUri: String = SecurityConfig.getMetaRedirectUri(),
    val apiVersion: String = "v19.0",
    val requiredScopes: List<String> = listOf(
        "public_profile",
        "email",
        "pages_show_list",
        "pages_read_engagement",
        "pages_manage_posts",
        "instagram_basic",
        "instagram_content_publish",
        "instagram_manage_comments",
        "instagram_manage_insights"
    ),
    val optionalScopes: List<String> = listOf(
        "pages_messaging",
        "instagram_manage_messages"
    ),
    val environment: ExecutionEnvironment = if (!appId.isNullOrBlank()) ExecutionEnvironment.REAL else ExecutionEnvironment.MOCK
) {
    val isConfigured: Boolean get() = !appId.isNullOrBlank()

    val allScopes: List<String> get() = requiredScopes + optionalScopes

    val facebookRequiredScopes: List<String> get() = listOf(
        "public_profile",
        "email",
        "pages_show_list",
        "pages_read_engagement",
        "pages_manage_posts"
    )

    val facebookOptionalScopes: List<String> get() = listOf(
        "pages_messaging"
    )

    val instagramRequiredScopes: List<String> get() = listOf(
        "instagram_basic",
        "instagram_content_publish",
        "instagram_manage_comments",
        "instagram_manage_insights"
    )

    val instagramOptionalScopes: List<String> get() = listOf(
        "instagram_manage_messages"
    )

    /**
     * Generates standard Facebook/Meta authorization URL for Custom Tab / Browser redirect.
     * App Secret is strictly excluded.
     */
    fun generateAuthorizationUrl(state: String): String {
        val activeAppId = appId
        require(!activeAppId.isNullOrBlank()) { "Meta App ID is required to generate authorization URL." }
        val scopeParam = allScopes.joinToString(",")
        return "https://www.facebook.com/$apiVersion/dialog/oauth?" +
                "client_id=$activeAppId" +
                "&redirect_uri=$redirectUri" +
                "&state=$state" +
                "&scope=$scopeParam" +
                "&response_type=code"
    }
}
