package com.example.data.repository

import com.example.data.model.ScreenerRecord
import com.example.data.model.ScreenerStreamEvent
import com.example.data.remote.NetworkModule
import com.example.data.remote.PortfolioJsonParser
import com.example.data.remote.ScreenerStreamClient
import com.example.data.remote.VercelApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ScreenerRepository(
    private val api: VercelApiService = NetworkModule.vercelApiService,
    private val streamClient: ScreenerStreamClient = NetworkModule.screenerStreamClient,
) {
    suspend fun fetchSnapshot(companyCode: String): Result<ScreenerRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getScreenerSnapshot(companyCode)
            if (!response.isSuccessful) {
                val errBody = response.errorBody()?.string()
                val fallback = when (response.code()) {
                    400 -> "Invalid company code '$companyCode' (HTTP 400)."
                    422 -> "Unable to parse Screener data for '$companyCode' (HTTP 422)."
                    500 -> "Screener backend error (HTTP 500)."
                    502 -> "Upstream Screener gateway error (HTTP 502)."
                    else -> "Snapshot failed (HTTP ${response.code()})"
                }
                throw IllegalStateException(
                    PortfolioJsonParser.extractErrorMessage(errBody, fallback)
                )
            }
            val raw = response.body()?.string().orEmpty()
            val record = PortfolioJsonParser.parseScreenerSnapshotResponse(raw, companyCode)
            if (record.price == null) {
                throw IllegalStateException("Stock price missing in Screener snapshot for $companyCode")
            }
            record
        }
    }

    fun streamCompany(companyCode: String, everySeconds: Int): Flow<ScreenerStreamEvent> {
        return streamClient.streamCompany(companyCode, everySeconds)
    }
}
