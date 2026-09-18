package com.jarvis.android.data.learning

/**
 * On-device learning room for a child profile.
 *
 * This is a local preview, not a server feature: no lesson, answer or reward
 * ever goes to the gateway, and nothing here is a contract. Progress is a set
 * of completed lesson ids; rewards unlock strictly from that set, so the rules
 * can be tested without Android or a backend.
 */
data class Lesson(
    val id: String,
    val title: String,
    val prompt: String,
    /** Compared after trim + lowercase, so casing and surrounding space don't matter. */
    val answer: String,
)

data class Reward(
    val id: String,
    val title: String,
    val description: String,
    /** How many completed lessons it takes. Zero means it is there from the start. */
    val lessonsRequired: Int,
)

data class RewardStatus(val reward: Reward, val unlocked: Boolean)

/**
 * The look the app earns. Each step includes the previous one, so finishing
 * more lessons never takes a reward away.
 */
enum class RewardTheme { DEFAULT, NEBULA, GOLD }

/**
 * One child, one progress. Ids are local and stable so renaming a profile
 * never resets what they already finished. Progress never crosses profiles.
 */
data class LearnerProfile(val id: String, val name: String)

/** `profileId|lessonId` so two children can finish the same lesson independently. */
object LessonProgress {
    fun encode(profileId: String, lessonId: String): String =
        "${LearnerProfiles.sanitize(profileId)}|$lessonId"

    fun completedFor(progress: Set<String>, profileId: String): Set<String> {
        val prefix = "${LearnerProfiles.sanitize(profileId)}|"
        return progress.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
    }
}

object LearnerProfiles {
    const val DEFAULT_ID = "learner_default"

    fun sanitize(id: String): String {
        val folded = java.text.Normalizer.normalize(id.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return folded.take(24).ifBlank { DEFAULT_ID }
    }
}

object LearningCatalog {

    val lessons: List<Lesson> = listOf(
        Lesson("math-1", "Addition", "What is 7 + 5?", "12"),
        Lesson("words-1", "Spelling", "Which is correct: becuz or because?", "because"),
        Lesson("science-1", "Planets", "Which planet is closest to the Sun?", "mercury"),
        Lesson("logic-1", "Patterns", "What comes next: 2, 4, 6, ?", "8"),
        Lesson("geo-1", "Places", "What is the capital of France?", "paris"),
    )

    val rewards: List<Reward> = listOf(
        Reward("starter", "Starter badge", "Yours for showing up.", lessonsRequired = 0),
        Reward("theme-nebula", "Nebula theme", "A new colour theme for JARVIS.", lessonsRequired = 2),
        Reward("orb-gold", "Gold orb", "JARVIS's orb turns gold.", lessonsRequired = 4),
        Reward("champion", "Champion badge", "Finish every lesson.", lessonsRequired = lessons.size),
    )

    fun lesson(id: String): Lesson? = lessons.firstOrNull { it.id == id }

    /** A completed lesson can never be answered again. */
    fun check(lessonId: String, attempt: String, alreadyCompleted: Set<String>): Boolean {
        if (lessonId in alreadyCompleted) return false
        val expected = lesson(lessonId)?.answer ?: return false
        return attempt.trim().lowercase() == expected
    }

    fun rewardsFor(completedCount: Int): List<RewardStatus> =
        rewards.map { RewardStatus(it, completedCount >= it.lessonsRequired) }

    fun unlockedCount(completedCount: Int): Int =
        rewards.count { completedCount >= it.lessonsRequired }

    /** Highest theme earned by the lessons actually completed. Unknown ids don't count. */
    fun themeFor(completed: Set<String>): RewardTheme {
        val done = completed.count { lesson(it) != null }
        return when {
            done >= 4 -> RewardTheme.GOLD
            done >= 2 -> RewardTheme.NEBULA
            else -> RewardTheme.DEFAULT
        }
    }
}
