package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.example.data.preferences.ThemeMode

private val FintechDarkColorScheme = darkColorScheme(
    primary = DarkStockFolioColors.accentViolet,
    onPrimary = DarkStockFolioColors.onAccent,
    primaryContainer = DarkStockFolioColors.accentVioletDeep.copy(alpha = 0.22f),
    onPrimaryContainer = DarkStockFolioColors.accentViolet,
    secondary = DarkStockFolioColors.blue,
    onSecondary = DarkStockFolioColors.onAccent,
    secondaryContainer = DarkStockFolioColors.elevatedCard,
    onSecondaryContainer = DarkStockFolioColors.textPrimary,
    tertiary = DarkStockFolioColors.green,
    onTertiary = DarkStockFolioColors.onAccent,
    background = DarkStockFolioColors.canvas,
    onBackground = DarkStockFolioColors.textPrimary,
    surface = DarkStockFolioColors.kpiCard,
    onSurface = DarkStockFolioColors.textPrimary,
    surfaceVariant = DarkStockFolioColors.elevatedCard,
    onSurfaceVariant = DarkStockFolioColors.textSecondary,
    surfaceContainerLowest = DarkStockFolioColors.navSurface,
    surfaceContainerLow = DarkStockFolioColors.canvas,
    surfaceContainer = DarkStockFolioColors.kpiCard,
    surfaceContainerHigh = DarkStockFolioColors.elevatedCard,
    surfaceContainerHighest = DarkStockFolioColors.border,
    outline = DarkStockFolioColors.border,
    outlineVariant = DarkStockFolioColors.kpiDivider,
    error = DarkStockFolioColors.red,
    onError = DarkStockFolioColors.onAccent,
    errorContainer = DarkStockFolioColors.red.copy(alpha = 0.16f),
    onErrorContainer = DarkStockFolioColors.red,
)

private val FintechLightColorScheme = lightColorScheme(
    primary = LightStockFolioColors.accentViolet,
    onPrimary = LightStockFolioColors.onAccent,
    primaryContainer = LightStockFolioColors.accentViolet.copy(alpha = 0.14f),
    onPrimaryContainer = LightStockFolioColors.accentVioletDeep,
    secondary = LightStockFolioColors.blue,
    onSecondary = LightStockFolioColors.onAccent,
    secondaryContainer = LightStockFolioColors.elevatedCard,
    onSecondaryContainer = LightStockFolioColors.textPrimary,
    tertiary = LightStockFolioColors.green,
    onTertiary = LightStockFolioColors.onAccent,
    background = LightStockFolioColors.canvas,
    onBackground = LightStockFolioColors.textPrimary,
    surface = LightStockFolioColors.kpiCard,
    onSurface = LightStockFolioColors.textPrimary,
    surfaceVariant = LightStockFolioColors.elevatedCard,
    onSurfaceVariant = LightStockFolioColors.textSecondary,
    surfaceContainerLowest = LightStockFolioColors.navSurface,
    surfaceContainerLow = LightStockFolioColors.canvas,
    surfaceContainer = LightStockFolioColors.kpiCard,
    surfaceContainerHigh = LightStockFolioColors.elevatedCard,
    surfaceContainerHighest = LightStockFolioColors.border,
    outline = LightStockFolioColors.border,
    outlineVariant = LightStockFolioColors.kpiDivider,
    error = LightStockFolioColors.red,
    onError = LightStockFolioColors.onAccent,
    errorContainer = LightStockFolioColors.red.copy(alpha = 0.12f),
    onErrorContainer = LightStockFolioColors.red,
)

private val FintechShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun resolveIsDarkTheme(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
}

@Composable
fun StockFolioTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = resolveIsDarkTheme(themeMode)
    val colors = if (isDark) DarkStockFolioColors else LightStockFolioColors
    val colorScheme = if (isDark) FintechDarkColorScheme else FintechLightColorScheme

    CompositionLocalProvider(LocalStockFolioColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StockFolioTypography,
            shapes = FintechShapes,
            content = content,
        )
    }
}
