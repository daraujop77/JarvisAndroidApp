package com.jarvis.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Regression: a Wiki outage cannot prevent read-only recovery of saved portraits. */
class WritingCopilotIndependentRecoveryWiringTest {
    @Test
    fun portraitRecoveryStartsBeforeOptionalWikiAndTimelineRequests() {
        val source = listOf(
            File("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt"),
            File("../app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt"),
        ).firstOrNull { it.isFile }?.readText()
            ?: error("Missing checked-in ViewModel source")

        val begin = source.indexOf("    fun refreshWritingWorkspace(projectId: String) {")
        val end = source.indexOf("    fun recoverWritingCopilotPortraits(projectId: String)", begin)
        assertTrue(begin >= 0 && end > begin)
        val loader = source.substring(begin, end)

        val recovery = loader.indexOf("        recoverWritingCopilotPortraits(projectId)")
        val optionalWiki = loader.indexOf("val wikiHome = ")
        val optionalTimeline = loader.indexOf("val wikiTimeline = ")
        assertTrue(recovery >= 0)
        assertTrue(optionalWiki > recovery && optionalTimeline > recovery)
        assertEquals(
            1,
            Regex("""(?m)^        recoverWritingCopilotPortraits\(projectId\)$""")
                .findAll(loader).count(),
        )
        assertTrue(loader.indexOf("if (publicFailure != null)") > recovery)
    }
}
