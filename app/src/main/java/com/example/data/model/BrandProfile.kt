package com.example.data.model

import java.util.UUID

enum class BrandTone(val displayName: String) {
    PROFESSIONAL("Professional"),
    FRIENDLY("Friendly"),
    FUNNY("Funny"),
    EDUCATIONAL("Educational"),
    LUXURY("Luxury"),
    CASUAL("Casual"),
    INSPIRATIONAL("Inspirational"),
    SALES_FOCUSED("Sales-focused")
}

enum class BrandLanguage(val displayName: String) {
    ENGLISH("English"),
    BANGLA("Bangla"),
    BANGLISH("Banglish")
}

data class BrandProfile(
    val id: String = UUID.randomUUID().toString(),
    val brandName: String = "TechPulse Inc.",
    val businessDescription: String = "Automated social media management platform powered by autonomous AI agents.",
    val industry: String = "Software / SaaS",
    val targetAudience: String = "Founders, Creators, & Marketing Teams",
    val primaryLanguage: BrandLanguage = BrandLanguage.ENGLISH,
    val secondaryLanguage: BrandLanguage? = BrandLanguage.BANGLISH,
    val brandTone: BrandTone = BrandTone.PROFESSIONAL,
    val writingStyle: String = "Concise, data-driven, engaging with high-value insights.",
    val preferredCta: String = "Try AI Agent free today at techpulse.ai",
    val preferredHashtags: String = "#AIAgents #BuildInPublic #SaaS #Growth",
    val wordsToAvoid: String = "cheap, guaranteed, spammy, discount",
    val brandKeywords: String = "AI automation, social copilot, engagement, growth",
    val productsServices: String = "AI Social Agent, Auto Post Scheduler, Sentiment Guard",
    val website: String = "https://techpulse.ai",
    val contactInfo: String = "hello@techpulse.ai | +1 (800) 555-0199"
)
