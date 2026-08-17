package com.example.data.config

import com.example.data.model.ExecutionEnvironment

enum class MetaConfigStatusLevel(val label: String) {
    READY("READY"),
    INCOMPLETE("INCOMPLETE"),
    BACKEND_REQUIRED("BACKEND_REQUIRED")
}

data class MetaConfigurationStatus(
    val environment: ExecutionEnvironment,
    val appIdConfigured: Boolean,
    val redirectUriConfigured: Boolean,
    val backendConfigured: Boolean,
    val secretServerSide: Boolean = true,
    val isReady: Boolean,
    val statusLevel: MetaConfigStatusLevel,
    val errors: List<String> = emptyList(),
    val apiVersion: String = "v19.0",
    val redirectUri: String = ""
) {
    override fun toString(): String {
        return "MetaConfigurationStatus(environment=$environment, appIdConfigured=$appIdConfigured, redirectUriConfigured=$redirectUriConfigured, backendConfigured=$backendConfigured, secretServerSide=$secretServerSide, isReady=$isReady, statusLevel=$statusLevel, errors=$errors)"
    }
}

object MetaConfigurationValidator {

    fun validate(
        config: MetaOAuthConfig = MetaOAuthConfig(),
        isBackendConfigured: Boolean = false,
        environment: ExecutionEnvironment = config.environment
    ): MetaConfigurationStatus {
        if (environment == ExecutionEnvironment.MOCK) {
            return MetaConfigurationStatus(
                environment = ExecutionEnvironment.MOCK,
                appIdConfigured = config.isConfigured,
                redirectUriConfigured = config.redirectUri.isNotBlank(),
                backendConfigured = true,
                secretServerSide = true,
                isReady = true,
                statusLevel = MetaConfigStatusLevel.READY,
                errors = emptyList(),
                apiVersion = config.apiVersion,
                redirectUri = config.redirectUri
            )
        }

        val errors = mutableListOf<String>()

        val appIdOk = !config.appId.isNullOrBlank()
        if (!appIdOk) {
            errors.add("META_APP_ID_MISSING: Meta App ID is not configured in environment variables or BuildConfig.")
        }

        val redirectUriOk = config.redirectUri.isNotBlank() &&
                (config.redirectUri.startsWith("https://") || config.redirectUri.startsWith("http://localhost"))
        if (!redirectUriOk) {
            errors.add("META_REDIRECT_URI_MISSING: Valid Meta OAuth redirect URI is missing or invalid.")
        }

        if (!isBackendConfigured) {
            errors.add("META_BACKEND_NOT_CONFIGURED: Server-side token exchange backend is not configured.")
        }

        val isReady = appIdOk && redirectUriOk && isBackendConfigured
        val statusLevel = when {
            isReady -> MetaConfigStatusLevel.READY
            !isBackendConfigured && appIdOk && redirectUriOk -> MetaConfigStatusLevel.BACKEND_REQUIRED
            else -> MetaConfigStatusLevel.INCOMPLETE
        }

        return MetaConfigurationStatus(
            environment = environment,
            appIdConfigured = appIdOk,
            redirectUriConfigured = redirectUriOk,
            backendConfigured = isBackendConfigured,
            secretServerSide = true,
            isReady = isReady,
            statusLevel = statusLevel,
            errors = errors,
            apiVersion = config.apiVersion,
            redirectUri = config.redirectUri
        )
    }
}
