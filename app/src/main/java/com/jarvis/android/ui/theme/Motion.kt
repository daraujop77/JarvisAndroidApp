package com.jarvis.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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

    /** JVM-testable: decorative duration collapses to 0 when reduced motion is on. */
    fun durationMs(reducedMotion: Boolean, durationMs: Int): Int =
        if (reducedMotion) 0 else durationMs

    fun loopMs(reducedMotion: Boolean, durationMs: Int): Int? =
        if (reducedMotion) null else durationMs

    /** Entrance for chat bubbles and cards. */
    @Composable
    @ReadOnlyComposable
    fun <T> enter(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) tween(0)
        else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Standard state cross-fade / colour change. */
    @Composable
    @ReadOnlyComposable
    fun <T> standard(durationMs: Int = 280): FiniteAnimationSpec<T> =
        tween(durationMs(LocalReducedMotion.current, durationMs), easing = EmphasizedEasing)

    /** Loop duration helper: returns null when looping motion should be skipped. */
    @Composable
    @ReadOnlyComposable
    fun loopMs(durationMs: Int): Int? =
        loopMs(LocalReducedMotion.current, durationMs)
}
