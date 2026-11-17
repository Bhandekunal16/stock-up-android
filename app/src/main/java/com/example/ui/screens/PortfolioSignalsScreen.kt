package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SignalsExecutionStatus
import com.example.data.model.SignalsHistoryEntry
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.GraphiteSectionCard
import com.example.ui.components.KpiCard
import com.example.ui.components.ResponsiveKpiGrid
import com.example.ui.components.StatusBadge
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteAmber
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteGreen
import com.example.ui.theme.GraphiteKpiDivider
import com.example.ui.theme.GraphiteNavSurface
import com.example.ui.theme.GraphiteOnAccent
import com.example.ui.theme.GraphiteRed
import com.example.ui.theme.GraphiteTextMuted
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.GraphiteTextSecondary
import com.example.ui.theme.MonoCodeTextStyle
import com.example.ui.viewmodel.SignalsUiState
import com.example.util.Formatters

@Composable
fun PortfolioSignalsScreen(
    uiState: SignalsUiState,
    kpiColumns: Int,
    onExecuteSignals: () -> Unit,
    onSelectHistoryEntry: (SignalsHistoryEntry) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val isRunning = uiState.status == SignalsExecutionStatus.RUNNING
    val hasRunBefore = uiState.history.isNotEmpty() || uiState.status != SignalsExecutionStatus.NOT_RUN

    val (statusLabel, statusColor, statusIcon) = when (uiState.status) {
        SignalsExecutionStatus.NOT_RUN -> Triple("Not Run", GraphiteTextMuted, Icons.Outlined.Terminal)
        SignalsExecutionStatus.RUNNING -> Triple("Running", GraphiteAmber, Icons.Outlined.Bolt)
        SignalsExecutionStatus.SUCCESS -> Triple("Success", GraphiteGreen, Icons.Outlined.CheckCircle)
        SignalsExecutionStatus.FAILED -> Triple("Failed", GraphiteRed, Icons.Outlined.ErrorOutline)
    }

    val kpis: List<@Composable (Modifier) -> Unit> = listOf(
        { mod ->
            KpiCard(
                title = "Status",
                value = statusLabel,
                supportingText = if (isRunning) "Executing script on backend..." else "Signal runner state",
                icon = statusIcon,
                valueColor = statusColor,
                iconTint = statusColor,
                modifier = mod,
                testTag = "signals_status_kpi",
            )
        },
        { mod ->
            val exitStr = uiState.exitCode?.toString() ?: "—"
            val exitColor = when (uiState.exitCode) {
                0 -> GraphiteGreen
                null -> GraphiteTextMuted
                else -> GraphiteRed
            }
            KpiCard(
                title = "Exit Code",
                value = exitStr,
                supportingText = if (uiState.exitCode == 0) "Clean exit (0)" else if (uiState.exitCode != null) "Non-zero exit" else "Pending execution",
                icon = Icons.Outlined.Code,
                valueColor = exitColor,
                iconTint = exitColor,
                valueStyle = MonoCodeTextStyle,
                modifier = mod,
                testTag = "signals_exit_code_kpi",
            )
        },
        { mod ->
            KpiCard(
                title = "Last Execution",
                value = uiState.lastExecutedAt?.substringAfter(", ") ?: "—",
                supportingText = uiState.lastExecutedAt ?: "Not executed in this session",
                icon = Icons.Outlined.Schedule,
                iconTint = GraphiteAccentViolet,
                modifier = mod,
            )
        },
        { mod ->
            KpiCard(
                title = "Measured Duration",
                value = uiState.durationMs?.let { Formatters.formatDurationMs(it) } ?: "—",
                supportingText = "End-to-end execution latency",
                icon = Icons.Outlined.Timer,
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
        // Header + Trigger Button (Never auto-executed on screen load)
        item {
            GraphiteSectionCard(
                title = "Portfolio Signals",
                subtitle = "Run server-side portfolio signal analysis on demand. Last executed: ${uiState.lastExecutedAt ?: "Never"}",
            ) {
                Button(
                    onClick = onExecuteSignals,
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GraphiteAccentViolet,
                        contentColor = GraphiteOnAccent,
                        disabledContainerColor = GraphiteAccentViolet.copy(alpha = 0.35f),
                        disabledContentColor = GraphiteOnAccent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("run_signals_button"),
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            color = GraphiteOnAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running Signals...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            imageVector = if (hasRunBefore) Icons.Outlined.Refresh else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasRunBefore) "Refresh Signals" else "Generate Signals",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // 4 Execution Summary KPI Cards
        item {
            ResponsiveKpiGrid(columns = kpiColumns.coerceAtMost(2), items = kpis)
        }

        // Conditional Error Details Alert
        if (uiState.error.isNotBlank()) {
            item {
                ErrorStateBanner(
                    title = "Error Details",
                    message = uiState.error,
                    onRetry = if (!isRunning) onExecuteSignals else null,
                )
            }
        }

        // Monospace Terminal Output Panel
        item {
            GraphiteSectionCard(
                title = "Terminal Output",
                subtitle = "Raw monospace execution stdout (columns preserved)",
                headerAction = {
                    if (uiState.output.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(uiState.output))
                                onShowMessage("Terminal output copied to clipboard")
                            },
                            border = BorderStroke(1.dp, GraphiteBorder),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("copy_terminal_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy output",
                                tint = GraphiteAccentViolet,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy", color = GraphiteTextPrimary, fontSize = 12.sp)
                        }
                    }
                },
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GraphiteNavSurface,
                    border = BorderStroke(1.dp, GraphiteBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 380.dp),
                ) {
                    val vScroll = rememberScrollState()
                    val hScroll = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(vScroll)
                            .horizontalScroll(hScroll)
                            .padding(14.dp),
                    ) {
                        val displayOutput = when {
                            isRunning -> "$ GET /api/portfolio/signals\nExecuting signal script...\nWaiting for stdout..."
                            uiState.output.isNotBlank() -> uiState.output
                            uiState.status == SignalsExecutionStatus.NOT_RUN ->
                                "Ready. Tap \"Generate Signals\" above to execute /api/portfolio/signals."
                            else -> "(No stdout returned)"
                        }
                        Text(
                            text = displayOutput,
                            style = MonoCodeTextStyle.copy(
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = if (uiState.output.isNotBlank()) GraphiteTextPrimary else GraphiteTextMuted,
                            ),
                            softWrap = false,
                            modifier = Modifier.testTag("signals_terminal_output"),
                        )
                    }
                }
            }
        }

        // Session Execution History (in-memory during session)
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = GraphiteAccentViolet,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Session Execution History (${uiState.history.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = GraphiteTextPrimary,
                )
            }
        }

        if (uiState.history.isEmpty()) {
            item {
                Text(
                    text = "No signal runs yet in this session. Tap Generate Signals to begin.",
                    color = GraphiteTextMuted,
                    fontSize = 13.sp,
                )
            }
        } else {
            items(uiState.history, key = { it.id }) { entry ->
                val selected = entry.id == uiState.selectedEntryId
                val entryColor = when (entry.status) {
                    SignalsExecutionStatus.SUCCESS -> GraphiteGreen
                    SignalsExecutionStatus.FAILED -> GraphiteRed
                    SignalsExecutionStatus.RUNNING -> GraphiteAmber
                    SignalsExecutionStatus.NOT_RUN -> GraphiteTextMuted
                }

                Surface(
                    onClick = { onSelectHistoryEntry(entry) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) GraphiteAccentViolet.copy(alpha = 0.12f) else GraphiteNavSurface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) GraphiteAccentViolet else GraphiteKpiDivider,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signals_history_${entry.id}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = entry.executedAt,
                                color = GraphiteTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Exit: ${entry.exitCode ?: "—"} • Duration: ${entry.durationMs?.let { Formatters.formatDurationMs(it) } ?: "—"}",
                                style = MonoCodeTextStyle.copy(fontSize = 11.sp, color = GraphiteTextMuted),
                            )
                        }
                        StatusBadge(
                            label = if (entry.status == SignalsExecutionStatus.SUCCESS) "Success" else "Failed",
                            color = entryColor,
                        )
                    }
                }
            }
        }
    }
}
