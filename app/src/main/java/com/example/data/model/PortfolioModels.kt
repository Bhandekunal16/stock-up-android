package com.example.data.model

data class StockItem(
    val company: String,
    val shares: Double,
    val currentValue: Double,
    val referenceValue: Double,
) {
    val netDifference: Double
        get() = currentValue - referenceValue

    val returnPercent: Double
        get() = if (referenceValue > 0.0) ((currentValue - referenceValue) / referenceValue) * 100.0 else 0.0
}

data class PortfolioSnapshot(
    val date: String,
    val stocks: List<StockItem>,
) {
    val totalCurrentValue: Double
        get() = stocks.sumOf { it.currentValue }

    val totalReferenceValue: Double
        get() = stocks.sumOf { it.referenceValue }

    val netDifference: Double
        get() = totalCurrentValue - totalReferenceValue

    val returnPercent: Double
        get() = if (totalReferenceValue > 0.0) {
            (netDifference / totalReferenceValue) * 100.0
        } else {
            0.0
        }
}

data class ExtractResult(
    val date: String,
    val stocks: List<StockItem>,
    val message: String? = null,
)

enum class ServerReachability {
    UNKNOWN,
    CHECKING,
    UP,
    DOWN,
}

data class ServerStatusState(
    val reachability: ServerReachability = ServerReachability.UNKNOWN,
    val lastCheckedAt: String? = null,
    val detail: String? = null,
)
