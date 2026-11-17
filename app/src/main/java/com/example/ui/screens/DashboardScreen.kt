package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.PortfolioSnapshot
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.FullScreenErrorState
import com.example.ui.components.GraphiteSectionCard
import com.example.ui.components.IconSegmentedToggle
import com.example.ui.components.KpiCard
import com.example.ui.components.LinePoint
import com.example.ui.components.LineSeries
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.ResponsiveKpiGrid
import com.example.ui.components.SegmentedTextToggle
import com.example.ui.components.StockLineChart
import com.example.ui.theme.ChartPalette
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteBlue
import com.example.ui.theme.GraphiteGreen
import com.example.ui.theme.GraphiteRed
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.viewmodel.PortfolioLoadState
import com.example.ui.viewmodel.PortfolioUiState
import com.example.util.Formatters

@Composable
fun DashboardScreen(
    uiState: PortfolioUiState,
    kpiColumns: Int,
    onRetry: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToHoldings: () -> Unit,
    onNavigateToSnapshot: (String) -> Unit,
) {
    when (val load = uiState.loadState) {
        is PortfolioLoadState.Loading -> {
            LoadingStateBox(message = "Loading portfolio overview...")
        }
        is PortfolioLoadState.Error -> {
            FullScreenErrorState(message = load.message, onRetry = onRetry)
        }
        is PortfolioLoadState.Success -> {
            val snapshots = load.snapshots
            val latest = snapshots.lastOrNull()
            if (latest == null) {
                EmptyStateBox(
                    title = "No portfolio snapshots yet",
                    subtitle = "Upload a broker portfolio screenshot to extract holdings and track valuation trends.",
                    icon = Icons.Outlined.CloudUpload,
                    actionLabel = "Upload Portfolio",
                    onAction = onNavigateToUpload,
                )
            } else {
                DashboardContent(
                    snapshots = snapshots,
                    latest = latest,
                    kpiColumns = kpiColumns,
                    onNavigateToHoldings = onNavigateToHoldings,
                    onNavigateToSnapshot = onNavigateToSnapshot,
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    snapshots: List<PortfolioSnapshot>,
    latest: PortfolioSnapshot,
    kpiColumns: Int,
    onNavigateToHoldings: () -> Unit,
    onNavigateToSnapshot: (String) -> Unit,
) {
    var performanceMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Valuation, 1 = Net P&L
    var trajectoryMode by rememberSaveable { mutableIntStateOf(0) }  // 0 = Over Time, 1 = Weight %

    val netDiff = latest.netDifference
    val isProfit = netDiff >= 0.0
    val pnlColor = if (isProfit) GraphiteGreen else GraphiteRed

    val kpiItems: List<@Composable (Modifier) -> Unit> = listOf(
        { mod ->
            KpiCard(
                title = "Total Current Value",
                value = Formatters.formatInr(latest.totalCurrentValue),
                supportingText = "${snapshots.size} snapshot${if (snapshots.size == 1) "" else "s"} tracked",
                icon = Icons.Outlined.AccountBalanceWallet,
                valueColor = GraphiteTextPrimary,
                iconTint = GraphiteAccentViolet,
                modifier = mod,
                testTag = "kpi_current_value",
            )
        },
        { mod ->
            KpiCard(
                title = "Reference / Invested",
                value = Formatters.formatInr(latest.totalReferenceValue),
                supportingText = "Baseline invested capital",
                icon = Icons.Outlined.Savings,
                valueColor = GraphiteTextPrimary,
                iconTint = GraphiteBlue,
                modifier = mod,
                testTag = "kpi_reference_value",
            )
        },
        { mod ->
            KpiCard(
                title = "Net Difference (P&L)",
                value = Formatters.formatSignedInr(netDiff),
                supportingText = "${Formatters.formatPercent(latest.returnPercent, signed = true)} overall return",
                icon = if (isProfit) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                valueColor = pnlColor,
                iconTint = pnlColor,
                modifier = mod,
                testTag = "kpi_net_difference",
            )
        },
        { mod ->
            KpiCard(
                title = "Total Stocks",
                value = "${latest.stocks.size}",
                supportingText = "Tap to view holdings",
                icon = Icons.Outlined.Layers,
                valueColor = GraphiteTextPrimary,
                iconTint = GraphiteBlue,
                modifier = mod,
                testTag = "kpi_total_stocks",
                onClick = onNavigateToHoldings,
            )
        },
        { mod ->
            KpiCard(
                title = "Last Updated Date",
                value = Formatters.formatDateLabel(latest.date),
                supportingText = "${latest.date} • Tap to inspect",
                icon = Icons.Outlined.CalendarToday,
                valueColor = GraphiteTextPrimary,
                iconTint = GraphiteBlue,
                modifier = mod,
                testTag = "kpi_last_updated",
                onClick = { onNavigateToSnapshot(latest.date) },
            )
        },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ResponsiveKpiGrid(
                columns = kpiColumns,
                items = kpiItems,
                balanceLastRow = true,
            )
        }

        // Chart 1: Portfolio Performance Trend
        item {
            val dateLabels = remember(snapshots) {
                snapshots.map { Formatters.formatShortAxisDate(it.date) }
            }
            val currentLineColor = GraphiteAccentViolet
            val refLineColor = GraphiteBlue
            val positiveColor = GraphiteGreen
            val negativeColor = GraphiteRed
            val performanceSeries = remember(
                snapshots,
                performanceMode,
                currentLineColor,
                refLineColor,
                positiveColor,
                negativeColor,
            ) {
                if (performanceMode == 0) {
                    listOf(
                        LineSeries(
                            name = "Current Value",
                            color = currentLineColor,
                            points = snapshots.map { LinePoint(it.totalCurrentValue) },
                            showDots = snapshots.size <= 15,
                        ),
                        LineSeries(
                            name = "Reference Value",
                            color = refLineColor,
                            points = snapshots.map { LinePoint(it.totalReferenceValue) },
                            dashed = true,
                        ),
                    )
                } else {
                    val lastNet = snapshots.lastOrNull()?.netDifference ?: 0.0
                    listOf(
                        LineSeries(
                            name = "Net P&L",
                            color = if (lastNet >= 0.0) positiveColor else negativeColor,
                            points = snapshots.map {
                                LinePoint(it.netDifference, highlighted = it.netDifference > 0)
                            },
                            showDots = true,
                        )
                    )
                }
            }

            GraphiteSectionCard(
                title = "Portfolio Performance Trend",
                subtitle = if (performanceMode == 0) {
                    "Current valuation vs reference capital"
                } else {
                    "Net unrealized profit & loss over time"
                },
                headerAction = {
                    SegmentedTextToggle(
                        options = listOf("Valuation", "Net P&L"),
                        selectedIndex = performanceMode,
                        onSelect = { performanceMode = it },
                    )
                },
            ) {
                StockLineChart(
                    xLabels = dateLabels,
                    series = performanceSeries,
                    yAxisFormatter = { Formatters.formatCompactInr(it) },
                    showZeroReferenceLine = performanceMode == 1,
                )
            }
        }

        // Chart 2: Allocation / Holdings Trajectory (Line-graphs only)
        item {
            val topCompanies = remember(latest) {
                latest.stocks
                    .sortedByDescending { it.currentValue }
                    .take(5)
                    .map { it.company }
            }

            GraphiteSectionCard(
                title = "Allocation / Holdings Trajectory",
                subtitle = if (trajectoryMode == 0) {
                    "Top 5 holdings valuation over time"
                } else {
                    "Current weight % vs invested weight % across top holdings"
                },
                headerAction = {
                    IconSegmentedToggle(
                        items = listOf(
                            Icons.Outlined.Timeline to "Over Time",
                            Icons.Outlined.Percent to "Weight Percentage",
                        ),
                        selectedIndex = trajectoryMode,
                        onSelect = { trajectoryMode = it },
                    )
                },
            ) {
                if (trajectoryMode == 0) {
                    val dateLabels = remember(snapshots) {
                        snapshots.map { Formatters.formatShortAxisDate(it.date) }
                    }
                    val palette = ChartPalette
                    val multiSeries = remember(snapshots, topCompanies, palette) {
                        topCompanies.mapIndexed { idx, company ->
                            LineSeries(
                                name = company,
                                color = palette[idx % palette.size],
                                points = snapshots.map { snap ->
                                    val valForCompany = snap.stocks
                                        .firstOrNull { it.company.equals(company, ignoreCase = true) }
                                        ?.currentValue ?: 0.0
                                    LinePoint(valForCompany)
                                },
                                showDots = snapshots.size <= 10,
                            )
                        }
                    }
                    StockLineChart(
                        xLabels = dateLabels,
                        series = multiSeries,
                        yAxisFormatter = { Formatters.formatCompactInr(it) },
                    )
                } else {
                    val sortedStocks = remember(latest) {
                        latest.stocks.sortedByDescending { it.currentValue }.take(10)
                    }
                    val totalCurrent = latest.totalCurrentValue.coerceAtLeast(1e-6)
                    val totalRef = latest.totalReferenceValue.coerceAtLeast(1e-6)
                    val labels = remember(sortedStocks) {
                        sortedStocks.map { it.company.take(8) }
                    }
                    val currentWeightColor = GraphiteAccentViolet
                    val investedWeightColor = GraphiteBlue
                    val weightSeries = remember(
                        sortedStocks,
                        totalCurrent,
                        totalRef,
                        currentWeightColor,
                        investedWeightColor,
                    ) {
                        listOf(
                            LineSeries(
                                name = "Current %",
                                color = currentWeightColor,
                                points = sortedStocks.map {
                                    LinePoint((it.currentValue / totalCurrent) * 100.0)
                                },
                                showDots = true,
                            ),
                            LineSeries(
                                name = "Invested %",
                                color = investedWeightColor,
                                points = sortedStocks.map {
                                    LinePoint((it.referenceValue / totalRef) * 100.0)
                                },
                                dashed = true,
                                showDots = true,
                            ),
                        )
                    }
                    StockLineChart(
                        xLabels = labels,
                        series = weightSeries,
                        yAxisFormatter = { Formatters.formatPercent(it, decimals = 1) },
                    )
                }
            }
        }
    }
}
