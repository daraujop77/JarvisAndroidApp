package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.VisualCharacterDirectionProfile
import com.jarvis.android.transport.live.WritingWikiEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotAppearanceIntentTest {
    private val characters = listOf(
        WritingWikiEntity(id = "character:naruto", type = "character",
            canonical_name = "Naruto", name = "Naruto"),
        WritingWikiEntity(id = "character:sasuke", type = "character",
            canonical_name = "Sasuke", name = "Sasuke"),
    )

    @Test fun readAppearanceUsesOnlyActiveWikiIdentity() {
        val intent = resolveCopilotAppearance("Muéstrame la apariencia de Naruto", characters)
        assertNotNull(intent)
        assertEquals("character:naruto", intent?.characterId)
        assertFalse(intent!!.isChange)
        assertEquals("character:sasuke",
            resolveCopilotAppearance("¿Cómo se ve Sasuke?", characters)?.characterId)
        assertNull(resolveCopilotAppearance("Muéstrame la apariencia de Sakura", characters))
    }

    @Test fun naturalChangeUpdatesExactlyOneFieldInDraftOnly() {
        val intent = resolveCopilotAppearance("Cambia el cabello de Naruto a rubio y corto", characters)
        assertNotNull(intent)
        assertEquals("hair", intent?.field)
        assertEquals("rubio y corto", intent?.value)
        val original = VisualCharacterDirectionProfile(
            project_id = "prj_story", character_id = "character:naruto",
            revision = 7, eyes = "azules", hair = "rubio", armor = "sin armadura",
        )
        val updated = applyCopilotAppearanceChange(original, intent!!)
        assertEquals(7, updated?.revision)
        assertEquals("rubio y corto", updated?.hair)
        assertEquals("azules", updated?.eyes)
        assertEquals("sin armadura", updated?.armor)
        assertEquals("rubio", original.hair)
    }

    @Test fun differentCharacterAndAmbiguousNamesFailClosed() {
        assertNull(resolveCopilotAppearance("Cambia pelo de Naruto y Sasuke a rojo", characters))
        assertNull(resolveCopilotAppearance("Cambia pelo de Naruto a rojo y cambia ojos", characters))
        val duplicate = characters + WritingWikiEntity(
            id = "character:other_naruto", type = "character", canonical_name = "Naruto",
        )
        assertNull(resolveCopilotAppearance("Muéstrame apariencia de Naruto", duplicate))
        assertNull(resolveCopilotAppearance("Cambia pelo de Naruto a azul", duplicate))
        assertNull(resolveCopilotAppearance("Cambia pelo de Naruto a x", characters))
    }

    @Test fun preservesCanonAuthorityAndRejectsUnrequestedCommands() {
        assertFalse(looksLikeCopilotAppearanceCommand("Genera imagen de Naruto"))
        assertFalse(looksLikeCopilotAppearanceCommand("Naruto tiene ojos distintos en capítulo 3"))
        assertNull(applyCopilotAppearanceChange(VisualCharacterDirectionProfile(),
            CopilotAppearanceIntent("character:naruto", "Naruto")))
        val field = resolveCopilotAppearance("Modifica la armadura de Sasuke por plateada", characters)
        assertEquals("armor", field?.field)
        assertEquals("plateada", field?.value)
        assertTrue(looksLikeCopilotAppearanceCommand("Modifica la armadura de Sasuke por plateada"))
    }
}
