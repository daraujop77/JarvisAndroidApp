package com.jarvis.android.transport.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PCB-LIVE-1 fail-closed URL policy: the bearer token may only ever be sent to
 * an approved private front door (plan §23 / runbook §9).
 */
class LiveHostPolicyTest {

    private fun allowed(host: String) = JarvisAppSession.isAllowedLiveHost(host)

    @Test
    fun tailscaleMagicDnsAndCgnatAreAllowed() {
        assertTrue(allowed("http://desktop-l59hjk4"))
        assertTrue(allowed("http://desktop-l59hjk4.api-magicdns.ts.net"))
        assertTrue(allowed("http://100.95.123.102:8787"))
    }

    @Test
    fun loopbackAndRfc1918AreAllowed() {
        assertTrue(allowed("http://127.0.0.1:8787"))
        assertTrue(allowed("http://localhost:8787"))
        assertTrue(allowed("http://192.168.1.20:8080"))
        assertTrue(allowed("http://10.0.0.5"))
        assertTrue(allowed("http://172.16.0.9"))
    }

    @Test
    fun publicHostsAreRejected() {
        assertFalse(allowed("https://jarvis.example.com"))
        assertFalse(allowed("https://api.openai.com"))
        assertFalse(allowed("http://8.8.8.8"))
        assertFalse(allowed("http://172.32.0.1")) // just outside RFC1918
        assertFalse(allowed("http://100.63.0.1")) // just outside CGNAT /10
        assertFalse(allowed(""))
        assertFalse(allowed("not a url"))
    }

    @Test
    fun normalizeBaseAddsSchemeAndStripsTrailingSlash() {
        assertEquals("http://desktop-l59hjk4", JarvisAppSession.normalizeBase("desktop-l59hjk4/"))
        assertEquals("https://host.ts.net", JarvisAppSession.normalizeBase(" https://host.ts.net///"))
        assertEquals(null, JarvisAppSession.normalizeBase("   "))
    }

    @Test
    fun memoryStoreRoundTripsSessionFields() {
        val store = JarvisAppSession.MemoryStore()
        val session = JarvisAppSession(store)
        assertEquals(null, session.token)
        assertFalse(session.isAuthenticated)

        store.put(
            mapOf(
                JarvisAppSession.KEY_TOKEN to "tok",
                JarvisAppSession.KEY_BASE to "http://desktop-l59hjk4",
                JarvisAppSession.KEY_USER_ID to "u1",
                JarvisAppSession.KEY_ROLE to "owner",
            ),
        )
        assertEquals("tok", session.token)
        assertEquals("Bearer tok", session.authHeader())
        assertTrue(session.isAuthenticated)
        assertTrue(session.deviceId.startsWith("android_"))
        assertEquals(session.deviceId, session.deviceId) // stable across calls
        session.clear()
        assertEquals(null, session.token)
    }
}
