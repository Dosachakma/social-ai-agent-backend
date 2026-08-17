package com.example

import com.example.data.ai.MockAIProvider
import com.example.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MockAIProviderTest {

    private val provider = MockAIProvider()

    @Test
    fun testProviderNameAndEnvironment() {
        assertEquals("Mock AI Engine (Demo)", provider.providerName)
        assertEquals(ExecutionEnvironment.MOCK, provider.executionEnvironment)
    }

    @Test
    fun testGenerateTextReturnsSuccess() = runBlocking {
        val result = provider.generateText(
            prompt = "Launch Campaign",
            brandProfile = BrandProfile(brandName = "AlphaLab")
        )

        assertTrue(result.isSuccess)
        val text = result.getOrNull()
        assertNotNull(text)
        assertTrue(text!!.contains("AlphaLab"))
    }

    @Test
    fun testGenerateContentPreviewReturnsAwaitingApproval() = runBlocking {
        val result = provider.generateContentPreview(
            userRequest = "Create Instagram Reel post",
            brandProfile = BrandProfile(brandName = "Aura"),
            platform = PlatformType.INSTAGRAM
        )

        assertTrue(result.isSuccess)
        val preview = result.getOrNull()
        assertNotNull(preview)
        assertEquals(PlatformType.INSTAGRAM, preview!!.platform)
        assertEquals(ActionApprovalState.AWAITING_APPROVAL, preview.approvalState)
        assertEquals(ExecutionEnvironment.MOCK, preview.executionEnvironment)
    }
}
