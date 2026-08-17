package com.example.data.ai

import com.example.data.model.ActionApprovalState
import com.example.data.model.ExecutionEnvironment
import com.example.data.model.PlatformType
import java.util.UUID

enum class PlanStepStatus(val label: String) {
    PROPOSED("Proposed"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    EXECUTING("Executing"),
    SUCCESS("Success"),
    FAILED("Failed"),
    SKIPPED("Skipped")
}

data class AgentToolCall(
    val toolName: String,
    val arguments: Map<String, Any?> = emptyMap(),
    val reason: String = "",
    val requiresApproval: Boolean = true
)

data class AgentPlanStep(
    val stepId: String = UUID.randomUUID().toString(),
    val toolName: String,
    val targetPlatform: PlatformType? = null,
    val arguments: Map<String, Any?> = emptyMap(),
    val reason: String = "",
    var status: PlanStepStatus = PlanStepStatus.PROPOSED,
    val requiresApproval: Boolean = true,
    var result: AgentToolResult? = null
)

data class AgentPlan(
    val planId: String = UUID.randomUUID().toString(),
    val userRequest: String,
    val steps: List<AgentPlanStep>,
    val requiresApproval: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    var approvalState: ActionApprovalState = ActionApprovalState.AWAITING_APPROVAL,
    val riskLevel: String = "Low",
    val summaryText: String = "",
    val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment.MOCK
)

data class AgentTextResponse(
    val text: String,
    val plan: AgentPlan? = null
)

data class AIUsageRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val estimatedCost: Double = 0.0
)
