package com.example.ui.screens.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PostStatus
import com.example.data.model.SocialPost
import com.example.data.repository.SocialMediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContentUiState(
    val posts: List<SocialPost> = emptyList(),
    val filterStatus: PostStatus? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

class ContentViewModel(
    private val repository: SocialMediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContentUiState(isLoading = true))
    val uiState: StateFlow<ContentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllPosts().collect { list ->
                _uiState.update { it.copy(posts = list, isLoading = false) }
            }
        }
    }

    fun setFilter(status: PostStatus?) {
        _uiState.update { it.copy(filterStatus = status) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            repository.deletePost(postId)
        }
    }
}
