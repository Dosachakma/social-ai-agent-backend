package com.example.data.session

import com.example.data.config.SecurityConfig
import com.example.data.remote.api.SocialStudioApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory for Retrofit and OkHttpClient targeting the Social AI Studio Backend API.
 */
object ApiClient {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun createOkHttpClient(
        sessionManager: SessionManager = SessionManager.getInstance(),
        customInterceptors: List<okhttp3.Interceptor> = emptyList()
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(sessionManager))
            .addInterceptor(loggingInterceptor)

        for (interceptor in customInterceptors) {
            builder.addInterceptor(interceptor)
        }

        return builder.build()
    }

    fun createService(
        baseUrl: String = SecurityConfig.getMetaBackendBaseUrl(),
        okHttpClient: OkHttpClient = createOkHttpClient()
    ): SocialStudioApiService {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SocialStudioApiService::class.java)
    }
}
