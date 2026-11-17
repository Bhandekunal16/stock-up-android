package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.StockItem
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.FullScreenErrorState
import com.example.ui.components.GraphiteSectionCard
import com.example.ui.components.KpiCard
import com.example.ui.components.LinePoint
import com.example.ui.components.LineSeries
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.ResponsiveKpiGrid
import com.example.ui.components.StatusBadge
import com.example.ui.components.StockLineChart
import com.example.ui.theme.ChartPalette
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteBlue
import com.example.ui.theme.GraphiteGreen
import com.example.ui.theme.GraphiteKpiDivider
import com.example.ui.theme.GraphiteNavSurface
import com.example.ui.theme.GraphiteRed
import com.example.ui.theme.GraphiteTextMuted
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.TabularNumberTextStyle
import com.example.ui.viewmodel.PortfolioLoadState
import com.example.ui.viewmodel.PortfolioUiState
import com.example.util.Formatters

@Composable
fun AnalyticsScreen(
    uiState: PortfolioUiState,
    kpiColumns: Int,
    onRetry: () -> Unit,
    onNavigateToUpload: () -> Unit,
) {
    when (val load = uiState.loadState) {
        is PortfolioLoadState.Loading -> LoadingStateBox("Computing portfolio analytics...")
        is PortfolioLoadState.Error -> FullScreenErrorState(load.message, onRetry = onRetry)
        is PortfolioLoadState.Success -> {
            val snapshots = load.snapshots
            val latest = snapshots.lastOrNull()
            if (latest == null || latest.stocks.isEmpty()) {
                EmptyStateBox(
                    title = "No analytics data yet",
                    subtitle = "Upload at least one portfolio snapshot to unlock return rankings and line-graph trajectories.",
                    icon = Icons.Outlined.CloudUpload,
                    actionLabel = "Upload Portfolio",
                    onAction = onNavigateToUpload,
                )
            } else {
                AnalyticsContent(
                    snapshots = snapshots,
                    latest = latest,
                    kpiColumns = kpiColumns,
                )
            }
        }
    }
}

@Composable
private fun AnalyticsContent(
    snapshots: List<PortfolioSnapshot>,
    latest: PortfolioSnapshot,
    kpiColumns: Int,
) {
    val bestStock = remember(latest) { latest.stocks.maxByOrNull { it.returnPercent } }
    val worstStock = remember(latest) { latest.stocks.minByOrNull { it.returnPercent } }
    val avgReturn = remember(latest) {
        if (latest.stocks.isEmpty()) 0.0 else latest.stocks.map { it.returnPercent }.average()
    }
    val top3Weight = remember(latest) {
        val total = latest.totalCurrentValue.coerceAtLeast(1e-6)
        (latest.stocks.sortedByDescending { it.currentValue }.take(3).sumOf { it.currentValue } / total) * 100.0
    }

    val topGainers = remember(latest) {
        latest.stocks.filter { it.netDifference >= 0.0 }.sortedByDescending { it.returnPercent }.take(5)
    }
    val underperformers = remember(latest) {
        latest.stocks.filter { it.netDifference < 0.0 }.sortedBy { it.returnPercent }.take(5)
    }

    val kpis: List<@Composable (Modifier) -> Unit> = listOf(
        { mod ->
            KpiCard(
                title = "Best Performer",
                value = bestStock?.let { Formatters.formatPercent(it.returnPercent, signed = true) } ?: "—",
                supportingText = bestStock?.company ?: "No holdings",
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                valueColor = GraphiteGreen,
                iconTint = GraphiteGreen,
                modifier = mod,
            )
        },
        { mod ->
            val worstColor = if ((worstStock?.returnPercent ?: 0.0) < 0) GraphiteRed else GraphiteTextPrimary
            KpiCard(
                title = "Worst Performer",
                value = worstStock?.let { Formatters.formatPercent(it.returnPercent, signed = true) } ?: "—",
                supportingText = worstStock?.company ?: "No holdings",
                icon = Icons.AutoMirrored.Outlined.TrendingDown,
                valueColor = worstColor,
                iconTint = worstColor,
                modifier = mod,
            )
        },
        { mod ->
            val avgColor = if (avgReturn >= 0.0) GraphiteGreen else GraphiteRed
            KpiCard(
                title = "Average Return",
                value = Formatters.formatPercent(avgReturn, signed = true),
                supportingText = "Mean across ${latest.stocks.size} holdings",
                icon = Icons.Outlined.Percent,
                valueColor = avgColor,
                iconTint = avgColor,
                modifier = mod,
            )
        },
        { mod ->
            KpiCard(
                title = "Top-3 Concentration",
                value = Formatters.formatPercent(top3Weight, decimals = 1),
                supportingText = "Weight of top 3 positions",
                icon = Icons.Outlined.PieChartOutline,
                iconTint = GraphiteAccentViolet,
                modifier = mod,
            )
        },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ResponsiveKpiGrid(columns = kpiColumns.coerceAtMost(2), items = kpis)
        }

        // 1. Historical Growth Line Chart
        item {
            val dateLabels = remember(snapshots) {
                snapshots.map { Formatters.formatShortAxisDate(it.date) }
            }
            val currentValColor = GraphiteAccentViolet
            val refValColor = GraphiteBlue
            val growthSeries = remember(snapshots, currentValColor, refValColor) {
                listOf(
                    LineSeries(
                        name = "Portfolio Value",
                        color = currentValColor,
                        points = snapshots.map { LinePoint(it.totalCurrentValue) },
                        showDots = true,
                    ),
                    LineSeries(
                        name = "Invested Capital",
                        color = refValColor,
                        points = snapshots.map { LinePoint(it.totalReferenceValue) },
                        dashed = true,
                    ),
                )
            }
            GraphiteSectionCard(
                title = "Historical Growth Line Chart",
                subtitle = "Portfolio value growth against reference capital across snapshots",
            ) {
                StockLineChart(
                    xLabels = dateLabels,
                    series = growthSeries,
                    yAxisFormatter = { Formatters.formatCompactInr(it) },
                )
            }
        }

        // 2. Current vs Reference Line Chart (Holdings comparison)
        item {
            val rankedStocks = remember(latest) {
                latest.stocks.sortedByDescending { it.currentValue }.take(10)
            }
            val stockLabels = remember(rankedStocks) {
                rankedStocks.map { it.company.take(8) }
            }
            val currentValColor = GraphiteAccentViolet
            val refValColor = GraphiteBlue
            val comparisonSeries = remember(rankedStocks, currentValColor, refValColor) {
                listOf(
                    LineSeries(
                        name = "Current Value",
                        color = currentValColor,
                        points = rankedStocks.map { LinePoint(it.currentValue) },
                        showDots = true,
                    ),
                    LineSeries(
                        name = "Reference Value",
                        color = refValColor,
                        points = rankedStocks.map { LinePoint(it.referenceValue) },
                        dashed = true,
                        showDots = true,
                    ),
                )
            }
            GraphiteSectionCard(
                title = "Current vs Reference Line Chart",
                subtitle = "Per-holding valuation vs cost basis (top ${rankedStocks.size} holdings)",
            ) {
                StockLineChart(
                    xLabels = stockLabels,
                    series = comparisonSeries,
                    yAxisFormatter = { Formatters.formatCompactInr(it) },
                )
            }
        }

        // 3. Allocation Trajectory Line Chart
        item {
            val dateLabels = remember(snapshots) {
                snapshots.map { Formatters.formatShortAxisDate(it.date) }
            }
            val topCompanies = remember(latest) {
                latest.stocks.sortedByDescending { it.currentValue }.take(5).map { it.company }
            }
            val palette = ChartPalette
            val allocationSeries = remember(snapshots, topCompanies, palette) {
                topCompanies.mapIndexed { idx, company ->
                    LineSeries(
                        name = company,
                        color = palette[idx % palette.size],
                        points = snapshots.map { snap ->
                            val total = snap.totalCurrentValue.coerceAtLeast(1e-6)
                            val valForStock = snap.stocks
                                .firstOrNull { it.company.equals(company, ignoreCase = true) }
                                ?.currentValue ?: 0.0
                            LinePoint((valForStock / total) * 100.0)
                        },
                        showDots = snapshots.size <= 12,
                    )
                }
            }
            GraphiteSectionCard(
                title = "Allocation Trajectory (%)",
                subtitle = "Portfolio weight percentage shift of top 5 holdings over time",
            ) {
                StockLineChart(
                    xLabels = dateLabels,
                    series = allocationSeries,
                    yAxisFormatter = { Formatters.formatPercent(it, decimals = 1) },
                )
            }
        }

        // 4. Top Gainers
        item {
            AnalyticsRankingsCard(
                title = "Top Gainers",
                subtitle = "Highest positive return holdings in latest snapshot",
                stocks = topGainers,
                isPositive = true,
                emptyText = "No stocks with positive net return in the latest snapshot.",
            )
        }

        // 5. Underperforming Stocks
        item {
            AnalyticsRankingsCard(
                title = "Underperforming Stocks",
                subtitle = "Holdings trading below reference cost basis",
                stocks = underperformers,
                isPositive = false,
                emptyText = "All holdings are currently at or above reference value.",
            )
        }
    }
}

@Composable
private fun AnalyticsRankingsCard(
    title: String,
    subtitle: String,
    stocks: List<StockItem>,
    isPositive: Boolean,
    emptyText: String,
) {
    val badgeColor = if (isPositive) GraphiteGreen else GraphiteRed
    GraphiteSectionCard(title = title, subtitle = subtitle) {
        if (stocks.isEmpty()) {
            Text(text = emptyText, color = GraphiteTextMuted, fontSize = 13.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stocks.forEachIndexed { idx, stock ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GraphiteNavSurface,
                        border = BorderStroke(1.dp, GraphiteKpiDivider),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(end = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "${idx + 1}. ${stock.company}",
                                    color = GraphiteTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Current: ${Formatters.formatInr(stock.currentValue)} • P&L: ${Formatters.formatSignedInr(stock.netDifference)}",
                                    style = TabularNumberTextStyle.copy(fontSize = 11.sp, color = GraphiteTextMuted),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            StatusBadge(
                                label = Formatters.formatPercent(stock.returnPercent, signed = true),
                                color = badgeColor,
                                showDot = false,
                            )
                        }
                    }
                    if (idx < stocks.lastIndex) {
                        HorizontalDivider(thickness = 0.dp, color = GraphiteKpiDivider.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}
