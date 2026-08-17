package com.example.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.MockScheduledPostRepository
import com.example.data.repository.ScheduledPostRepository
import com.example.data.scheduler.DefaultSchedulerService
import com.example.data.scheduler.SchedulerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CalendarViewMode(val label: String) {
    DAY("Day View"),
    WEEK("Week View"),
    MONTH("Month View")
}

data class CalendarUiState(
    val scheduledPosts: List<SocialPost> = emptyList(),
    val selectedDay: String = "12",
    val selectedMonth: String = "August 2026",
    val viewMode: CalendarViewMode = CalendarViewMode.WEEK,
    val selectedTimezone: String = "America/New_York (EST)",
    val isLoading: Boolean = false,
    val showScheduleDialog: Boolean = false,
    val selectedPostDetail: SocialPost? = null,
    val userMessage: String? = null,
    val filterPlatform: PlatformType? = null,
    
    // Create Schedule Form fields
    val formTitle: String = "",
    val formContent: String = "",
    val formPlatforms: Set<PlatformType> = setOf(PlatformType.FACEBOOK, PlatformType.INSTAGRAM),
    val formMediaUrl: String = "",
    val formScheduledDate: String = "2026-08-12",
    val formScheduledTime: String = "15:00",
    val formTimezone: String = "America/New_York",
    val formRepeatOption: RecurrenceOption = RecurrenceOption.NONE,
    val formRequireApproval: Boolean = true
)

class CalendarViewModel(
    private val schedulerService: SchedulerService = DefaultSchedulerService(),
    private val repository: ScheduledPostRepository = MockScheduledPostRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState(isLoading = true))
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadPosts()
    }

    private fun loadPosts() {
        viewModelScope.launch {
            repository.getAllAsFlow().collect { list ->
                _uiState.update { it.copy(scheduledPosts = list, isLoading = false) }
            }
        }
    }

    fun setViewMode(mode: CalendarViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun selectDay(dayLabel: String) {
        _uiState.update { it.copy(selectedDay = dayLabel) }
    }

    fun filterByPlatform(platform: PlatformType?) {
        _uiState.update { it.copy(filterPlatform = platform) }
    }

    fun openScheduleDialog() {
        _uiState.update { 
            it.copy(
                showScheduleDialog = true,
                formTitle = "",
                formContent = "",
                formPlatforms = setOf(PlatformType.FACEBOOK, PlatformType.INSTAGRAM)
            ) 
        }
    }

    fun closeScheduleDialog() {
        _uiState.update { it.copy(showScheduleDialog = false) }
    }

    fun updateFormTitle(title: String) {
        _uiState.update { it.copy(formTitle = title) }
    }

    fun updateFormContent(content: String) {
        _uiState.update { it.copy(formContent = content) }
    }

    fun toggleFormPlatform(platform: PlatformType) {
        _uiState.update { state ->
            val updated = state.formPlatforms.toMutableSet()
            if (updated.contains(platform)) {
                if (updated.size > 1) updated.remove(platform)
            } else {
                updated.add(platform)
            }
            state.copy(formPlatforms = updated)
        }
    }

    fun updateFormMediaUrl(url: String) {
        _uiState.update { it.copy(formMediaUrl = url) }
    }

    fun updateFormScheduledDate(date: String) {
        _uiState.update { it.copy(formScheduledDate = date) }
    }

    fun updateFormScheduledTime(time: String) {
        _uiState.update { it.copy(formScheduledTime = time) }
    }

    fun updateFormTimezone(tz: String) {
        _uiState.update { it.copy(formTimezone = tz) }
    }

    fun updateFormRepeatOption(repeat: RecurrenceOption) {
        _uiState.update { it.copy(formRepeatOption = repeat) }
    }

    fun updateFormRequireApproval(require: Boolean) {
        _uiState.update { it.copy(formRequireApproval = require) }
    }

    fun submitSchedulePost() {
        val state = _uiState.value
        if (state.formTitle.isBlank() || state.formContent.isBlank()) {
            _uiState.update { it.copy(userMessage = "Please enter post title and content.") }
            return
        }

        val newPost = SocialPost(
            title = state.formTitle,
            content = state.formContent,
            targetPlatforms = state.formPlatforms.toList(),
            scheduledTime = "${state.formScheduledDate} at ${state.formScheduledTime}",
            scheduledAt = "${state.formScheduledDate}T${state.formScheduledTime}:00",
            timezone = state.formTimezone,
            repeatOption = state.formRepeatOption,
            requireApproval = state.formRequireApproval,
            status = if (state.formRequireApproval) PostStatus.DRAFT else PostStatus.SCHEDULED,
            approvalState = if (state.formRequireApproval) ActionApprovalState.AWAITING_APPROVAL else ActionApprovalState.APPROVED,
            mediaUrl = state.formMediaUrl.ifBlank { null }
        )

        viewModelScope.launch {
            val result = schedulerService.schedulePost(newPost)
            when (result) {
                is AppResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            showScheduleDialog = false,
                            userMessage = "Post scheduled successfully (${if (state.formRequireApproval) "Awaiting Approval" else "Approved"})."
                        ) 
                    }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(userMessage = "Error: ${result.error.message}") }
                }
            }
        }
    }

    fun selectPostForDetail(post: SocialPost?) {
        _uiState.update { it.copy(selectedPostDetail = post) }
    }

    fun approvePost(postId: String) {
        viewModelScope.launch {
            val res = schedulerService.approvePost(postId)
            if (res is AppResult.Success) {
                _uiState.update { 
                    it.copy(
                        userMessage = "Post approved and added to active queue.",
                        selectedPostDetail = res.data
                    ) 
                }
            }
        }
    }

    fun cancelPost(postId: String) {
        viewModelScope.launch {
            val res = schedulerService.cancelScheduledPost(postId)
            if (res is AppResult.Success) {
                _uiState.update { 
                    it.copy(
                        userMessage = "Post cancelled successfully.",
                        selectedPostDetail = null
                    ) 
                }
            }
        }
    }

    fun triggerMockExecution(postId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = schedulerService.executeScheduledPost(postId)
            when (res) {
                is AppResult.Success -> {
                    val updated = repository.getById(postId).getOrNull()
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            userMessage = "Mock execution completed successfully.",
                            selectedPostDetail = updated
                        ) 
                    }
                }
                is AppResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            userMessage = "Execution failed: ${res.error.message}"
                        ) 
                    }
                }
            }
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
