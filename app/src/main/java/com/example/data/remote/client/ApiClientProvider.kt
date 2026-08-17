package com.example.data.remote.client

import com.example.data.remote.api.SocialMediaApiService
import com.example.data.remote.session.WorkspaceSessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClientProvider {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()

        val token = WorkspaceSessionManager.getAuthToken()
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        val workspaceId = WorkspaceSessionManager.getWorkspaceId()
        if (workspaceId.isNotBlank()) {
            builder.header("X-Workspace-Id", workspaceId)
        }

        builder.header("Accept", "application/json")
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedApiService: SocialMediaApiService? = null

    fun getApiService(customBaseUrl: String? = null): SocialMediaApiService {
        val targetBaseUrl = (customBaseUrl ?: WorkspaceSessionManager.getBackendUrl()).let { url ->
            if (url.endsWith("/")) url else "$url/"
        }

        val current = cachedApiService
        if (current != null && cachedBaseUrl == targetBaseUrl) {
            return current
        }

        synchronized(this) {
            if (cachedApiService != null && cachedBaseUrl == targetBaseUrl) {
                return cachedApiService!!
            }

            val retrofit = Retrofit.Builder()
                .baseUrl(targetBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            val service = retrofit.create(SocialMediaApiService::class.java)
            cachedApiService = service
            cachedBaseUrl = targetBaseUrl
            return service
        }
    }
}
