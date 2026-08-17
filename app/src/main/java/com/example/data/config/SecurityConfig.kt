package com.example.data.config

/**
 * Security & credentials management configuration.
 * Prevents hardcoding sensitive credentials in source code.
 */
interface SecretsManager {
    fun getSecret(key: String): String?
    fun isKeyConfigured(key: String): Boolean
}

class EnvironmentSecretsManager : SecretsManager {
    override fun getSecret(key: String): String? {
        val envVal = System.getenv(key)
        if (!envVal.isNull_or_blank()) return envVal
        val buildConfigVal = getBuildConfigField(key)
        if (!buildConfigVal.isNull_or_blank()) return buildConfigVal
        return null
    }

    private fun getBuildConfigField(key: String): String? {
        return try {
            val clazz = Class.forName("com.example.BuildConfig")
            val field = clazz.getDeclaredField(key)
            field.get(null) as? String
        } catch (e: Exception) {
            null
        }
    }

    override fun isKeyConfigured(key: String): Boolean {
        return !getSecret(key).isNull_or_blank()
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
}

object SecurityConfig {
    val secretsManager: SecretsManager = EnvironmentSecretsManager()

    const val GEMINI_API_KEY_ENV = "GEMINI_API_KEY"
    const val OAUTH_CLIENT_ID_ENV = "SOCIAL_OAUTH_CLIENT_ID"
    const val OAUTH_CLIENT_SECRET_ENV = "SOCIAL_OAUTH_CLIENT_SECRET"
    const val META_APP_ID_ENV = "META_APP_ID"
    const val FACEBOOK_APP_ID_ENV = "FACEBOOK_APP_ID"
    const val META_OAUTH_REDIRECT_URI_ENV = "META_OAUTH_REDIRECT_URI"
    const val META_BACKEND_URL_ENV = "META_BACKEND_URL"

    const val TWITTER_CLIENT_ID_ENV = "TWITTER_CLIENT_ID"
    const val TWITTER_REDIRECT_URI_ENV = "TWITTER_REDIRECT_URI"

    const val DEFAULT_META_APP_ID = "2499515240476024"
    const val DEFAULT_META_BACKEND_URL = "https://social-ai-agent-backend.onrender.com"
    const val DEFAULT_META_REDIRECT_URI = "https://social-ai-agent-backend.onrender.com/auth/facebook/callback"

    const val DEFAULT_TWITTER_CLIENT_ID = "MFF1UGdFTy05VG1QZEhrcmU5clc6MTpjaXA"
    const val DEFAULT_TWITTER_REDIRECT_URI = "https://social-ai-agent-backend.onrender.com/auth/twitter/callback"

    fun isGeminiConfigured(): Boolean = secretsManager.isKeyConfigured(GEMINI_API_KEY_ENV)

    fun getMetaAppId(): String? = secretsManager.getSecret(META_APP_ID_ENV) ?: secretsManager.getSecret(FACEBOOK_APP_ID_ENV) ?: DEFAULT_META_APP_ID

    fun isMetaConfigured(): Boolean = !getMetaAppId().isNullOrBlank()

    fun getTwitterClientId(): String? = secretsManager.getSecret(TWITTER_CLIENT_ID_ENV) ?: DEFAULT_TWITTER_CLIENT_ID

    fun isTwitterConfigured(): Boolean = !getTwitterClientId().isNullOrBlank()

    fun getTwitterRedirectUri(): String = secretsManager.getSecret(TWITTER_REDIRECT_URI_ENV) ?: DEFAULT_TWITTER_REDIRECT_URI

    fun getMetaBackendBaseUrl(): String = secretsManager.getSecret(META_BACKEND_URL_ENV) ?: DEFAULT_META_BACKEND_URL

    fun getMetaRedirectUri(): String = secretsManager.getSecret(META_OAUTH_REDIRECT_URI_ENV) ?: DEFAULT_META_REDIRECT_URI
}
