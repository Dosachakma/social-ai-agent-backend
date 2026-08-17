package com.example.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PublishResultDto(
    @Json(name = "id") val id: String,
    @Json(name = "post_id") val postId: String,
    @Json(name = "workspace_id") val workspaceId: String? = null,
    @Json(name = "platform") val platform: String,
    @Json(name = "status") val status: String,
    @Json(name = "external_post_id") val externalPostId: String? = null,
    @Json(name = "error_message") val errorMessage: String? = null,
    @Json(name = "idempotency_key") val idempotencyKey: String? = null,
    @Json(name = "execution_environment") val executionEnvironment: String? = null,
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SavePublishResultRequest(
    @Json(name = "platform") val platform: String,
    @Json(name = "status") val status: String,
    @Json(name = "externalPostId") val externalPostId: String? = null,
    @Json(name = "errorMessage") val errorMessage: String? = null,
    @Json(name = "idempotencyKey") val idempotencyKey: String? = null,
    @Json(name = "executionEnvironment") val executionEnvironment: String? = null,
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null
)
