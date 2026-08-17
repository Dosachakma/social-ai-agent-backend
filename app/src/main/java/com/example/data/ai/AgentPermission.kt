package com.example.data.ai

import com.example.data.model.*

enum class AgentPermission(val displayName: String, val description: String) {
    CREATE_CONTENT("Create Content", "Draft copy and post text"),
    GENERATE_MEDIA("Generate Media", "Create or edit images and visuals"),
    SCHEDULE_CONTENT("Schedule Content", "Queue posts in calendar"),
    PUBLISH_CONTENT("Publish Content", "Publish posts directly to channels"),
    REPLY_COMMENT("Reply Comment", "Post replies to public comments"),
    DELETE_COMMENT("Delete Comment", "Remove public comments"),
    SEND_MESSAGE("Send Message", "Send private direct messages"),
    ANALYZE_ACCOUNT("Analyze Account", "View performance metrics and analytics")
}

class AgentPermissionManager(
    private val enabledPermissions: MutableSet<AgentPermission> = AgentPermission.values().toMutableSet()
) {
    fun hasPermission(permission: AgentPermission): Boolean {
        return enabledPermissions.contains(permission)
    }

    fun enablePermission(permission: AgentPermission) {
        enabledPermissions.add(permission)
    }

    fun disablePermission(permission: AgentPermission) {
        enabledPermissions.remove(permission)
    }

    fun checkToolPermission(toolName: String): Boolean {
        val requiredPerm = getRequiredPermissionForTool(toolName) ?: return true
        return hasPermission(requiredPerm)
    }

    fun getRequiredPermissionForTool(toolName: String): AgentPermission? {
        return when (toolName.lowercase()) {
            "createpost", "create_post" -> AgentPermission.CREATE_CONTENT
            "generateimage", "generate_image" -> AgentPermission.GENERATE_MEDIA
            "schedulepost", "schedule_post" -> AgentPermission.SCHEDULE_CONTENT
            "publishpost", "publish_post" -> AgentPermission.PUBLISH_CONTENT
            "getcomments", "get_comments" -> AgentPermission.ANALYZE_ACCOUNT
            "replycomment", "reply_comment" -> AgentPermission.REPLY_COMMENT
            "deletecomment", "delete_comment" -> AgentPermission.DELETE_COMMENT
            "getmessages", "get_messages" -> AgentPermission.ANALYZE_ACCOUNT
            "replymessage", "reply_message" -> AgentPermission.SEND_MESSAGE
            "analyzeaccount", "analyze_account" -> AgentPermission.ANALYZE_ACCOUNT
            "detectspam", "detect_spam" -> AgentPermission.ANALYZE_ACCOUNT
            "detectphishing", "detect_phishing" -> AgentPermission.ANALYZE_ACCOUNT
            else -> null
        }
    }
}

/**
 * Requirement 15: 7-Layer Execution Safety Chain
 */
data class SafetyCheckResult(
    val isAllowed: Boolean,
    val blockingReason: String? = null,
    val errorCode: String? = null
)

object AgentSafetyValidator {
    fun validateExecution(
        userPermissionGranted: Boolean,
        account: SocialAccount?,
        requiredCapability: SocialCapability?,
        toolPermissionGranted: Boolean,
        approvalState: ActionApprovalState
    ): SafetyCheckResult {
        // Layer 1: User agent permission
        if (!userPermissionGranted) {
            return SafetyCheckResult(
                isAllowed = false,
                blockingReason = "User agent permission denied.",
                errorCode = "USER_PERMISSION_DENIED"
            )
        }

        // Layer 2: Account connection
        if (account == null || !account.isConnected || account.connectionStatus != ConnectionStatus.CONNECTED) {
            return SafetyCheckResult(
                isAllowed = false,
                blockingReason = "Social account is disconnected.",
                errorCode = "ACCOUNT_DISCONNECTED"
            )
        }

        // Layer 3: Token status
        if (account.tokenStatus == TokenStatus.EXPIRED || account.tokenStatus == TokenStatus.REVOKED) {
            return SafetyCheckResult(
                isAllowed = false,
                blockingReason = "Social account authentication token is expired. Reconnection required.",
                errorCode = "REAUTH_REQUIRED"
            )
        }

        // Layer 4: Platform capability
        if (requiredCapability != null && !account.availableCapabilities.contains(requiredCapability)) {
            return SafetyCheckResult(
                isAllowed = false,
                blockingReason = "Social account missing capability '${requiredCapability.displayName}'.",
                errorCode = "CAPABILITY_UNSUPPORTED"
            )
        }

        // Layer 5: Tool permission
        if (!toolPermissionGranted) {
            return SafetyCheckResult(
                isAllowed = false,
                blockingReason = "Tool permission denied.",
                errorCode = "TOOL_PERMISSION_DENIED"
            )
        }

        // Layer 6: Approval state
        if (approvalState != ActionApprovalState.APPROVED) {
            return SafetyCheckResult(
                isAllowed = false,
                blockingReason = "Action requires explicit user approval.",
                errorCode = "APPROVAL_REQUIRED"
            )
        }

        // Layer 7: Platform-specific constraints / account type support
        if (account.platform == PlatformType.INSTAGRAM && account.accountType == AccountType.PERSONAL &&
            (requiredCapability == SocialCapability.PUBLISH_POST || requiredCapability == SocialCapability.STORY_PUBLISH || requiredCapability == SocialCapability.REEL_PUBLISH)
        ) {
            return SafetyCheckResult(
                isAllowed = false,
                blockingReason = "This Instagram account type is not supported for this feature.",
                errorCode = "UNSUPPORTED_INSTAGRAM_ACCOUNT_TYPE"
            )
        }

        return SafetyCheckResult(isAllowed = true)
    }
}
