package com.spendtracker.app.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceTint = LightPrimary,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceTint = DarkPrimary,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest
)

/**
 * Semantic money colors that are not part of the Material color scheme.
 * Always read them through [SpendTrackerTheme.finance] so they follow the
 * app's own light/dark choice rather than the system setting.
 *
 * - [income] / [expense]: text/icon colour for money in / money out amounts.
 * - [surplusContainer] / [onSurplusContainer]: green-tinted background + text
 *   (saved, paid, "auto-categorised" hints).
 * - [deficitContainer] / [onDeficitContainer]: red-tinted background + text
 *   (overspent, overdue).
 * - [warning], [warningContainer] / [onWarningContainer]: amber for cautions
 *   (possible duplicate, needs review, different amount).
 * Never use raw `Color(0x…)` in screens: pick one of these or a MaterialTheme role.
 */
@Immutable
data class FinanceColors(
    val income: Color,
    val expense: Color,
    val surplusContainer: Color,
    val onSurplusContainer: Color,
    val deficitContainer: Color,
    val onDeficitContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

private val LightFinance = FinanceColors(
    income = Color(0xFF1E7A55),
    expense = Color(0xFFC0392B),
    surplusContainer = Color(0xFFD5F2E3),
    onSurplusContainer = Color(0xFF0B4A31),
    deficitContainer = Color(0xFFFCE1DD),
    onDeficitContainer = Color(0xFF7A1D14),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF78350F)
)

private val DarkFinance = FinanceColors(
    income = Color(0xFF7DD6A8),
    expense = Color(0xFFFF9A8C),
    surplusContainer = Color(0xFF16392A),
    onSurplusContainer = Color(0xFFB3EBCD),
    deficitContainer = Color(0xFF45201C),
    onDeficitContainer = Color(0xFFFFD2CB),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF4A3410),
    onWarningContainer = Color(0xFFFDE68A)
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinance }

object SpendTrackerTheme {
    val finance: FinanceColors
        @Composable get() = LocalFinanceColors.current
}

@Composable
fun SpendTrackerTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalFinanceColors provides if (darkTheme) DarkFinance else LightFinance) {
        MaterialTheme(
            colorScheme = colors,
            typography = SpendTypography,
            shapes = SpendShapes,
            content = content
        )
    }
}
