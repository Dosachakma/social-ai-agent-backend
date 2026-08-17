package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SocialAccountDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "workspace_id") val workspaceId: String? = null,
    @Json(name = "platform") val platform: String? = null,
    @Json(name = "platform_user_id") val platformUserId: String? = null,
    @Json(name = "account_name") val accountName: String? = null,
    @Json(name = "handle") val handle: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "account_type") val accountType: String? = null,
    @Json(name = "connection_status") val connectionStatus: String? = null,
    @Json(name = "token_status") val tokenStatus: String? = null,
    @Json(name = "scopes") val scopes: List<String>? = emptyList(),
    @Json(name = "follower_count") val followerCount: Int? = 0,
    @Json(name = "posts_today_count") val postsTodayCount: Int? = 0,
    @Json(name = "capabilities") val capabilities: List<String>? = emptyList(),
    @Json(name = "last_synced_at") val lastSyncedAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ConnectSocialAccountRequest(
    @Json(name = "platform") val platform: String,
    @Json(name = "platformUserId") val platformUserId: String? = null,
    @Json(name = "accountName") val accountName: String,
    @Json(name = "handle") val handle: String? = null,
    @Json(name = "avatarUrl") val avatarUrl: String? = null,
    @Json(name = "accountType") val accountType: String? = null,
    @Json(name = "connectionStatus") val connectionStatus: String? = "CONNECTED",
    @Json(name = "tokenStatus") val tokenStatus: String? = "VALID",
    @Json(name = "scopes") val scopes: List<String>? = null,
    @Json(name = "followerCount") val followerCount: Int? = null,
    @Json(name = "encryptedAccessToken") val encryptedAccessToken: String? = null,
    @Json(name = "encryptedRefreshToken") val encryptedRefreshToken: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateSocialAccountRequest(
    @Json(name = "accountName") val accountName: String? = null,
    @Json(name = "handle") val handle: String? = null,
    @Json(name = "avatarUrl") val avatarUrl: String? = null,
    @Json(name = "connectionStatus") val connectionStatus: String? = null,
    @Json(name = "tokenStatus") val tokenStatus: String? = null,
    @Json(name = "followerCount") val followerCount: Int? = null,
    @Json(name = "postsTodayCount") val postsTodayCount: Int? = null
)
