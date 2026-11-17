package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.ServerReachability
import com.example.data.preferences.ThemeMode
import com.example.ui.components.GraphiteSectionCard
import com.example.ui.components.StatusBadge
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
import com.example.ui.theme.MonoCodeTextStyle
import com.example.ui.viewmodel.PortfolioUiState

@Composable
fun SettingsScreen(
    uiState: PortfolioUiState,
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onCheckServerStatus: () -> Unit,
    onResetAllSnapshots: () -> Unit,
) {
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = GraphiteKpiCard,
            titleContentColor = GraphiteTextPrimary,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = GraphiteRed,
                )
            },
            title = { Text("Reset All Snapshots?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This permanently deletes all stored portfolio snapshots from the backend (DELETE /api/portfolio). This action cannot be undone.",
                    color = GraphiteTextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onResetAllSnapshots()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GraphiteRed,
                        contentColor = GraphiteOnAccent,
                    ),
                    modifier = Modifier.testTag("confirm_reset_button"),
                ) {
                    Text("Delete All", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = GraphiteTextMuted)
                }
            },
        )
    }

    val status = uiState.serverStatus
    val (statusLabel, statusColor) = when (status.reachability) {
        ServerReachability.UNKNOWN -> "Unknown" to GraphiteTextMuted
        ServerReachability.CHECKING -> "Checking..." to GraphiteAmber
        ServerReachability.UP -> "Up" to GraphiteGreen
        ServerReachability.DOWN -> "Down" to GraphiteRed
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 0. Appearance / Theme Mode Selector
        item {
            GraphiteSectionCard(
                title = "Appearance",
                subtitle = "Choose Dark, Light, or follow System theme",
                headerAction = {
                    StatusBadge(
                        label = themeMode.label,
                        color = GraphiteAccentViolet,
                        showDot = false,
                    )
                },
            ) {
                ThemeModeSelectorRow(
                    selectedMode = themeMode,
                    onSelect = onSelectThemeMode,
                )
            }
        }

        // 1. Server Status Card
        item {
            GraphiteSectionCard(
                title = "Server Status",
                subtitle = "Portfolio OCR & Snapshot API health check",
                headerAction = {
                    StatusBadge(
                        label = statusLabel,
                        color = statusColor,
                        modifier = Modifier.testTag("server_status_badge"),
                    )
                },
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GraphiteNavSurface,
                    border = BorderStroke(1.dp, GraphiteKpiDivider),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Dns,
                                contentDescription = null,
                                tint = GraphiteAccentViolet,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = BuildConfig.PORTFOLIO_API_BASE_URL,
                                style = MonoCodeTextStyle.copy(fontSize = 12.sp, color = GraphiteTextPrimary),
                            )
                        }
                        if (status.lastCheckedAt != null) {
                            Text(
                                text = "Last checked: ${status.lastCheckedAt}",
                                color = GraphiteTextMuted,
                                fontSize = 11.sp,
                            )
                        }
                        if (!status.detail.isNullOrBlank()) {
                            Text(
                                text = status.detail,
                                color = statusColor,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onCheckServerStatus,
                    enabled = status.reachability != ServerReachability.CHECKING,
                    border = BorderStroke(1.dp, GraphiteBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("check_status_button"),
                ) {
                    if (status.reachability == ServerReachability.CHECKING) {
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check Status", color = GraphiteTextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 2. Configured Backend Endpoints
        item {
            GraphiteSectionCard(
                title = "Configured Endpoints",
                subtitle = "Injected via BuildConfig / gradle.properties",
            ) {
                EndpointRow(label = "Portfolio API", url = BuildConfig.PORTFOLIO_API_BASE_URL)
                HorizontalDivider(thickness = 1.dp, color = GraphiteKpiDivider)
                EndpointRow(label = "Vercel Signals & Screener", url = BuildConfig.VERCEL_BACKEND_BASE_URL)
            }
        }

        // 3. Reset All Snapshots (Danger Zone)
        item {
            GraphiteSectionCard(
                title = "Danger Zone",
                subtitle = "Manage stored portfolio snapshot history",
            ) {
                Text(
                    text = "Currently tracking ${uiState.snapshots.size} snapshot${if (uiState.snapshots.size == 1) "" else "s"} on the portfolio backend.",
                    color = GraphiteTextMuted,
                    fontSize = 13.sp,
                )
                Button(
                    onClick = { showResetDialog = true },
                    enabled = !uiState.isResetting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GraphiteRed,
                        contentColor = GraphiteOnAccent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("reset_snapshots_button"),
                ) {
                    if (uiState.isResetting) {
                        CircularProgressIndicator(
                            color = GraphiteOnAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isResetting) "Resetting..." else "Reset All Snapshots",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeModeSelectorRow(
    selectedMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        Triple(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
        Triple(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
        Triple(ThemeMode.SYSTEM, "System", Icons.Outlined.BrightnessAuto),
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = GraphiteNavSurface,
        border = BorderStroke(1.dp, GraphiteBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (mode, label, icon) ->
                val selected = selectedMode == mode
                Surface(
                    onClick = { onSelect(mode) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (selected) GraphiteAccentViolet else GraphiteNavSurface,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("theme_option_${mode.name.lowercase()}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (selected) GraphiteOnAccent else GraphiteTextMuted,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) GraphiteOnAccent else GraphiteTextMuted,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointRow(label: String, url: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = GraphiteTextMuted, fontSize = 11.sp)
        Text(
            text = url,
            style = MonoCodeTextStyle.copy(fontSize = 12.sp, color = GraphiteTextPrimary),
        )
    }
}
