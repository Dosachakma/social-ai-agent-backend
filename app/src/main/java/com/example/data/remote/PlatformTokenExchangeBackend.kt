package com.example.data.remote

import com.example.data.config.SecurityConfig
import com.example.data.model.*
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class TwitterExchangeMetadata(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "handle") val handle: String? = null,
    @Json(name = "profileImageUrl") val profileImageUrl: String? = null,
    @Json(name = "platform") val platform: String? = null,
    @Json(name = "followerCount") val followerCount: Int? = null,
    @Json(name = "followingCount") val followingCount: Int? = null,
    @Json(name = "tweetCount") val tweetCount: Int? = null,
    @Json(name = "capabilities") val capabilities: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class TwitterExchangeData(
    @Json(name = "account") val account: TwitterExchangeMetadata? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null
)

@JsonClass(generateAdapter = true)
data class TwitterExchangeResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: TwitterExchangeData? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "message") val message: String? = null
)

interface PlatformExchangeApiService {
    @POST("auth/twitter/exchange")
    suspend fun exchangeTwitterTicket(@Body request: BackendExchangeRequest): Response<TwitterExchangeResponse>
}

interface TwitterTokenExchangeBackend {
    suspend fun exchangeTwitterTicket(
        ticket: String,
        state: String? = null
    ): AppResult<TwitterExchangeMetadata>
}

class RealHttpPlatformTokenExchangeBackend(
    baseUrl: String = SecurityConfig.getMetaBackendBaseUrl(),
    customOkHttpClient: OkHttpClient? = null
) : TwitterTokenExchangeBackend {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = customOkHttpClient ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    private val apiService: PlatformExchangeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PlatformExchangeApiService::class.java)
    }

    override suspend fun exchangeTwitterTicket(
        ticket: String,
        state: String?
    ): AppResult<TwitterExchangeMetadata> {
        return try {
            val response = apiService.exchangeTwitterTicket(BackendExchangeRequest(ticket = ticket, state = state))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data?.account != null) {
                    AppResult.Success(body.data.account)
                } else {
                    val errorCode = body?.error ?: "TICKET_EXCHANGE_FAILED"
                    val errorMessage = body?.message ?: "Failed to exchange X/Twitter ticket with backend."
                    AppResult.Error(AgentError(code = errorCode, message = errorMessage))
                }
            } else {
                val errorCode = when (response.code()) {
                    401 -> "TICKET_EXPIRED"
                    404 -> "TICKET_NOT_FOUND"
                    400 -> "INVALID_REQUEST"
                    else -> "HTTP_${response.code()}"
                }
                AppResult.Error(AgentError(code = errorCode, message = "X/Twitter backend error (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error communicating with Twitter token exchange backend."
                )
            )
        }
    }
}
