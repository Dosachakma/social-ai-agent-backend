package com.example.data.remote.api

import retrofit2.Response
import retrofit2.http.*

/**
 * Complete Retrofit 2 API service contract for the Social AI Studio Backend.
 * Strictly maps to Node.js / PostgreSQL endpoints.
 */
interface SocialStudioApiService {

    // --- Health ---
    @GET("health")
    suspend fun healthCheck(): Response<HealthCheckResponse>

    // --- Workspaces ---
    @GET("api/v1/workspaces")
    suspend fun getWorkspaces(): Response<ApiResponse<List<WorkspaceDto>>>

    @GET("api/v1/workspaces/{workspaceId}")
    suspend fun getWorkspaceDetails(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiResponse<WorkspaceDetailsDto>>

    // --- Brand Profiles ---
    @GET("api/v1/workspaces/{workspaceId}/brand-profiles")
    suspend fun getBrandProfiles(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiResponse<List<BrandProfileDto>>>

    @GET("api/v1/workspaces/{workspaceId}/brand-profiles/{id}")
    suspend fun getBrandProfileById(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiResponse<BrandProfileDto>>

    @POST("api/v1/workspaces/{workspaceId}/brand-profiles")
    suspend fun createBrandProfile(
        @Path("workspaceId") workspaceId: String,
        @Body request: BrandProfileMutationRequest
    ): Response<ApiResponse<BrandProfileDto>>

    @PUT("api/v1/workspaces/{workspaceId}/brand-profiles/{id}")
    suspend fun updateBrandProfile(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String,
        @Body request: BrandProfileMutationRequest
    ): Response<ApiResponse<BrandProfileDto>>

    @DELETE("api/v1/workspaces/{workspaceId}/brand-profiles/{id}")
    suspend fun deleteBrandProfile(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiResponse<Unit>>

    // --- Social Accounts ---
    @GET("api/v1/workspaces/{workspaceId}/accounts")
    suspend fun getAccounts(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiResponse<List<SocialAccountDto>>>

    @GET("api/v1/workspaces/{workspaceId}/accounts/{id}")
    suspend fun getAccountById(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiResponse<SocialAccountDto>>

    @POST("api/v1/workspaces/{workspaceId}/accounts")
    suspend fun connectAccount(
        @Path("workspaceId") workspaceId: String,
        @Body request: ConnectAccountRequest
    ): Response<ApiResponse<SocialAccountDto>>

    @PUT("api/v1/workspaces/{workspaceId}/accounts/{id}")
    suspend fun updateAccount(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String,
        @Body request: UpdateAccountRequest
    ): Response<ApiResponse<SocialAccountDto>>

    @DELETE("api/v1/workspaces/{workspaceId}/accounts/{id}")
    suspend fun deleteAccount(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiResponse<Unit>>

    // --- Social Posts ---
    @GET("api/v1/workspaces/{workspaceId}/posts")
    suspend fun getPosts(
        @Path("workspaceId") workspaceId: String,
        @Query("status") status: String? = null,
        @Query("platform") platform: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<List<SocialPostDto>>>

    @GET("api/v1/workspaces/{workspaceId}/posts/scheduled")
    suspend fun getScheduledPosts(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiResponse<List<SocialPostDto>>>

    @GET("api/v1/workspaces/{workspaceId}/posts/drafts")
    suspend fun getDraftPosts(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiResponse<List<SocialPostDto>>>

    @GET("api/v1/workspaces/{workspaceId}/posts/{id}")
    suspend fun getPostById(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiResponse<SocialPostDto>>

    @POST("api/v1/workspaces/{workspaceId}/posts")
    suspend fun createPost(
        @Path("workspaceId") workspaceId: String,
        @Body request: CreatePostRequest
    ): Response<ApiResponse<SocialPostDto>>

    @PUT("api/v1/workspaces/{workspaceId}/posts/{id}")
    suspend fun updatePost(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String,
        @Body request: UpdatePostRequest
    ): Response<ApiResponse<SocialPostDto>>

    @DELETE("api/v1/workspaces/{workspaceId}/posts/{id}")
    suspend fun deletePost(
        @Path("workspaceId") workspaceId: String,
        @Path("id") id: String
    ): Response<ApiResponse<Unit>>

    // --- Platform Publish Results ---
    @GET("api/v1/workspaces/{workspaceId}/posts/{postId}/publish-results")
    suspend fun getPublishResults(
        @Path("workspaceId") workspaceId: String,
        @Path("postId") postId: String
    ): Response<ApiResponse<List<PublishResultDto>>>

    @POST("api/v1/workspaces/{workspaceId}/posts/{postId}/publish-results")
    suspend fun savePublishResult(
        @Path("workspaceId") workspaceId: String,
        @Path("postId") postId: String,
        @Body request: SavePublishResultRequest
    ): Response<ApiResponse<PublishResultDto>>

    // --- Agent Logs ---
    @GET("api/v1/workspaces/{workspaceId}/agent-logs")
    suspend fun getAgentLogs(
        @Path("workspaceId") workspaceId: String,
        @Query("limit") limit: Int? = null,
        @Query("status") status: String? = null
    ): Response<ApiResponse<List<AgentLogDto>>>

    @POST("api/v1/workspaces/{workspaceId}/agent-logs")
    suspend fun createAgentLog(
        @Path("workspaceId") workspaceId: String,
        @Body request: CreateAgentLogRequest
    ): Response<ApiResponse<AgentLogDto>>

    // --- Analytics ---
    @GET("api/v1/workspaces/{workspaceId}/analytics")
    suspend fun getAnalytics(
        @Path("workspaceId") workspaceId: String
    ): Response<ApiResponse<AnalyticsDto>>
}
