package com.example.ui.screens.brand

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BrandLanguage
import com.example.data.model.BrandProfile
import com.example.data.model.BrandTone
import com.example.data.repository.BrandProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrandProfileUiState(
    val profile: BrandProfile = BrandProfile(),
    val isSaved: Boolean = false,
    val showSuccessMessage: Boolean = false
)

class BrandProfileViewModel(
    private val repository: BrandProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrandProfileUiState(profile = repository.getProfile()))
    val uiState: StateFlow<BrandProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.brandProfile.collect { updatedProfile ->
                _uiState.update { it.copy(profile = updatedProfile) }
            }
        }
    }

    fun updateBrandName(name: String) {
        _uiState.update { it.copy(profile = it.profile.copy(brandName = name), isSaved = false) }
    }

    fun updateBusinessDescription(description: String) {
        _uiState.update { it.copy(profile = it.profile.copy(businessDescription = description), isSaved = false) }
    }

    fun updateIndustry(industry: String) {
        _uiState.update { it.copy(profile = it.profile.copy(industry = industry), isSaved = false) }
    }

    fun updateTargetAudience(audience: String) {
        _uiState.update { it.copy(profile = it.profile.copy(targetAudience = audience), isSaved = false) }
    }

    fun updatePrimaryLanguage(language: BrandLanguage) {
        _uiState.update { it.copy(profile = it.profile.copy(primaryLanguage = language), isSaved = false) }
    }

    fun updateSecondaryLanguage(language: BrandLanguage?) {
        _uiState.update { it.copy(profile = it.profile.copy(secondaryLanguage = language), isSaved = false) }
    }

    fun updateBrandTone(tone: BrandTone) {
        _uiState.update { it.copy(profile = it.profile.copy(brandTone = tone), isSaved = false) }
    }

    fun updateWritingStyle(style: String) {
        _uiState.update { it.copy(profile = it.profile.copy(writingStyle = style), isSaved = false) }
    }

    fun updatePreferredCta(cta: String) {
        _uiState.update { it.copy(profile = it.profile.copy(preferredCta = cta), isSaved = false) }
    }

    fun updatePreferredHashtags(hashtags: String) {
        _uiState.update { it.copy(profile = it.profile.copy(preferredHashtags = hashtags), isSaved = false) }
    }

    fun updateWordsToAvoid(words: String) {
        _uiState.update { it.copy(profile = it.profile.copy(wordsToAvoid = words), isSaved = false) }
    }

    fun updateBrandKeywords(keywords: String) {
        _uiState.update { it.copy(profile = it.profile.copy(brandKeywords = keywords), isSaved = false) }
    }

    fun updateProductsServices(products: String) {
        _uiState.update { it.copy(profile = it.profile.copy(productsServices = products), isSaved = false) }
    }

    fun updateWebsite(website: String) {
        _uiState.update { it.copy(profile = it.profile.copy(website = website), isSaved = false) }
    }

    fun updateContactInfo(info: String) {
        _uiState.update { it.copy(profile = it.profile.copy(contactInfo = info), isSaved = false) }
    }

    fun saveProfile() {
        repository.updateProfile(_uiState.value.profile)
        _uiState.update { it.copy(isSaved = true, showSuccessMessage = true) }
    }

    fun dismissSuccessMessage() {
        _uiState.update { it.copy(showSuccessMessage = false) }
    }
}
