package com.example.data.repository

import com.example.data.model.BrandProfile
import com.example.data.remote.session.WorkspaceSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HybridBrandProfileRepository(
    private val remoteRepo: RemoteBrandProfileRepository = RemoteBrandProfileRepository(),
    private val mockRepo: MockBrandProfileRepository = MockBrandProfileRepository()
) : BrandProfileRepository {

    private val _brandProfile = MutableStateFlow(BrandProfile())
    override val brandProfile: StateFlow<BrandProfile> = _brandProfile.asStateFlow()

    init {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            WorkspaceSessionManager.sessionState.collect { state ->
                val sourceFlow = if (state.isDemoMode) mockRepo.brandProfile else remoteRepo.brandProfile
                sourceFlow.collect { profile ->
                    _brandProfile.value = profile
                }
            }
        }
    }

    private fun getActiveRepo(): BrandProfileRepository {
        return if (WorkspaceSessionManager.isDemoMode()) mockRepo else remoteRepo
    }

    override fun getProfile(): BrandProfile = getActiveRepo().getProfile()

    override fun updateProfile(profile: BrandProfile) {
        _brandProfile.value = profile
        getActiveRepo().updateProfile(profile)
    }
}
