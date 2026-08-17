package com.example.data.ai

import com.example.data.config.SecurityConfig
import com.example.data.model.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SocialAgentContext(
    val userRequest: String,
    val brandProfile: BrandProfile?,
    val conversationHistory: List<AgentMessage> = emptyList(),
    val availablePlatforms: List<PlatformType> = PlatformType.values().toList(),
    val availableTools: List<AgentTool> = AgentToolRegistry.getAllTools(),
    val autonomousLevel: AutonomousLevel = AutonomousLevel.ASSISTED,
    val currentDateTime: String = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US).format(Date()),
    val userTimezone: String = "America/New_York",
    val scheduledPostsCount: Int = 12,
    val connectedAccountsCount: Int = 5
)

class SocialAgentOrchestrator(
    private var modelConfig: AiModelConfig = AiModelConfig(),
    private val permissionManager: AgentPermissionManager = AgentPermissionManager(),
    private val brandContextBuilder: BrandContextBuilder = BrandContextBuilder()
) {

    private val mockProvider = MockAIProvider(brandContextBuilder)
    private val geminiProvider = GeminiAIProvider(modelConfig, SecurityConfig.secretsManager, brandContextBuilder)

    fun updateConfig(config: AiModelConfig) {
        this.modelConfig = config
    }

    fun getActiveProvider(): AIProvider {
        return if (modelConfig.provider == AIProviderType.GEMINI && SecurityConfig.isGeminiConfigured()) {
            geminiProvider
        } else {
            mockProvider
        }
    }

    suspend fun createPlan(context: SocialAgentContext): AgentPlan {
        delay(300)
        val lowerReq = context.userRequest.lowercase()
        val brand = context.brandProfile ?: BrandProfile()
        val activeEnv = if (modelConfig.provider == AIProviderType.GEMINI && SecurityConfig.isGeminiConfigured()) {
            ExecutionEnvironment.REAL
        } else {
            ExecutionEnvironment.MOCK
        }

        val steps = mutableListOf<AgentPlanStep>()

        // Analyze user request and decompose into structured tool steps
        val includesMultiChannel = lowerReq.contains("facebook") || lowerReq.contains("instagram") ||
                lowerReq.contains("linkedin") || lowerReq.contains("twitter") || lowerReq.contains("tiktok")
        val includesImage = lowerReq.contains("image") || lowerReq.contains("visual") || lowerReq.contains("photo")
        val includesSchedule = lowerReq.contains("schedule") || lowerReq.contains("tomorrow") ||
                lowerReq.contains("calendar") || lowerReq.contains("7 pm") || lowerReq.contains("pm")

        // 1. Create Post step
        if (lowerReq.contains("post") || lowerReq.contains("draft") || lowerReq.contains("create") || lowerReq.contains("content")) {
            val platforms = mutableListOf<String>()
            if (lowerReq.contains("facebook")) platforms.add("FACEBOOK")
            if (lowerReq.contains("instagram")) platforms.add("INSTAGRAM")
            if (lowerReq.contains("linkedin")) platforms.add("LINKEDIN")
            if (lowerReq.contains("twitter") || lowerReq.contains("x")) platforms.add("TWITTER")
            if (platforms.isEmpty()) platforms.add("INSTAGRAM")

            steps.add(
                AgentPlanStep(
                    toolName = "CreatePost",
                    targetPlatform = if (platforms.contains("FACEBOOK")) PlatformType.FACEBOOK else PlatformType.INSTAGRAM,
                    arguments = mapOf(
                        "platforms" to platforms,
                        "topic" to context.userRequest,
                        "brandName" to brand.brandName,
                        "tone" to brand.brandTone.displayName,
                        "language" to brand.primaryLanguage.displayName
                    ),
                    reason = "Draft copy tailored to ${brand.brandName} tone guidelines for $platforms",
                    requiresApproval = context.autonomousLevel != AutonomousLevel.AUTONOMOUS
                )
            )
        }

        // 2. Generate Image step
        if (includesImage || lowerReq.contains("ai product") || lowerReq.contains("instagram")) {
            steps.add(
                AgentPlanStep(
                    toolName = "GenerateImage",
                    targetPlatform = PlatformType.INSTAGRAM,
                    arguments = mapOf(
                        "prompt" to "Professional visual concept for ${brand.brandName}: ${context.userRequest}",
                        "aspectRatio" to "1:1"
                    ),
                    reason = "Generate high-quality visual asset matching brand style",
                    requiresApproval = context.autonomousLevel != AutonomousLevel.AUTONOMOUS
                )
            )
        }

        // 3. Schedule Post step
        if (includesSchedule) {
            val scheduledTime = when {
                lowerReq.contains("7 pm") -> "Tomorrow at 7:00 PM (${context.userTimezone})"
                lowerReq.contains("tomorrow") -> "Tomorrow at 6:00 PM (${context.userTimezone})"
                else -> "Today at 6:00 PM (${context.userTimezone})"
            }

            steps.add(
                AgentPlanStep(
                    toolName = "SchedulePost",
                    targetPlatform = PlatformType.FACEBOOK,
                    arguments = mapOf(
                        "scheduledTime" to scheduledTime,
                        "timezone" to context.userTimezone
                    ),
                    reason = "Queue generated post into content calendar slot",
                    requiresApproval = context.autonomousLevel != AutonomousLevel.AUTONOMOUS
                )
            )
        }

        // Fallback step if request was broad or didn't match specific triggers
        if (steps.isEmpty()) {
            steps.add(
                AgentPlanStep(
                    toolName = "CreatePost",
                    targetPlatform = PlatformType.INSTAGRAM,
                    arguments = mapOf(
                        "platform" to "INSTAGRAM",
                        "topic" to context.userRequest,
                        "tone" to brand.brandTone.displayName
                    ),
                    reason = "Draft social post for ${brand.brandName}",
                    requiresApproval = context.autonomousLevel != AutonomousLevel.AUTONOMOUS
                )
            )
        }

        val riskLevel = if (steps.any { it.toolName.equals("PublishPost", ignoreCase = true) }) "High"
        else if (steps.any { it.toolName.equals("SchedulePost", ignoreCase = true) }) "Medium"
        else "Low"

        val requiresApproval = context.autonomousLevel != AutonomousLevel.AUTONOMOUS || riskLevel == "High"

        return AgentPlan(
            userRequest = context.userRequest,
            steps = steps,
            requiresApproval = requiresApproval,
            approvalState = if (requiresApproval) ActionApprovalState.AWAITING_APPROVAL else ActionApprovalState.APPROVED,
            riskLevel = riskLevel,
            summaryText = "Action Plan created (${steps.size} tool steps proposed)",
            executionEnvironment = activeEnv
        )
    }

    suspend fun executePlan(plan: AgentPlan): AgentPlan {
        plan.approvalState = ActionApprovalState.EXECUTING

        for (step in plan.steps) {
            step.status = PlanStepStatus.EXECUTING

            // 1. Permission check
            if (!permissionManager.checkToolPermission(step.toolName)) {
                step.status = PlanStepStatus.FAILED
                step.result = AgentToolResult(
                    success = false,
                    status = "PERMISSION_DENIED",
                    outputMessage = "Permission denied for tool '${step.toolName}'.",
                    executionEnvironment = plan.executionEnvironment,
                    error = "User permissions prohibit executing ${step.toolName}"
                )
                plan.approvalState = ActionApprovalState.FAILED
                return plan
            }

            // 2. Lookup registered tool
            val tool = AgentToolRegistry.getTool(step.toolName)
            if (tool == null) {
                step.status = PlanStepStatus.FAILED
                step.result = AgentToolResult(
                    success = false,
                    status = "UNKNOWN_TOOL",
                    outputMessage = "Tool '${step.toolName}' is not registered in AgentToolRegistry.",
                    executionEnvironment = plan.executionEnvironment,
                    error = "Unregistered tool"
                )
                plan.approvalState = ActionApprovalState.FAILED
                return plan
            }

            // 3. Execute tool strictly via registry (NEVER direct platform API call)
            val result = tool.execute(step.arguments)
            when (result) {
                is AppResult.Success -> {
                    step.status = PlanStepStatus.SUCCESS
                    step.result = result.data
                }
                is AppResult.Error -> {
                    step.status = PlanStepStatus.FAILED
                    step.result = AgentToolResult(
                        success = false,
                        status = "EXECUTION_ERROR",
                        outputMessage = result.error.message,
                        executionEnvironment = plan.executionEnvironment,
                        error = result.error.code
                    )
                    plan.approvalState = ActionApprovalState.FAILED
                    return plan
                }
            }
        }

        plan.approvalState = ActionApprovalState.SUCCESS
        return plan
    }
}
