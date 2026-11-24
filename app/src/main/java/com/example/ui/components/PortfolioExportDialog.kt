package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PortfolioSnapshot
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteKpiCard
import com.example.ui.theme.GraphiteNavSurface
import com.example.ui.theme.GraphiteOnAccent
import com.example.ui.theme.GraphiteTextMuted
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.GraphiteTextSecondary
import com.example.ui.theme.MonoCodeTextStyle
import com.example.util.CsvExportPayload
import com.example.util.CsvExportScope
import com.example.util.PortfolioCsvExporter

@Composable
fun PortfolioExportDialog(
    snapshots: List<PortfolioSnapshot>,
    initialScope: CsvExportScope,
    targetSnapshot: PortfolioSnapshot? = null,
    onSaveToDevice: (CsvExportPayload) -> Unit,
    onShareCsv: (CsvExportPayload) -> Unit,
    onDismiss: () -> Unit,
) {
    val availableScopes = remember(targetSnapshot) {
        buildList {
            if (targetSnapshot != null) {
                add(CsvExportScope.SINGLE_SNAPSHOT)
            }
            add(CsvExportScope.LATEST_HOLDINGS)
            add(CsvExportScope.ALL_HISTORY_HOLDINGS)
            add(CsvExportScope.SNAPSHOT_SUMMARY)
        }
    }

    var selectedScope by remember(initialScope, targetSnapshot) {
        mutableStateOf(
            if (initialScope in availableScopes) initialScope else availableScopes.first()
        )
    }

    val previewPayload = remember(snapshots, selectedScope, targetSnapshot) {
        PortfolioCsvExporter.buildCsvPayload(
            snapshots = snapshots,
            scope = selectedScope,
            targetSnapshot = targetSnapshot,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GraphiteKpiCard,
        titleContentColor = GraphiteTextPrimary,
        icon = {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = null,
                tint = GraphiteAccentViolet,
            )
        },
        title = {
            Text(
                text = "Export Portfolio CSV",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Choose the dataset to save locally or share for spreadsheet tracking.",
                    color = GraphiteTextSecondary,
                    fontSize = 12.sp,
                )

                availableScopes.forEach { scope ->
                    val selected = selectedScope == scope
                    val scopePayload = remember(snapshots, scope, targetSnapshot) {
                        PortfolioCsvExporter.buildCsvPayload(
                            snapshots = snapshots,
                            scope = scope,
                            targetSnapshot = targetSnapshot,
                        )
                    }
                    val title = if (scope == CsvExportScope.SINGLE_SNAPSHOT && targetSnapshot != null) {
                        "Snapshot (${targetSnapshot.date})"
                    } else {
                        scope.title
                    }

                    Surface(
                        onClick = { selectedScope = scope },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) GraphiteAccentViolet.copy(alpha = 0.10f) else GraphiteNavSurface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) GraphiteAccentViolet.copy(alpha = 0.60f) else GraphiteBorder,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_scope_${scope.name.lowercase()}"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = GraphiteAccentViolet,
                                    unselectedColor = GraphiteTextSecondary,
                                ),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = title,
                                        color = GraphiteTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${scopePayload.rowCount} rows",
                                        color = GraphiteAccentViolet,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Text(
                                    text = scope.description,
                                    color = GraphiteTextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }

                // Preview Info Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GraphiteNavSurface,
                    border = BorderStroke(1.dp, GraphiteBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "File Preview",
                            color = GraphiteTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = previewPayload.fileName,
                            style = MonoCodeTextStyle.copy(fontSize = 12.sp, color = GraphiteTextPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (selectedScope == CsvExportScope.SNAPSHOT_SUMMARY) {
                                "Columns: Date, Holdings Count, Current, Reference, Net P&L, Return %"
                            } else {
                                "Columns: Date, Company, Shares, Current, Reference, Avg/Share, Net P&L, Return %, Weight %"
                            },
                            color = GraphiteTextMuted,
                            fontSize = 11.sp,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onShareCsv(previewPayload)
                        },
                        enabled = previewPayload.rowCount > 0,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GraphiteBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("export_share_csv_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            tint = GraphiteAccentViolet,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = GraphiteTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onSaveToDevice(previewPayload)
                        },
                        enabled = previewPayload.rowCount > 0,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GraphiteAccentViolet,
                            contentColor = GraphiteOnAccent,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("export_save_csv_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save CSV", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("export_cancel_button"),
            ) {
                Text("Cancel", color = GraphiteTextSecondary)
            }
        },
    )
}

fun launchCsvShareIntent(
    context: Context,
    fileName: String,
    shareUri: android.net.Uri,
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, shareUri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, "Export Portfolio CSV").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
