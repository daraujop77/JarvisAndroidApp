package com.jarvis.android.data.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryCharactersTest {

    @Test
    fun canonCharactersContainsExpectedRoster() {
        assertEquals(15, CANON_CHARACTERS.size)

        val expectedIds = listOf(
            "alexander",
            "william",
            "melody",
            "el_guardian",
            "emmanuel",
            "soren",
            "buhin",
            "simbin",
            "doom",
            "might_guy",
            "killer_bee",
            "shino",
            "naruto_sasuke",
            "milashita",
            "boruto",
        )

        val actualIds = CANON_CHARACTERS.map { it.id }
        assertEquals(expectedIds.sorted(), actualIds.sorted())
    }

    @Test
    fun findCharacterLocatesExpectedEntities() {
        val alexander = findCharacter("alexander")
        assertNotNull(alexander)
        assertEquals("Alexander", alexander?.name)
        assertEquals(CharacterLifeStatus.ALIVE, alexander?.status)

        val doom = findCharacter("doom")
        assertNotNull(doom)
        assertTrue(doom?.name?.contains("Doom") == true)

        val guy = findCharacter("might_guy")
        assertNotNull(guy)
        assertEquals(CharacterLifeStatus.CRITICAL_INJURY, guy?.status)

        val narutoSasuke = findCharacter("naruto_sasuke")
        assertNotNull(narutoSasuke)
        assertEquals(CharacterLifeStatus.IN_COMA, narutoSasuke?.status)

        val shino = findCharacter("shino")
        assertNotNull(shino)
        assertEquals(CharacterLifeStatus.DECEASED, shino?.status)
    }

    @Test
    fun searchCharactersFiltersCorrectly() {
        val sorenResults = searchCharacters("Soren")
        assertTrue(sorenResults.any { it.id == "soren" })

        val doomResults = searchCharacters("Doom")
        assertTrue(doomResults.any { it.id == "doom" })

        val guyResults = searchCharacters("Guy")
        assertTrue(guyResults.any { it.id == "might_guy" })

        val kuramaResults = searchCharacters("Kurama")
        assertTrue(kuramaResults.any { it.id == "william" })

        val emptyResults = searchCharacters("NonExistentCharacterQuery123")
        assertTrue(emptyResults.isEmpty())
    }

    @Test
    fun relatedCharacterIdsArePopulatedAndValid() {
        val alexander = findCharacter("alexander")
        assertNotNull(alexander)
        assertTrue(alexander!!.relatedCharacterIds.contains("william"))
        assertTrue(alexander.relatedCharacterIds.contains("melody"))
        assertTrue(alexander.relatedCharacterIds.contains("el_guardian"))
        assertTrue(alexander.relatedCharacterIds.contains("emmanuel"))

        val doom = findCharacter("doom")
        assertNotNull(doom)
        assertTrue(doom!!.relatedCharacterIds.contains("el_guardian"))
        assertTrue(doom.relatedCharacterIds.contains("naruto_sasuke"))
        assertTrue(doom.relatedCharacterIds.contains("killer_bee"))

        // Ensure all referenced related character IDs actually exist
        CANON_CHARACTERS.forEach { char ->
            char.relatedCharacterIds.forEach { relId ->
                val target = findCharacter(relId)
                assertNotNull("Related character $relId for ${char.id} should exist", target)
            }
        }
    }

    @Test
    fun canonMilestonesCoverExpectedEras() {
        assertEquals(3, CANON_MILESTONES.size)
        val eras = CANON_MILESTONES.map { it.era }
        assertTrue(eras.contains("Capítulos 1–20"))
        assertTrue(eras.contains("Capítulos 21–29"))
        assertTrue(eras.contains("Capítulos 30–37"))

        CANON_MILESTONES.forEach { milestone ->
            milestone.keyCharacterIds.forEach { charId ->
                assertNotNull("Milestone key character $charId should exist", findCharacter(charId))
            }
        }
    }

    @Test
    fun canonFactionsContainExpectedGroups() {
        assertEquals(5, CANON_FACTIONS.size)
        val factionNames = CANON_FACTIONS.map { it.name }
        assertTrue(factionNames.contains("Nuevo Equipo 7"))
        assertTrue(factionNames.contains("Liderazgo de Konoha"))
        assertTrue(factionNames.contains("Los Marcados del Norte"))
        assertTrue(factionNames.contains("Amenaza Primordial"))
        assertTrue(factionNames.contains("Leyendas Caídas"))

        CANON_FACTIONS.forEach { faction ->
            faction.memberIds.forEach { memberId ->
                assertNotNull("Faction member $memberId should exist", findCharacter(memberId))
            }
        }
    }
}

