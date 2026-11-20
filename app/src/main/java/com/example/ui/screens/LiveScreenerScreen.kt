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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RatioValue
import com.example.data.model.ScreenerConnectionStatus
import com.example.data.model.ScreenerRecord
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.GraphiteSectionCard
import com.example.ui.components.KpiCard
import com.example.ui.components.LinePoint
import com.example.ui.components.LineSeries
import com.example.ui.components.ResponsiveKpiGrid
import com.example.ui.components.StatusBadge
import com.example.ui.components.StockLineChart
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteAmber
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteGreen
import com.example.ui.theme.GraphiteKpiCard
import com.example.ui.theme.GraphiteKpiDivider
import com.example.ui.theme.GraphiteNavSurface
import com.example.ui.theme.GraphiteOnAccent
import com.example.ui.theme.GraphiteRed
import com.example.ui.theme.GraphiteTextMuted
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.GraphiteTextSecondary
import com.example.ui.theme.MonoCodeTextStyle
import com.example.ui.theme.TabularNumberTextStyle
import com.example.ui.viewmodel.ScreenerUiState
import com.example.util.Formatters

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveScreenerScreen(
    uiState: ScreenerUiState,
    kpiColumns: Int,
    onCompanyInputChange: (String) -> Unit,
    onSelectPreset: (String) -> Unit,
    onSelectInterval: (Int) -> Unit,
    onFetchSnapshot: () -> Unit,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val (connLabel, connColor) = when (uiState.connectionStatus) {
        ScreenerConnectionStatus.CONNECTING -> "Connecting" to GraphiteAmber
        ScreenerConnectionStatus.CONNECTED -> "Connected" to GraphiteGreen
        ScreenerConnectionStatus.DISCONNECTED -> "Disconnected" to GraphiteTextSecondary
        ScreenerConnectionStatus.ERROR -> "Error" to GraphiteRed
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Company Selection & Stream Controls
        item {
            GraphiteSectionCard(
                title = "Live Stock Screener",
                subtitle = "Uppercase ticker code streaming over NDJSON",
                headerAction = {
                    StatusBadge(
                        label = connLabel,
                        color = connColor,
                        modifier = Modifier.testTag("screener_connection_badge"),
                    )
                },
            ) {
                OutlinedTextField(
                    value = uiState.companyInput,
                    onValueChange = onCompanyInputChange,
                    label = { Text("Company Code (e.g. TATAGOLD)", color = GraphiteTextSecondary) },
                    placeholder = { Text("RELIANCE", color = GraphiteTextMuted) },
                    isError = uiState.inputError != null,
                    supportingText = uiState.inputError?.let { err ->
                        { Text(err, color = GraphiteRed, fontSize = 12.sp) }
                    },
                    singleLine = true,
                    textStyle = MonoCodeTextStyle.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GraphiteTextPrimary,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            onFetchSnapshot()
                        },
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GraphiteAccentViolet,
                        unfocusedBorderColor = GraphiteBorder,
                        errorBorderColor = GraphiteRed,
                        focusedContainerColor = GraphiteNavSurface,
                        unfocusedContainerColor = GraphiteNavSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("screener_company_input"),
                )

                // Quick Preset Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ScreenerUiState.PRESETS.forEach { preset ->
                        val selected = uiState.activeCompanyCode == preset
                        FilterChip(
                            selected = selected,
                            onClick = {
                                keyboardController?.hide()
                                onSelectPreset(preset)
                            },
                            label = {
                                Text(
                                    text = preset,
                                    style = MonoCodeTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GraphiteAccentViolet,
                                selectedLabelColor = GraphiteOnAccent,
                                containerColor = GraphiteNavSurface,
                                labelColor = GraphiteTextSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = GraphiteBorder,
                                selectedBorderColor = GraphiteAccentViolet,
                            ),
                            modifier = Modifier.testTag("preset_$preset"),
                        )
                    }
                }

                // Polling Interval Selector (wraps cleanly on narrow screens)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Polling Interval",
                        color = GraphiteTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScreenerUiState.INTERVALS.forEach { sec ->
                            val selected = uiState.pollingIntervalSeconds == sec
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectInterval(sec) },
                                label = {
                                    Text(
                                        text = "${sec}s",
                                        style = TabularNumberTextStyle.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GraphiteAccentViolet.copy(alpha = 0.18f),
                                    selectedLabelColor = GraphiteAccentViolet,
                                    containerColor = GraphiteNavSurface,
                                    labelColor = GraphiteTextSecondary,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = GraphiteBorder,
                                    selectedBorderColor = GraphiteAccentViolet,
                                ),
                                modifier = Modifier.testTag("interval_${sec}s"),
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            keyboardController?.hide()
                            onFetchSnapshot()
                        },
                        enabled = !uiState.isFetchingSnapshot,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GraphiteBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("fetch_snapshot_button"),
                    ) {
                        if (uiState.isFetchingSnapshot) {
                            CircularProgressIndicator(
                                color = GraphiteAccentViolet,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                tint = GraphiteAccentViolet,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fetch Snapshot", color = GraphiteTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    if (uiState.isStreaming) {
                        Button(
                            onClick = onStopStreaming,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GraphiteRed,
                                contentColor = GraphiteOnAccent,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("stop_stream_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Stream", fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    } else {
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                onStartStreaming()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GraphiteAccentViolet,
                                contentColor = GraphiteOnAccent,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("start_stream_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Stream", fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Error Banner (if any)
        if (uiState.errorMessage != null) {
            item {
                ErrorStateBanner(
                    title = "Screener Error",
                    message = uiState.errorMessage,
                    onRetry = onFetchSnapshot,
                )
            }
        }

        // 2. Stock Overview Banner + 6 KPI Cards
        val snap = uiState.currentSnapshot
        if (snap != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GraphiteKpiCard,
                    border = BorderStroke(1.dp, GraphiteBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = snap.companyName,
                                style = MaterialTheme.typography.titleLarge,
                                color = GraphiteTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Last Updated: ${Formatters.formatIstTimestamp(snap.time)}",
                                style = TabularNumberTextStyle.copy(fontSize = 12.sp, color = GraphiteTextSecondary),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GraphiteAccentViolet.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, GraphiteAccentViolet.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = snap.companyCode,
                                style = MonoCodeTextStyle.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GraphiteAccentViolet,
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            item {
                val priceColor = if (snap.changed) GraphiteGreen else GraphiteTextPrimary
                val kpis: List<@Composable (Modifier) -> Unit> = listOf(
                    { mod ->
                        KpiCard(
                            title = "Current Price",
                            value = snap.price?.let { Formatters.formatInr(it) } ?: "—",
                            supportingText = if (snap.changed) "Price changed on tick" else "Latest observed price",
                            icon = Icons.Outlined.AttachMoney,
                            valueColor = priceColor,
                            iconTint = if (snap.changed) GraphiteGreen else GraphiteAccentViolet,
                            modifier = mod,
                            testTag = "screener_kpi_price",
                        )
                    },
                    { mod ->
                        KpiCard(
                            title = "Market Cap",
                            value = snap.marketCap?.let { "₹${Formatters.formatIndianNumber(it, 0)} Cr" } ?: "—",
                            supportingText = "Market capitalization",
                            icon = Icons.Outlined.AccountBalance,
                            iconTint = GraphiteTextSecondary,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        KpiCard(
                            title = "Stock P/E",
                            value = snap.stockPE?.let { Formatters.formatIndianNumber(it, 2) } ?: "—",
                            supportingText = "Price-to-earnings ratio",
                            icon = Icons.AutoMirrored.Outlined.ShowChart,
                            iconTint = GraphiteAccentViolet,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        KpiCard(
                            title = "High",
                            value = snap.high?.let { Formatters.formatInr(it) } ?: "—",
                            supportingText = "52-week / range high",
                            icon = Icons.Outlined.ArrowUpward,
                            iconTint = GraphiteGreen,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        KpiCard(
                            title = "Low",
                            value = snap.low?.let { Formatters.formatInr(it) } ?: "—",
                            supportingText = "52-week / range low",
                            icon = Icons.Outlined.ArrowDownward,
                            iconTint = GraphiteRed,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        KpiCard(
                            title = "Last Updated",
                            value = Formatters.formatIstShortTime(snap.time),
                            supportingText = Formatters.formatIstTimestamp(snap.time),
                            icon = Icons.Outlined.Schedule,
                            iconTint = GraphiteTextSecondary,
                            modifier = mod,
                        )
                    },
                )
                ResponsiveKpiGrid(columns = kpiColumns.coerceAtMost(3), items = kpis)
            }
        }

        // 3. Price History Line Chart
        item {
            val xLabels = remember(uiState.priceHistory) {
                uiState.priceHistory.map { Formatters.formatIstShortTime(it.time) }
            }
            val lineColor = GraphiteAccentViolet
            val priceSeries = remember(uiState.priceHistory, lineColor) {
                if (uiState.priceHistory.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        LineSeries(
                            name = "${uiState.activeCompanyCode} Price",
                            color = lineColor,
                            points = uiState.priceHistory.mapNotNull { rec ->
                                rec.price?.let { LinePoint(value = it, highlighted = rec.changed) }
                            },
                            showDots = true,
                        )
                    )
                }
            }
            GraphiteSectionCard(
                title = "Price History Line Chart",
                subtitle = "Deduplicated price points • Green nodes highlight changed ticks",
            ) {
                StockLineChart(
                    xLabels = xLabels,
                    series = priceSeries,
                    yAxisFormatter = { Formatters.formatInr(it, decimals = 2) },
                    emptyTitle = "No price history yet",
                    emptySubtitle = "Fetch a snapshot or start streaming to plot live price points.",
                )
            }
        }

        // 4. Dynamic Financial Ratios Table
        if (snap != null && snap.ratios.isNotEmpty()) {
            item {
                GraphiteSectionCard(
                    title = "Financial Ratios",
                    subtitle = "Dynamically rendered from Screener ratios payload",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val entries = snap.ratios.entries.toList()
                        entries.forEachIndexed { idx, (key, value) ->
                            DynamicRatioRow(name = key, ratioValue = value)
                            if (idx < entries.lastIndex) {
                                HorizontalDivider(thickness = 1.dp, color = GraphiteKpiDivider)
                            }
                        }
                    }
                }
            }
        }

        // 5. Streamed Price Records List
        item {
            GraphiteSectionCard(
                title = "Streamed Price Records (${uiState.streamedRecords.size})",
                subtitle = "Reverse-chronological live NDJSON ticks (session memory only)",
            ) {
                if (uiState.streamedRecords.isEmpty()) {
                    Text(
                        text = "No streamed ticks recorded yet. Tap Start Stream to receive live updates every ${uiState.pollingIntervalSeconds}s.",
                        color = GraphiteTextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        if (uiState.streamedRecords.isNotEmpty()) {
            items(
                items = uiState.streamedRecords,
                key = { "${it.time}_${it.price}_${it.changed}_${it.hashCode()}" },
            ) { record ->
                StreamedRecordRow(record = record)
            }
        }
    }
}

@Composable
private fun DynamicRatioRow(
    name: String,
    ratioValue: RatioValue,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = GraphiteTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        when (ratioValue) {
            is RatioValue.NullVal -> {
                Text(
                    text = "—",
                    style = TabularNumberTextStyle.copy(fontSize = 13.sp, color = GraphiteTextMuted),
                )
            }
            is RatioValue.NumberVal -> {
                Text(
                    text = Formatters.formatIndianNumber(ratioValue.value, maxDecimals = 2),
                    style = TabularNumberTextStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GraphiteTextPrimary,
                    ),
                )
            }
            is RatioValue.TextVal -> {
                Text(
                    text = ratioValue.value,
                    style = TabularNumberTextStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GraphiteTextPrimary,
                    ),
                )
            }
            is RatioValue.RangeVal -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "High: ${ratioValue.high?.let { Formatters.formatIndianNumber(it, 2) } ?: "—"}",
                        style = TabularNumberTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GraphiteGreen,
                        ),
                    )
                    Text(
                        text = "Low: ${ratioValue.low?.let { Formatters.formatIndianNumber(it, 2) } ?: "—"}",
                        style = TabularNumberTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GraphiteRed,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamedRecordRow(record: ScreenerRecord) {
    val highlight = record.changed
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlight) GraphiteGreen.copy(alpha = 0.10f) else GraphiteKpiCard,
        border = BorderStroke(
            1.dp,
            if (highlight) GraphiteGreen.copy(alpha = 0.35f) else GraphiteBorder,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = Formatters.formatIstTimestamp(record.time),
                    style = TabularNumberTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (highlight) GraphiteGreen else GraphiteTextPrimary,
                    ),
                )
                Text(
                    text = if (highlight) "CHANGED TICK" else "UNCHANGED",
                    style = MonoCodeTextStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (highlight) GraphiteGreen else GraphiteTextSecondary,
                    ),
                )
            }
            Text(
                text = record.price?.let { Formatters.formatInr(it) } ?: "—",
                style = TabularNumberTextStyle.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (highlight) GraphiteGreen else GraphiteTextPrimary,
                ),
            )
        }
    }
}
