package com.jarvis.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Formal JARVIS HUD tokens. Packed as ARGB ints so contrast tests can run
 * on the JVM without Compose Color. These stay in-app; there is no downloadable
 * theme and no network asset.
 */
object JarvisTokens {
    val CYAN = 0xFF22D3EE.toInt()
    val CYAN_BRIGHT = 0xFF67E8F9.toInt()
    val CYAN_DEEP = 0xFF0E7490.toInt()
    val VIOLET = 0xFF8B5CF6.toInt()
    val AMBER = 0xFFFACC15.toInt()
    val GREEN = 0xFF22C55E.toInt()
    val RED = 0xFFF87171.toInt()
    val DEEP_SPACE = 0xFF060B16.toInt()
    val SURFACE_1 = 0xFF0E1626.toInt()
    val SURFACE_2 = 0xFF16223A.toInt()
    val INK = 0xFFE8EEF8.toInt()
    val MUTED = 0xFFB7C7DC.toInt()
    val OUTLINE = 0xFF2A3B57.toInt()
    val ON_PRIMARY = 0xFF041018.toInt()
    val BACKDROP_TOP = 0xFF081020.toInt()
    val BACKDROP_BOTTOM = 0xFF0A1424.toInt()
    val USER_BUBBLE_START = 0xFF0E7490.toInt()
    val USER_BUBBLE_END = 0xFF155E75.toInt()
    val OFFLINE = 0xFF8095B0.toInt()
    val GRID = 0x1A22D3EE
    val NAV_UNSELECTED = 0xFF9FB3CE.toInt()
}

fun tokenColor(argb: Int): Color = Color(argb)
