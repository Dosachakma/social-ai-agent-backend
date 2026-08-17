package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlatformPublishResultDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "workspace_id") val workspaceId: String? = null,
    @Json(name = "post_id") val postId: String? = null,
    @Json(name = "platform") val platform: String,
    @Json(name = "status") val status: String,
    @Json(name = "external_post_id") val externalPostId: String? = null,
    @Json(name = "error_message") val errorMessage: String? = null,
    @Json(name = "execution_environment") val executionEnvironment: String? = "MOCK",
    @Json(name = "idempotency_key") val idempotencyKey: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SavePublishResultRequest(
    @Json(name = "platform") val platform: String,
    @Json(name = "status") val status: String,
    @Json(name = "externalPostId") val externalPostId: String? = null,
    @Json(name = "errorMessage") val errorMessage: String? = null,
    @Json(name = "executionEnvironment") val executionEnvironment: String? = "MOCK",
    @Json(name = "idempotencyKey") val idempotencyKey: String? = null
)
