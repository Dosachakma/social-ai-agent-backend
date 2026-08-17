package com.example.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AgentLogDto(
    @Json(name = "id") val id: String,
    @Json(name = "workspace_id") val workspaceId: String? = null,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "action") val action: String,
    @Json(name = "platform") val platform: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "execution_environment") val executionEnvironment: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "detail") val detail: String? = null,
    @Json(name = "error_message") val errorMessage: String? = null,
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateAgentLogRequest(
    @Json(name = "action") val action: String,
    @Json(name = "platform") val platform: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "executionEnvironment") val executionEnvironment: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "detail") val detail: String? = null,
    @Json(name = "errorMessage") val errorMessage: String? = null,
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null
)
