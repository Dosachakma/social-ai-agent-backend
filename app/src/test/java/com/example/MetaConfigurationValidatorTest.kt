package com.example

import com.example.data.config.*
import com.example.data.model.*
import com.example.data.remote.MetaTokenExchangeBackend
import com.example.data.remote.RealMetaOAuthService
import com.example.data.remote.UnconfiguredMetaTokenExchangeBackend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MetaConfigurationValidatorTest {

    private class MockConfiguredBackend : MetaTokenExchangeBackend {
        override suspend fun exchangeCodeForToken(code: String, redirectUri: String): AppResult<SocialAccessToken> {
            return AppResult.Success(
                SocialAccessToken(accessToken = "test_token", tokenType = "bearer")
            )
        }

        override suspend fun fetchFacebookPages(accessToken: SocialAccessToken): AppResult<List<SocialPage>> {
            return AppResult.Success(emptyList())
        }

        override suspend fun fetchInstagramAccounts(accessToken: SocialAccessToken): AppResult<List<SocialAccount>> {
            return AppResult.Success(emptyList())
        }
    }

    @Test
    fun `1 MOCK environment validation produces ready status with no errors`() {
        val config = MetaOAuthConfig(appId = null)
        val status = MetaConfigurationValidator.validate(
            config = config,
            isBackendConfigured = false,
            environment = ExecutionEnvironment.MOCK
        )

        assertTrue(status.isReady)
        assertEquals(MetaConfigStatusLevel.READY, status.statusLevel)
        assertTrue(status.errors.isEmpty())
        assertEquals(ExecutionEnvironment.MOCK, status.environment)
    }

    @Test
    fun `2 DEVELOPMENT environment with missing App ID fails with META_APP_ID_MISSING`() {
        val config = MetaOAuthConfig(appId = null, redirectUri = "https://socialagent.app/oauth/callback")
        val status = MetaConfigurationValidator.validate(
            config = config,
            isBackendConfigured = true,
            environment = ExecutionEnvironment.DEVELOPMENT
        )

        assertFalse(status.isReady)
        assertEquals(MetaConfigStatusLevel.INCOMPLETE, status.statusLevel)
        assertTrue(status.errors.any { it.contains("META_APP_ID_MISSING") })
    }

    @Test
    fun `3 PRODUCTION environment with missing redirect URI fails with META_REDIRECT_URI_MISSING`() {
        val config = MetaOAuthConfig(appId = "1234567890", redirectUri = "")
        val status = MetaConfigurationValidator.validate(
            config = config,
            isBackendConfigured = true,
            environment = ExecutionEnvironment.PRODUCTION
        )

        assertFalse(status.isReady)
        assertEquals(MetaConfigStatusLevel.INCOMPLETE, status.statusLevel)
        assertTrue(status.errors.any { it.contains("META_REDIRECT_URI_MISSING") })
    }

    @Test
    fun `4 REAL environment with unconfigured backend reports BACKEND_REQUIRED`() {
        val config = MetaOAuthConfig(appId = "1234567890", redirectUri = "https://socialagent.app/oauth/callback")
        val status = MetaConfigurationValidator.validate(
            config = config,
            isBackendConfigured = false,
            environment = ExecutionEnvironment.REAL
        )

        assertFalse(status.isReady)
        assertEquals(MetaConfigStatusLevel.BACKEND_REQUIRED, status.statusLevel)
        assertTrue(status.errors.any { it.contains("META_BACKEND_NOT_CONFIGURED") })
    }

    @Test
    fun `5 Complete REAL configuration is marked READY with zero errors`() {
        val config = MetaOAuthConfig(appId = "1234567890", redirectUri = "https://socialagent.app/oauth/callback")
        val status = MetaConfigurationValidator.validate(
            config = config,
            isBackendConfigured = true,
            environment = ExecutionEnvironment.REAL
        )

        assertTrue(status.isReady)
        assertEquals(MetaConfigStatusLevel.READY, status.statusLevel)
        assertTrue(status.errors.isEmpty())
        assertTrue(status.secretServerSide)
    }

    @Test
    fun `6 RealMetaOAuthService blocks session creation when configuration is invalid`() = runTest {
        val config = MetaOAuthConfig(appId = null, environment = ExecutionEnvironment.REAL)
        val service = RealMetaOAuthService(config = config, tokenExchangeBackend = UnconfiguredMetaTokenExchangeBackend())

        val result = service.createOAuthSession(OAuthProvider.FACEBOOK)
        assertTrue(result is AppResult.Error)
        val err = (result as AppResult.Error).error
        assertTrue(err.code == "META_APP_ID_MISSING" || err.code == "META_OAUTH_NOT_CONFIGURED" || err.code == "META_BACKEND_NOT_CONFIGURED")
    }

    @Test
    fun `7 RealMetaOAuthService creates session when configuration is valid`() = runTest {
        val config = MetaOAuthConfig(appId = "9876543210", redirectUri = "https://socialagent.app/oauth/callback")
        val service = RealMetaOAuthService(config = config, tokenExchangeBackend = MockConfiguredBackend())

        val status = service.getConfigurationStatus(ExecutionEnvironment.REAL)
        assertTrue(status.isReady)

        val result = service.createOAuthSession(OAuthProvider.FACEBOOK)
        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `8 Diagnostics object toString never exposes secret fields`() {
        val status = MetaConfigurationStatus(
            environment = ExecutionEnvironment.PRODUCTION,
            appIdConfigured = true,
            redirectUriConfigured = true,
            backendConfigured = true,
            secretServerSide = true,
            isReady = true,
            statusLevel = MetaConfigStatusLevel.READY,
            errors = emptyList()
        )

        val str = status.toString()
        assertFalse(str.contains("appSecret", ignoreCase = true))
        assertFalse(str.contains("accessToken", ignoreCase = true))
        assertFalse(str.contains("refreshToken", ignoreCase = true))
        assertFalse(str.contains("authorizationCode", ignoreCase = true))
    }

    @Test
    fun `9 Reflection verification confirms no secret fields in MetaConfigurationStatus`() {
        val fields = MetaConfigurationStatus::class.java.declaredFields.map { it.name }
        assertFalse(fields.contains("appSecret"))
        assertFalse(fields.contains("accessToken"))
        assertFalse(fields.contains("refreshToken"))
        assertFalse(fields.contains("authorizationCode"))
    }

    @Test
    fun `10 Facebook and Instagram scopes are correctly partitioned`() {
        val config = MetaOAuthConfig()
        assertTrue(config.facebookRequiredScopes.contains("pages_manage_posts"))
        assertTrue(config.facebookOptionalScopes.contains("pages_messaging"))

        assertTrue(config.instagramRequiredScopes.contains("instagram_content_publish"))
        assertTrue(config.instagramOptionalScopes.contains("instagram_manage_messages"))
    }
}
