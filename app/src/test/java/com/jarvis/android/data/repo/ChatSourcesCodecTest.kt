package com.jarvis.android.data.repo

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ChatSourcesCodecTest {
    private fun receipt(url: String): String = "web:" +
        MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(20)

    @Test fun typedSourcesSurviveEncodingAndRejectForgedRefs() {
        val url = "https://example.org/canon"
        val raw = Json.parseToJsonElement("""
            {"schema":"jarvis.chat.content.v1", "blocks":[
              {"type":"text","text":"Research with URL"}
            ], "sources":[
              {"ref":"${receipt(url)}","title":"Canonical reference","url":"$url"},
              {"ref":"${receipt(url)}","title":"duplicate","url":"$url"},
              {"ref":"web:aaaaaaaaaaaaaaaaaaaa","title":"forged","url":"https://example.org/other"},
              {"ref":"web:bbbbbbbbbbbbbbbbbbbb","title":"local","url":"http://localhost/private"}
            ]}
        """.trimIndent()).jsonObject
        val encoded = ChatSourcesCodec.encodeValidated(raw)
        val sources = ChatSourcesCodec.decode(encoded)
        assertEquals(1, sources.size)
        assertEquals("Canonical reference", sources.single().title)
        assertEquals(url, sources.single().url)
        assertEquals(receipt(url), sources.single().ref)
        assertTrue(ChatSourcesCodec.decode("[{\"ref\":\"garbage\",\"url\":\"file:///etc/passwd\"}]").isEmpty())
    }

    @Test fun missingSchemaOrUntrustedTypedDataNeverBecomesLink() {
        val untyped = Json.parseToJsonElement(
            """{"sources":[{"ref":"web:aaaaaaaaaaaaaaaaaaaa","url":"https://example.org"}]}"""
        ).jsonObject
        assertEquals("", ChatSourcesCodec.encodeValidated(untyped))
        assertTrue(ChatSourcesCodec.decode("").isEmpty())
        assertTrue(ChatSourcesCodec.decode("malformed").isEmpty())
    }
}
