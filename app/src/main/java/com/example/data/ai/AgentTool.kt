package com.example.data.ai

import com.example.data.model.*
import com.example.data.security.AccountValidationEngine
import com.example.data.security.AccountValidationResult
import kotlinx.coroutines.delay

data class AgentToolResult(
    val success: Boolean,
    val status: String = if (success) "SUCCESS" else "FAILED",
    val outputMessage: String,
    val approvalState: ActionApprovalState = ActionApprovalState.AWAITING_APPROVAL,
    val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment.MOCK,
    val data: Map<String, Any?> = emptyMap(),
    val error: String? = null
)

interface AgentTool {
    val name: String
    val description: String
    val requiredPermission: AgentPermission?
    val requiredCapability: SocialCapability? get() = null
    suspend fun execute(parameters: Map<String, Any?>): AppResult<AgentToolResult>
}

abstract class BaseMockAgentTool(
    override val name: String,
    override val description: String,
    override val requiredPermission: AgentPermission? = null,
    override val requiredCapability: SocialCapability? = null
) : AgentTool {
    var targetAccountOverride: SocialAccount? = null
    var userPermissionGrantedOverride: Boolean? = null

    override suspend fun execute(parameters: Map<String, Any?>): AppResult<AgentToolResult> {
        delay(100)

        val accountToValidate = targetAccountOverride ?: (parameters["account"] as? SocialAccount)
        val hasPermission = userPermissionGrantedOverride ?: true

        if (accountToValidate != null || parameters.containsKey("account")) {
            val validation = AccountValidationEngine().validateActionExecution(
                account = accountToValidate,
                requiredCapability = requiredCapability,
                hasUserPermission = hasPermission,
                isApproved = true
            )

            if (validation is AccountValidationResult.Blocked) {
                return AppResult.Success(
                    AgentToolResult(
                        success = false,
                        status = "VALIDATION_FAILED",
                        outputMessage = "Action blocked: ${validation.message}",
                        approvalState = ActionApprovalState.FAILED,
                        executionEnvironment = ExecutionEnvironment.MOCK,
                        data = parameters,
                        error = validation.code
                    )
                )
            }
        } else if (!hasPermission) {
            return AppResult.Success(
                AgentToolResult(
                    success = false,
                    status = "PERMISSION_DENIED",
                    outputMessage = "Action blocked: User permission missing or denied.",
                    approvalState = ActionApprovalState.FAILED,
                    executionEnvironment = ExecutionEnvironment.MOCK,
                    data = parameters,
                    error = "PERMISSION_DENIED"
                )
            )
        }

        return AppResult.Success(
            AgentToolResult(
                success = true,
                status = "SUCCESS",
                outputMessage = "Mock tool '$name' execution completed.",
                approvalState = ActionApprovalState.SUCCESS,
                executionEnvironment = ExecutionEnvironment.MOCK,
                data = parameters
            )
        )
    }
}

class CreatePostTool : BaseMockAgentTool("CreatePost", "Drafts copy for social media posts using brand memory", AgentPermission.CREATE_CONTENT, SocialCapability.CREATE_POST)
class GenerateImageTool : BaseMockAgentTool("GenerateImage", "Generates visual asset concepts with aspect ratios", AgentPermission.GENERATE_MEDIA, SocialCapability.MEDIA_UPLOAD)
class SchedulePostTool : BaseMockAgentTool("SchedulePost", "Schedules posts into calendar queues", AgentPermission.SCHEDULE_CONTENT, SocialCapability.CREATE_POST)
class PublishPostTool : BaseMockAgentTool("PublishPost", "Publishes post drafts to selected social platforms", AgentPermission.PUBLISH_CONTENT, SocialCapability.PUBLISH_POST)
class GetCommentsTool : BaseMockAgentTool("GetComments", "Retrieves recent comments across connected channels", AgentPermission.ANALYZE_ACCOUNT, SocialCapability.READ_COMMENTS)
class ReplyCommentTool : BaseMockAgentTool("ReplyComment", "Generates and posts responses to user comments", AgentPermission.REPLY_COMMENT, SocialCapability.REPLY_COMMENT)
class GetMessagesTool : BaseMockAgentTool("GetMessages", "Retrieves direct messages across connected channels", AgentPermission.ANALYZE_ACCOUNT, SocialCapability.READ_MESSAGES)
class ReplyMessageTool : BaseMockAgentTool("ReplyMessage", "Generates and posts responses to direct messages", AgentPermission.SEND_MESSAGE, SocialCapability.SEND_MESSAGE)
class AnalyzeAccountTool : BaseMockAgentTool("AnalyzeAccount", "Analyzes performance metrics and audience engagement", AgentPermission.ANALYZE_ACCOUNT, SocialCapability.READ_ANALYTICS)
class DetectSpamTool : BaseMockAgentTool("DetectSpam", "Scans incoming comments for spam patterns", AgentPermission.ANALYZE_ACCOUNT, SocialCapability.READ_COMMENTS)
class DetectPhishingTool : BaseMockAgentTool("DetectPhishing", "Scans incoming messages/links for security threats", AgentPermission.ANALYZE_ACCOUNT, SocialCapability.READ_MESSAGES)

object AgentToolRegistry {
    private val tools: MutableMap<String, AgentTool> = listOf(
        CreatePostTool(),
        GenerateImageTool(),
        SchedulePostTool(),
        PublishPostTool(),
        GetCommentsTool(),
        ReplyCommentTool(),
        GetMessagesTool(),
        ReplyMessageTool(),
        AnalyzeAccountTool(),
        DetectSpamTool(),
        DetectPhishingTool()
    ).associateBy { it.name.lowercase() }.toMutableMap()

    fun getTool(name: String): AgentTool? {
        val key = name.lowercase().replace("_", "")
        return tools[key] ?: tools.values.find { it.name.lowercase().replace("_", "") == key }
    }

    fun getAllTools(): List<AgentTool> = tools.values.toList()
}
