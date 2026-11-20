package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteGreen
import com.example.ui.theme.GraphiteKpiCard
import com.example.ui.theme.GraphiteTextMuted
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.GraphiteTextSecondary
import com.example.ui.theme.TabularNumberTextStyle
import kotlin.math.abs
import kotlin.math.roundToInt

data class LinePoint(
    val value: Double,
    val highlighted: Boolean = false,
)

data class LineSeries(
    val name: String,
    val color: Color,
    val points: List<LinePoint>,
    val dashed: Boolean = false,
    val showDots: Boolean = false,
)

/**
 * Pure line-graph component built with Jetpack Compose Canvas.
 * Smooth cubic Bezier path rendering, subtle grid lines, crisp axis labels,
 * generous legend spacing, and responsive touch scrub tooltip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StockLineChart(
    xLabels: List<String>,
    series: List<LineSeries>,
    yAxisFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 236.dp,
    showZeroReferenceLine: Boolean = false,
    emptyTitle: String = "No chart data available",
    emptySubtitle: String = "Data points will appear here once available.",
) {
    val nonEmptySeries = series.filter { it.points.isNotEmpty() }
    if (xLabels.isEmpty() || nonEmptySeries.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(chartHeight),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                    contentDescription = emptyTitle,
                    tint = GraphiteTextMuted,
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    text = emptyTitle,
                    color = GraphiteTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = emptySubtitle,
                    color = GraphiteTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    val allValues = nonEmptySeries.flatMap { s -> s.points.map { it.value } } +
        if (showZeroReferenceLine) listOf(0.0) else emptyList()
    val rawMin = allValues.minOrNull() ?: 0.0
    val rawMax = allValues.maxOrNull() ?: 1.0
    val spread = (rawMax - rawMin).let {
        if (abs(it) < 1e-6) maxOf(abs(rawMax) * 0.1, 1.0) else it
    }
    val minY = rawMin - spread * 0.08
    val maxY = rawMax + spread * 0.08
    val rangeY = (maxY - minY).coerceAtLeast(1e-6)

    var selectedIndex by remember(xLabels, series) { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Legend with clean visual separation above the chart
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            nonEmptySeries.forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (s.dashed) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(width = 7.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(s.color),
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 7.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(s.color),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 16.dp, height = 3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(s.color),
                        )
                    }
                    Text(
                        text = s.name,
                        color = GraphiteTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Scrubbed Inspector Tooltip Banner
        val activeIdx = selectedIndex?.coerceIn(0, xLabels.lastIndex)
        if (activeIdx != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = GraphiteCard,
                border = BorderStroke(1.dp, GraphiteBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = xLabels[activeIdx],
                        color = GraphiteTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        nonEmptySeries.take(4).forEach { s ->
                            val pt = s.points.getOrNull(activeIdx)
                            if (pt != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (pt.highlighted) GraphiteGreen else s.color),
                                    )
                                    Text(
                                        text = "${s.name}:",
                                        color = GraphiteTextSecondary,
                                        fontSize = 11.sp,
                                    )
                                    Text(
                                        text = yAxisFormatter(pt.value),
                                        style = TabularNumberTextStyle.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = GraphiteTextPrimary,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val yTicks = remember(minY, maxY) {
            val mid = (minY + maxY) / 2.0
            listOf(maxY, mid, minY)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Y-axis Labels (11.sp tabular numerals, readable secondary color)
                Column(
                    modifier = Modifier
                        .height(chartHeight)
                        .widthIn(min = 56.dp)
                        .padding(end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    yTicks.forEach { tick ->
                        Text(
                            text = yAxisFormatter(tick),
                            style = TabularNumberTextStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = GraphiteTextSecondary,
                            ),
                            maxLines = 1,
                        )
                    }
                }

                // Canvas Plot Area (capture colors in @Composable scope)
                val gridColor = GraphiteBorder
                val mutedColor = GraphiteTextMuted
                val greenColor = GraphiteGreen
                val cardBgColor = GraphiteKpiCard

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(chartHeight),
                ) {
                    val pointCount = xLabels.size
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(pointCount) {
                                detectTapGestures { offset ->
                                    if (pointCount <= 1) {
                                        selectedIndex = 0
                                    } else {
                                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val idx = (ratio * (pointCount - 1)).roundToInt()
                                        selectedIndex = if (selectedIndex == idx) null else idx
                                    }
                                }
                            }
                            .pointerInput(pointCount) {
                                detectDragGestures(
                                    onDragEnd = { },
                                    onDragCancel = { },
                                ) { change, _ ->
                                    change.consume()
                                    if (pointCount <= 1) {
                                        selectedIndex = 0
                                    } else {
                                        val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        selectedIndex = (ratio * (pointCount - 1)).roundToInt()
                                    }
                                }
                            },
                    ) {
                        val topPad = 12.dp.toPx()
                        val bottomPad = 12.dp.toPx()
                        val leftPad = 6.dp.toPx()
                        val rightPad = 6.dp.toPx()
                        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                        fun mapY(v: Double): Float {
                            val norm = ((v - minY) / rangeY).toFloat().coerceIn(0f, 1f)
                            return topPad + plotH * (1f - norm)
                        }

                        fun mapX(index: Int, total: Int): Float {
                            if (total <= 1) return leftPad + plotW / 2f
                            return leftPad + (index.toFloat() / (total - 1).toFloat()) * plotW
                        }

                        // Subtle horizontal grid lines
                        val gridDash = PathEffect.dashPathEffect(floatArrayOf(5f, 6f), 0f)
                        listOf(0f, 0.5f, 1f).forEach { frac ->
                            val y = topPad + plotH * frac
                            drawLine(
                                color = gridColor.copy(alpha = 0.65f),
                                start = Offset(leftPad, y),
                                end = Offset(leftPad + plotW, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = gridDash,
                            )
                        }

                        // Optional zero reference line
                        if (showZeroReferenceLine && minY <= 0.0 && maxY >= 0.0) {
                            val zeroY = mapY(0.0)
                            drawLine(
                                color = mutedColor.copy(alpha = 0.55f),
                                start = Offset(leftPad, zeroY),
                                end = Offset(leftPad + plotW, zeroY),
                                strokeWidth = 1.2.dp.toPx(),
                            )
                        }

                        // Active Scrub Crosshair
                        if (activeIdx != null) {
                            val cx = mapX(activeIdx, pointCount)
                            drawLine(
                                color = mutedColor.copy(alpha = 0.5f),
                                start = Offset(cx, topPad),
                                end = Offset(cx, topPad + plotH),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = gridDash,
                            )
                        }

                        // Draw each line series using smooth monotone-clamped cubic Bezier curves
                        nonEmptySeries.forEach { s ->
                            val pts = s.points
                            if (pts.size == 1) {
                                val cx = mapX(0, 1)
                                val cy = mapY(pts[0].value)
                                val dotColor = if (pts[0].highlighted) greenColor else s.color
                                drawCircle(color = dotColor, radius = 5.dp.toPx(), center = Offset(cx, cy))
                            } else if (pts.size > 1) {
                                val offsets = pts.mapIndexed { idx, pt ->
                                    Offset(mapX(idx, pts.size), mapY(pt.value))
                                }
                                val path = Path().apply {
                                    moveTo(offsets[0].x, offsets[0].y)
                                    for (i in 0 until offsets.lastIndex) {
                                        val p0 = offsets[i]
                                        val p1 = offsets[i + 1]
                                        val cx = (p0.x + p1.x) / 2f
                                        cubicTo(
                                            cx, p0.y,
                                            cx, p1.y,
                                            p1.x, p1.y,
                                        )
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = s.color,
                                    style = Stroke(
                                        width = if (s.dashed) 2.1.dp.toPx() else 2.5.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                        pathEffect = if (s.dashed) {
                                            PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                                        } else null,
                                    ),
                                )

                                offsets.forEachIndexed { idx, offset ->
                                    val pt = pts[idx]
                                    if (pt.highlighted) {
                                        drawCircle(
                                            color = cardBgColor,
                                            radius = 5.5.dp.toPx(),
                                            center = offset,
                                        )
                                        drawCircle(
                                            color = greenColor,
                                            radius = 3.8.dp.toPx(),
                                            center = offset,
                                        )
                                    } else if (activeIdx == idx) {
                                        drawCircle(
                                            color = cardBgColor,
                                            radius = 5.5.dp.toPx(),
                                            center = offset,
                                        )
                                        drawCircle(
                                            color = s.color,
                                            radius = 3.8.dp.toPx(),
                                            center = offset,
                                        )
                                    } else if (s.showDots) {
                                        drawCircle(
                                            color = s.color,
                                            radius = 2.6.dp.toPx(),
                                            center = offset,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // X-axis Labels aligned beneath the Canvas plot area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 66.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (xLabels.size == 1) {
                    Text(
                        text = xLabels.first(),
                        style = TabularNumberTextStyle.copy(fontSize = 11.sp, color = GraphiteTextSecondary),
                    )
                } else {
                    Text(
                        text = xLabels.first(),
                        style = TabularNumberTextStyle.copy(fontSize = 11.sp, color = GraphiteTextSecondary),
                        maxLines = 1,
                    )
                    if (xLabels.size >= 3) {
                        Text(
                            text = xLabels[xLabels.size / 2],
                            style = TabularNumberTextStyle.copy(fontSize = 11.sp, color = GraphiteTextSecondary),
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = xLabels.last(),
                        style = TabularNumberTextStyle.copy(fontSize = 11.sp, color = GraphiteTextSecondary),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
