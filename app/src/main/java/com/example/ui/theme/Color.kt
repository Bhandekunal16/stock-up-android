package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class StockFolioColors(
    val canvas: Color,
    val navSurface: Color,
    val kpiCard: Color,
    val elevatedCard: Color,
    val kpiDivider: Color,
    val border: Color,
    val accentViolet: Color,
    val accentVioletDeep: Color,
    val onAccent: Color,
    val green: Color,
    val amber: Color,
    val red: Color,
    val blue: Color,
    val slateChart: Color,
    val softVioletChart: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val isDark: Boolean,
)

/**
 * Midnight Finance – Premium Dark palette.
 */
val DarkStockFolioColors = StockFolioColors(
    canvas = Color(0xFF080D12),
    navSurface = Color(0xFF0B1117),
    kpiCard = Color(0xFF101820),
    elevatedCard = Color(0xFF16202B),
    kpiDivider = Color(0xFF202B36),
    border = Color(0xFF202B36),
    accentViolet = Color(0xFFA855F7),
    accentVioletDeep = Color(0xFF9333EA),
    onAccent = Color(0xFF080D12),
    green = Color(0xFF22C55E),
    amber = Color(0xFFF59E0B),
    red = Color(0xFFEF4444),
    blue = Color(0xFF60A5FA),
    slateChart = Color(0xFF94A3B8),
    softVioletChart = Color(0xFFC084FC),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFA1AAB5),
    textMuted = Color(0xFF748091),
    textSubtle = Color(0xFF748091),
    isDark = true,
)

/**
 * Clean Wealth Light palette matching Midnight Finance semantics.
 */
val LightStockFolioColors = StockFolioColors(
    canvas = Color(0xFFF5F7FA),
    navSurface = Color(0xFFFFFFFF),
    kpiCard = Color(0xFFFFFFFF),
    elevatedCard = Color(0xFFEEF2F6),
    kpiDivider = Color(0xFFE2E8F0),
    border = Color(0xFFCBD5E1),
    accentViolet = Color(0xFF9333EA),
    accentVioletDeep = Color(0xFF7E22CE),
    onAccent = Color(0xFFFFFFFF),
    green = Color(0xFF16A34A),
    amber = Color(0xFFD97706),
    red = Color(0xFFDC2626),
    blue = Color(0xFF2563EB),
    slateChart = Color(0xFF64748B),
    softVioletChart = Color(0xFFA855F7),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    textSubtle = Color(0xFF64748B),
    isDark = false,
)

val LocalStockFolioColors = staticCompositionLocalOf { DarkStockFolioColors }

// Dynamic theme-aware tokens backed by LocalStockFolioColors
val GraphiteCanvas: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.canvas

val GraphiteNavSurface: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.navSurface

val GraphiteKpiCard: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.kpiCard

val GraphiteCard: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.elevatedCard

val GraphiteKpiDivider: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.kpiDivider

val GraphiteBorder: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.border

val GraphiteAccentViolet: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.accentViolet

val GraphiteAccentVioletDeep: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.accentVioletDeep

val GraphiteOnAccent: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.onAccent

val GraphiteGreen: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.green

val GraphiteAmber: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.amber

val GraphiteRed: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.red

val GraphiteBlue: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.blue

val GraphiteTextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.textPrimary

val GraphiteTextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.textSecondary

val GraphiteTextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.textMuted

val GraphiteTextSubtle: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.textSubtle

val ChartPalette: List<Color>
    @Composable @ReadOnlyComposable get() {
        val c = LocalStockFolioColors.current
        return listOf(c.green, c.accentViolet, c.blue, c.amber, c.slateChart)
    }
