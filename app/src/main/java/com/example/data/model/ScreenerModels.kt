package com.example.data.model

enum class ScreenerConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

sealed class RatioValue {
    data class NumberVal(val value: Double) : RatioValue()
    data class TextVal(val value: String) : RatioValue()
    data class RangeVal(val high: Double?, val low: Double?) : RatioValue()
    data object NullVal : RatioValue()
}

data class ScreenerRecord(
    val companyCode: String,
    val companyName: String,
    val time: String,
    val price: Double?,
    val marketCap: Double?,
    val stockPE: Double?,
    val high: Double?,
    val low: Double?,
    val changed: Boolean,
    val ratios: Map<String, RatioValue>,
)

sealed class ScreenerStreamEvent {
    data object Connecting : ScreenerStreamEvent()
    data object Connected : ScreenerStreamEvent()
    data class Data(val record: ScreenerRecord) : ScreenerStreamEvent()
    data class Warning(val message: String) : ScreenerStreamEvent()
    data class Error(val message: String) : ScreenerStreamEvent()
    data object Completed : ScreenerStreamEvent()
}
