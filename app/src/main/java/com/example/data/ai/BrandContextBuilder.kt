package com.example.data.ai

import com.example.data.model.AgentMessage
import com.example.data.model.BrandProfile
import com.example.data.model.PlatformType

/**
 * Abstraction responsible for combining Brand Profile memory, platform context, 
 * user requests, content rules, and conversation history into a structured AI context prompt.
 */
class BrandContextBuilder {

    fun buildSystemPrompt(
        brandProfile: BrandProfile?,
        platform: PlatformType? = null,
        contentRules: String? = null,
        conversationContext: List<AgentMessage>? = null
    ): String {
        val brand = brandProfile ?: BrandProfile()
        val platformContext = platform?.displayName ?: "Multi-platform"
        val historyContext = conversationContext?.takeLast(3)?.joinToString("\n") {
            "${it.sender.name}: ${it.text}"
        } ?: "No prior conversation history"

        return """
            SYSTEM CONTEXT & BRAND MEMORY:
            - Brand Name: ${brand.brandName}
            - Industry: ${brand.industry}
            - Description: ${brand.businessDescription}
            - Target Audience: ${brand.targetAudience}
            - Primary Language: ${brand.primaryLanguage.displayName}
            - Secondary Language: ${brand.secondaryLanguage?.displayName ?: "None"}
            - Tone: ${brand.brandTone.displayName}
            - Writing Style: ${brand.writingStyle}
            - Preferred Call To Action (CTA): ${brand.preferredCta}
            - Preferred Hashtags: ${brand.preferredHashtags}
            - Keywords: ${brand.brandKeywords}
            - Words/Phrases to Avoid: ${brand.wordsToAvoid}
            - Key Offerings: ${brand.productsServices}
            - Web/Contact: ${brand.website} | ${brand.contactInfo}
            - Target Platform: $platformContext
            ${contentRules?.let { "- Content Rules: $it" } ?: ""}
            
            RECENT CONVERSATION CONTEXT:
            $historyContext
        """.trimIndent()
    }

    fun buildFullUserPrompt(
        brandProfile: BrandProfile?,
        userRequest: String,
        platform: PlatformType? = null,
        contentRules: String? = null,
        conversationContext: List<AgentMessage>? = null
    ): String {
        val systemContext = buildSystemPrompt(brandProfile, platform, contentRules, conversationContext)
        return "$systemContext\n\nUSER REQUEST:\n$userRequest"
    }
}
