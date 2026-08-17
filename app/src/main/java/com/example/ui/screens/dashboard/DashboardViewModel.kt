package com.example.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.AiAgentService
import com.example.data.repository.SocialMediaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val connectedAccounts: List<SocialAccount> = emptyList(),
    val scheduledPostsToday: List<SocialPost> = emptyList(),
    val aiSuggestions: List<AiSuggestion> = emptyList(),
    val recentActivity: List<ActivityLog> = emptyList(),
    val analytics: AnalyticsData = AnalyticsData(),
    val isLoading: Boolean = false,
    val aiGeneratedCopy: String? = null
)

class DashboardViewModel(
    private val repository: SocialMediaRepository,
    private val aiAgentService: AiAgentService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            combine(
                repository.getConnectedAccounts(),
                repository.getScheduledPosts(),
                repository.getAiSuggestions(),
                repository.getRecentActivity(),
                repository.getAnalytics()
            ) { accounts, posts, suggestions, activity, analytics ->
                DashboardUiState(
                    connectedAccounts = accounts,
                    scheduledPostsToday = posts,
                    aiSuggestions = suggestions,
                    recentActivity = activity,
                    analytics = analytics,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun createNewPost(post: SocialPost) {
        viewModelScope.launch {
            repository.createPost(post)
        }
    }

    fun generateAiCopy(topic: String, platform: PlatformType) {
        viewModelScope.launch {
            val result = aiAgentService.generatePostCopy(topic, platform, "Professional")
            result.onSuccess { copy ->
                _uiState.update { it.copy(aiGeneratedCopy = copy) }
            }
        }
    }

    fun clearGeneratedCopy() {
        _uiState.update { it.copy(aiGeneratedCopy = null) }
    }
}
