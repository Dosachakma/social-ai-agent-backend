package com.example

import com.example.data.ai.*
import com.example.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AgentOrchestratorTest {

    private lateinit var permissionManager: AgentPermissionManager
    private lateinit var orchestrator: SocialAgentOrchestrator

    @Before
    fun setUp() {
        permissionManager = AgentPermissionManager()
        orchestrator = SocialAgentOrchestrator(
            modelConfig = AiModelConfig(provider = AIProviderType.MOCK),
            permissionManager = permissionManager
        )
    }

    @Test
    fun orchestrator_decomposesMultiStepRequest() = runBlocking {
        val context = SocialAgentContext(
            userRequest = "Create a post for Facebook, generate an image concept, and schedule it for 7 PM",
            brandProfile = BrandProfile(brandName = "AI Suite")
        )

        val plan = orchestrator.createPlan(context)

        assertNotNull(plan)
        assertTrue(plan.steps.size >= 3)
        assertTrue(plan.steps.any { it.toolName.equals("CreatePost", ignoreCase = true) })
        assertTrue(plan.steps.any { it.toolName.equals("GenerateImage", ignoreCase = true) })
        assertTrue(plan.steps.any { it.toolName.equals("SchedulePost", ignoreCase = true) })
        assertEquals(ActionApprovalState.AWAITING_APPROVAL, plan.approvalState)
    }

    @Test
    fun orchestrator_executesPlan_updatesStepStatuses() = runBlocking {
        val context = SocialAgentContext(
            userRequest = "Create a post for Instagram",
            brandProfile = BrandProfile(brandName = "AI Suite")
        )

        val plan = orchestrator.createPlan(context)
        assertEquals(ActionApprovalState.AWAITING_APPROVAL, plan.approvalState)

        val executedPlan = orchestrator.executePlan(plan)
        assertEquals(ActionApprovalState.SUCCESS, executedPlan.approvalState)
        assertTrue(executedPlan.steps.all { it.status == PlanStepStatus.SUCCESS })
    }
}
