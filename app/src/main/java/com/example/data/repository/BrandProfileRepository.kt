package com.example.data.repository

import com.example.data.model.AgentError
import com.example.data.model.AppResult
import com.example.data.model.BrandProfile
import com.example.data.remote.api.SocialStudioApiService
import com.example.data.remote.mappers.DomainMappers.toDomain
import com.example.data.remote.mappers.DomainMappers.toMutationRequest
import com.example.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface BrandProfileRepository {
    val brandProfile: StateFlow<BrandProfile>
    fun getProfile(): BrandProfile
    fun updateProfile(profile: BrandProfile)
    suspend fun saveProfile(profile: BrandProfile): AppResult<BrandProfile> {
        updateProfile(profile)
        return AppResult.Success(profile)
    }
    suspend fun refreshProfile(): AppResult<BrandProfile> = AppResult.Success(getProfile())
}

class MockBrandProfileRepository : BrandProfileRepository {
    private val _brandProfile = MutableStateFlow(BrandProfile())
    override val brandProfile: StateFlow<BrandProfile> = _brandProfile.asStateFlow()

    override fun getProfile(): BrandProfile = _brandProfile.value

    override fun updateProfile(profile: BrandProfile) {
        _brandProfile.value = profile
    }
}

/**
 * Production Brand Profile Repository communicating with PostgreSQL via Retrofit API.
 * Maintains an active reactive state flow and executes mutations against /api/v1/workspaces/{workspaceId}/brand-profiles.
 */
class ProductionBrandProfileRepository(
    private val apiService: SocialStudioApiService,
    private val sessionManager: SessionManager = SessionManager.getInstance(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : BrandProfileRepository {

    private val _brandProfile = MutableStateFlow(BrandProfile())
    override val brandProfile: StateFlow<BrandProfile> = _brandProfile.asStateFlow()

    private var currentRemoteProfileId: String? = null

    init {
        coroutineScope.launch {
            refreshProfile()
        }
    }

    override fun getProfile(): BrandProfile = _brandProfile.value

    override fun updateProfile(profile: BrandProfile) {
        _brandProfile.value = profile
        coroutineScope.launch {
            saveProfile(profile)
        }
    }

    override suspend fun refreshProfile(): AppResult<BrandProfile> {
        val workspaceId = sessionManager.currentWorkspaceId
        return try {
            val response = apiService.getBrandProfiles(workspaceId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && !body.data.isNullOrEmpty()) {
                    val firstProfileDto = body.data.first()
                    currentRemoteProfileId = firstProfileDto.id
                    val domainProfile = firstProfileDto.toDomain()
                    _brandProfile.value = domainProfile
                    AppResult.Success(domainProfile)
                } else {
                    // No profile created yet in this workspace, return current default
                    AppResult.Success(_brandProfile.value)
                }
            } else {
                AppResult.Error(
                    AgentError(
                        code = "HTTP_${response.code()}",
                        message = "Failed to fetch brand profile from backend (HTTP ${response.code()})"
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Failed to connect to brand profile service.",
                    cause = e
                )
            )
        }
    }

    override suspend fun saveProfile(profile: BrandProfile): AppResult<BrandProfile> {
        val workspaceId = sessionManager.currentWorkspaceId
        val mutationReq = profile.toMutationRequest()
        _brandProfile.value = profile

        return try {
            val existingId = currentRemoteProfileId
            val response = if (existingId != null) {
                apiService.updateBrandProfile(workspaceId, existingId, mutationReq)
            } else {
                apiService.createBrandProfile(workspaceId, mutationReq)
            }

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    currentRemoteProfileId = body.data.id
                    val savedDomain = body.data.toDomain()
                    _brandProfile.value = savedDomain
                    AppResult.Success(savedDomain)
                } else {
                    AppResult.Error(
                        AgentError(
                            code = body?.error ?: "SAVE_PROFILE_FAILED",
                            message = body?.message ?: "Failed to persist brand profile."
                        )
                    )
                }
            } else {
                AppResult.Error(
                    AgentError(
                        code = "HTTP_${response.code()}",
                        message = "Server rejected brand profile save (HTTP ${response.code()})"
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error while saving brand profile to backend.",
                    cause = e
                )
            )
        }
    }
}
