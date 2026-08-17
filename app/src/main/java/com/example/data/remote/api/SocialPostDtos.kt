package com.example.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SocialPostDto(
    @Json(name = "id") val id: String,
    @Json(name = "workspace_id") val workspaceId: String? = null,
    @Json(name = "author_id") val authorId: String? = null,
    @Json(name = "title") val title: String,
    @Json(name = "content") val content: String,
    @Json(name = "target_platforms") val targetPlatforms: List<String>? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "approval_state") val approvalState: String? = null,
    @Json(name = "scheduled_time") val scheduledTime: String? = null,
    @Json(name = "scheduled_at") val scheduledAt: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "timezone") val timezone: String? = null,
    @Json(name = "repeat_option") val repeatOption: String? = null,
    @Json(name = "require_approval") val requireApproval: Boolean? = null,
    @Json(name = "media_urls") val mediaUrls: List<String>? = null,
    @Json(name = "media_url") val mediaUrl: String? = null,
    @Json(name = "hashtags") val hashtags: String? = null,
    @Json(name = "cta") val cta: String? = null,
    @Json(name = "is_ai_generated") val isAiGenerated: Boolean? = null,
    @Json(name = "engagement_score") val engagementScore: Int? = null,
    @Json(name = "error_message") val errorMessage: String? = null,
    @Json(name = "retry_count") val retryCount: Int? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "publish_results") val publishResults: List<PublishResultDto>? = null
)

@JsonClass(generateAdapter = true)
data class CreatePostRequest(
    @Json(name = "title") val title: String,
    @Json(name = "content") val content: String,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "targetPlatforms") val targetPlatforms: List<String>? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "approvalState") val approvalState: String? = null,
    @Json(name = "scheduledTime") val scheduledTime: String? = null,
    @Json(name = "scheduledAt") val scheduledAt: String? = null,
    @Json(name = "timezone") val timezone: String? = null,
    @Json(name = "repeatOption") val repeatOption: String? = null,
    @Json(name = "requireApproval") val requireApproval: Boolean? = null,
    @Json(name = "mediaUrls") val mediaUrls: List<String>? = null,
    @Json(name = "mediaUrl") val mediaUrl: String? = null,
    @Json(name = "hashtags") val hashtags: String? = null,
    @Json(name = "cta") val cta: String? = null,
    @Json(name = "isAiGenerated") val isAiGenerated: Boolean? = null,
    @Json(name = "engagementScore") val engagementScore: Int? = null
)

@JsonClass(generateAdapter = true)
data class UpdatePostRequest(
    @Json(name = "title") val title: String? = null,
    @Json(name = "content") val content: String? = null,
    @Json(name = "targetPlatforms") val targetPlatforms: List<String>? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "approvalState") val approvalState: String? = null,
    @Json(name = "scheduledTime") val scheduledTime: String? = null,
    @Json(name = "scheduledAt") val scheduledAt: String? = null,
    @Json(name = "publishedAt") val publishedAt: String? = null,
    @Json(name = "timezone") val timezone: String? = null,
    @Json(name = "repeatOption") val repeatOption: String? = null,
    @Json(name = "requireApproval") val requireApproval: Boolean? = null,
    @Json(name = "mediaUrls") val mediaUrls: List<String>? = null,
    @Json(name = "hashtags") val hashtags: String? = null,
    @Json(name = "cta") val cta: String? = null,
    @Json(name = "errorMessage") val errorMessage: String? = null,
    @Json(name = "retryCount") val retryCount: Int? = null
)
