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
}
