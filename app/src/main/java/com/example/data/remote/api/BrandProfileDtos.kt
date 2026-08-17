package com.example.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BrandProfileDto(
    @Json(name = "id") val id: String,
    @Json(name = "workspace_id") val workspaceId: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "brand_name") val brandName: String? = null,
    @Json(name = "industry") val industry: String? = null,
    @Json(name = "tagline") val tagline: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "target_audience") val targetAudience: String? = null,
    @Json(name = "primary_language") val primaryLanguage: String? = null,
    @Json(name = "secondary_language") val secondaryLanguage: String? = null,
    @Json(name = "tone_of_voice") val toneOfVoice: String? = null,
    @Json(name = "writing_style") val writingStyle: String? = null,
    @Json(name = "preferred_cta") val preferredCta: String? = null,
    @Json(name = "preferred_hashtags") val preferredHashtags: List<String>? = null,
    @Json(name = "words_to_avoid") val wordsToAvoid: List<String>? = null,
    @Json(name = "brand_keywords") val brandKeywords: List<String>? = null,
    @Json(name = "products_services") val productsServices: String? = null,
    @Json(name = "website") val website: String? = null,
    @Json(name = "contact_info") val contactInfo: String? = null,
    @Json(name = "is_default") val isDefault: Boolean? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class BrandProfileMutationRequest(
    @Json(name = "name") val name: String,
    @Json(name = "brand_name") val brandName: String? = null,
    @Json(name = "industry") val industry: String? = null,
    @Json(name = "tagline") val tagline: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "target_audience") val targetAudience: String? = null,
    @Json(name = "primary_language") val primaryLanguage: String? = null,
    @Json(name = "secondary_language") val secondaryLanguage: String? = null,
    @Json(name = "tone_of_voice") val toneOfVoice: String? = null,
    @Json(name = "writing_style") val writingStyle: String? = null,
    @Json(name = "preferred_cta") val preferredCta: String? = null,
    @Json(name = "preferred_hashtags") val preferredHashtags: List<String>? = null,
    @Json(name = "words_to_avoid") val wordsToAvoid: List<String>? = null,
    @Json(name = "brand_keywords") val brandKeywords: List<String>? = null,
    @Json(name = "products_services") val productsServices: String? = null,
    @Json(name = "website") val website: String? = null,
    @Json(name = "contact_info") val contactInfo: String? = null,
    @Json(name = "is_default") val isDefault: Boolean? = null
)
