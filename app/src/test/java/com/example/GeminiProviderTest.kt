package com.example

import com.example.data.ai.GeminiAIProvider
import com.example.data.config.SecretsManager
import com.example.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GeminiProviderTest {

    @Test
    fun geminiProvider_missingKey_returnsStructuredErrorWithoutCrashing() = runBlocking {
        val mockSecretsManager = object : SecretsManager {
            override fun getSecret(key: String): String? = null
            override fun isKeyConfigured(key: String): Boolean = false
        }

        val provider = GeminiAIProvider(
            modelConfig = AiModelConfig(provider = AIProviderType.GEMINI),
            secretsManager = mockSecretsManager
        )

        val result = provider.generateText("Create a social post")
        assertTrue(result is AppResult.Error)

        val error = (result as AppResult.Error).error
        assertEquals("GEMINI_API_KEY_MISSING", error.code)
        assertFalse(error.message.isBlank())
    }

    @Test
    fun geminiProvider_voiceToText_missingKey_returnsError() = runBlocking {
        val mockSecretsManager = object : SecretsManager {
            override fun getSecret(key: String): String? = null
            override fun isKeyConfigured(key: String): Boolean = false
        }

        val provider = GeminiAIProvider(
            secretsManager = mockSecretsManager
        )

        val result = provider.processVoiceToText()
        assertTrue(result is AppResult.Error)
    }
}
