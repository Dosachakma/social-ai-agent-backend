package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AgentLogDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "workspace_id") val workspaceId: String? = null,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "action") val action: String,
    @Json(name = "platform") val platform: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "execution_environment") val executionEnvironment: String? = "MOCK",
    @Json(name = "error") val error: String? = null,
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateAgentLogRequest(
    @Json(name = "action") val action: String,
    @Json(name = "platform") val platform: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "executionEnvironment") val executionEnvironment: String? = "MOCK",
    @Json(name = "error") val error: String? = null,
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null
)
