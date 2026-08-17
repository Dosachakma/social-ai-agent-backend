package com.example.data.remote.api

import com.example.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface SocialMediaApiService {

    // --- Workspaces ---
    @GET("api/v1/workspaces")
    suspend fun getWorkspaces(): Response<ApiListResponse<WorkspaceDto>>

    @GET("api/v1/workspaces/{workspaceId}")
    suspend fun getWorkspace(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiResponse<WorkspaceDetailDataDto>>

    // --- Brand Profiles ---
    @GET("api/v1/workspaces/{workspaceId}/brand-profiles")
    suspend fun getBrandProfiles(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiListResponse<BrandProfileDto>>

    @GET("api/v1/workspaces/{workspaceId}/brand-profiles/{id}")
    suspend fun getBrandProfileById(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiResponse<BrandProfileDto>>

    @POST("api/v1/workspaces/{workspaceId}/brand-profiles")
    suspend fun createBrandProfile(
        @Path("workspaceId") workspaceId: String,
        @Body request: CreateOrUpdateBrandProfileRequest
    ): Response<ApiResponse<BrandProfileDto>>

    @PUT("api/v1/workspaces/{workspaceId}/brand-profiles/{id}")
    suspend fun updateBrandProfile(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String,
        @Body request: CreateOrUpdateBrandProfileRequest
    ): Response<ApiResponse<BrandProfileDto>>

    @DELETE("api/v1/workspaces/{workspaceId}/brand-profiles/{id}")
    suspend fun deleteBrandProfile(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiSimpleResponse>

    // --- Social Accounts ---
    @GET("api/v1/workspaces/{workspaceId}/accounts")
    suspend fun getAccounts(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiListResponse<SocialAccountDto>>

    @GET("api/v1/workspaces/{workspaceId}/accounts/{id}")
    suspend fun getAccountById(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiResponse<SocialAccountDto>>

    @POST("api/v1/workspaces/{workspaceId}/accounts")
    suspend fun connectAccount(
        @Path("workspaceId") workspaceId: String,
        @Body request: ConnectSocialAccountRequest
    ): Response<ApiResponse<SocialAccountDto>>

    @PUT("api/v1/workspaces/{workspaceId}/accounts/{id}")
    suspend fun updateAccount(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String,
        @Body request: UpdateSocialAccountRequest
    ): Response<ApiResponse<SocialAccountDto>>

    @DELETE("api/v1/workspaces/{workspaceId}/accounts/{id}")
    suspend fun deleteAccount(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiSimpleResponse>

    // --- Social Posts ---
    @GET("api/v1/workspaces/{workspaceId}/posts")
    suspend fun getPosts(
        @Path("workspaceId") workspaceId: String,
        @Query("status") status: String? = null,
        @Query("approvalState") approvalState: String? = null,
        @Query("platform") platform: String? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): Response<ApiListResponse<SocialPostDto>>

    @GET("api/v1/workspaces/{workspaceId}/posts/drafts")
    suspend fun getDrafts(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiListResponse<SocialPostDto>>

    @GET("api/v1/workspaces/{workspaceId}/posts/scheduled")
    suspend fun getScheduledPosts(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiListResponse<SocialPostDto>>

    @GET("api/v1/workspaces/{workspaceId}/posts/{id}")
    suspend fun getPostById(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiResponse<SocialPostDto>>

    @POST("api/v1/workspaces/{workspaceId}/posts")
    suspend fun createPost(
        @Path("workspaceId") workspaceId: String,
        @Body request: CreateSocialPostRequest
    ): Response<ApiResponse<SocialPostDto>>

    @PUT("api/v1/workspaces/{workspaceId}/posts/{id}")
    suspend fun updatePost(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String,
        @Body request: UpdateSocialPostRequest
    ): Response<ApiResponse<SocialPostDto>>

    @DELETE("api/v1/workspaces/{workspaceId}/posts/{id}")
    suspend fun deletePost(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiSimpleResponse>

    // --- Platform Publish Results ---
    @GET("api/v1/workspaces/{workspaceId}/posts/{postId}/publish-results")
    suspend fun getPublishResults(
        @Path("workspaceId") workspaceId: String,
        @Path("postId") postId: String
    ): Response<ApiListResponse<PlatformPublishResultDto>>

    @POST("api/v1/workspaces/{workspaceId}/posts/{postId}/publish-results")
    suspend fun savePublishResult(
        @Path("workspaceId") workspaceId: String,
        @Path("postId") postId: String,
        @Body request: SavePublishResultRequest
    ): Response<ApiResponse<PlatformPublishResultDto>>

    // --- Agent Logs ---
    @GET("api/v1/workspaces/{workspaceId}/agent-logs")
    suspend fun getAgentLogs(
        @Path("workspaceId") workspaceId: String,
        @Query("action") action: String? = null,
        @Query("platform") platform: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiListResponse<AgentLogDto>>

    @POST("api/v1/workspaces/{workspaceId}/agent-logs")
    suspend fun createAgentLog(
        @Path("workspaceId") workspaceId: String,
        @Body request: CreateAgentLogRequest
    ): Response<ApiResponse<AgentLogDto>>

    // --- Analytics ---
    @GET("api/v1/workspaces/{workspaceId}/analytics")
    suspend fun getAnalytics(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiResponse<AnalyticsDataDto>>
}
