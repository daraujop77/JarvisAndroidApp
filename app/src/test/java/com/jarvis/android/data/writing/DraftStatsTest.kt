package com.jarvis.android.data.writing

import org.junit.Assert.assertEquals
import org.junit.Test

class DraftStatsTest {

    @Test
    fun blankIsZeroWords() {
        assertEquals(0, DraftStats.words(""))
        assertEquals(0, DraftStats.words("   \n\t"))
    }

    @Test
    fun countsWordsNotWhitespace() {
        assertEquals(4, DraftStats.words("  one two\nthree   four  "))
    }
}
