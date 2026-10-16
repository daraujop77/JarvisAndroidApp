package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.VisualExternalCharacter
import com.jarvis.android.transport.live.WritingWikiEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: Character Studio must not start empty with an empty Wiki. */
class CharacterStudioCatalogTest {
    @Test fun externalReferencesPopulateStudioWithoutWikiCharacters() {
        val external = listOf("Naruto", "Sasuke", "Kakashi", "Boruto").map {
            VisualExternalCharacter(
                character_id = "reference:" + it.lowercase(),
                display_name = it,
                franchise = "Naruto",
            )
        }
        val options = mergeCharacterStudioOptions(emptyList(), external)
        assertEquals(4, options.size)
        assertEquals(setOf("Naruto", "Sasuke", "Kakashi", "Boruto"),
                     options.map { it.name }.toSet())
        assertTrue(options.all { it.authority == "REFERENCE" })
        assertTrue(options.all { it.role.startsWith("Referencia visual externa") })
    }

    @Test fun wikiCanonWinsOverExternalDuplicate() {
        val canon = WritingWikiEntity(
            id = "character:alexander",
            name = "Alexander",
            type = "character",
            authority = "OFFICIAL_CANON",
            appearance = "Canonical Alexander design",
        )
        val external = listOf(
            VisualExternalCharacter(character_id = "character:alexander",
                                    display_name = "Impostor", franchise = "External"),
            VisualExternalCharacter(character_id = "reference:naruto",
                                    display_name = "Naruto", franchise = "Naruto"),
        )
        val options = mergeCharacterStudioOptions(listOf(canon), external)
        assertEquals(2, options.size)
        val alexander = options.single { it.id == "character:alexander" }
        assertEquals("OFFICIAL_CANON", alexander.authority)
        assertEquals("Canonical Alexander design", alexander.appearance)
        assertEquals("Alexander", alexander.name)
        assertEquals("REFERENCE", options.single { it.name == "Naruto" }.authority)
    }

    @Test fun nonCharactersAndBlankIdsNeverAppear() {
        val sources = listOf(
            WritingWikiEntity(id = "chapter:1", type = "chapter", name = "Chapter One"),
            WritingWikiEntity(id = "", type = "character", name = "Unknown"),
        )
        val options = mergeCharacterStudioOptions(
            sources, listOf(VisualExternalCharacter(character_id = "")))
        assertTrue(options.isEmpty())
    }

    @Test fun approvedCopilotMasterRemainsReachableWhenWikiIsUnavailable() {
        val options = mergeCharacterStudioOptions(
            emptyList(), emptyList(), recoveredCharacterId = "character:alexander",
        )
        assertEquals(1, options.size)
        assertEquals("character:alexander", options.single().id)
        assertEquals("REFERENCE", options.single().authority)
        assertTrue(options.single().role.contains("Wiki pendiente"))
        // An untrusted or malformed target must never be offered as an option.
        assertTrue(mergeCharacterStudioOptions(
            emptyList(), emptyList(), recoveredCharacterId = "../foreign",
        ).isEmpty())
        assertTrue(mergeCharacterStudioOptions(
            emptyList(), emptyList(), recoveredCharacterId = "character:../foreign",
        ).isEmpty())
    }

    @Test fun loadedWikiCanonReplacesAnyRecoveredReferencePlaceholder() {
        val official = WritingWikiEntity(
            id = "character:alexander", name = "Alexander",
            canonical_name = "Alexander", type = "character",
            authority = "OFFICIAL_CANON",
        )
        val options = mergeCharacterStudioOptions(
            listOf(official), emptyList(), recoveredCharacterId = "character:alexander",
        )
        assertEquals(1, options.size)
        assertEquals("OFFICIAL_CANON", options.single().authority)
        assertEquals("Alexander", options.single().name)
    }
}
