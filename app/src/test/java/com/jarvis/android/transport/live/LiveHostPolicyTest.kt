package com.jarvis.android.transport.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Current live URL policy: no Tailscale; HTTPS in production, HTTP allowed only locally/LAN. */
class LiveHostPolicyTest {

    private fun allowed(host: String) = JarvisAppSession.isAllowedLiveHost(host)

    @Test
    fun loopbackAndRfc1918AreAllowed() {
        assertTrue(allowed("http://127.0.0.1:8787"))
        assertTrue(allowed("http://localhost:8787"))
        assertTrue(allowed("http://192.168.1.20:8080"))
        assertTrue(allowed("http://10.0.0.5"))
        assertTrue(allowed("http://172.16.0.9"))
    }

    @Test
    fun productionHttpsHostsAreAllowedButCleartextPublicHostsAreRejected() {
        assertTrue(allowed("https://jarvis.example.com"))
        assertTrue(allowed("https://203.0.113.10:8443"))
        assertFalse(allowed("http://jarvis.example.com"))
        assertFalse(allowed("http://203.0.113.10:8443"))
    }

    @Test
    fun retiredTailscaleAndMagicDnsTargetsAreRejected() {
        assertFalse(allowed("https://vps-8817149e.tail6eec63.ts.net"))
        assertFalse(allowed("https://other-host.tail6eec63.ts.net:8443"))
        assertFalse(allowed("http://100.95.123.102:8787"))
        assertFalse(allowed("http://desktop-l59hjk4"))
    }

    @Test
    fun malformedTargetsAreRejected() {
        assertFalse(allowed(""))
        assertFalse(allowed("not a url"))
        assertFalse(allowed("ftp://jarvis.example.com"))
    }

    @Test
    fun normalizeBaseAddsSchemeAndStripsTrailingSlash() {
        assertEquals("http://192.168.1.20:8788", JarvisAppSession.normalizeBase("192.168.1.20:8788/"))
        assertEquals("https://jarvis.example.com", JarvisAppSession.normalizeBase(" https://jarvis.example.com///"))
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
                JarvisAppSession.KEY_BASE to "http://192.168.1.20:8788",
                JarvisAppSession.KEY_USER_ID to "u1",
                JarvisAppSession.KEY_ROLE to "owner",
            ),
        )
        assertEquals("tok", session.token)
        assertEquals("Bearer tok", session.authHeader())
        assertTrue(session.isAuthenticated)
        assertEquals("http://192.168.1.20:8788", session.baseUrl)
        assertTrue(session.deviceId.startsWith("android_"))
        assertEquals(session.deviceId, session.deviceId)
        session.clear()
        assertEquals(null, session.token)
    }
}
