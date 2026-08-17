package com.example.data.repository

import com.example.data.model.BrandProfile
import com.example.data.remote.api.SocialMediaApiService
import com.example.data.remote.client.ApiClientProvider
import com.example.data.remote.dto.DtoMappers
import com.example.data.remote.session.WorkspaceSessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemoteBrandProfileRepository(
    private val apiService: SocialMediaApiService = ApiClientProvider.getApiService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BrandProfileRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _brandProfile = MutableStateFlow(BrandProfile())
    override val brandProfile: StateFlow<BrandProfile> = _brandProfile.asStateFlow()

    init {
        fetchProfileAsync()
        // Reactively reload brand profile when workspace changes
        scope.launch {
            WorkspaceSessionManager.sessionState.collect {
                fetchProfileAsync()
            }
        }
    }

    private fun getWorkspaceId(): String = WorkspaceSessionManager.getWorkspaceId()

    override fun getProfile(): BrandProfile = _brandProfile.value

    override fun updateProfile(profile: BrandProfile) {
        _brandProfile.value = profile
        scope.launch {
            try {
                val req = DtoMappers.toCreateBrandProfileRequest(profile)
                val existingId = profile.id.trim()
                val isExistingValidServerId = existingId.isNotBlank() && 
                    !existingId.equals("default_brand", ignoreCase = true) &&
                    !existingId.equals("bp_default", ignoreCase = true)

                if (isExistingValidServerId) {
                    val updateRes = apiService.updateBrandProfile(getWorkspaceId(), existingId, req)
                    val data = updateRes.body()?.data
                    if (updateRes.isSuccessful && data != null) {
                        _brandProfile.value = DtoMappers.toBrandProfile(data)
                        return@launch
                    }
                }

                // If not updated or no server id, create / upsert
                val createRes = apiService.createBrandProfile(getWorkspaceId(), req)
                val createData = createRes.body()?.data
                if (createRes.isSuccessful && createData != null) {
                    _brandProfile.value = DtoMappers.toBrandProfile(createData)
                }
            } catch (e: Exception) {
                // Keep the locally updated profile state
            }
        }
    }

    fun fetchProfileAsync() {
        scope.launch {
            try {
                val response = apiService.getBrandProfiles(getWorkspaceId())
                if (response.isSuccessful && response.body()?.success == true) {
                    val first = response.body()?.data?.firstOrNull()
                    if (first != null) {
                        _brandProfile.value = DtoMappers.toBrandProfile(first)
                    }
                }
            } catch (e: Exception) {
                // Hold current state
            }
        }
    }
}

