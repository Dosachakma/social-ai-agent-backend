package com.example.data.repository

import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstraction for Social Media account management, activity feeds, and posts.
 */
interface SocialMediaRepository {
    fun getConnectedAccounts(): Flow<List<SocialAccount>>
    fun getScheduledPosts(): Flow<List<SocialPost>>
    fun getAllPosts(): Flow<List<SocialPost>>
    fun getAiSuggestions(): Flow<List<AiSuggestion>>
    fun getRecentActivity(): Flow<List<ActivityLog>>
    fun getAnalytics(): Flow<AnalyticsData>
    
    suspend fun createPost(post: SocialPost): Result<SocialPost>
    suspend fun deletePost(postId: String): Result<Unit>
    suspend fun toggleAccountConnection(accountId: String): Result<SocialAccount>
    suspend fun connectAccount(provider: OAuthProvider, code: String = "code_mock_auth"): AppResult<SocialAccount>
    suspend fun saveConnectedAccount(account: SocialAccount): AppResult<SocialAccount>
    suspend fun disconnectAccount(accountId: String): AppResult<Boolean>
    suspend fun updateTokenStatus(accountId: String, tokenStatus: TokenStatus): AppResult<SocialAccount>
    suspend fun getAccountByPlatform(platform: PlatformType): SocialAccount?
}

/**
 * Interface for AI Agent capabilities (text generation, image prompt, comment assistance, spam/phishing detection, strategy advice).
 */
interface AiAgentService {
    suspend fun sendAgentPrompt(userPrompt: String): Flow<AgentMessage>
    suspend fun generatePostCopy(topic: String, targetPlatform: PlatformType, tone: String): Result<String>
    suspend fun generateImageConcept(prompt: String): Result<String>
    suspend fun detectSpamOrPhishing(commentText: String): Result<Pair<Boolean, String>>
    suspend fun generateCommentReply(commentText: String, tone: String): Result<String>
    suspend fun processVoiceToText(audioBytes: ByteArray): Result<String>
    suspend fun processTextToVoice(text: String): Result<ByteArray>
}

/**
 * Core AI Service interface defining conversation streaming, action execution, and multi-modal handlers.
 * Allows seamless swap between Mock implementation and real Gemini/AI provider.
 */
interface AIService {
    suspend fun processUserPrompt(prompt: String): Flow<AgentMessage>
    suspend fun executeAction(action: AgentAction, preview: GeneratedContentPreview?): Flow<AgentMessage>
    suspend fun executePlan(plan: com.example.data.ai.AgentPlan): Flow<AgentMessage>
    suspend fun processVoiceToText(): Result<String>
    suspend fun processAttachment(attachmentName: String): Result<String>
    fun updateModelConfig(config: AiModelConfig)
    fun getModelConfig(): AiModelConfig
}

/**
 * Interface for scheduling, calendar queues, and autonomous triggers.
 */
interface PostSchedulerService {
    suspend fun schedulePost(post: SocialPost, timeInMillis: Long): Result<Boolean>
    suspend fun cancelScheduledPost(postId: String): Result<Boolean>
    suspend fun getScheduledCalendarSlots(): Flow<Map<String, List<SocialPost>>>
}
