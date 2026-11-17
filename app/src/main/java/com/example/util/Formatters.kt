package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Formatters {
    private val indianLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
    val istZone: ZoneId = ZoneId.of("Asia/Kolkata")

    private val istDateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm:ss a", indianLocale)
        .withZone(istZone)
    private val istTimeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", indianLocale)
        .withZone(istZone)
    private val istShortDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", indianLocale)
    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun todayIstDate(): String {
        return LocalDate.now(istZone).format(isoDateFormatter)
    }

    fun formatInr(value: Double, decimals: Int = 2): String {
        val isNegative = value < 0
        val absVal = abs(value)
        val formatted = formatIndianNumber(absVal, maxDecimals = decimals, minDecimals = decimals)
        return if (isNegative) "-₹$formatted" else "₹$formatted"
    }

    fun formatSignedInr(value: Double, decimals: Int = 2): String {
        return when {
            value > 0 -> "+${formatInr(value, decimals)}"
            value < 0 -> formatInr(value, decimals)
            else -> formatInr(0.0, decimals)
        }
    }

    fun formatCompactInr(value: Double): String {
        val isNeg = value < 0
        val absVal = abs(value)
        val prefix = if (isNeg) "-₹" else "₹"
        return when {
            absVal >= 1_00_00_000 -> "$prefix${String.format(Locale.US, "%.2fCr", absVal / 1_00_00_000.0)}"
            absVal >= 1_00_000 -> "$prefix${String.format(Locale.US, "%.2fL", absVal / 1_00_000.0)}"
            absVal >= 1_000 -> "$prefix${String.format(Locale.US, "%.1fk", absVal / 1_000.0)}"
            else -> "$prefix${String.format(Locale.US, "%.0f", absVal)}"
        }
    }

    fun formatIndianNumber(value: Double, maxDecimals: Int = 2, minDecimals: Int? = null): String {
        val isNeg = value < 0
        val absVal = abs(value)
        val effectiveMin = minDecimals ?: if (absVal % 1.0 == 0.0) 0 else minOf(2, maxDecimals)
        val rawStr = String.format(Locale.US, "%.${maxDecimals}f", absVal)
        val parts = rawStr.split(".")
        val intPart = parts[0]
        val decPartRaw = if (parts.size > 1) parts[1] else ""
        val trimmedDec = if (effectiveMin < maxDecimals) {
            val stripped = decPartRaw.trimEnd('0')
            if (stripped.length < effectiveMin) decPartRaw.take(effectiveMin) else stripped
        } else {
            decPartRaw
        }

        val groupedInt = if (intPart.length <= 3) {
            intPart
        } else {
            val lastThree = intPart.takeLast(3)
            val rest = intPart.dropLast(3)
            val restGrouped = rest.reversed().chunked(2).joinToString(",").reversed()
            "$restGrouped,$lastThree"
        }

        val sign = if (isNeg) "-" else ""
        return if (trimmedDec.isNotEmpty()) "$sign$groupedInt.$trimmedDec" else "$sign$groupedInt"
    }

    fun formatPercent(value: Double, signed: Boolean = false, decimals: Int = 2): String {
        val formatted = String.format(Locale.US, "%.${decimals}f%%", abs(value))
        return when {
            value < 0 -> "-$formatted"
            signed && value > 0 -> "+$formatted"
            else -> formatted
        }
    }

    fun formatDateLabel(dateStr: String): String {
        return runCatching {
            val date = LocalDate.parse(dateStr, isoDateFormatter)
            date.format(istShortDateFormatter)
        }.getOrElse { dateStr }
    }

    fun formatShortAxisDate(dateStr: String): String {
        return runCatching {
            val date = LocalDate.parse(dateStr, isoDateFormatter)
            date.format(DateTimeFormatter.ofPattern("dd MMM", indianLocale))
        }.getOrElse { dateStr.takeLast(5) }
    }

    fun formatIstTimestamp(isoOrRaw: String?): String {
        if (isoOrRaw.isNullOrBlank()) return "—"
        return runCatching {
            val instant = Instant.parse(isoOrRaw)
            istDateTimeFormatter.format(instant)
        }.getOrElse { isoOrRaw }
    }

    fun formatIstShortTime(isoOrRaw: String?): String {
        if (isoOrRaw.isNullOrBlank()) return "—"
        return runCatching {
            val instant = Instant.parse(isoOrRaw)
            istTimeOnlyFormatter.format(instant)
        }.getOrElse {
            isoOrRaw.takeLast(8)
        }
    }

    fun formatDurationMs(durationMs: Long): String {
        return when {
            durationMs < 1000L -> "${durationMs} ms"
            durationMs < 60_000L -> String.format(Locale.US, "%.2f s", durationMs / 1000.0)
            else -> {
                val mins = durationMs / 60_000L
                val secs = (durationMs % 60_000L) / 1000.0
                String.format(Locale.US, "%dm %.1fs", mins, secs)
            }
        }
    }
}
