package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.WritingWikiEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotPortraitIntentTest {
    private val wiki = listOf(
        WritingWikiEntity(id = "character:naruto", type = "character", name = "Naruto", canonical_name = "Naruto"),
        WritingWikiEntity(id = "character:naruto-uzumaki", type = "character", name = "Naruto Uzumaki", canonical_name = "Naruto Uzumaki"),
        WritingWikiEntity(id = "character:sasuke", type = "character", name = "Sasuke", canonical_name = "Sasuke"),
        WritingWikiEntity(id = "location:konoha", type = "location", name = "Konoha", canonical_name = "Konoha"),
    )

    @Test fun explicitPortraitResolvesCanonicalId() {
        val request = resolveCopilotPortrait("Genera retrato de Naruto", wiki)
        assertEquals("character:naruto", request?.characterId)
        assertEquals("", request?.adjustment)
    }

    @Test fun longerCharacterNameWinsOverShortName() {
        val request = resolveCopilotPortrait("Crea una imagen de Naruto Uzumaki con su chaqueta", wiki)
        assertEquals("character:naruto-uzumaki", request?.characterId)
        assertEquals("con su chaqueta", request?.adjustment)
    }

    @Test fun doesNotInferMissingOrAmbiguousCharacter() {
        assertNull(resolveCopilotPortrait("Genera retrato de Boruto", wiki))
        assertNull(resolveCopilotPortrait("Genera retrato de Naruto y Sasuke", wiki))
        assertNull(resolveCopilotPortrait("Genera retrato de Konoha", wiki))
    }

    @Test fun additionalWikiCharacterInPromptRequiresClarification() {
        assertNull(resolveCopilotPortrait("Genera retrato de Naruto con Sasuke", wiki))
        assertNull(resolveCopilotPortrait("Haz un retrato de Naruto, con Sasuke al lado", wiki))
        val valid = resolveCopilotPortrait("Genera retrato de Naruto con su chaqueta naranja", wiki)
        assertEquals("character:naruto", valid?.characterId)
    }

    @Test fun normalChatAndRequestsWithoutExplicitPortraitDoNotTrigger() {
        assertFalse(looksLikeCopilotPortraitCommand("¿Cómo creamos retratos de Naruto?"))
        assertFalse(looksLikeCopilotPortraitCommand("Dibuja una batalla de Naruto y Sasuke"))
        assertTrue(looksLikeCopilotPortraitCommand("Genera retrato de Naruto"))
    }

    @Test fun collisionRequiresClarification() {
        val duplicates = wiki + WritingWikiEntity(
            id = "character:naruto-alt", type = "character",
            name = "Naruto", canonical_name = "Naruto",
        )
        assertNull(resolveCopilotPortrait("Genera retrato de Naruto", duplicates))
    }
}
