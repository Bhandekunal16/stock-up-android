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
    val cyan: Color,
    val pink: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val isDark: Boolean,
)

val DarkStockFolioColors = StockFolioColors(
    canvas = Color(0xFF050807),
    navSurface = Color(0xFF0A0E0D),
    kpiCard = Color(0xFF101514),
    elevatedCard = Color(0xFF161D1B),
    kpiDivider = Color(0xFF202927),
    border = Color(0xFF202927),
    accentViolet = Color(0xFFA855F7),
    accentVioletDeep = Color(0xFF9333EA),
    onAccent = Color(0xFF050807),
    green = Color(0xFF00D69A),
    amber = Color(0xFFF59E0B),
    red = Color(0xFFF87171),
    blue = Color(0xFF3B82F6),
    cyan = Color(0xFF22D3EE),
    pink = Color(0xFFEC4899),
    textPrimary = Color(0xFFF5F7F6),
    textSecondary = Color(0xFFD1D7D5),
    textMuted = Color(0xFF929B98),
    textSubtle = Color(0xFF6B7572),
    isDark = true,
)

val LightStockFolioColors = StockFolioColors(
    canvas = Color(0xFFF4F7F6),
    navSurface = Color(0xFFFFFFFF),
    kpiCard = Color(0xFFFFFFFF),
    elevatedCard = Color(0xFFEEF2F1),
    kpiDivider = Color(0xFFDCE3E0),
    border = Color(0xFFCBD5D1),
    accentViolet = Color(0xFF9333EA),
    accentVioletDeep = Color(0xFF7E22CE),
    onAccent = Color(0xFFFFFFFF),
    green = Color(0xFF059669),
    amber = Color(0xFFD97706),
    red = Color(0xFFDC2626),
    blue = Color(0xFF2563EB),
    cyan = Color(0xFF0891B2),
    pink = Color(0xFFDB2777),
    textPrimary = Color(0xFF0B1210),
    textSecondary = Color(0xFF2C3835),
    textMuted = Color(0xFF55635F),
    textSubtle = Color(0xFF73827D),
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

val GraphiteCyan: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.cyan

val GraphitePink: Color
    @Composable @ReadOnlyComposable get() = LocalStockFolioColors.current.pink

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
        return listOf(c.accentViolet, c.blue, c.cyan, c.amber, c.pink)
    }
