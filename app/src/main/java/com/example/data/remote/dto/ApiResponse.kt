package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "data") val data: T? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "meta") val meta: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class ApiListResponse<T>(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "data") val data: List<T>? = emptyList(),
    @Json(name = "error") val error: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "meta") val meta: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class ApiSimpleResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String? = null,
    @Json(name = "error") val error: String? = null
)
