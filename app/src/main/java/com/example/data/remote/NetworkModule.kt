package com.example.data.remote

import com.example.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    val portfolioOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val vercelRestOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val screenerStreamOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Continuous NDJSON stream
            .retryOnConnectionFailure(false)
            .build()
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    val portfolioApiService: PortfolioApiService by lazy {
        Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(BuildConfig.PORTFOLIO_API_BASE_URL))
            .client(portfolioOkHttpClient)
            .build()
            .create(PortfolioApiService::class.java)
    }

    val vercelApiService: VercelApiService by lazy {
        Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(BuildConfig.VERCEL_BACKEND_BASE_URL))
            .client(vercelRestOkHttpClient)
            .build()
            .create(VercelApiService::class.java)
    }

    val screenerStreamClient: ScreenerStreamClient by lazy {
        ScreenerStreamClient(
            baseUrl = normalizeBaseUrl(BuildConfig.VERCEL_BACKEND_BASE_URL),
            client = screenerStreamOkHttpClient,
        )
    }
}
