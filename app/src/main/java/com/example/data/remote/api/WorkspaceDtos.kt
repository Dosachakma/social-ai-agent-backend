package com.example.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WorkspaceDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "slug") val slug: String? = null,
    @Json(name = "owner_id") val ownerId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "user_role") val userRole: String? = null,
    @Json(name = "member_count") val memberCount: Int? = null,
    @Json(name = "account_count") val accountCount: Int? = null,
    @Json(name = "post_count") val postCount: Int? = null
)

@JsonClass(generateAdapter = true)
data class MembershipDto(
    @Json(name = "workspace_id") val workspaceId: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "role") val role: String,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class WorkspaceDetailsDto(
    @Json(name = "workspace") val workspace: WorkspaceDto,
    @Json(name = "membership") val membership: MembershipDto? = null
)
