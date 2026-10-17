package com.jarvis.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReplyPresentationTest {
    @Test fun generatedImagesFromBothRoutesUseTheExistingViewer() {
        assertTrue(isGeneratedImageMessage("img_123"))
        assertTrue(isGeneratedImageMessage("agentimg_turn123"))
        assertFalse(isGeneratedImageMessage("user_123"))
    }

    @Test fun backendSourceAppendixBecomesCompactButRemainsClickable() {
        val raw = "Hoy hay lluvia.\n\nFuentes consultadas:\n" +
            "- [KRGV](https://www.krgv.com/weather)\n" +
            "- [Tiempo](https://www.tiempo.com/forecast)"
        val result = splitChatSources(raw)
        assertEquals("Hoy hay lluvia.", result.body)
        assertEquals(2, result.sources.size)
        assertEquals("KRGV", result.sources.first().title)
        assertEquals("https://www.krgv.com/weather", result.sources.first().url)
    }

    @Test fun malformedAndUntrustedAppendicesRemainVisibleInReply() {
        val raw = "Respuesta\n\nFuentes consultadas:\n- [bad](http://localhost/admin)"
        assertEquals(raw, splitChatSources(raw).body)
        assertTrue(splitChatSources(raw).sources.isEmpty())
        assertEquals("Respuesta", splitChatSources("Respuesta").body)
    }
    @Test fun naturalSpanishReferenceFooterIsCompactLikeRealDeviceReport() {
        val raw = "Victor von Doom es un personaje Marvel.\n\nReferencias confiables\n" +
            "• Marvel, ficha oficial\nhttps://www.marvel.com/characters/doctor-doom-victor-von-doom"
        val phone = splitChatSources(raw)
        assertEquals("Victor von Doom es un personaje Marvel.", phone.body)
        assertEquals(1, phone.sources.size)
        assertEquals("Marvel, ficha oficial", phone.sources.single().title)
        val bullets = "Victor von Doom es un personaje Marvel.\n\nReferencias confiables\n" +
            "- Marvel, ficha oficial:\nhttps://www.marvel.com/characters/doctor-doom-victor-von-doom\n" +
            "- Alexander History, capítulos 31–35:\nhttps://docs.google.com/document/d/example/edit"
        val parsed = splitChatSources(bullets)
        assertEquals("Victor von Doom es un personaje Marvel.", parsed.body)
        assertEquals(2, parsed.sources.size)
        assertEquals("Marvel, ficha oficial", parsed.sources[0].title)
        assertEquals("https://docs.google.com/document/d/example/edit", parsed.sources[1].url)
    }

    @Test fun proseWithReferenceHeadingDoesNotDiscardText() {
        val raw = "Referencias\nMi análisis personal empieza aquí.\nNo hay enlaces."
        val parsed = splitChatSources(raw)
        assertEquals(raw, parsed.body)
        assertTrue(parsed.sources.isEmpty())
    }

    @Test fun privateOrUnusualPortReferencesAreNeverMadeClickable() {
        val blocked = listOf(
            "https://localhost/admin",
            "https://127.0.0.1/secret",
            "https://[::1]/secret",
            "https://metadata.internal/credentials",
            "https://example.local/private",
            "https://example.org:8443/private",
            "https://user:password@example.org/private",
            "https://0x7f000001/private",
        )
        blocked.forEach { url ->
            val raw = "Respuesta\n\nFuentes consultadas:\n- [Ref]($url)"
            val rendered = splitChatSources(raw)
            assertEquals(raw, rendered.body)
            assertTrue(rendered.sources.isEmpty())
        }
        val valid = "Respuesta\n\nFuentes consultadas:\n" +
            "- [Ref](https://www.marvel.com/characters/doctor-doom-victor-von-doom)"
        assertEquals(1, splitChatSources(valid).sources.size)
    }

    @Test fun invalidSchemeAndCredentialedSourcesAreNotMadeClickable() {
        val raw = "Respuesta\n\nFuentes\n- Sitio: https://user:secret@example.com/private"
        val parsed = splitChatSources(raw)
        assertEquals(raw, parsed.body)
        assertTrue(parsed.sources.isEmpty())
    }

}
