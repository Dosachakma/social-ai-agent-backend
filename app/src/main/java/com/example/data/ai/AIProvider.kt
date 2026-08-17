package com.example.data.ai

import com.example.data.model.*
import kotlinx.coroutines.delay

interface AIProvider {
    val providerName: String
    val executionEnvironment: ExecutionEnvironment

    suspend fun generateText(
        prompt: String,
        brandProfile: BrandProfile? = null,
        platform: PlatformType? = null,
        contentRules: String? = null,
        conversationContext: List<AgentMessage>? = null
    ): AppResult<String>

    suspend fun generateContentPreview(
        userRequest: String,
        brandProfile: BrandProfile? = null,
        platform: PlatformType? = null,
        contentRules: String? = null,
        conversationContext: List<AgentMessage>? = null
    ): AppResult<GeneratedContentPreview>

    suspend fun processVoiceToText(audioBytes: ByteArray? = null): AppResult<String>
}

class MockAIProvider(
    private val contextBuilder: BrandContextBuilder = BrandContextBuilder()
) : AIProvider {

    override val providerName: String = "Mock AI Engine (Demo)"
    override val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment.MOCK

    override suspend fun generateText(
        prompt: String,
        brandProfile: BrandProfile?,
        platform: PlatformType?,
        contentRules: String?,
        conversationContext: List<AgentMessage>?
    ): AppResult<String> {
        delay(300)
        val brandName = brandProfile?.brandName ?: "Your Brand"
        return AppResult.Success(
            "🚀 $prompt: $brandName delivers automated, contextual social media strategies!"
        )
    }

    override suspend fun generateContentPreview(
        userRequest: String,
        brandProfile: BrandProfile?,
        platform: PlatformType?,
        contentRules: String?,
        conversationContext: List<AgentMessage>?
    ): AppResult<GeneratedContentPreview> {
        delay(400)
        val brand = brandProfile ?: BrandProfile()
        val targetPlatform = platform ?: PlatformType.INSTAGRAM

        val preview = GeneratedContentPreview(
            platform = targetPlatform,
            tone = brand.brandTone.displayName,
            title = "${brand.brandName} ${targetPlatform.displayName} Post",
            content = "🚀 Elevate your strategy with ${brand.brandName}!\n\n${brand.businessDescription}\n\n👉 ${brand.preferredCta}\n\n${brand.preferredHashtags}",
            actionType = AgentAction.CREATE_POST,
            scheduledTime = "Today at 6:00 PM",
            approvalState = ActionApprovalState.AWAITING_APPROVAL,
            executionEnvironment = ExecutionEnvironment.MOCK,
            executionMessage = "Mock content generated. Awaiting user approval."
        )

        return AppResult.Success(preview)
    }

    override suspend fun processVoiceToText(audioBytes: ByteArray?): AppResult<String> {
        delay(300)
        return AppResult.Success("Create a social post using my brand guidelines")
    }
}

/**
 * Backwards compatible Gemini Provider reference, delegating to GeminiAIProvider.
 */
class GeminiAIProviderPlaceholder(
    private val apiKey: String? = null
) : AIProvider {

    private val realProvider = GeminiAIProvider()

    override val providerName: String get() = realProvider.providerName
    override val executionEnvironment: ExecutionEnvironment get() = realProvider.executionEnvironment

    override suspend fun generateText(
        prompt: String,
        brandProfile: BrandProfile?,
        platform: PlatformType?,
        contentRules: String?,
        conversationContext: List<AgentMessage>?
    ): AppResult<String> {
        return realProvider.generateText(prompt, brandProfile, platform, contentRules, conversationContext)
    }

    override suspend fun generateContentPreview(
        userRequest: String,
        brandProfile: BrandProfile?,
        platform: PlatformType?,
        contentRules: String?,
        conversationContext: List<AgentMessage>?
    ): AppResult<GeneratedContentPreview> {
        return realProvider.generateContentPreview(userRequest, brandProfile, platform, contentRules, conversationContext)
    }

    override suspend fun processVoiceToText(audioBytes: ByteArray?): AppResult<String> {
        return realProvider.processVoiceToText(audioBytes)
    }
}
