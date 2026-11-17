package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.StockItem
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.FullScreenErrorState
import com.example.ui.components.KpiCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.ResponsiveKpiGrid
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteBlue
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteGreen
import com.example.ui.theme.GraphiteKpiCard
import com.example.ui.theme.GraphiteKpiDivider
import com.example.ui.theme.GraphiteNavSurface
import com.example.ui.theme.GraphiteOnAccent
import com.example.ui.theme.GraphiteRed
import com.example.ui.theme.GraphiteTextMuted
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.TabularNumberTextStyle
import com.example.ui.viewmodel.PortfolioLoadState
import com.example.ui.viewmodel.PortfolioUiState
import com.example.util.Formatters

enum class HoldingSortField(val label: String) {
    CURRENT_VALUE("Current"),
    PNL_AMOUNT("P&L ₹"),
    RETURN_PCT("Return %"),
    WEIGHT_PCT("Weight %"),
    NAME("Name"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortfolioHoldingsScreen(
    uiState: PortfolioUiState,
    kpiColumns: Int,
    onRetry: () -> Unit,
    onNavigateToUpload: () -> Unit,
) {
    when (val load = uiState.loadState) {
        is PortfolioLoadState.Loading -> LoadingStateBox("Loading holdings...")
        is PortfolioLoadState.Error -> FullScreenErrorState(load.message, onRetry = onRetry)
        is PortfolioLoadState.Success -> {
            val latest = load.snapshots.lastOrNull()
            if (latest == null || latest.stocks.isEmpty()) {
                EmptyStateBox(
                    title = "No holdings found",
                    subtitle = "Upload a portfolio screenshot to view individual stock holdings, weights, and returns.",
                    icon = Icons.Outlined.CloudUpload,
                    actionLabel = "Upload Portfolio",
                    onAction = onNavigateToUpload,
                )
            } else {
                HoldingsContent(latest = latest, kpiColumns = kpiColumns)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HoldingsContent(
    latest: PortfolioSnapshot,
    kpiColumns: Int,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortField by rememberSaveable { mutableStateOf(HoldingSortField.CURRENT_VALUE) }
    var sortDescending by rememberSaveable { mutableStateOf(true) }

    val totalCurrent = latest.totalCurrentValue.coerceAtLeast(1e-6)
    val netDiff = latest.netDifference
    val pnlColor = if (netDiff >= 0.0) GraphiteGreen else GraphiteRed

    val filteredSorted = remember(latest.stocks, searchQuery, sortField, sortDescending) {
        val q = searchQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            latest.stocks
        } else {
            latest.stocks.filter { it.company.lowercase().contains(q) }
        }
        val comparator = when (sortField) {
            HoldingSortField.CURRENT_VALUE -> compareBy<StockItem> { it.currentValue }
            HoldingSortField.PNL_AMOUNT -> compareBy { it.netDifference }
            HoldingSortField.RETURN_PCT -> compareBy { it.returnPercent }
            HoldingSortField.WEIGHT_PCT -> compareBy { it.currentValue / totalCurrent }
            HoldingSortField.NAME -> compareBy { it.company.lowercase() }
        }
        if (sortDescending) filtered.sortedWith(comparator.reversed()) else filtered.sortedWith(comparator)
    }

    val kpis: List<@Composable (Modifier) -> Unit> = listOf(
        { mod ->
            KpiCard(
                title = "Total Current Value",
                value = Formatters.formatInr(latest.totalCurrentValue),
                supportingText = "${latest.stocks.size} holdings • ${latest.date}",
                icon = Icons.Outlined.AccountBalanceWallet,
                iconTint = GraphiteAccentViolet,
                modifier = mod,
            )
        },
        { mod ->
            KpiCard(
                title = "Reference Capital",
                value = Formatters.formatInr(latest.totalReferenceValue),
                supportingText = "Total invested baseline",
                icon = Icons.Outlined.Savings,
                iconTint = GraphiteBlue,
                modifier = mod,
            )
        },
        { mod ->
            KpiCard(
                title = "Net Profit / Loss",
                value = Formatters.formatSignedInr(netDiff),
                supportingText = if (netDiff >= 0) "Net unrealized gain" else "Net unrealized drawdown",
                icon = if (netDiff >= 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                valueColor = pnlColor,
                iconTint = pnlColor,
                modifier = mod,
            )
        },
        { mod ->
            KpiCard(
                title = "Total Return %",
                value = Formatters.formatPercent(latest.returnPercent, signed = true),
                supportingText = "Portfolio-wide return",
                icon = Icons.Outlined.Percent,
                valueColor = pnlColor,
                iconTint = pnlColor,
                modifier = mod,
            )
        },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ResponsiveKpiGrid(columns = kpiColumns.coerceAtMost(2), items = kpis)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search holdings by company...", color = GraphiteTextMuted) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = "Search", tint = GraphiteTextMuted)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GraphiteAccentViolet,
                        unfocusedBorderColor = GraphiteBorder,
                        focusedContainerColor = GraphiteNavSurface,
                        unfocusedContainerColor = GraphiteNavSurface,
                        focusedTextColor = GraphiteTextPrimary,
                        unfocusedTextColor = GraphiteTextPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("holdings_search_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HoldingSortField.entries.forEach { field ->
                            val selected = sortField == field
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        sortDescending = !sortDescending
                                    } else {
                                        sortField = field
                                        sortDescending = field != HoldingSortField.NAME
                                    }
                                },
                                label = { Text(field.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GraphiteAccentViolet,
                                    selectedLabelColor = GraphiteOnAccent,
                                    containerColor = GraphiteKpiCard,
                                    labelColor = GraphiteTextMuted,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = GraphiteBorder,
                                    selectedBorderColor = GraphiteAccentViolet,
                                ),
                            )
                        }
                    }

                    IconButton(
                        onClick = { sortDescending = !sortDescending },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("holdings_sort_direction"),
                    ) {
                        Icon(
                            imageVector = if (sortDescending) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                            contentDescription = if (sortDescending) "Descending" else "Ascending",
                            tint = GraphiteAccentViolet,
                        )
                    }
                }
            }
        }

        if (filteredSorted.isEmpty()) {
            item {
                EmptyStateBox(
                    title = "No matching holdings",
                    subtitle = "No stock matches \"$searchQuery\".",
                    icon = Icons.Outlined.Layers,
                )
            }
        } else {
            items(filteredSorted, key = { "${it.company}_${it.shares}" }) { stock ->
                val weightPct = (stock.currentValue / totalCurrent) * 100.0
                HoldingItemCard(stock = stock, weightPercent = weightPct)
            }
        }
    }
}

@Composable
fun HoldingItemCard(
    stock: StockItem,
    weightPercent: Double,
) {
    val pnl = stock.netDifference
    val pnlColor = if (pnl >= 0.0) GraphiteGreen else GraphiteRed

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GraphiteKpiCard,
        border = BorderStroke(1.dp, GraphiteKpiDivider),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = stock.company,
                        color = GraphiteTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "${Formatters.formatIndianNumber(stock.shares, maxDecimals = 4)} shares",
                            style = TabularNumberTextStyle.copy(fontSize = 12.sp, color = GraphiteTextMuted),
                        )
                        Text(
                            text = "• Weight ${Formatters.formatPercent(weightPercent, decimals = 1)}",
                            style = TabularNumberTextStyle.copy(fontSize = 12.sp, color = GraphiteAccentViolet),
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatInr(stock.currentValue),
                        style = TabularNumberTextStyle.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GraphiteTextPrimary,
                        ),
                    )
                    Text(
                        text = "${Formatters.formatSignedInr(pnl)} (${Formatters.formatPercent(stock.returnPercent, signed = true)})",
                        style = TabularNumberTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = pnlColor,
                        ),
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = GraphiteKpiDivider)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Reference: ${Formatters.formatInr(stock.referenceValue)}",
                    style = TabularNumberTextStyle.copy(fontSize = 11.sp, color = GraphiteTextMuted),
                )
                val perShare = if (stock.shares > 0.0) stock.currentValue / stock.shares else 0.0
                if (perShare > 0.0) {
                    Text(
                        text = "Avg Value/Sh: ${Formatters.formatInr(perShare)}",
                        style = TabularNumberTextStyle.copy(fontSize = 11.sp, color = GraphiteTextMuted),
                    )
                }
            }
        }
    }
}
