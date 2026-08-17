package com.example

import com.example.data.ai.*
import com.example.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ToolCallingSafetyTest {

    @Test
    fun permissionManager_blocksExecution_whenPermissionDisabled() = runBlocking {
        val permissionManager = AgentPermissionManager()
        permissionManager.disablePermission(AgentPermission.PUBLISH_CONTENT)

        val orchestrator = SocialAgentOrchestrator(
            permissionManager = permissionManager
        )

        val plan = AgentPlan(
            userRequest = "Publish post",
            steps = listOf(
                AgentPlanStep(
                    toolName = "PublishPost",
                    arguments = mapOf("postId" to "123")
                )
            )
        )

        val executedPlan = orchestrator.executePlan(plan)
        assertEquals(ActionApprovalState.FAILED, executedPlan.approvalState)

        val step = executedPlan.steps.first()
        assertEquals(PlanStepStatus.FAILED, step.status)
        assertEquals("PERMISSION_DENIED", step.result?.status)
    }

    @Test
    fun orchestrator_handlesUnregisteredTool_gracefully() = runBlocking {
        val orchestrator = SocialAgentOrchestrator()

        val plan = AgentPlan(
            userRequest = "Run mystery action",
            steps = listOf(
                AgentPlanStep(
                    toolName = "UnknownToolX",
                    arguments = emptyMap()
                )
            )
        )

        val executedPlan = orchestrator.executePlan(plan)
        assertEquals(ActionApprovalState.FAILED, executedPlan.approvalState)

        val step = executedPlan.steps.first()
        assertEquals(PlanStepStatus.FAILED, step.status)
        assertEquals("UNKNOWN_TOOL", step.result?.status)
    }
}
