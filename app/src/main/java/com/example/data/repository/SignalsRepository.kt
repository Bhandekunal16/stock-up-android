package com.example.data.repository

import android.os.SystemClock
import com.example.data.model.SignalsExecutionStatus
import com.example.data.model.SignalsHistoryEntry
import com.example.data.remote.NetworkModule
import com.example.data.remote.PortfolioJsonParser
import com.example.data.remote.VercelApiService
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

class SignalsRepository(
    private val api: VercelApiService = NetworkModule.vercelApiService,
) {
    suspend fun executeSignals(): SignalsHistoryEntry = withContext(Dispatchers.IO) {
        val startMs = SystemClock.elapsedRealtime()
        val executedAt = Formatters.formatIstTimestamp(Instant.now().toString())

        try {
            val response = api.runPortfolioSignals()
            val durationMs = SystemClock.elapsedRealtime() - startMs
            val bodyString = if (response.isSuccessful) {
                response.body()?.string().orEmpty()
            } else {
                response.errorBody()?.string().orEmpty()
            }

            if (bodyString.isNotBlank()) {
                val parsed = PortfolioJsonParser.parseSignalsResponse(bodyString, response.code())
                val status = if (parsed.success && response.isSuccessful) {
                    SignalsExecutionStatus.SUCCESS
                } else {
                    SignalsExecutionStatus.FAILED
                }
                val resolvedError = when {
                    parsed.error.isNotBlank() -> parsed.error
                    status == SignalsExecutionStatus.FAILED -> "Signals execution failed (HTTP ${response.code()})"
                    else -> ""
                }
                SignalsHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    executedAt = executedAt,
                    status = status,
                    exitCode = parsed.exitCode,
                    durationMs = durationMs,
                    output = parsed.output,
                    error = resolvedError,
                )
            } else {
                SignalsHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    executedAt = executedAt,
                    status = if (response.isSuccessful) SignalsExecutionStatus.SUCCESS else SignalsExecutionStatus.FAILED,
                    exitCode = if (response.isSuccessful) 0 else null,
                    durationMs = durationMs,
                    output = "",
                    error = if (response.isSuccessful) "" else "Request failed with HTTP ${response.code()}",
                )
            }
        } catch (t: Throwable) {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            SignalsHistoryEntry(
                id = UUID.randomUUID().toString(),
                executedAt = executedAt,
                status = SignalsExecutionStatus.FAILED,
                exitCode = null,
                durationMs = durationMs,
                output = "",
                error = t.message ?: "Network error while executing portfolio signals",
            )
        }
    }
}
