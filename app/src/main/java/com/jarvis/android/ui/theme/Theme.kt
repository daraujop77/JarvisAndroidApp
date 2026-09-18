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
val JarvisCyan = tokenColor(JarvisTokens.CYAN)
val JarvisCyanBright = tokenColor(JarvisTokens.CYAN_BRIGHT)
val JarvisCyanDeep = tokenColor(JarvisTokens.CYAN_DEEP)
val JarvisViolet = tokenColor(JarvisTokens.VIOLET)
val JarvisAmber = tokenColor(JarvisTokens.AMBER)
val JarvisGreen = tokenColor(JarvisTokens.GREEN)
val JarvisRed = tokenColor(JarvisTokens.RED)

private val DeepSpace = tokenColor(JarvisTokens.DEEP_SPACE)
private val Surface1 = tokenColor(JarvisTokens.SURFACE_1)
private val Surface2 = tokenColor(JarvisTokens.SURFACE_2)

/** Default body/title ink on the HUD. Never black. */
val HudInk = tokenColor(JarvisTokens.INK)
val HudMuted = tokenColor(JarvisTokens.MUTED)

private val JarvisDark = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = tokenColor(JarvisTokens.ON_PRIMARY),
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
    outline = tokenColor(JarvisTokens.OUTLINE),
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
    backdrop = Brush.verticalGradient(
        listOf(
            tokenColor(JarvisTokens.BACKDROP_TOP),
            DeepSpace,
            tokenColor(JarvisTokens.BACKDROP_BOTTOM),
        ),
    ),
    orbGlow = JarvisCyan,
    userBubble = Brush.linearGradient(
        listOf(
            tokenColor(JarvisTokens.USER_BUBBLE_START),
            tokenColor(JarvisTokens.USER_BUBBLE_END),
        ),
    ),
    assistantBubble = Surface2, // always a dark fill so on-surface text stays light
    online = JarvisGreen,
    degraded = JarvisAmber,
    offline = tokenColor(JarvisTokens.OFFLINE),
    grid = Color(JarvisTokens.GRID),
)

val LocalJarvisAccents = staticCompositionLocalOf { DarkAccents }

private fun TextStyle.hud(color: Color = HudInk) = copy(color = color)

private val JarvisTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.hud(),
        displayMedium = base.displayMedium.hud(),
        displaySmall = base.displaySmall.hud(),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Light, letterSpacing = 2.sp).hud(),
        headlineMedium = base.headlineMedium.hud(),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Light, letterSpacing = 1.sp).hud(),
        titleLarge = base.titleLarge.hud(),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium).hud(),
        titleSmall = base.titleSmall.hud(),
        bodyLarge = base.bodyLarge.hud(),
        bodyMedium = base.bodyMedium.hud(),
        bodySmall = base.bodySmall.hud(HudMuted),
        labelLarge = base.labelLarge.copy(letterSpacing = 0.6.sp).hud(),
        labelMedium = base.labelMedium.hud(HudMuted),
        labelSmall = base.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.4.sp,
        ).hud(HudMuted),
    )
}

/** Monospaced, wide-tracked style for HUD-style readouts. */
val HudTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.5.sp,
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
    unfocusedBorderColor = tokenColor(JarvisTokens.OUTLINE),
    disabledBorderColor = tokenColor(JarvisTokens.OUTLINE).copy(alpha = 0.5f),
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
