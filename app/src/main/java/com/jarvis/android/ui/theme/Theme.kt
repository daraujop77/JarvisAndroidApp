package com.jarvis.android.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Core accent used by the orb, glows and active states. */
val JarvisCyan = Color(0xFF22D3EE)
val JarvisCyanBright = Color(0xFF67E8F9)
val JarvisCyanDeep = Color(0xFF0E7490)
val JarvisViolet = Color(0xFF8B5CF6)
val JarvisAmber = Color(0xFFFACC15)
val JarvisGreen = Color(0xFF22C55E)
val JarvisRed = Color(0xFFF87171)

private val DeepSpace = Color(0xFF060B16)
private val Surface1 = Color(0xFF0E1626)
private val Surface2 = Color(0xFF16223A)

/** Default body/title ink on the HUD. Never black. */
val HudInk = Color(0xFFE8EEF8)
val HudMuted = Color(0xFFB7C7DC)

private val JarvisDark = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF041018),
    primaryContainer = JarvisCyanDeep,
    onPrimaryContainer = Color(0xFFCFFAFE),
    secondary = JarvisCyanBright,
    tertiary = JarvisViolet,
    background = DeepSpace,
    onBackground = HudInk,
    surface = Surface1,
    onSurface = HudInk,
    surfaceVariant = Surface2,
    onSurfaceVariant = HudMuted,
    outline = Color(0xFF2A3B57),
    error = JarvisRed,
    errorContainer = Color(0xFF4A1D1D),
)

/**
 * Extra brand values Material3's scheme has no slot for: ambient background
 * gradient, glow tints and the assistant/user bubble fills.
 */
data class JarvisAccents(
    val backdrop: Brush,
    val orbGlow: Color,
    val userBubble: Brush,
    val assistantBubble: Color,
    val online: Color,
    val degraded: Color,
    val offline: Color,
    val grid: Color,
)

private val DarkAccents = JarvisAccents(
    backdrop = Brush.verticalGradient(listOf(Color(0xFF081020), DeepSpace, Color(0xFF0A1424))),
    orbGlow = JarvisCyan,
    userBubble = Brush.linearGradient(listOf(Color(0xFF0E7490), Color(0xFF155E75))),
    assistantBubble = Surface2, // always a dark fill so on-surface text stays light
    online = JarvisGreen,
    degraded = JarvisAmber,
    offline = Color(0xFF8095B0),
    grid = Color(0x1A22D3EE),
)

val LocalJarvisAccents = staticCompositionLocalOf { DarkAccents }

private fun TextStyle.hud(color: Color = HudInk) = copy(color = color)

private val JarvisTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.hud(),
        displayMedium = base.displayMedium.hud(),
        displaySmall = base.displaySmall.hud(),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Light, letterSpacing = 1.5.sp).hud(),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Light, letterSpacing = 0.4.sp).hud(),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp).hud(),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp).hud(),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.05.sp).hud(),
        titleSmall = base.titleSmall.hud(),
        bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp).hud(),
        bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp).hud(),
        bodySmall = base.bodySmall.copy(lineHeight = 18.sp).hud(HudMuted),
        labelLarge = base.labelLarge.copy(letterSpacing = 0.4.sp).hud(),
        labelMedium = base.labelMedium.hud(HudMuted),
        labelSmall = base.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.6.sp,
        ).hud(HudMuted),
    )
}

/** Monospaced, wide-tracked style for HUD-style readouts. */
val HudTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.1.sp,
    fontSize = 11.sp,
    color = HudMuted,
)

@Composable
fun jarvisTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HudInk,
    unfocusedTextColor = HudInk,
    disabledTextColor = HudMuted.copy(alpha = 0.7f),
    cursorColor = JarvisCyan,
    focusedLabelColor = JarvisCyanBright,
    unfocusedLabelColor = HudMuted,
    disabledLabelColor = HudMuted.copy(alpha = 0.6f),
    focusedPlaceholderColor = HudMuted,
    unfocusedPlaceholderColor = HudMuted,
    focusedBorderColor = JarvisCyan.copy(alpha = 0.85f),
    unfocusedBorderColor = Color(0xFF2A3B57),
    disabledBorderColor = Color(0xFF2A3B57).copy(alpha = 0.5f),
    focusedContainerColor = Surface1.copy(alpha = 0.7f),
    unfocusedContainerColor = Surface1.copy(alpha = 0.45f),
    disabledContainerColor = Surface1.copy(alpha = 0.3f),
    focusedTrailingIconColor = HudInk,
    unfocusedTrailingIconColor = HudMuted,
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisDark,
        typography = JarvisTypography,
    ) {
        // Transparent Scaffolds inherit LocalContentColor; without this it stays
        // the platform default (black) and titles/fields vanish on the HUD.
        CompositionLocalProvider(
            LocalJarvisAccents provides DarkAccents,
            LocalContentColor provides HudInk,
            content = content,
        )
    }
}
