package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.StockItem
import com.example.ui.components.ErrorStateBanner
import com.example.ui.components.GraphiteSectionCard
import com.example.ui.components.KpiCard
import com.example.ui.components.ResponsiveKpiGrid
import com.example.ui.components.StatusBadge
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteBlue
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteGreen
import com.example.ui.theme.GraphiteKpiDivider
import com.example.ui.theme.GraphiteNavSurface
import com.example.ui.theme.GraphiteOnAccent
import com.example.ui.theme.GraphiteRed
import com.example.ui.theme.GraphiteTextMuted
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.TabularNumberTextStyle
import com.example.ui.viewmodel.UploadUiState
import com.example.util.Formatters
import java.io.File

@Composable
fun UploadPortfolioScreen(
    uploadState: UploadUiState,
    kpiColumns: Int,
    onSelectImage: (Uri?) -> Unit,
    onUpload: (Context) -> Unit,
    onClear: () -> Unit,
    onNavigateToDashboard: () -> Unit,
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) onSelectImage(uri)
        },
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && pendingCameraUri != null) {
                onSelectImage(pendingCameraUri)
            }
        },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GraphiteSectionCard(
                title = "Screenshot OCR Extractor",
                subtitle = "Select a broker portfolio screenshot (JPEG, PNG, WebP • Max 10 MB) to extract holdings and save today's snapshot.",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !uploadState.isUploading,
                        border = BorderStroke(1.dp, GraphiteBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("pick_gallery_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = GraphiteAccentViolet,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gallery", color = GraphiteTextPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            val cameraUri = createTempCameraUri(context)
                            pendingCameraUri = cameraUri
                            cameraLauncher.launch(cameraUri)
                        },
                        enabled = !uploadState.isUploading,
                        border = BorderStroke(1.dp, GraphiteBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("take_photo_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            tint = GraphiteAccentViolet,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Camera", color = GraphiteTextPrimary)
                    }
                }

                if (uploadState.selectedUri != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GraphiteNavSurface,
                        border = BorderStroke(1.dp, GraphiteBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = uploadState.selectedUri,
                                contentDescription = "Selected portfolio screenshot preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }

                    if (uploadState.isUploading) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = GraphiteAccentViolet,
                                trackColor = GraphiteCard,
                            )
                            Text(
                                text = "Uploading screenshot and running OCR extraction...",
                                color = GraphiteTextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { onUpload(context) },
                            enabled = !uploadState.isUploading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GraphiteAccentViolet,
                                contentColor = GraphiteOnAccent,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("extract_portfolio_button"),
                        ) {
                            if (uploadState.isUploading) {
                                CircularProgressIndicator(
                                    color = GraphiteOnAccent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uploadState.isUploading) "Extracting..." else "Extract Portfolio",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        OutlinedButton(
                            onClick = onClear,
                            enabled = !uploadState.isUploading,
                            border = BorderStroke(1.dp, GraphiteBorder),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("clear_upload_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear image",
                                tint = GraphiteTextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear", color = GraphiteTextMuted)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GraphiteNavSurface)
                            .padding(vertical = 28.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = null,
                                tint = GraphiteTextMuted,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = "No screenshot selected",
                                color = GraphiteTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "Choose from Gallery or capture with Camera above",
                                color = GraphiteTextMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        if (uploadState.errorMessage != null) {
            item {
                ErrorStateBanner(
                    title = "Extraction Failed",
                    message = uploadState.errorMessage,
                    onRetry = if (uploadState.selectedUri != null) {
                        { onUpload(context) }
                    } else null,
                )
            }
        }

        val result = uploadState.extractResult
        if (result != null) {
            val totalCurrent = result.stocks.sumOf { it.currentValue }
            val totalRef = result.stocks.sumOf { it.referenceValue }
            val netDiff = totalCurrent - totalRef
            val pct = if (totalRef > 0.0) (netDiff / totalRef) * 100.0 else 0.0

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = GraphiteGreen,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Extraction Summary (${result.date})",
                            style = MaterialTheme.typography.titleMedium,
                            color = GraphiteTextPrimary,
                        )
                    }
                    StatusBadge(label = "Saved", color = GraphiteGreen)
                }
            }

            item {
                val summaryKpis: List<@Composable (Modifier) -> Unit> = listOf(
                    { mod ->
                        KpiCard(
                            title = "Extracted Stocks",
                            value = "${result.stocks.size}",
                            supportingText = "Parsed holdings rows",
                            icon = Icons.Outlined.Layers,
                            iconTint = GraphiteAccentViolet,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        KpiCard(
                            title = "Total Current Value",
                            value = Formatters.formatInr(totalCurrent),
                            supportingText = "Snapshot date ${result.date}",
                            icon = Icons.Outlined.AccountBalanceWallet,
                            iconTint = GraphiteAccentViolet,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        KpiCard(
                            title = "Reference / Invested",
                            value = Formatters.formatInr(totalRef),
                            supportingText = "Baseline cost basis",
                            icon = Icons.Outlined.Savings,
                            iconTint = GraphiteBlue,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        val pnlColor = if (netDiff >= 0.0) GraphiteGreen else GraphiteRed
                        KpiCard(
                            title = "Net Difference",
                            value = Formatters.formatSignedInr(netDiff),
                            supportingText = "${Formatters.formatPercent(pct, signed = true)} return",
                            icon = if (netDiff >= 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                            valueColor = pnlColor,
                            iconTint = pnlColor,
                            modifier = mod,
                        )
                    },
                )
                ResponsiveKpiGrid(columns = kpiColumns.coerceAtMost(2), items = summaryKpis)
            }

            item {
                GraphiteSectionCard(
                    title = "Extracted Stock Table",
                    subtitle = "${result.stocks.size} holdings extracted from screenshot",
                    headerAction = {
                        OutlinedButton(
                            onClick = onNavigateToDashboard,
                            border = BorderStroke(1.dp, GraphiteBorder),
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) {
                            Text("View Dashboard", color = GraphiteAccentViolet, fontSize = 12.sp)
                        }
                    },
                ) {
                    if (result.stocks.isEmpty()) {
                        Text(
                            text = "No stock rows returned in the OCR response.",
                            color = GraphiteTextMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            items(result.stocks, key = { "${it.company}_${it.shares}_${it.currentValue}" }) { stock ->
                ExtractedStockRowCard(stock = stock)
            }
        }
    }
}

@Composable
fun ExtractedStockRowCard(stock: StockItem) {
    val pnlColor = if (stock.netDifference >= 0.0) GraphiteGreen else GraphiteRed
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GraphiteCard.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, GraphiteKpiDivider),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stock.company,
                        color = GraphiteTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${Formatters.formatIndianNumber(stock.shares, maxDecimals = 4)} shares",
                        style = TabularNumberTextStyle.copy(fontSize = 11.sp, color = GraphiteTextMuted),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatInr(stock.currentValue),
                        style = TabularNumberTextStyle.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = GraphiteTextPrimary,
                        ),
                    )
                    Text(
                        text = "${Formatters.formatSignedInr(stock.netDifference)} (${Formatters.formatPercent(stock.returnPercent, signed = true)})",
                        style = TabularNumberTextStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
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
                Text(
                    text = "Current: ${Formatters.formatInr(stock.currentValue)}",
                    style = TabularNumberTextStyle.copy(fontSize = 11.sp, color = GraphiteTextMuted),
                )
            }
        }
    }
}

private fun createTempCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "portfolio_capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
