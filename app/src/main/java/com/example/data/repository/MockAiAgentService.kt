package com.example.data.repository

import com.example.data.ai.*
import com.example.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class MockAiAgentService(
    private val brandRepository: BrandProfileRepository = MockBrandProfileRepository(),
    private val contextBuilder: BrandContextBuilder = BrandContextBuilder(),
    private var modelConfig: AiModelConfig = AiModelConfig(),
    private val permissionManager: AgentPermissionManager = AgentPermissionManager(),
    private val orchestrator: SocialAgentOrchestrator = SocialAgentOrchestrator(modelConfig, permissionManager, contextBuilder)
) : AiAgentService, AIService {

    private val actionLogs = mutableListOf<AgentActionLog>()

    override fun updateModelConfig(config: AiModelConfig) {
        this.modelConfig = config
        orchestrator.updateConfig(config)
    }

    override fun getModelConfig(): AiModelConfig = modelConfig

    override suspend fun processUserPrompt(prompt: String): Flow<AgentMessage> = flow {
        val currentBrand = brandRepository.getProfile()

        // Emit User message first
        emit(
            AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = SenderType.USER,
                text = prompt,
                timestamp = "Just now"
            )
        )

        delay(400) // Simulate cognitive processing

        val agentContext = SocialAgentContext(
            userRequest = prompt,
            brandProfile = currentBrand,
            autonomousLevel = modelConfig.autonomousLevel
        )

        val plan = orchestrator.createPlan(agentContext)

        val lower = prompt.lowercase()
        val toneName = currentBrand.brandTone.displayName
        val brandName = currentBrand.brandName
        val cta = currentBrand.preferredCta
        val hashtags = currentBrand.preferredHashtags

        val targetPlatform = when {
            lower.contains("facebook") -> PlatformType.FACEBOOK
            lower.contains("linkedin") -> PlatformType.LINKEDIN
            lower.contains("twitter") || lower.contains("x") -> PlatformType.TWITTER
            lower.contains("tiktok") -> PlatformType.TIKTOK
            else -> PlatformType.INSTAGRAM
        }

        val preview = GeneratedContentPreview(
            platform = targetPlatform,
            tone = toneName,
            title = "$brandName Proposal (${plan.steps.size} tool steps)",
            content = "🚀 Elevate strategy with $brandName!\n\n${currentBrand.businessDescription}\n\n👉 $cta\n\n$hashtags",
            actionType = AgentAction.CREATE_POST,
            scheduledTime = if (lower.contains("7 pm")) "Tomorrow at 7:00 PM" else "Today at 6:00 PM",
            approvalState = plan.approvalState,
            executionEnvironment = plan.executionEnvironment,
            executionMessage = "Agent Action Plan ready. Tap Approve to execute."
        )

        val responseMsg = when {
            plan.steps.size > 1 -> "I have constructed an AI Action Plan with ${plan.steps.size} steps based on $brandName Brand Memory."
            else -> "I have drafted a social media proposal for $brandName."
        }

        val log = AgentActionLog(
            action = preview.actionType,
            platform = preview.platform,
            status = plan.approvalState,
            executionEnvironment = plan.executionEnvironment
        )
        actionLogs.add(log)

        emit(
            AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = SenderType.AGENT,
                text = responseMsg,
                timestamp = "Just now",
                action = preview.actionType,
                contentPreview = preview,
                agentPlan = plan,
                isAutonomousAction = false,
                approvalState = plan.approvalState,
                executionEnvironment = plan.executionEnvironment
            )
        )
    }

    override suspend fun executePlan(plan: AgentPlan): Flow<AgentMessage> = flow {
        emit(
            AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = SenderType.SYSTEM,
                text = "⚡ Executing AI Action Plan (${plan.steps.size} steps)...",
                timestamp = "Just now",
                approvalState = ActionApprovalState.EXECUTING,
                executionEnvironment = plan.executionEnvironment
            )
        )

        delay(500)

        val updatedPlan = orchestrator.executePlan(plan)

        val confirmMessage = if (updatedPlan.approvalState == ActionApprovalState.SUCCESS) {
            "⚡ Plan executed successfully across ${updatedPlan.steps.size} tool steps! (Environment: ${updatedPlan.executionEnvironment.displayName})"
        } else {
            "⚠️ Plan execution stopped. One or more steps encountered errors."
        }

        val updatedLog = AgentActionLog(
            action = AgentAction.CREATE_POST,
            platform = plan.steps.firstOrNull()?.targetPlatform,
            status = updatedPlan.approvalState,
            executionEnvironment = updatedPlan.executionEnvironment,
            metadata = mapOf("stepsCount" to plan.steps.size.toString())
        )
        actionLogs.add(updatedLog)

        emit(
            AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = SenderType.AGENT,
                text = confirmMessage,
                timestamp = "Just now",
                agentPlan = updatedPlan,
                isAutonomousAction = false,
                approvalState = updatedPlan.approvalState,
                executionEnvironment = updatedPlan.executionEnvironment
            )
        )
    }

    override suspend fun executeAction(
        action: AgentAction,
        preview: GeneratedContentPreview?
    ): Flow<AgentMessage> = flow {
        val actionLabel = preview?.title ?: action.label
        val platformLabel = preview?.platform?.displayName ?: "Social Channels"

        emit(
            AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = SenderType.SYSTEM,
                text = "⚡ Executing Action: ${action.label} ($actionLabel) for $platformLabel...",
                timestamp = "Just now",
                approvalState = ActionApprovalState.EXECUTING,
                executionEnvironment = preview?.executionEnvironment ?: ExecutionEnvironment.MOCK
            )
        )

        delay(400)

        val confirmMessage = when (action) {
            AgentAction.CREATE_POST, AgentAction.PUBLISH_POST ->
                "⚡ Mock execution completed for $platformLabel. (Note: No live social media API was called in Mock Mode)."
            AgentAction.SCHEDULE_POST ->
                "📅 Mock execution completed: Post added to local demo schedule queue for $platformLabel."
            AgentAction.GENERATE_IMAGE ->
                "🎨 Mock execution completed: Visual asset attached to draft."
            AgentAction.REPLY_COMMENT ->
                "💬 Mock execution completed: Comment replies staged for review."
            AgentAction.ANALYZE_ACCOUNT ->
                "📊 Mock execution completed: Analytics report updated with demo data."
            else ->
                "⚡ Mock execution completed for '${action.label}'."
        }

        val updatedLog = AgentActionLog(
            action = action,
            platform = preview?.platform,
            status = ActionApprovalState.SUCCESS,
            executionEnvironment = preview?.executionEnvironment ?: ExecutionEnvironment.MOCK,
            metadata = mapOf("note" to "Mock execution completed")
        )
        actionLogs.add(updatedLog)

        emit(
            AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = SenderType.AGENT,
                text = confirmMessage,
                timestamp = "Just now",
                isAutonomousAction = false,
                approvalState = ActionApprovalState.SUCCESS,
                executionEnvironment = preview?.executionEnvironment ?: ExecutionEnvironment.MOCK
            )
        )
    }

    override suspend fun processVoiceToText(): Result<String> {
        delay(300)
        return Result.success("Create a post using my brand memory guidelines")
    }

    override suspend fun processAttachment(attachmentName: String): Result<String> {
        delay(200)
        return Result.success("Attachment '$attachmentName' uploaded into AI context.")
    }

    override suspend fun sendAgentPrompt(userPrompt: String): Flow<AgentMessage> {
        return processUserPrompt(userPrompt)
    }

    override suspend fun generatePostCopy(topic: String, targetPlatform: PlatformType, tone: String): Result<String> {
        val result = orchestrator.getActiveProvider().generateText(
            prompt = topic,
            platform = targetPlatform,
            contentRules = "Tone: $tone"
        )
        return when (result) {
            is AppResult.Success -> Result.success(result.data)
            is AppResult.Error -> Result.failure(Exception(result.error.message))
        }
    }

    override suspend fun generateImageConcept(prompt: String): Result<String> {
        delay(300)
        return Result.success("https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800")
    }

    override suspend fun detectSpamOrPhishing(commentText: String): Result<Pair<Boolean, String>> {
        delay(200)
        return Result.success(Pair(false, "Safe comment (Mock Detection)"))
    }

    override suspend fun generateCommentReply(commentText: String, tone: String): Result<String> {
        delay(200)
        return Result.success("Thank you for reaching out! We appreciate your engagement.")
    }

    override suspend fun processVoiceToText(audioBytes: ByteArray): Result<String> {
        return processVoiceToText()
    }

    override suspend fun processTextToVoice(text: String): Result<ByteArray> {
        return Result.success(ByteArray(0))
    }
}
