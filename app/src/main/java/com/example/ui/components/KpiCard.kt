package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteKpiCard
import com.example.ui.theme.GraphiteKpiDivider
import com.example.ui.theme.GraphiteTextMuted
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.TabularNumberTextStyle

/**
 * Standardized 3-section Fintech KPI Card:
 * - Consistent 14.dp border radius, #101514 surface, 1.dp #202927 border, subtle 1.dp elevation
 * - 1. Top: 13.sp readable muted label on left + 36.dp aligned semantic icon badge on right
 * - 2. Middle: Prominent 22.sp bold tabular numeral value
 * - 3. Bottom: 1.dp #202927 divider + 12.sp high-contrast supporting text
 */
@Composable
fun KpiCard(
    title: String,
    value: String,
    supportingText: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    valueColor: Color = GraphiteTextPrimary,
    iconTint: Color = GraphiteAccentViolet,
    valueStyle: TextStyle = TabularNumberTextStyle,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .let { if (testTag != null) it.testTag(testTag) else it }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Top Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        color = GraphiteTextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconTint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Middle Section
                Text(
                    text = value,
                    style = valueStyle.copy(
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = valueColor,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Bottom Section
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = GraphiteKpiDivider,
                )
                Text(
                    text = supportingText,
                    color = GraphiteTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = cardModifier,
            shape = RoundedCornerShape(14.dp),
            color = GraphiteKpiCard,
            border = BorderStroke(1.dp, GraphiteKpiDivider),
            shadowElevation = 1.dp,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = cardModifier,
            shape = RoundedCornerShape(14.dp),
            color = GraphiteKpiCard,
            border = BorderStroke(1.dp, GraphiteKpiDivider),
            shadowElevation = 1.dp,
        ) {
            content()
        }
    }
}

/**
 * Equal-height responsive KPI grid.
 * When `balanceLastRow` is true and the final row has fewer items than `columns`,
 * items in the final row stretch evenly across the full row width so no orphaned gap appears.
 */
@Composable
fun ResponsiveKpiGrid(
    columns: Int,
    items: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier = Modifier,
    balanceLastRow: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val safeColumns = columns.coerceAtLeast(1)
        val rows = items.chunked(safeColumns)
        rows.forEachIndexed { rowIdx, rowItems ->
            val isLastRow = rowIdx == rows.lastIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (item in rowItems) {
                    item(Modifier.weight(1f))
                }
                if (!balanceLastRow || !isLastRow) {
                    val emptySlots = safeColumns - rowItems.size
                    repeat(emptySlots) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
