package com.example.data.remote

import com.example.data.model.ScreenerStreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class ScreenerStreamClient(
    private val baseUrl: String,
    private val client: OkHttpClient,
) {
    fun streamCompany(companyCode: String, everySeconds: Int): Flow<ScreenerStreamEvent> = flow {
        emit(ScreenerStreamEvent.Connecting)

        val url = "${baseUrl}api/screener/stream?company=$companyCode&every=$everySeconds"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/x-ndjson, application/json, text/plain")
            .build()

        val call = client.newCall(request)
        val jobHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
            call.cancel()
        }

        try {
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errText = runCatching { resp.body?.string() }.getOrNull()
                    val msg = PortfolioJsonParser.extractErrorMessage(
                        errText,
                        "Stream request failed (HTTP ${resp.code})"
                    )
                    emit(ScreenerStreamEvent.Error(msg))
                    return@flow
                }

                val body = resp.body
                if (body == null) {
                    emit(ScreenerStreamEvent.Error("Empty stream response body"))
                    return@flow
                }

                emit(ScreenerStreamEvent.Connected)
                val source = body.source()

                while (currentCoroutineContext().isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue

                    val parsedEvent = parseNdjsonLine(trimmed, companyCode)
                    if (parsedEvent != null) {
                        emit(parsedEvent)
                        if (parsedEvent is ScreenerStreamEvent.Error) {
                            break
                        }
                    }
                }

                if (currentCoroutineContext().isActive) {
                    emit(ScreenerStreamEvent.Completed)
                }
            }
        } catch (ioe: IOException) {
            if (currentCoroutineContext().isActive && !call.isCanceled()) {
                emit(ScreenerStreamEvent.Error(ioe.message ?: "Stream connection lost"))
            }
        } catch (t: Throwable) {
            if (currentCoroutineContext().isActive && !call.isCanceled()) {
                emit(ScreenerStreamEvent.Error(t.message ?: "Unexpected streaming error"))
            }
        } finally {
            jobHandle?.dispose()
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun parseNdjsonLine(line: String, companyCode: String): ScreenerStreamEvent? {
        return try {
            val obj = JSONObject(line)
            if (obj.has("success") && !obj.optBoolean("success", true)) {
                val err = obj.optString("error").takeIf { it.isNotBlank() }
                    ?: obj.optString("message").takeIf { it.isNotBlank() }
                    ?: "Stream returned error payload"
                return ScreenerStreamEvent.Error(err)
            }
            if (obj.has("error") && obj.optString("error").isNotBlank() && !obj.has("price")) {
                return ScreenerStreamEvent.Error(obj.optString("error"))
            }
            val record = PortfolioJsonParser.parseScreenerRecordObject(obj, companyCode)
            if (record.price == null) {
                ScreenerStreamEvent.Warning("Stream update for $companyCode arrived without stock price")
            } else {
                ScreenerStreamEvent.Data(record)
            }
        } catch (_: Throwable) {
            ScreenerStreamEvent.Warning("Skipped malformed NDJSON line")
        }
    }
}
