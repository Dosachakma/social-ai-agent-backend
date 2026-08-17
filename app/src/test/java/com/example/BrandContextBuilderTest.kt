package com.example

import com.example.data.ai.BrandContextBuilder
import com.example.data.model.*
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandContextBuilderTest {

    private val builder = BrandContextBuilder()

    @Test
    fun testBuildSystemPromptContainsBrandDetails() {
        val brand = BrandProfile(
            brandName = "NovaTech",
            industry = "SaaS",
            targetAudience = "Developers & Founders",
            brandTone = BrandTone.PROFESSIONAL,
            preferredCta = "Sign up for early access"
        )

        val prompt = builder.buildSystemPrompt(
            brandProfile = brand,
            platform = PlatformType.LINKEDIN,
            contentRules = "Keep under 200 words"
        )

        assertTrue(prompt.contains("NovaTech"))
        assertTrue(prompt.contains("SaaS"))
        assertTrue(prompt.contains("Developers & Founders"))
        assertTrue(prompt.contains("LinkedIn"))
        assertTrue(prompt.contains("Keep under 200 words"))
    }

    @Test
    fun testBuildFullUserPromptContainsUserRequest() {
        val brand = BrandProfile(brandName = "AuraApp")
        val userRequest = "Write a promotional launch tweet"

        val fullPrompt = builder.buildFullUserPrompt(
            brandProfile = brand,
            userRequest = userRequest,
            platform = PlatformType.TWITTER
        )

        assertTrue(fullPrompt.contains("USER REQUEST:\nWrite a promotional launch tweet"))
        assertTrue(fullPrompt.contains("AuraApp"))
    }
}
