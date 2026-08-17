package com.example

import com.example.data.model.ActionApprovalState
import org.junit.Assert.*
import org.junit.Test

class AgentActionStateTest {

    @Test
    fun testActionStateTransitions() {
        var state = ActionApprovalState.PROPOSED
        assertEquals("Proposed", state.label)

        state = ActionApprovalState.AWAITING_APPROVAL
        assertEquals("Awaiting Approval", state.label)

        state = ActionApprovalState.APPROVED
        assertEquals("Approved", state.label)

        state = ActionApprovalState.EXECUTING
        assertEquals("Executing", state.label)

        state = ActionApprovalState.SUCCESS
        assertEquals("Success", state.label)
    }

    @Test
    fun testTerminalStates() {
        val success = ActionApprovalState.SUCCESS
        val failed = ActionApprovalState.FAILED
        val cancelled = ActionApprovalState.CANCELLED

        assertNotEquals(success, failed)
        assertNotEquals(failed, cancelled)
    }
}
