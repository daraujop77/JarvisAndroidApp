package com.jarvis.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * When true, decorative/looping motion is disabled and transitions collapse to
 * near-instant. Driven by the user's Settings toggle plus the OS animation
 * scale, so the app honours accessibility preferences (plan AND-W2 lane:
 * "accessibility/reduced-motion").
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

object JarvisMotion {

    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Entrance for chat bubbles and cards. */
    @Composable
    @ReadOnlyComposable
    fun <T> enter(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) tween(0)
        else tween(180, easing = EmphasizedEasing)

    /** Standard state cross-fade / colour change. */
    @Composable
    @ReadOnlyComposable
    fun <T> standard(durationMs: Int = 180): FiniteAnimationSpec<T> =
        tween(if (LocalReducedMotion.current) 0 else durationMs, easing = EmphasizedEasing)

    /** Loop duration helper: returns null when looping motion should be skipped. */
    @Composable
    @ReadOnlyComposable
    fun loopMs(durationMs: Int): Int? =
        if (LocalReducedMotion.current) null else durationMs
}
