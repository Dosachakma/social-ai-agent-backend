package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BrandProfileDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "workspace_id") val workspaceId: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "business_description") val businessDescription: String? = null,
    @Json(name = "industry") val industry: String? = null,
    @Json(name = "target_audience") val targetAudience: String? = null,
    @Json(name = "primary_language") val primaryLanguage: String? = null,
    @Json(name = "secondary_language") val secondaryLanguage: String? = null,
    @Json(name = "tone_of_voice") val toneOfVoice: String? = null,
    @Json(name = "writing_style") val writingStyle: String? = null,
    @Json(name = "preferred_cta") val preferredCta: String? = null,
    @Json(name = "preferred_hashtags") val preferredHashtags: String? = null,
    @Json(name = "words_to_avoid") val wordsToAvoid: Any? = null,
    @Json(name = "keywords") val keywords: Any? = null,
    @Json(name = "products_services") val productsServices: String? = null,
    @Json(name = "website") val website: String? = null,
    @Json(name = "contact_info") val contactInfo: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateOrUpdateBrandProfileRequest(
    @Json(name = "name") val name: String,
    @Json(name = "businessDescription") val businessDescription: String? = null,
    @Json(name = "industry") val industry: String? = null,
    @Json(name = "targetAudience") val targetAudience: String? = null,
    @Json(name = "primaryLanguage") val primaryLanguage: String? = null,
    @Json(name = "secondaryLanguage") val secondaryLanguage: String? = null,
    @Json(name = "toneOfVoice") val toneOfVoice: String? = null,
    @Json(name = "writingStyle") val writingStyle: String? = null,
    @Json(name = "preferredCta") val preferredCta: String? = null,
    @Json(name = "preferredHashtags") val preferredHashtags: String? = null,
    @Json(name = "wordsToAvoid") val wordsToAvoid: String? = null,
    @Json(name = "keywords") val keywords: List<String>? = null,
    @Json(name = "productsServices") val productsServices: String? = null,
    @Json(name = "website") val website: String? = null,
    @Json(name = "contactInfo") val contactInfo: String? = null
)
