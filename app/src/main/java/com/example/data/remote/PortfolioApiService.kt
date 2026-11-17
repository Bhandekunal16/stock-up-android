package com.example.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface PortfolioApiService {
    @GET("api/portfolio")
    suspend fun getAllPortfolios(): Response<ResponseBody>

    @GET("api/portfolio/{date}")
    suspend fun getPortfolioByDate(@Path("date") date: String): Response<ResponseBody>

    @Multipart
    @POST("api/portfolio/extract")
    suspend fun extractPortfolio(@Part image: MultipartBody.Part): Response<ResponseBody>

    @DELETE("api/portfolio")
    suspend fun deleteAllPortfolios(): Response<ResponseBody>
}
