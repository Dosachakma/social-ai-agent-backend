package com.example.data.session

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor that injects standard headers and the active Bearer JWT token.
 * Strictly adheres to security rules: never modifies or leaks token content in logging.
 */
class AuthInterceptor(
    private val sessionManager: SessionManager = SessionManager.getInstance()
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .header("User-Agent", "SocialStudio-Android/1.0.0")
            .header("Accept", "application/json")

        val token = sessionManager.currentToken
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val workspaceId = sessionManager.currentWorkspaceId
        if (workspaceId.isNotBlank()) {
            requestBuilder.header("X-Workspace-Id", workspaceId)
        }

        return chain.proceed(requestBuilder.build())
    }
}
