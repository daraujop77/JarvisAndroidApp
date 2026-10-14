package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.JarvisChatContent
import com.jarvis.android.transport.live.JarvisChatContentSource
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotPublicSourcesTest {
    private fun receipt(url: String) = "web:" +
        MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(20)

    @Test fun verifiedWebSourcesAreVisibleOnlyUnderTypedContract() {
        val url = "https://example.org/character-reference"
        val item = JarvisChatContentSource(
            title = "Public character reference", url = url, ref = receipt(url))
        val typed = JarvisChatContent(
            schema = "jarvis.chat.content.v1", sources = listOf(item, item))
        val sources = verifiedCopilotPublicSources(typed)
        assertEquals(1, sources.size)
        assertEquals(url, sources.single().url)
        assertEquals("Public character reference", sources.single().title)
        assertTrue(verifiedCopilotPublicSources(
            typed.copy(schema = "jarvis.unverified")).isEmpty())
    }

    @Test fun forgedPrivateOrInsecureWebLinksNeverBecomeClickable() {
        val valid = "https://example.org/real"
        val urls = listOf(
            "https://127.0.0.1/private", "https://localhost/internal",
            "http://example.org/unsecured",
            "https://192.168.1.2/admin", "file:///etc/passwd",
            "https://user:password@example.org/private",
            "https://169.254.169.254/latest/meta-data/",
            "https://[::1]/admin",
            "https://router.lan/",
            "https://example.org./",
        )
        val hostile = urls.map { url ->
            JarvisChatContentSource(ref = receipt(url), url = url, title = "Forged")
        } + JarvisChatContentSource(
            ref = "web:aaaaaaaaaaaaaaaaaaaa", url = valid, title = "Bad digest")
        val body = JarvisChatContent(schema = "jarvis.chat.content.v1", sources = hostile)
        assertTrue(verifiedCopilotPublicSources(body).isEmpty())
        assertTrue(verifiedCopilotPublicSources(null).isEmpty())
    }
}
