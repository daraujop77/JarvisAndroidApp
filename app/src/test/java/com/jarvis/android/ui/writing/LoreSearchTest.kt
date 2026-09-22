package com.jarvis.android.ui.writing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoreSearchTest {

    private val entries = listOf(
        "Alexander" to "Lead",
        "William" to "Character",
        "Melody" to "Character",
    )

    @Test
    fun blankQueryKeepsTheGivenOrder() {
        assertEquals(entries, LoreSearch.filter(entries, "   "))
    }

    @Test
    fun matchesNameRegardlessOfCase() {
        assertEquals(listOf("Melody" to "Character"), LoreSearch.filter(entries, "mel"))
    }

    @Test
    fun matchesRoleToo() {
        assertEquals(listOf("Alexander" to "Lead"), LoreSearch.filter(entries, "lead"))
    }

    @Test
    fun noMatchReturnsEmptyRatherThanEverything() {
        assertTrue(LoreSearch.filter(entries, "dragon").isEmpty())
    }
}
