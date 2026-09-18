package com.jarvis.android.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local rules only: grading and reward gates, no transport and no Android. */
class LearningRoomTest {

    @Test
    fun gradingIgnoresCaseAndSurroundingSpace() {
        assertTrue(LearningCatalog.check("math-1", " 12 ", emptySet()))
        assertTrue(LearningCatalog.check("geo-1", "Paris", emptySet()))
    }

    @Test
    fun wrongOrUnknownAnswersFail() {
        assertFalse(LearningCatalog.check("math-1", "13", emptySet()))
        assertFalse(LearningCatalog.check("nope", "12", emptySet()))
    }

    @Test
    fun completedLessonCannotBeAnsweredAgain() {
        assertFalse(LearningCatalog.check("math-1", "12", setOf("math-1")))
    }

    @Test
    fun rewardsUnlockInOrderAndNeverGoBackwards() {
        assertEquals(1, LearningCatalog.unlockedCount(0))
        assertEquals(2, LearningCatalog.unlockedCount(2))
        assertEquals(LearningCatalog.rewards.size, LearningCatalog.unlockedCount(LearningCatalog.lessons.size))

        val atTwo = LearningCatalog.rewardsFor(2)
        assertTrue(atTwo.first { it.reward.id == "theme-nebula" }.unlocked)
        assertFalse(atTwo.first { it.reward.id == "orb-gold" }.unlocked)
    }

    @Test
    fun themeStepsUpAndNeverBack() {
        assertEquals(RewardTheme.DEFAULT, LearningCatalog.themeFor(emptySet()))
        assertEquals(RewardTheme.NEBULA, LearningCatalog.themeFor(setOf("math-1", "words-1")))
        assertEquals(RewardTheme.GOLD, LearningCatalog.themeFor(setOf("math-1", "words-1", "science-1", "logic-1")))
        assertEquals(RewardTheme.DEFAULT, LearningCatalog.themeFor(setOf("not-a-lesson", "also-no")))
    }

    @Test
    fun progressNeverCrossesProfiles() {
        val stored = setOf(LessonProgress.encode("Ana", "math-1"), LessonProgress.encode("ana", "words-1"))
        assertEquals(setOf("math-1", "words-1"), LessonProgress.completedFor(stored, "Ana"))
        assertTrue(LessonProgress.completedFor(stored, "Luis").isEmpty())
    }

    @Test
    fun profileIdsStayStableAndSafe() {
        assertEquals("ana_maria", LearnerProfiles.sanitize("Ana María"))
        assertEquals(LearnerProfiles.DEFAULT_ID, LearnerProfiles.sanitize("   "))
    }

    @Test
    fun championRequiresEveryLesson() {
        val oneShort = LearningCatalog.unlockedCount(LearningCatalog.lessons.size - 1)
        assertFalse(LearningCatalog.rewardsFor(LearningCatalog.lessons.size - 1)
            .first { it.reward.id == "champion" }.unlocked)
        assertTrue(oneShort < LearningCatalog.rewards.size)
    }
}
