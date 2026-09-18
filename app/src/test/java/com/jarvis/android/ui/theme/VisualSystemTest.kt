package com.jarvis.android.ui.theme

import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.home.JarvisVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSystemTest {

    @Test
    fun reducedMotionCollapsesDurationsEvenWhenIntensityIsStandard() {
        assertEquals(
            0,
            JarvisVisualSystem.durationMs(
                reducedMotion = true,
                intensity = AnimationIntensity.STANDARD,
                durationMs = 420,
            ),
        )
        assertNull(
            JarvisVisualSystem.loopMs(
                reducedMotion = true,
                intensity = AnimationIntensity.SUBTLE,
                durationMs = 1800,
            ),
        )
        assertEquals(0f, JarvisVisualSystem.motionScale(true, AnimationIntensity.STANDARD))
        assertEquals(0f, JarvisVisualSystem.orbSpeed(OrbActivity.THINKING, true, AnimationIntensity.STANDARD))
        val pulse = JarvisVisualSystem.orbPulse(true, AnimationIntensity.STANDARD)
        assertEquals(1f, pulse.start)
        assertEquals(1f, pulse.endInclusive)
    }

    @Test
    fun subtleIntensityScalesButNeverOverridesReducedMotion() {
        val standard = JarvisVisualSystem.durationMs(false, AnimationIntensity.STANDARD, 280)
        val subtle = JarvisVisualSystem.durationMs(false, AnimationIntensity.SUBTLE, 280)
        assertEquals(280, standard)
        assertTrue(subtle in 1 until standard)
        assertEquals(
            0,
            JarvisVisualSystem.durationMs(true, AnimationIntensity.SUBTLE, 280),
        )
    }

    @Test
    fun ambientEnvelopeIsBounded() {
        JarvisVisualState.entries.forEach { state ->
            val drift = JarvisVisualSystem.ambientDriftScale(state, false, AnimationIntensity.STANDARD)
            val bloom = JarvisVisualSystem.ambientBloom(state, false, AnimationIntensity.STANDARD)
            assertTrue(drift in 0f..1.25f)
            assertTrue(bloom in 0.35f..1.35f)
            assertEquals(
                0f,
                JarvisVisualSystem.ambientDriftScale(state, true, AnimationIntensity.STANDARD),
            )
        }
        assertEquals(
            0f,
            JarvisVisualSystem.ambientDriftScale(
                JarvisVisualState.OFFLINE,
                reducedMotion = false,
                intensity = AnimationIntensity.STANDARD,
            ),
        )
    }

    @Test
    fun phoneTabletBreakpoints() {
        val phone = JarvisVisualSystem.layout(411f, 891f)
        assertEquals(WidthClass.COMPACT, phone.width)
        assertFalse(phone.useNavigationRail)
        val tablet = JarvisVisualSystem.layout(840f, 1200f)
        assertEquals(WidthClass.EXPANDED, tablet.width)
        assertTrue(tablet.useNavigationRail)
        assertTrue(tablet.maxContentWidthDp <= 720)
        val short = JarvisVisualSystem.layout(411f, 500f)
        assertEquals(104, short.orbHeroDp)
    }

    @Test
    fun densityTokensStayLocalAndCompactIsTighter() {
        assertEquals(UiDensity.COMPACT, JarvisVisualSystem.densityOf("compact"))
        assertEquals(UiDensity.COMFORTABLE, JarvisVisualSystem.densityOf("unknown"))
        assertTrue(
            JarvisVisualSystem.pagePaddingDp(UiDensity.COMPACT) <
                JarvisVisualSystem.pagePaddingDp(UiDensity.COMFORTABLE),
        )
        assertEquals("subtle", JarvisVisualSystem.persist(AnimationIntensity.SUBTLE))
        assertEquals("comfortable", JarvisVisualSystem.persist(UiDensity.COMFORTABLE))
    }

    @Test
    fun darkThemeInkPassesWcagAaOnHudSurfaces() {
        assertTrue(JarvisVisualSystem.passesWcagAa(JarvisTokens.INK, JarvisTokens.DEEP_SPACE))
        assertTrue(JarvisVisualSystem.passesWcagAa(JarvisTokens.INK, JarvisTokens.SURFACE_1))
        assertTrue(JarvisVisualSystem.passesWcagAa(JarvisTokens.INK, JarvisTokens.SURFACE_2))
        assertTrue(JarvisVisualSystem.passesWcagAa(JarvisTokens.MUTED, JarvisTokens.DEEP_SPACE))
        assertTrue(JarvisVisualSystem.passesWcagAa(JarvisTokens.MUTED, JarvisTokens.SURFACE_1))
        assertTrue(JarvisVisualSystem.passesWcagAa(JarvisTokens.ON_PRIMARY, JarvisTokens.CYAN))
        assertTrue(JarvisVisualSystem.contrastRatio(JarvisTokens.INK, JarvisTokens.DEEP_SPACE) >= 4.5)
        assertFalse(JarvisVisualSystem.passesWcagAa(JarvisTokens.CYAN_DEEP, JarvisTokens.DEEP_SPACE))
    }
}
