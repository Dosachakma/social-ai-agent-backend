package com.example.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Standard API wrapper responses matching Express backend schema.
 */
@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: T? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "meta") val meta: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class SimpleMessageResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class HealthCheckResponse(
    @Json(name = "status") val status: String,
    @Json(name = "service") val service: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null
)
