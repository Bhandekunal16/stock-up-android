package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PortfolioSnapshot
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.FullScreenErrorState
import com.example.ui.components.KpiCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.ResponsiveKpiGrid
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteGreen
import com.example.ui.theme.GraphiteRed
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.GraphiteTextSecondary
import com.example.ui.viewmodel.SnapshotDetailState
import com.example.util.Formatters

@Composable
fun SnapshotDetailScreen(
    date: String,
    detailState: SnapshotDetailState,
    kpiColumns: Int,
    onLoadSnapshot: (String) -> Unit,
    onBack: () -> Unit,
    onExportSnapshotCsv: (PortfolioSnapshot) -> Unit,
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(date) {
        onLoadSnapshot(date)
    }

    when (detailState) {
        is SnapshotDetailState.Idle,
        is SnapshotDetailState.Loading -> {
            LoadingStateBox(message = "Loading snapshot for $date...")
        }
        is SnapshotDetailState.Error -> {
            FullScreenErrorState(
                message = detailState.message,
                onRetry = { onLoadSnapshot(date) },
            )
        }
        is SnapshotDetailState.Success -> {
            val snapshot = detailState.snapshot
            if (snapshot.stocks.isEmpty()) {
                EmptyStateBox(
                    title = "No holdings on $date",
                    subtitle = "This snapshot does not contain any stock records.",
                    icon = Icons.Outlined.Layers,
                    actionLabel = "Go Back",
                    onAction = onBack,
                )
            } else {
                val totalCurrent = snapshot.totalCurrentValue.coerceAtLeast(1e-6)
                val pnl = snapshot.netDifference
                val pnlColor = if (pnl >= 0.0) GraphiteGreen else GraphiteRed

                val kpis: List<@Composable (Modifier) -> Unit> = listOf(
                    { mod ->
                        KpiCard(
                            title = "Snapshot Current",
                            value = Formatters.formatInr(snapshot.totalCurrentValue),
                            supportingText = Formatters.formatDateLabel(snapshot.date),
                            icon = Icons.Outlined.AccountBalanceWallet,
                            iconTint = GraphiteAccentViolet,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        KpiCard(
                            title = "Reference Value",
                            value = Formatters.formatInr(snapshot.totalReferenceValue),
                            supportingText = "Invested capital baseline",
                            icon = Icons.Outlined.Savings,
                            iconTint = GraphiteTextSecondary,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        KpiCard(
                            title = "Snapshot Net P&L",
                            value = Formatters.formatSignedInr(pnl),
                            supportingText = "${Formatters.formatPercent(snapshot.returnPercent, signed = true)} return",
                            icon = if (pnl >= 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                            valueColor = pnlColor,
                            iconTint = pnlColor,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        KpiCard(
                            title = "Holdings Count",
                            value = "${snapshot.stocks.size}",
                            supportingText = "Stocks on ${snapshot.date}",
                            icon = Icons.Outlined.Layers,
                            iconTint = GraphiteAccentViolet,
                            modifier = mod,
                        )
                    },
                )

                val sortedStocks = snapshot.stocks.sortedByDescending { it.currentValue }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ResponsiveKpiGrid(columns = kpiColumns.coerceAtMost(2), items = kpis)
                    }
                    item {
                        OutlinedButton(
                            onClick = { onExportSnapshotCsv(snapshot) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GraphiteBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("snapshot_export_csv_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = "Export snapshot CSV",
                                tint = GraphiteAccentViolet,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export Snapshot CSV (${snapshot.date})",
                                color = GraphiteTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    items(sortedStocks, key = { "${it.company}_${it.shares}" }) { stock ->
                        val weight = (stock.currentValue / totalCurrent) * 100.0
                        HoldingItemCard(stock = stock, weightPercent = weight)
                    }
                }
            }
        }
    }
}
