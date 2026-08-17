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
data class BackendExchangeRequest(
    @Json(name = "ticket") val ticket: String,
    @Json(name = "state") val state: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendPageMetadata(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "tasks") val tasks: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class BackendAccountMetadata(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "pages") val pages: List<BackendPageMetadata>? = null
)

@JsonClass(generateAdapter = true)
data class BackendExchangeData(
    @Json(name = "account") val account: BackendAccountMetadata? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendExchangeResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: BackendExchangeData? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "message") val message: String? = null
)

interface MetaExchangeApiService {
    @POST("auth/facebook/exchange")
    suspend fun exchangeTicket(@Body request: BackendExchangeRequest): Response<BackendExchangeResponse>
}

/**
 * Requirement 4 & 7: Backend Token Exchange Abstraction
 * Abstraction for exchanging short-lived single-use tickets for sanitized account metadata server-side.
 * Client application NEVER handles or transmits the Meta App Secret.
 */
interface MetaTokenExchangeBackend {
    suspend fun exchangeTicket(
        ticket: String,
        state: String? = null
    ): AppResult<BackendAccountMetadata> = AppResult.Error(
        AgentError(
            code = "BACKEND_NOT_CONFIGURED",
            message = "Meta OAuth backend token exchange endpoint is not configured."
        )
    )

    suspend fun exchangeCodeForToken(
        code: String,
        redirectUri: String
    ): AppResult<SocialAccessToken> = AppResult.Error(
        AgentError(
            code = "BACKEND_NOT_CONFIGURED",
            message = "Meta OAuth backend code exchange endpoint is not configured."
        )
    )

    suspend fun fetchFacebookPages(
        accessToken: SocialAccessToken
    ): AppResult<List<SocialPage>> = AppResult.Success(emptyList())

    suspend fun fetchInstagramAccounts(
        accessToken: SocialAccessToken
    ): AppResult<List<SocialAccount>> = AppResult.Success(emptyList())
}

/**
 * Real Network implementation of MetaTokenExchangeBackend communicating with the backend server.
 */
class RealHttpMetaTokenExchangeBackend(
    baseUrl: String = SecurityConfig.getMetaBackendBaseUrl(),
    customOkHttpClient: OkHttpClient? = null
) : MetaTokenExchangeBackend {

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

    private val apiService: MetaExchangeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MetaExchangeApiService::class.java)
    }

    override suspend fun exchangeTicket(
        ticket: String,
        state: String?
    ): AppResult<BackendAccountMetadata> {
        return try {
            val response = apiService.exchangeTicket(BackendExchangeRequest(ticket = ticket, state = state))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data?.account != null) {
                    AppResult.Success(body.data.account)
                } else {
                    val errorCode = body?.error ?: "TICKET_EXCHANGE_FAILED"
                    val errorMessage = body?.message ?: "Failed to exchange ticket with backend."
                    AppResult.Error(AgentError(code = errorCode, message = errorMessage))
                }
            } else {
                val errorBodyStr = response.errorBody()?.string()
                val parsedError = parseErrorBody(errorBodyStr)
                val errorCode = parsedError?.error ?: when (response.code()) {
                    401 -> "TICKET_EXPIRED"
                    404 -> "TICKET_NOT_FOUND"
                    400 -> "INVALID_REQUEST"
                    else -> "HTTP_${response.code()}"
                }
                val errorMessage = parsedError?.message ?: "Backend error (HTTP ${response.code()})"
                AppResult.Error(AgentError(code = errorCode, message = errorMessage))
            }
        } catch (e: Exception) {
            AppResult.Error(
                AgentError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error communicating with token exchange backend."
                )
            )
        }
    }

    private fun parseErrorBody(json: String?): BackendExchangeResponse? {
        if (json.isNullOrBlank()) return null
        return try {
            val adapter = moshi.adapter(BackendExchangeResponse::class.java)
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun exchangeCodeForToken(
        code: String,
        redirectUri: String
    ): AppResult<SocialAccessToken> {
        return AppResult.Error(
            AgentError(
                code = "DIRECT_CODE_EXCHANGE_DEPRECATED",
                message = "Use backend ticket exchange flow instead of direct code exchange."
            )
        )
    }

    override suspend fun fetchFacebookPages(
        accessToken: SocialAccessToken
    ): AppResult<List<SocialPage>> {
        return AppResult.Success(emptyList())
    }

    override suspend fun fetchInstagramAccounts(
        accessToken: SocialAccessToken
    ): AppResult<List<SocialAccount>> {
        return AppResult.Success(emptyList())
    }
}

/**
 * Default implementation returned when live backend exchange server is not configured.
 * Safely fails without faking credentials or raw exchange.
 */
class UnconfiguredMetaTokenExchangeBackend : MetaTokenExchangeBackend {
    override suspend fun exchangeTicket(
        ticket: String,
        state: String?
    ): AppResult<BackendAccountMetadata> {
        return AppResult.Error(
            AgentError(
                code = "BACKEND_NOT_CONFIGURED",
                message = "Meta OAuth backend token exchange endpoint is not configured in this environment."
            )
        )
    }

    override suspend fun exchangeCodeForToken(
        code: String,
        redirectUri: String
    ): AppResult<SocialAccessToken> {
        return AppResult.Error(
            AgentError(
                code = "BACKEND_NOT_CONFIGURED",
                message = "Meta OAuth backend token exchange endpoint is not configured in this environment."
            )
        )
    }

    override suspend fun fetchFacebookPages(
        accessToken: SocialAccessToken
    ): AppResult<List<SocialPage>> {
        return AppResult.Error(
            AgentError(
                code = "BACKEND_NOT_CONFIGURED",
                message = "Meta OAuth backend page discovery endpoint is not configured."
            )
        )
    }

    override suspend fun fetchInstagramAccounts(
        accessToken: SocialAccessToken
    ): AppResult<List<SocialAccount>> {
        return AppResult.Error(
            AgentError(
                code = "BACKEND_NOT_CONFIGURED",
                message = "Meta OAuth backend Instagram discovery endpoint is not configured."
            )
        )
    }
}

