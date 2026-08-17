package com.example

import com.example.data.repository.MockAiAgentService
import com.example.data.model.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApprovalFlowTest {

    private val agentService = MockAiAgentService()

    @Test
    fun testProposalFlowRequiresApproval() = runBlocking {
        val messages = agentService.processUserPrompt("Create a Facebook post").toList()

        assertEquals(2, messages.size)
        val userMsg = messages[0]
        val agentMsg = messages[1]

        assertEquals(SenderType.USER, userMsg.sender)
        assertEquals(SenderType.AGENT, agentMsg.sender)
        assertFalse(agentMsg.isAutonomousAction) // Must NOT treat AI suggestion as autonomous without user approval
        assertEquals(ActionApprovalState.AWAITING_APPROVAL, agentMsg.approvalState)
        assertNotNull(agentMsg.contentPreview)
        assertEquals(ActionApprovalState.AWAITING_APPROVAL, agentMsg.contentPreview!!.approvalState)
    }

    @Test
    fun testExecuteActionTransitionsToSuccess() = runBlocking {
        val preview = GeneratedContentPreview(
            platform = PlatformType.FACEBOOK,
            title = "Test Proposal",
            content = "Proposed content"
        )

        val messages = agentService.executeAction(AgentAction.CREATE_POST, preview).toList()

        assertEquals(2, messages.size)
        val systemExecuting = messages[0]
        val agentSuccess = messages[1]

        assertEquals(SenderType.SYSTEM, systemExecuting.sender)
        assertEquals(ActionApprovalState.EXECUTING, systemExecuting.approvalState)

        assertEquals(SenderType.AGENT, agentSuccess.sender)
        assertEquals(ActionApprovalState.SUCCESS, agentSuccess.approvalState)
        assertTrue(agentSuccess.text.contains("Mock execution completed"))
    }
}
