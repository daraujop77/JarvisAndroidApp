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

    private val facts = listOf(
        LoreFact("The city floods.", "canon"),
        LoreFact("Mara knows the way.", "character", knownTo = setOf("Mara")),
        LoreFact("The ending.", "author"),
        LoreFact("The betrayal.", "locked"),
    )

    @Test
    fun aCharacterSeesOnlyCanonAndWhatTheyKnow() {
        val visible = LoreSearch.visibleFacts(facts, "Mara").map { it.claim }
        assertEquals(listOf("The city floods.", "Mara knows the way."), visible)
    }

    @Test
    fun anotherCharacterDoesNotInheritThatKnowledge() {
        val visible = LoreSearch.visibleFacts(facts, "Alexander").map { it.claim }
        assertEquals(listOf("The city floods."), visible)
    }

    @Test
    fun authorSecretsAndLockedFuturesNeverReachACharacter() {
        val scopes = LoreSearch.visibleFacts(facts, "Mara").map { it.scope }
        assertTrue("author" !in scopes && "locked" !in scopes)
    }
}
