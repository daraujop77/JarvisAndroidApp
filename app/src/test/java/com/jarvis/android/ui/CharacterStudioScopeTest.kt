package com.jarvis.android.ui

import com.jarvis.android.transport.live.VisualExternalCharacter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Previously selecting an external character immediately emptied its picker. */
class CharacterStudioScopeTest {
    private val naruto = VisualExternalCharacter(
        character_id = "reference:naruto",
        display_name = "Naruto",
        franchise = "Naruto",
    )
    private val sasuke = VisualExternalCharacter(
        character_id = "reference:sasuke",
        display_name = "Sasuke",
        franchise = "Naruto",
    )

    @Test fun sameProjectCharacterSwitchKeepsReferenceCatalogWithoutWiki() {
        val initial = JarvisViewModel.CharacterStudioState(
            projectId = "story",
            externalCharacters = listOf(naruto, sasuke),
        )
        val selected = newCharacterStudioSelection(
            initial, "story", "reference:naruto")
        assertEquals("reference:naruto", selected.characterId)
        assertEquals(listOf(naruto, sasuke), selected.externalCharacters)
        val next = newCharacterStudioSelection(
            selected, "story", "reference:sasuke")
        assertEquals("reference:sasuke", next.characterId)
        assertEquals(listOf(naruto, sasuke), next.externalCharacters)
    }

    @Test fun changingCharacterDiscardsMediaErrorsAndPreviousDetails() {
        val initial = JarvisViewModel.CharacterStudioState(
            projectId = "story",
            characterId = "reference:naruto",
            externalCharacters = listOf(naruto, sasuke),
            attachmentIds = mapOf("asset-private" to "local-private"),
            loadingAssetIds = setOf("asset-in-flight"),
            assetErrors = mapOf("asset-failure" to "error"),
            notice = "prior image",
            error = "prior failure",
            busy = true,
        )
        val selected = newCharacterStudioSelection(
            initial, "story", "reference:sasuke")
        assertTrue(selected.attachmentIds.isEmpty())
        assertTrue(selected.loadingAssetIds.isEmpty())
        assertTrue(selected.assetErrors.isEmpty())
        assertNull(selected.notice)
        assertNull(selected.error)
        assertFalse(selected.busy)
        assertNull(selected.detail)
        assertTrue(selected.assets.isEmpty())
        assertTrue(selected.characterVariants.isEmpty())
    }

    @Test fun differentProjectCannotInheritExternalCharacters() {
        val current = JarvisViewModel.CharacterStudioState(
            projectId = "project-a",
            characterId = "reference:naruto",
            externalCharacters = listOf(naruto),
            attachmentIds = mapOf("secret" to "private-image"),
        )
        val switched = newCharacterStudioSelection(
            current, "project-b", "character:alexander")
        assertTrue(switched.externalCharacters.isEmpty())
        assertTrue(switched.attachmentIds.isEmpty())
        assertNull(switched.masterRoster)
        assertNull(switched.projectStyle)
        assertEquals("project-b", switched.projectId)
    }

    @Test fun copilotSelectionWaitsForExactCharacterButOnlyHandlesRequestOnce() {
        val external = listOf("character:naruto", "character:sasuke")
        assertNull(pendingCopilotCharacterSelection(
            requestNumber = 2, lastHandledRequest = 1,
            requestedCharacterId = "character:alexander", availableCharacterIds = external,
        ))
        assertEquals("character:alexander", pendingCopilotCharacterSelection(
            requestNumber = 2, lastHandledRequest = 1,
            requestedCharacterId = "character:alexander",
            availableCharacterIds = external + "character:alexander",
        ))
        // After the first selection the owner may manually select Sasuke.
        // A later refresh of the same catalog must not select Alexander again.
        assertNull(pendingCopilotCharacterSelection(
            requestNumber = 2, lastHandledRequest = 2,
            requestedCharacterId = "character:alexander",
            availableCharacterIds = external + "character:alexander",
        ))
        assertEquals("character:naruto", pendingCopilotCharacterSelection(
            requestNumber = 3, lastHandledRequest = 2,
            requestedCharacterId = "character:naruto", availableCharacterIds = external,
        ))
    }

    @Test fun unknownOrStaleCopilotRequestsCannotSelectAnUnrelatedCharacter() {
        val available = listOf("character:alexander")
        assertNull(pendingCopilotCharacterSelection(
            0, 0, "character:alexander", available))
        assertNull(pendingCopilotCharacterSelection(
            5, 5, "character:alexander", available))
        assertNull(pendingCopilotCharacterSelection(
            5, 4, "character:impostor", available))
        assertNull(pendingCopilotCharacterSelection(
            5, 4, "", available))
    }
}
