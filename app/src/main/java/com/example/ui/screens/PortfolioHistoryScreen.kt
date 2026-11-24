package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.FullScreenErrorState
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusBadge
import com.example.ui.theme.GraphiteAccentViolet
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
import com.example.ui.theme.GraphiteTextSecondary
import com.example.ui.theme.TabularNumberTextStyle
import com.example.ui.viewmodel.PortfolioLoadState
import com.example.ui.viewmodel.PortfolioUiState
import com.example.util.Formatters

@Composable
fun PortfolioHistoryScreen(
    uiState: PortfolioUiState,
    onRetry: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onSelectSnapshot: (String) -> Unit,
    onExportCsv: () -> Unit,
) {
    when (val load = uiState.loadState) {
        is PortfolioLoadState.Loading -> LoadingStateBox("Loading snapshot history...")
        is PortfolioLoadState.Error -> FullScreenErrorState(load.message, onRetry = onRetry)
        is PortfolioLoadState.Success -> {
            val snapshotsNewestFirst = remember(load.snapshots) { load.snapshots.reversed() }
            if (snapshotsNewestFirst.isEmpty()) {
                EmptyStateBox(
                    title = "No snapshot history",
                    subtitle = "Snapshots appear chronologically once you upload portfolio screenshots.",
                    icon = Icons.Outlined.CloudUpload,
                    actionLabel = "Upload Portfolio",
                    onAction = onNavigateToUpload,
                )
            } else {
                HistoryContent(
                    snapshotsNewestFirst = snapshotsNewestFirst,
                    onSelectSnapshot = onSelectSnapshot,
                    onExportCsv = onExportCsv,
                )
            }
        }
    }
}

@Composable
private fun HistoryContent(
    snapshotsNewestFirst: List<PortfolioSnapshot>,
    onSelectSnapshot: (String) -> Unit,
    onExportCsv: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCompareDialog by rememberSaveable { mutableStateOf(false) }

    val filtered = remember(snapshotsNewestFirst, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            snapshotsNewestFirst
        } else {
            snapshotsNewestFirst.filter { snap ->
                snap.date.lowercase().contains(q) ||
                    Formatters.formatDateLabel(snap.date).lowercase().contains(q) ||
                    snap.stocks.any { it.company.lowercase().contains(q) }
            }
        }
    }

    if (showCompareDialog && snapshotsNewestFirst.size >= 2) {
        CompareSnapshotsDialog(
            snapshots = snapshotsNewestFirst,
            onDismiss = { showCompareDialog = false },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compactWidth = maxWidth < 460.dp
                if (compactWidth) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HistorySearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (snapshotsNewestFirst.size >= 2) {
                                Button(
                                    onClick = { showCompareDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GraphiteAccentViolet,
                                        contentColor = GraphiteOnAccent,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("compare_snapshots_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                                        contentDescription = "Compare snapshots",
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compare", fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                            OutlinedButton(
                                onClick = onExportCsv,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, GraphiteBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("history_export_csv_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FileDownload,
                                    contentDescription = "Export CSV",
                                    tint = GraphiteAccentViolet,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export CSV", color = GraphiteTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HistorySearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                        )

                        if (snapshotsNewestFirst.size >= 2) {
                            Button(
                                onClick = { showCompareDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GraphiteAccentViolet,
                                    contentColor = GraphiteOnAccent,
                                ),
                                modifier = Modifier
                                    .heightIn(min = 52.dp)
                                    .testTag("compare_snapshots_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                                    contentDescription = "Compare snapshots",
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compare", fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = onExportCsv,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GraphiteBorder),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag("history_export_csv_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = "Export CSV",
                                tint = GraphiteAccentViolet,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV", color = GraphiteTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateBox(
                    title = "No matching snapshots",
                    subtitle = "Try another date (YYYY-MM-DD) or company name.",
                    icon = Icons.Outlined.History,
                )
            }
        } else {
            items(filtered, key = { it.date }) { snapshot ->
                SnapshotHistoryRowCard(
                    snapshot = snapshot,
                    onClick = { onSelectSnapshot(snapshot.date) },
                )
            }
        }
    }
}

@Composable
private fun HistorySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Search date or stock...", color = GraphiteTextMuted) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = "Search", tint = GraphiteTextSecondary)
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
        modifier = modifier.testTag("history_search_input"),
    )
}

@Composable
private fun SnapshotHistoryRowCard(
    snapshot: PortfolioSnapshot,
    onClick: () -> Unit,
) {
    val pnl = snapshot.netDifference
    val pnlColor = if (pnl >= 0.0) GraphiteGreen else GraphiteRed

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = GraphiteKpiCard,
        border = BorderStroke(1.dp, GraphiteBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("snapshot_card_${snapshot.date}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = GraphiteAccentViolet,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = Formatters.formatDateLabel(snapshot.date),
                            color = GraphiteTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${snapshot.date} • ${snapshot.stocks.size} holdings",
                            style = TabularNumberTextStyle.copy(fontSize = 12.sp, color = GraphiteTextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(
                        label = Formatters.formatPercent(snapshot.returnPercent, signed = true),
                        color = pnlColor,
                        showDot = false,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Inspect snapshot",
                        tint = GraphiteTextSecondary,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = GraphiteKpiDivider)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Current", color = GraphiteTextSecondary, fontSize = 12.sp)
                    Text(
                        text = Formatters.formatInr(snapshot.totalCurrentValue),
                        style = TabularNumberTextStyle.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GraphiteTextPrimary,
                        ),
                        maxLines = 1,
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Reference", color = GraphiteTextSecondary, fontSize = 12.sp)
                    Text(
                        text = Formatters.formatInr(snapshot.totalReferenceValue),
                        style = TabularNumberTextStyle.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = GraphiteTextPrimary,
                        ),
                        maxLines = 1,
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Net P&L", color = GraphiteTextSecondary, fontSize = 12.sp)
                    Text(
                        text = Formatters.formatSignedInr(pnl),
                        style = TabularNumberTextStyle.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = pnlColor,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompareSnapshotsDialog(
    snapshots: List<PortfolioSnapshot>,
    onDismiss: () -> Unit,
) {
    var baseIdx by remember { mutableStateOf(1.coerceAtMost(snapshots.lastIndex)) }
    var targetIdx by remember { mutableStateOf(0) }

    val baseSnap = snapshots[baseIdx]
    val targetSnap = snapshots[targetIdx]

    val currentDelta = targetSnap.totalCurrentValue - baseSnap.totalCurrentValue
    val refDelta = targetSnap.totalReferenceValue - baseSnap.totalReferenceValue
    val pnlDelta = targetSnap.netDifference - baseSnap.netDifference
    val pctDelta = if (baseSnap.totalCurrentValue > 0.0) {
        (currentDelta / baseSnap.totalCurrentValue) * 100.0
    } else 0.0

    val allCompanies = remember(baseSnap, targetSnap) {
        (baseSnap.stocks.map { it.company } + targetSnap.stocks.map { it.company }).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GraphiteKpiCard,
        titleContentColor = GraphiteTextPrimary,
        title = { Text("Compare Snapshots", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SnapshotDateSelector(
                    label = "Baseline Date (A)",
                    snapshots = snapshots,
                    selectedIndex = baseIdx,
                    onSelect = { baseIdx = it },
                )
                SnapshotDateSelector(
                    label = "Comparison Date (B)",
                    snapshots = snapshots,
                    selectedIndex = targetIdx,
                    onSelect = { targetIdx = it },
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GraphiteNavSurface,
                    border = BorderStroke(1.dp, GraphiteBorder),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompareDeltaRow(
                            label = "Current Value Delta",
                            value = "${Formatters.formatSignedInr(currentDelta)} (${Formatters.formatPercent(pctDelta, signed = true)})",
                            isPositive = currentDelta >= 0.0,
                        )
                        CompareDeltaRow(
                            label = "Reference Capital Delta",
                            value = Formatters.formatSignedInr(refDelta),
                            isPositive = refDelta >= 0.0,
                        )
                        CompareDeltaRow(
                            label = "Net P&L Delta",
                            value = Formatters.formatSignedInr(pnlDelta),
                            isPositive = pnlDelta >= 0.0,
                        )
                        CompareDeltaRow(
                            label = "Holdings Count",
                            value = "${baseSnap.stocks.size} → ${targetSnap.stocks.size}",
                            isPositive = targetSnap.stocks.size >= baseSnap.stocks.size,
                        )
                    }
                }

                Text(
                    text = "Holdings Value Changes (B - A)",
                    color = GraphiteTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                allCompanies.take(12).forEach { company ->
                    val valA = baseSnap.stocks.firstOrNull { it.company.equals(company, ignoreCase = true) }?.currentValue ?: 0.0
                    val valB = targetSnap.stocks.firstOrNull { it.company.equals(company, ignoreCase = true) }?.currentValue ?: 0.0
                    val diff = valB - valA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = company,
                            color = GraphiteTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = Formatters.formatSignedInr(diff),
                            style = TabularNumberTextStyle.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (diff >= 0.0) GraphiteGreen else GraphiteRed,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = GraphiteAccentViolet, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun CompareDeltaRow(
    label: String,
    value: String,
    isPositive: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = GraphiteTextSecondary, fontSize = 12.sp)
        Text(
            text = value,
            style = TabularNumberTextStyle.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPositive) GraphiteGreen else GraphiteRed,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnapshotDateSelector(
    label: String,
    snapshots: List<PortfolioSnapshot>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = snapshots[selectedIndex].date,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = GraphiteTextSecondary, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GraphiteAccentViolet,
                unfocusedBorderColor = GraphiteBorder,
                focusedTextColor = GraphiteTextPrimary,
                unfocusedTextColor = GraphiteTextPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = GraphiteCard,
        ) {
            snapshots.forEachIndexed { idx, snap ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${snap.date} (${Formatters.formatCompactInr(snap.totalCurrentValue)})",
                            color = GraphiteTextPrimary,
                            fontSize = 13.sp,
                        )
                    },
                    onClick = {
                        onSelect(idx)
                        expanded = false
                    },
                )
            }
        }
    }
}
