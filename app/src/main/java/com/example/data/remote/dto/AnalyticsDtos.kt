package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlatformMetricDto(
    @Json(name = "platform") val platform: String,
    @Json(name = "reach") val reach: Int? = 0,
    @Json(name = "engagementRate") val engagementRate: Double? = 0.0,
    @Json(name = "followersGained") val followersGained: Int? = 0
)

@JsonClass(generateAdapter = true)
data class AnalyticsDataDto(
    @Json(name = "totalReach") val totalReach: Int? = 0,
    @Json(name = "totalEngagement") val totalEngagement: Int? = 0,
    @Json(name = "followerGrowthPercent") val followerGrowthPercent: Double? = 0.0,
    @Json(name = "totalScheduledPosts") val totalScheduledPosts: Int? = 0,
    @Json(name = "platformBreakdown") val platformBreakdown: List<PlatformMetricDto>? = emptyList()
)
