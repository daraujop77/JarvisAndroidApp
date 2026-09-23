package com.jarvis.android.transport.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatErrorTextTest {

    @Test
    fun webResearchOutageIsExplainedWithoutTheRawEnvelope() {
        val body = """{"schema":"jarvis.web.error.v1","error":"web_research_unavailable","message":"Hermes web search failed: research_failed"}"""
        val text = ChatErrorText.fromHttp(503, body)
        assertFalse(text.contains("{"))
        assertFalse(text.contains("Hermes"))
        assertEquals(
            "Web search isn't available right now. Try again, or ask something that doesn't need current information.",
            text,
        )
    }

    @Test
    fun unknownServerErrorFallsBackToStatusCode() {
        assertEquals(
            "JARVIS's server had a problem (502). Try again in a moment.",
            ChatErrorText.fromHttp(502, "<html>bad gateway</html>"),
        )
    }

    @Test
    fun rateLimitIsNamed() {
        assertEquals("Too many requests. Wait a moment and try again.", ChatErrorText.fromHttp(429, ""))
    }
}
