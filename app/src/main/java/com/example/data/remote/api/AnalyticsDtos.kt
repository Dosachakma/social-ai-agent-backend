package com.example.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlatformMetricDto(
    @Json(name = "platform") val platform: String,
    @Json(name = "reach") val reach: Int = 0,
    @Json(name = "engagement_rate") val engagementRate: Double = 0.0,
    @Json(name = "followers_gained") val followersGained: Int = 0
)

@JsonClass(generateAdapter = true)
data class AnalyticsDto(
    @Json(name = "workspace_id") val workspaceId: String? = null,
    @Json(name = "total_reach") val totalReach: Int = 0,
    @Json(name = "total_engagement") val totalEngagement: Int = 0,
    @Json(name = "follower_growth_percent") val followerGrowthPercent: Double = 0.0,
    @Json(name = "total_scheduled_posts") val totalScheduledPosts: Int = 0,
    @Json(name = "platform_breakdown") val platformBreakdown: List<PlatformMetricDto>? = null,
    @Json(name = "is_demo_data") val isDemoData: Boolean = false
)
