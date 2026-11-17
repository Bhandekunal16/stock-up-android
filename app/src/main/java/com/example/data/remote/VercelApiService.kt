package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface VercelApiService {
    @GET("api/portfolio/signals")
    suspend fun runPortfolioSignals(): Response<ResponseBody>

    @GET("api/screener/snapshot")
    suspend fun getScreenerSnapshot(
        @Query("company") company: String,
    ): Response<ResponseBody>
}
