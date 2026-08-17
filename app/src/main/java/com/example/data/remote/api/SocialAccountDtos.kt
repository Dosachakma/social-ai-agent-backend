package com.example.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SocialAccountDto(
    @Json(name = "id") val id: String,
    @Json(name = "workspace_id") val workspaceId: String? = null,
    @Json(name = "platform") val platform: String,
    @Json(name = "platform_user_id") val platformUserId: String? = null,
    @Json(name = "account_name") val accountName: String,
    @Json(name = "account_type") val accountType: String? = null,
    @Json(name = "handle") val handle: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "connection_status") val connectionStatus: String? = null,
    @Json(name = "token_status") val tokenStatus: String? = null,
    @Json(name = "follower_count") val followerCount: Int? = null,
    @Json(name = "scopes") val scopes: List<String>? = null,
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null,
    @Json(name = "connected_at") val connectedAt: String? = null,
    @Json(name = "last_synced_at") val lastSyncedAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ConnectAccountRequest(
    @Json(name = "platform") val platform: String,
    @Json(name = "accountName") val accountName: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "handle") val handle: String? = null,
    @Json(name = "platformUserId") val platformUserId: String? = null,
    @Json(name = "accountType") val accountType: String? = null,
    @Json(name = "avatarUrl") val avatarUrl: String? = null,
    @Json(name = "scopes") val scopes: List<String>? = null,
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateAccountRequest(
    @Json(name = "accountName") val accountName: String? = null,
    @Json(name = "handle") val handle: String? = null,
    @Json(name = "avatarUrl") val avatarUrl: String? = null,
    @Json(name = "connectionStatus") val connectionStatus: String? = null,
    @Json(name = "tokenStatus") val tokenStatus: String? = null,
    @Json(name = "followerCount") val followerCount: Int? = null,
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null
)
