package com.example.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AnalyticsData
import com.example.data.repository.SocialMediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnalyticsUiState(
    val analytics: AnalyticsData = AnalyticsData(),
    val timeRange: String = "Last 30 Days",
    val isLoading: Boolean = false
)

class AnalyticsViewModel(
    private val repository: SocialMediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState(isLoading = true))
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAnalytics().collect { data ->
                _uiState.update { it.copy(analytics = data, isLoading = false) }
            }
        }
    }

    fun setTimeRange(range: String) {
        _uiState.update { it.copy(timeRange = range) }
    }
}
