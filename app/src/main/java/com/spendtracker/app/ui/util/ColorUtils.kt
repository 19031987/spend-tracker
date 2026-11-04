package com.spendtracker.app.ui.util

import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * Centralized semantic financial color palette and color parsing utilities.
 */
object ColorUtils {

    val InflowGreen: Color = Color(0xFF059669)
    val OutflowRed: Color = Color(0xFFDC2626)

    val SurplusContainerLight: Color = Color(0xFFDCFCE7)
    val SurplusTextLight: Color = Color(0xFF15803D)
    val DeficitContainerLight: Color = Color(0xFFFEE2E2)
    val DeficitTextLight: Color = Color(0xFFB91C1C)

    val SurplusContainerDark: Color = Color(0xFF1B3824)
    val SurplusTextDark: Color = Color(0xFF81C784)
    val DeficitContainerDark: Color = Color(0xFF381B1B)
    val DeficitTextDark: Color = Color(0xFFE57373)

    /**
     * Parses a hex color string (e.g. "#FED7AA" or "FED7AA") into a Compose Color.
     * Fallback to pastel amber if invalid.
     */
    fun parseColorHex(hex: String): Color {
        return runCatching {
            val clean = hex.removePrefix("#")
            val parsed = clean.toLong(16)
            if (clean.length == 6) Color(parsed or 0xFF000000L)
            else Color(parsed)
        }.getOrDefault(Color(0xFFFED7AA))
    }

    /**
     * Maps bank name to a Pair of (surface card background tint, badge accent color).
     */
    fun getBankBrandColors(bankName: String): Pair<Color, Color> {
        return when (bankName.lowercase(Locale.ROOT)) {
            "chase" -> Color(0xFF1E3A8A).copy(alpha = 0.08f) to Color(0xFF1D4ED8)
            "hsbc" -> Color(0xFFDC2626).copy(alpha = 0.08f) to Color(0xFFDC2626)
            "monzo" -> Color(0xFFF97316).copy(alpha = 0.08f) to Color(0xFFF97316)
            "starling" -> Color(0xFF0D9488).copy(alpha = 0.08f) to Color(0xFF0D9488)
            "revolut" -> Color(0xFF0284C7).copy(alpha = 0.08f) to Color(0xFF0284C7)
            "barclays" -> Color(0xFF0284C7).copy(alpha = 0.08f) to Color(0xFF0284C7)
            "santander" -> Color(0xFFDC2626).copy(alpha = 0.08f) to Color(0xFFDC2626)
            else -> Color(0x1F79747E) to Color(0xFF6750A4)
        }
    }
}
