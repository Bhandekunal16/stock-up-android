package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.ExtractResult
import com.example.data.model.PortfolioSnapshot
import com.example.data.remote.NetworkModule
import com.example.data.remote.PortfolioApiService
import com.example.data.remote.PortfolioJsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class PortfolioRepository(
    private val api: PortfolioApiService = NetworkModule.portfolioApiService,
) {
    companion object {
        const val MAX_IMAGE_BYTES = 10L * 1024L * 1024L // 10 MB
        private val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }

    suspend fun getAllSnapshots(): Result<List<PortfolioSnapshot>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getAllPortfolios()
            if (response.code() == 404) {
                return@runCatching emptyList()
            }
            if (!response.isSuccessful) {
                val errBody = response.errorBody()?.string()
                throw IllegalStateException(
                    PortfolioJsonParser.extractErrorMessage(
                        errBody,
                        "Failed to fetch portfolio snapshots (HTTP ${response.code()})"
                    )
                )
            }
            val raw = response.body()?.string().orEmpty()
            PortfolioJsonParser.parseAllSnapshots(raw)
        }
    }

    suspend fun getSnapshotByDate(date: String): Result<PortfolioSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getPortfolioByDate(date)
            if (response.code() == 404) {
                return@runCatching PortfolioSnapshot(date = date, stocks = emptyList())
            }
            if (!response.isSuccessful) {
                val errBody = response.errorBody()?.string()
                throw IllegalStateException(
                    PortfolioJsonParser.extractErrorMessage(
                        errBody,
                        "Failed to load snapshot for $date (HTTP ${response.code()})"
                    )
                )
            }
            val raw = response.body()?.string().orEmpty()
            PortfolioJsonParser.parseSingleSnapshot(date, raw)
        }
    }

    suspend fun extractPortfolioFromUri(context: Context, uri: Uri): Result<ExtractResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val contentResolver = context.contentResolver
                val rawMime = contentResolver.getType(uri)?.lowercase()
                val mimeType = when {
                    rawMime == "image/jpg" -> "image/jpeg"
                    rawMime in ALLOWED_MIME_TYPES -> rawMime!!
                    else -> "image/jpeg"
                }
                if (mimeType !in ALLOWED_MIME_TYPES) {
                    throw IllegalArgumentException("Unsupported image format. Use JPEG, PNG, or WebP.")
                }

                val fileName = queryFileName(context, uri) ?: when (mimeType) {
                    "image/png" -> "portfolio.png"
                    "image/webp" -> "portfolio.webp"
                    else -> "portfolio.jpg"
                }

                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                } ?: throw IllegalStateException("Unable to read selected image file")

                if (bytes.isEmpty()) {
                    throw IllegalArgumentException("Selected image file is empty")
                }
                if (bytes.size > MAX_IMAGE_BYTES) {
                    throw IllegalArgumentException("Image exceeds maximum allowed size of 10 MB")
                }

                val reqBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("image", fileName, reqBody)

                val response = api.extractPortfolio(part)
                if (!response.isSuccessful) {
                    val errBody = response.errorBody()?.string()
                    val fallbackMsg = when (response.code()) {
                        400 -> "Invalid image request (HTTP 400). Verify file format."
                        413 -> "Image too large (HTTP 413). Maximum upload size is 10 MB."
                        422 -> "Could not extract holdings from screenshot (HTTP 422)."
                        500 -> "OCR server error while extracting holdings (HTTP 500)."
                        else -> "Upload failed (HTTP ${response.code()})"
                    }
                    throw IllegalStateException(
                        PortfolioJsonParser.extractErrorMessage(errBody, fallbackMsg)
                    )
                }

                val raw = response.body()?.string().orEmpty()
                PortfolioJsonParser.parseExtractResult(raw)
            }
        }

    suspend fun deleteAllSnapshots(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.deleteAllPortfolios()
            if (!response.isSuccessful && response.code() != 404) {
                val errBody = response.errorBody()?.string()
                throw IllegalStateException(
                    PortfolioJsonParser.extractErrorMessage(
                        errBody,
                        "Failed to clear snapshots (HTTP ${response.code()})"
                    )
                )
            }
        }
    }

    suspend fun checkServerUp(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getAllPortfolios()
            // 200 or 404 (empty portfolio store) means server is reachable and up
            response.isSuccessful || response.code() == 404
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }
}
