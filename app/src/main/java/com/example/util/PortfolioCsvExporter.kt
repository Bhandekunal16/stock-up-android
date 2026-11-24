package com.example.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.StockItem
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CsvExportScope(
    val title: String,
    val description: String,
) {
    LATEST_HOLDINGS(
        title = "Latest Holdings",
        description = "All stocks from your most recent portfolio snapshot",
    ),
    ALL_HISTORY_HOLDINGS(
        title = "All Snapshot Holdings",
        description = "Every holding across all tracked snapshot dates chronologically",
    ),
    SNAPSHOT_SUMMARY(
        title = "Daily Snapshot Summary",
        description = "Daily portfolio totals, invested capital, and net P&L over time",
    ),
    SINGLE_SNAPSHOT(
        title = "Selected Snapshot Holdings",
        description = "Holdings breakdown for the selected snapshot date",
    ),
}

data class CsvExportPayload(
    val scope: CsvExportScope,
    val fileName: String,
    val rowCount: Int,
    val content: String,
)

object PortfolioCsvExporter {

    private val HOLDINGS_HEADER = listOf(
        "Snapshot Date",
        "Company",
        "Shares",
        "Current Value (INR)",
        "Reference Value (INR)",
        "Avg Value Per Share (INR)",
        "Net P&L (INR)",
        "Return %",
        "Weight %",
    )

    private val SUMMARY_HEADER = listOf(
        "Snapshot Date",
        "Holdings Count",
        "Total Current Value (INR)",
        "Total Reference Value (INR)",
        "Net P&L (INR)",
        "Return %",
    )

    fun buildCsvPayload(
        snapshots: List<PortfolioSnapshot>,
        scope: CsvExportScope,
        targetSnapshot: PortfolioSnapshot? = null,
    ): CsvExportPayload {
        val sortedSnapshots = snapshots.sortedBy { it.date }
        val latest = sortedSnapshots.lastOrNull()
        val sb = StringBuilder()

        return when (scope) {
            CsvExportScope.LATEST_HOLDINGS -> {
                sb.appendLine(HOLDINGS_HEADER.joinToString(",") { escapeCsvField(it) })
                val rows = latest?.let { buildHoldingRows(it) }.orEmpty()
                rows.forEach { sb.appendLine(it) }
                val dateTag = sanitizeFileDate(latest?.date ?: Formatters.todayIstDate())
                CsvExportPayload(
                    scope = scope,
                    fileName = "stockfolio_holdings_$dateTag.csv",
                    rowCount = rows.size,
                    content = sb.toString(),
                )
            }

            CsvExportScope.ALL_HISTORY_HOLDINGS -> {
                sb.appendLine(HOLDINGS_HEADER.joinToString(",") { escapeCsvField(it) })
                var count = 0
                sortedSnapshots.forEach { snapshot ->
                    val rows = buildHoldingRows(snapshot)
                    rows.forEach { sb.appendLine(it) }
                    count += rows.size
                }
                val dateTag = sanitizeFileDate(latest?.date ?: Formatters.todayIstDate())
                CsvExportPayload(
                    scope = scope,
                    fileName = "stockfolio_all_holdings_$dateTag.csv",
                    rowCount = count,
                    content = sb.toString(),
                )
            }

            CsvExportScope.SNAPSHOT_SUMMARY -> {
                sb.appendLine(SUMMARY_HEADER.joinToString(",") { escapeCsvField(it) })
                sortedSnapshots.forEach { snapshot ->
                    val row = listOf(
                        escapeCsvField(sanitizeTextCell(snapshot.date)),
                        snapshot.stocks.size.toString(),
                        formatDecimal(snapshot.totalCurrentValue, 2),
                        formatDecimal(snapshot.totalReferenceValue, 2),
                        formatDecimal(snapshot.netDifference, 2),
                        formatDecimal(snapshot.returnPercent, 2),
                    ).joinToString(",")
                    sb.appendLine(row)
                }
                val dateTag = sanitizeFileDate(latest?.date ?: Formatters.todayIstDate())
                CsvExportPayload(
                    scope = scope,
                    fileName = "stockfolio_summary_$dateTag.csv",
                    rowCount = sortedSnapshots.size,
                    content = sb.toString(),
                )
            }

            CsvExportScope.SINGLE_SNAPSHOT -> {
                sb.appendLine(HOLDINGS_HEADER.joinToString(",") { escapeCsvField(it) })
                val snap = targetSnapshot ?: latest
                val rows = snap?.let { buildHoldingRows(it) }.orEmpty()
                rows.forEach { sb.appendLine(it) }
                val dateTag = sanitizeFileDate(snap?.date ?: Formatters.todayIstDate())
                CsvExportPayload(
                    scope = scope,
                    fileName = "stockfolio_snapshot_$dateTag.csv",
                    rowCount = rows.size,
                    content = sb.toString(),
                )
            }
        }
    }

    private fun buildHoldingRows(snapshot: PortfolioSnapshot): List<String> {
        val totalCurrent = snapshot.totalCurrentValue
        return snapshot.stocks
            .sortedByDescending { it.currentValue }
            .map { stock ->
                formatHoldingRow(
                    date = snapshot.date,
                    stock = stock,
                    totalCurrentValue = totalCurrent,
                )
            }
    }

    private fun formatHoldingRow(
        date: String,
        stock: StockItem,
        totalCurrentValue: Double,
    ): String {
        val avgPerShare = if (stock.shares > 0.0) stock.currentValue / stock.shares else 0.0
        val weightPercent = if (totalCurrentValue > 0.0) (stock.currentValue / totalCurrentValue) * 100.0 else 0.0

        return listOf(
            escapeCsvField(sanitizeTextCell(date)),
            escapeCsvField(sanitizeTextCell(stock.company)),
            formatShares(stock.shares),
            formatDecimal(stock.currentValue, 2),
            formatDecimal(stock.referenceValue, 2),
            formatDecimal(avgPerShare, 2),
            formatDecimal(stock.netDifference, 2),
            formatDecimal(stock.returnPercent, 2),
            formatDecimal(weightPercent, 2),
        ).joinToString(",")
    }

    internal fun escapeCsvField(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * Mitigates spreadsheet formula injection (CWE-1236) on untrusted text cells such as OCR company names.
     */
    internal fun sanitizeTextCell(value: String): String {
        val trimmed = value.replace("\r", " ").replace("\n", " ").trim()
        if (trimmed.isEmpty()) return ""
        val first = trimmed.first()
        return if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
            "'$trimmed"
        } else {
            trimmed
        }
    }

    private fun formatDecimal(value: Double, decimals: Int): String {
        val safe = if (value.isFinite()) value else 0.0
        return String.format(Locale.US, "%.${decimals}f", safe)
    }

    private fun formatShares(shares: Double): String {
        val safe = if (shares.isFinite()) shares else 0.0
        val raw = String.format(Locale.US, "%.4f", safe)
        return if (raw.contains('.')) {
            raw.trimEnd('0').trimEnd('.')
        } else {
            raw
        }
    }

    private fun sanitizeFileDate(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return cleaned.ifBlank { "export" }
    }

    suspend fun writeCsvToUri(
        context: Context,
        uri: Uri,
        csvContent: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(csvContent.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: throw IOException("Unable to open destination file for writing")
        }
    }

    suspend fun writeCsvToShareFile(
        context: Context,
        fileName: String,
        csvContent: String,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                throw IOException("Unable to create export cache directory")
            }
            // Clean previous temporary CSV exports so cache stays small
            exportDir.listFiles()?.forEach { oldFile ->
                if (oldFile.isFile && oldFile.name.endsWith(".csv")) {
                    oldFile.delete()
                }
            }
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val outFile = File(exportDir, safeName)
            outFile.writeText(csvContent, Charsets.UTF_8)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile,
            )
        }
    }
}
