package com.example.data.repository

import com.example.data.model.BrandProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface BrandProfileRepository {
    val brandProfile: StateFlow<BrandProfile>
    fun getProfile(): BrandProfile
    fun updateProfile(profile: BrandProfile)
}

class MockBrandProfileRepository : BrandProfileRepository {
    private val _brandProfile = MutableStateFlow(BrandProfile())
    override val brandProfile: StateFlow<BrandProfile> = _brandProfile.asStateFlow()

    override fun getProfile(): BrandProfile = _brandProfile.value

    override fun updateProfile(profile: BrandProfile) {
        _brandProfile.value = profile
    }
}
