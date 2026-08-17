package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WorkspaceDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "slug") val slug: String? = null,
    @Json(name = "tier") val tier: String? = "FREE",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class WorkspaceMembershipDto(
    @Json(name = "role") val role: String? = "OWNER",
    @Json(name = "permissions") val permissions: List<String>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkspaceDetailDataDto(
    @Json(name = "workspace") val workspace: WorkspaceDto,
    @Json(name = "membership") val membership: WorkspaceMembershipDto? = null
)
