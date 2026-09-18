package com.jarvis.android.ui.theme

import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.home.JarvisVisualState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Local-only visual language. Tokens, density and animation intensity never
 * leave the device and are not a server contract.
 */
enum class AnimationIntensity {
    SUBTLE,
    STANDARD,
}

enum class UiDensity {
    COMPACT,
    COMFORTABLE,
}

enum class WidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

data class AdaptiveLayout(
    val width: WidthClass,
    val twoPane: Boolean,
    val useNavigationRail: Boolean,
    val maxContentWidthDp: Int,
    val orbHeroDp: Int,
)

object JarvisVisualSystem {

    const val INTENSITY_SUBTLE = "subtle"
    const val INTENSITY_STANDARD = "standard"
    const val DENSITY_COMPACT = "compact"
    const val DENSITY_COMFORTABLE = "comfortable"

    const val AA_NORMAL = 4.5
    const val AA_LARGE = 3.0

    fun intensityOf(raw: String?): AnimationIntensity = when (raw?.lowercase()) {
        INTENSITY_SUBTLE -> AnimationIntensity.SUBTLE
        else -> AnimationIntensity.STANDARD
    }

    fun densityOf(raw: String?): UiDensity = when (raw?.lowercase()) {
        DENSITY_COMPACT -> UiDensity.COMPACT
        else -> UiDensity.COMFORTABLE
    }

    fun persist(intensity: AnimationIntensity): String = when (intensity) {
        AnimationIntensity.SUBTLE -> INTENSITY_SUBTLE
        AnimationIntensity.STANDARD -> INTENSITY_STANDARD
    }

    fun persist(density: UiDensity): String = when (density) {
        UiDensity.COMPACT -> DENSITY_COMPACT
        UiDensity.COMFORTABLE -> DENSITY_COMFORTABLE
    }

    /**
     * Decorative duration. Reduced motion always collapses to 0. Subtle
     * intensity halves motion; it never overrides the a11y kill switch.
     */
    fun durationMs(
        reducedMotion: Boolean,
        intensity: AnimationIntensity,
        durationMs: Int,
    ): Int {
        if (reducedMotion) return 0
        val scaled = when (intensity) {
            AnimationIntensity.SUBTLE -> (durationMs * 0.55f).toInt()
            AnimationIntensity.STANDARD -> durationMs
        }
        return scaled.coerceAtLeast(0)
    }

    fun loopMs(
        reducedMotion: Boolean,
        intensity: AnimationIntensity,
        durationMs: Int,
    ): Int? {
        if (reducedMotion) return null
        return durationMs(reducedMotion = false, intensity = intensity, durationMs = durationMs)
            .takeIf { it > 0 }
    }

    fun motionScale(reducedMotion: Boolean, intensity: AnimationIntensity): Float = when {
        reducedMotion -> 0f
        intensity == AnimationIntensity.SUBTLE -> 0.45f
        else -> 1f
    }

    fun orbSpeed(
        activity: OrbActivity,
        reducedMotion: Boolean,
        intensity: AnimationIntensity,
    ): Float {
        if (reducedMotion) return 0f
        val base = when (activity) {
            OrbActivity.IDLE -> 1f
            OrbActivity.LISTENING -> 1.8f
            OrbActivity.PROCESSING -> 2.2f
            OrbActivity.THINKING -> 2.6f
            OrbActivity.SPEAKING -> 2.4f
            OrbActivity.ERROR -> 0.6f
            OrbActivity.OFFLINE -> 0.45f
        }
        return (base * if (intensity == AnimationIntensity.SUBTLE) 0.55f else 1f)
            .coerceIn(0.35f, 2.6f)
    }

    fun orbPulse(reducedMotion: Boolean, intensity: AnimationIntensity): ClosedFloatingPointRange<Float> {
        if (reducedMotion) return 1f..1f
        return if (intensity == AnimationIntensity.SUBTLE) 0.94f..1.02f else 0.86f..1.06f
    }

    /** Ambient drift/bloom stay inside a closed envelope — never runaway glow. */
    fun ambientDriftScale(
        state: JarvisVisualState,
        reducedMotion: Boolean,
        intensity: AnimationIntensity,
    ): Float {
        if (reducedMotion || state == JarvisVisualState.OFFLINE) return 0f
        val base = when (state) {
            JarvisVisualState.BOOT, JarvisVisualState.THINKING -> 1.25f
            JarvisVisualState.LISTENING, JarvisVisualState.RESPONDING -> 1.15f
            JarvisVisualState.EXECUTING, JarvisVisualState.AWAITING_APPROVAL -> 1.1f
            JarvisVisualState.ERROR -> 0.55f
            JarvisVisualState.OFFLINE -> 0f
            JarvisVisualState.IDLE -> 1f
        }
        val scaled = base * if (intensity == AnimationIntensity.SUBTLE) 0.6f else 1f
        return scaled.coerceIn(0f, 1.25f)
    }

    fun ambientBloom(
        state: JarvisVisualState,
        reducedMotion: Boolean,
        intensity: AnimationIntensity,
    ): Float {
        if (reducedMotion) {
            return if (state == JarvisVisualState.OFFLINE) 0.35f else 0.55f
        }
        val base = when (state) {
            JarvisVisualState.BOOT, JarvisVisualState.THINKING -> 1.35f
            JarvisVisualState.LISTENING -> 1.25f
            JarvisVisualState.RESPONDING -> 1.4f
            JarvisVisualState.EXECUTING, JarvisVisualState.AWAITING_APPROVAL -> 1.2f
            JarvisVisualState.ERROR -> 0.55f
            JarvisVisualState.OFFLINE -> 0.35f
            JarvisVisualState.IDLE -> 1f
        }
        val scaled = base * if (intensity == AnimationIntensity.SUBTLE) 0.7f else 1f
        return scaled.coerceIn(0.35f, 1.35f)
    }

    fun layout(widthDp: Float, heightDp: Float = 800f): AdaptiveLayout {
        val width = when {
            widthDp >= 840f -> WidthClass.EXPANDED
            widthDp >= 600f -> WidthClass.MEDIUM
            else -> WidthClass.COMPACT
        }
        val compactHeight = heightDp < 560f
        return AdaptiveLayout(
            width = width,
            twoPane = width != WidthClass.COMPACT,
            useNavigationRail = width == WidthClass.EXPANDED,
            maxContentWidthDp = if (width == WidthClass.EXPANDED) 720 else 600,
            orbHeroDp = when {
                compactHeight -> 104
                width == WidthClass.EXPANDED -> 132
                else -> 108
            },
        )
    }

    fun pagePaddingDp(density: UiDensity): Int = if (density == UiDensity.COMPACT) 10 else 16
    fun cardPaddingDp(density: UiDensity): Int = if (density == UiDensity.COMPACT) 12 else 16
    fun sectionGapDp(density: UiDensity): Int = if (density == UiDensity.COMPACT) 10 else 14
    fun cardRadiusDp(density: UiDensity): Int = if (density == UiDensity.COMPACT) 14 else 18

    fun relativeLuminance(argb: Int): Double {
        fun channel(value: Int): Double {
            val s = (value and 0xFF) / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = channel(argb shr 16)
        val g = channel(argb shr 8)
        val b = channel(argb)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun contrastRatio(foregroundArgb: Int, backgroundArgb: Int): Double {
        val l1 = relativeLuminance(foregroundArgb)
        val l2 = relativeLuminance(backgroundArgb)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun passesWcagAa(foregroundArgb: Int, backgroundArgb: Int, largeText: Boolean = false): Boolean {
        val need = if (largeText) AA_LARGE else AA_NORMAL
        return contrastRatio(foregroundArgb, backgroundArgb) >= need
    }
}
