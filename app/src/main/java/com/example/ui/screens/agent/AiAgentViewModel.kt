package com.example.ui.screens.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AgentPlan
import com.example.data.model.*
import com.example.data.repository.AIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AgentUiState(
    val messages: List<AgentMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val autonomousLevel: AutonomousLevel = AutonomousLevel.ASSISTED,
    val modelConfig: AiModelConfig = AiModelConfig(),
    val currentPrompt: String = "",
    val attachedFileName: String? = null
)

class AiAgentViewModel(
    private val aiService: AIService
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AgentUiState(
            messages = listOf(
                AgentMessage(
                    sender = SenderType.AGENT,
                    text = "👋 Hello! I am your AI Social Agent. Tell me what you want to accomplish, or select one of the suggested prompts below.",
                    timestamp = "10:00 AM",
                    isAutonomousAction = false,
                    approvalState = ActionApprovalState.PROPOSED,
                    executionEnvironment = ExecutionEnvironment.MOCK
                )
            )
        )
    )
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(currentPrompt = prompt) }
    }

    fun sendPrompt(userPrompt: String) {
        sendMessage(userPrompt)
    }

    fun sendMessage(userPrompt: String = _uiState.value.currentPrompt) {
        if (userPrompt.isBlank()) return
        
        val attachedFile = _uiState.value.attachedFileName
        val fullPrompt = if (attachedFile != null) "$userPrompt [Attached: $attachedFile]" else userPrompt

        _uiState.update { it.copy(isProcessing = true, currentPrompt = "", attachedFileName = null) }

        viewModelScope.launch {
            aiService.processUserPrompt(fullPrompt).collect { newMsg ->
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + newMsg,
                        isProcessing = false
                    )
                }
            }
        }
    }

    fun executePlan(plan: AgentPlan) {
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            aiService.executePlan(plan).collect { newMsg ->
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + newMsg,
                        isProcessing = false
                    )
                }
            }
        }
    }

    fun rejectPlan(plan: AgentPlan) {
        val rejectedMsg = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = SenderType.SYSTEM,
            text = "❌ AI Action Plan rejected by user.",
            timestamp = "Just now",
            approvalState = ActionApprovalState.CANCELLED,
            executionEnvironment = plan.executionEnvironment
        )
        _uiState.update { state ->
            state.copy(messages = state.messages + rejectedMsg)
        }
    }

    fun executeAction(action: AgentAction, preview: GeneratedContentPreview?) {
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            aiService.executeAction(action, preview).collect { newMsg ->
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + newMsg,
                        isProcessing = false
                    )
                }
            }
        }
    }

    fun handleVoiceInput() {
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            val result = aiService.processVoiceToText()
            result.getOrNull()?.let { transcribedText ->
                _uiState.update { it.copy(currentPrompt = transcribedText, isProcessing = false) }
                sendMessage(transcribedText)
            } ?: run {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun handleAttachment(fileName: String = "Brand_Guidelines_2026.pdf") {
        viewModelScope.launch {
            aiService.processAttachment(fileName)
            _uiState.update { it.copy(attachedFileName = fileName) }
        }
    }

    fun removeAttachment() {
        _uiState.update { it.copy(attachedFileName = null) }
    }

    fun setAutonomousLevel(level: AutonomousLevel) {
        _uiState.update { it.copy(autonomousLevel = level) }
        val newConfig = _uiState.value.modelConfig.copy(autonomousLevel = level)
        aiService.updateModelConfig(newConfig)
    }

    fun updateAutonomousLevel(level: AutonomousLevel) {
        setAutonomousLevel(level)
    }

    fun updateModelProvider(providerType: AIProviderType) {
        val newConfig = _uiState.value.modelConfig.copy(provider = providerType)
        _uiState.update { it.copy(modelConfig = newConfig) }
        aiService.updateModelConfig(newConfig)
    }
}
