package com.example

import com.example.data.ai.*
import com.example.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MockModeExecutionTest {

    @Test
    fun mockMode_fullToolCallingFlow_runsEndToEndWithoutRealApi() = runBlocking {
        val orchestrator = SocialAgentOrchestrator(
            modelConfig = AiModelConfig(provider = AIProviderType.MOCK)
        )

        val prompt = "Create a professional Facebook and Instagram post about my AI product, generate an image, and schedule it for tomorrow at 7 PM."

        val context = SocialAgentContext(
            userRequest = prompt,
            brandProfile = BrandProfile(brandName = "Aegis AI", brandTone = BrandTone.PROFESSIONAL)
        )

        // 1. Plan creation
        val plan = orchestrator.createPlan(context)
        assertNotNull(plan)
        assertEquals(ExecutionEnvironment.MOCK, plan.executionEnvironment)
        assertEquals(ActionApprovalState.AWAITING_APPROVAL, plan.approvalState)

        // 2. Validate steps
        val stepToolNames = plan.steps.map { it.toolName }
        assertTrue(stepToolNames.contains("CreatePost"))
        assertTrue(stepToolNames.contains("GenerateImage"))
        assertTrue(stepToolNames.contains("SchedulePost"))

        val scheduleStep = plan.steps.find { it.toolName == "SchedulePost" }
        assertNotNull(scheduleStep)
        val timeArg = scheduleStep?.arguments?.get("scheduledTime") as? String
        assertNotNull(timeArg)
        assertTrue(timeArg!!.contains("Tomorrow at 7:00 PM"))

        // 3. Plan execution
        val executedPlan = orchestrator.executePlan(plan)
        assertEquals(ActionApprovalState.SUCCESS, executedPlan.approvalState)
        assertTrue(executedPlan.steps.all { it.status == PlanStepStatus.SUCCESS })

        // 4. Verify mock environment flags preserved
        executedPlan.steps.forEach { step ->
            assertNotNull(step.result)
            assertEquals(ExecutionEnvironment.MOCK, step.result?.executionEnvironment)
            assertEquals("SUCCESS", step.result?.status)
        }
    }
}
