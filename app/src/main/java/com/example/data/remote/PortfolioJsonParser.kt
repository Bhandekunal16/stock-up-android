package com.example.data.remote

import com.example.data.model.ExtractResult
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.RatioValue
import com.example.data.model.ScreenerRecord
import com.example.data.model.StockItem
import com.example.util.Formatters
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object PortfolioJsonParser {
    private val dateKeyRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun extractErrorMessage(rawBody: String?, fallback: String): String {
        if (rawBody.isNullOrBlank()) return fallback
        return runCatching {
            val parsed = JSONTokener(rawBody).nextValue()
            if (parsed is JSONObject) {
                parsed.optString("error").takeIf { it.isNotBlank() }
                    ?: parsed.optString("message").takeIf { it.isNotBlank() }
                    ?: parsed.optString("detail").takeIf { it.isNotBlank() }
                    ?: fallback
            } else {
                rawBody.take(300)
            }
        }.getOrElse { fallback }
    }

    fun parseAllSnapshots(rawJson: String): List<PortfolioSnapshot> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()
        val root = JSONTokener(trimmed).nextValue()
        val snapshots = mutableListOf<PortfolioSnapshot>()

        when (root) {
            is JSONObject -> {
                val container = unwrapSnapshotContainer(root)
                val keys = container.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (dateKeyRegex.matches(key)) {
                        val value = container.opt(key)
                        val stocks = parseStocksFromAny(value)
                        snapshots.add(PortfolioSnapshot(date = key, stocks = stocks))
                    }
                }
                if (snapshots.isEmpty()) {
                    // Check if wrapped in "snapshots" array
                    val arr = root.optJSONArray("snapshots") ?: root.optJSONArray("data")
                    if (arr != null) {
                        snapshots.addAll(parseSnapshotsArray(arr))
                    }
                }
            }
            is JSONArray -> {
                snapshots.addAll(parseSnapshotsArray(root))
            }
        }
        return snapshots.sortedBy { it.date }
    }

    private fun unwrapSnapshotContainer(obj: JSONObject): JSONObject {
        val hasDirectDateKeys = obj.keys().asSequence().any { dateKeyRegex.matches(it) }
        if (hasDirectDateKeys) return obj
        for (wrapper in listOf("data", "snapshots", "portfolio", "history")) {
            val nested = obj.optJSONObject(wrapper)
            if (nested != null) return nested
        }
        return obj
    }

    private fun parseSnapshotsArray(arr: JSONArray): List<PortfolioSnapshot> {
        val list = mutableListOf<PortfolioSnapshot>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val date = item.optString("date").takeIf { it.isNotBlank() } ?: continue
            val stocks = parseStocksFromAny(
                item.opt("stocks") ?: item.opt("data") ?: item.opt("items") ?: item.opt("holdings")
            )
            list.add(PortfolioSnapshot(date = date, stocks = stocks))
        }
        return list
    }

    fun parseSingleSnapshot(date: String, rawJson: String): PortfolioSnapshot {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return PortfolioSnapshot(date = date, stocks = emptyList())
        val root = JSONTokener(trimmed).nextValue()
        val stocks = when (root) {
            is JSONArray -> parseStocksArray(root)
            is JSONObject -> {
                val dataNode = root.opt("data")
                when {
                    dataNode != null -> parseStocksFromAny(dataNode)
                    root.has(date) -> parseStocksFromAny(root.opt(date))
                    else -> parseStocksFromAny(root)
                }
            }
            else -> emptyList()
        }
        return PortfolioSnapshot(date = date, stocks = stocks)
    }

    fun parseExtractResult(rawJson: String): ExtractResult {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return ExtractResult(date = Formatters.todayIstDate(), stocks = emptyList())
        }
        val root = JSONTokener(trimmed).nextValue()
        return when (root) {
            is JSONArray -> ExtractResult(
                date = Formatters.todayIstDate(),
                stocks = parseStocksArray(root),
            )
            is JSONObject -> {
                val date = root.optString("date").takeIf { it.isNotBlank() }
                    ?: root.optString("snapshotDate").takeIf { it.isNotBlank() }
                    ?: root.keys().asSequence().firstOrNull { dateKeyRegex.matches(it) }
                    ?: Formatters.todayIstDate()
                val rawData = root.opt("stocks")
                    ?: root.opt("data")
                    ?: root.opt("extracted")
                    ?: root.opt("items")
                    ?: root.opt("portfolio")
                    ?: root.opt(date)
                val stocks = parseStocksFromAny(rawData)
                val msg = root.optString("message").takeIf { it.isNotBlank() }
                ExtractResult(date = date, stocks = stocks, message = msg)
            }
            else -> ExtractResult(date = Formatters.todayIstDate(), stocks = emptyList())
        }
    }

    private fun parseStocksFromAny(node: Any?): List<StockItem> {
        return when (node) {
            is JSONArray -> parseStocksArray(node)
            is JSONObject -> {
                for (key in listOf("stocks", "items", "holdings", "data", "portfolio")) {
                    val arr = node.optJSONArray(key)
                    if (arr != null) return parseStocksArray(arr)
                }
                // Check if this JSONObject itself is a single stock item
                if (node.has("company") || node.has("name") || node.has("symbol")) {
                    listOfNotNull(parseStockObject(node))
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun parseStocksArray(arr: JSONArray): List<StockItem> {
        val items = mutableListOf<StockItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            parseStockObject(obj)?.let { items.add(it) }
        }
        return items
    }

    private fun parseStockObject(obj: JSONObject): StockItem? {
        val company = obj.optString("company").takeIf { it.isNotBlank() }
            ?: obj.optString("name").takeIf { it.isNotBlank() }
            ?: obj.optString("symbol").takeIf { it.isNotBlank() }
            ?: return null
        val shares = obj.optDoubleOrZero("shares", "quantity", "qty")
        val current = obj.optDoubleOrZero("currentValue", "current_value", "current")
        val reference = obj.optDoubleOrZero("referenceValue", "reference_value", "investedValue", "invested_value", "reference")
        return StockItem(
            company = company.trim(),
            shares = shares,
            currentValue = current,
            referenceValue = reference,
        )
    }

    data class ParsedSignalsResponse(
        val success: Boolean,
        val exitCode: Int?,
        val output: String,
        val error: String,
    )

    fun parseSignalsResponse(rawJson: String, httpCode: Int): ParsedSignalsResponse {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return ParsedSignalsResponse(
                success = false,
                exitCode = null,
                output = "",
                error = "Empty response from signals endpoint (HTTP $httpCode)",
            )
        }
        return runCatching {
            val obj = JSONObject(trimmed)
            val success = obj.optBoolean("success", httpCode in 200..299)
            val exitCode = if (obj.has("exitCode") && !obj.isNull("exitCode")) obj.optInt("exitCode") else null
            val output = obj.optString("output", "")
            val errorMsg = obj.optString("error").takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: ""
            ParsedSignalsResponse(
                success = success,
                exitCode = exitCode,
                output = output,
                error = errorMsg,
            )
        }.getOrElse {
            ParsedSignalsResponse(
                success = false,
                exitCode = null,
                output = "",
                error = "Malformed signals response (HTTP $httpCode): ${trimmed.take(200)}",
            )
        }
    }

    fun parseScreenerSnapshotResponse(rawJson: String, fallbackCode: String): ScreenerRecord {
        val obj = JSONObject(rawJson.trim())
        val success = obj.optBoolean("success", true)
        if (!success) {
            val msg = obj.optString("error").takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: "Screener snapshot request failed"
            throw IllegalStateException(msg)
        }
        val companyCode = obj.optString("companyCode").takeIf { it.isNotBlank() } ?: fallbackCode
        val dataObj = obj.optJSONObject("data") ?: obj
        return parseScreenerRecordObject(dataObj, companyCode)
    }

    fun parseScreenerRecordObject(obj: JSONObject, fallbackCode: String): ScreenerRecord {
        val dataObj = obj.optJSONObject("data") ?: obj
        val code = obj.optString("companyCode").takeIf { it.isNotBlank() }
            ?: dataObj.optString("companyCode").takeIf { it.isNotBlank() }
            ?: fallbackCode
        val companyName = dataObj.optString("company").takeIf { it.isNotBlank() } ?: code
        val time = dataObj.optString("time").takeIf { it.isNotBlank() } ?: ""
        val price = dataObj.optNullableDouble("price")
        val marketCap = dataObj.optNullableDouble("marketCap")
        val stockPE = dataObj.optNullableDouble("stockPE")
        val high = dataObj.optNullableDouble("high")
        val low = dataObj.optNullableDouble("low")
        val changed = dataObj.optBoolean("changed", false)

        val ratiosMap = linkedMapOf<String, RatioValue>()
        val ratiosObj = dataObj.optJSONObject("ratios")
        if (ratiosObj != null) {
            val keys = ratiosObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val rawVal = ratiosObj.opt(key)
                ratiosMap[key] = parseRatioValue(rawVal)
            }
        }

        return ScreenerRecord(
            companyCode = code,
            companyName = companyName,
            time = time,
            price = price,
            marketCap = marketCap,
            stockPE = stockPE,
            high = high,
            low = low,
            changed = changed,
            ratios = ratiosMap,
        )
    }

    private fun parseRatioValue(rawVal: Any?): RatioValue {
        return when (rawVal) {
            null, JSONObject.NULL -> RatioValue.NullVal
            is Number -> {
                val d = rawVal.toDouble()
                if (d.isNaN() || d.isInfinite()) RatioValue.NullVal else RatioValue.NumberVal(d)
            }
            is JSONArray -> {
                val high = rawVal.optNullableDoubleAt(0)
                val low = rawVal.optNullableDoubleAt(1)
                RatioValue.RangeVal(high = high, low = low)
            }
            is String -> {
                val trimmed = rawVal.trim()
                if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) {
                    RatioValue.NullVal
                } else {
                    RatioValue.TextVal(trimmed)
                }
            }
            else -> RatioValue.TextVal(rawVal.toString())
        }
    }

    private fun JSONObject.optDoubleOrZero(vararg keys: String): Double {
        for (k in keys) {
            val d = optNullableDouble(k)
            if (d != null) return d
        }
        return 0.0
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toDouble().takeIf { !it.isNaN() && !it.isInfinite() }
            is String -> value.replace(",", "").trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun JSONArray.optNullableDoubleAt(index: Int): Double? {
        if (index < 0 || index >= length() || isNull(index)) return null
        val value = opt(index)
        return when (value) {
            is Number -> value.toDouble().takeIf { !it.isNaN() && !it.isInfinite() }
            is String -> value.replace(",", "").trim().toDoubleOrNull()
            else -> null
        }
    }
}
