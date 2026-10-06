package com.jarvis.android.transport.live

import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class VisualAssetRequestTimeoutTest {
    @Test fun stalledResponseHasAFiniteDeadlineAndTheNextRequestWorks() {
        val server = MockWebServer()
        server.start()
        try {
            val session = JarvisAppSession(JarvisAppSession.MemoryStore())
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try {
                session.post(server.url("/").toString().trimEnd('/'),
                    "/api/app/writing-room/visual-assets/fetch", "{}", "Bearer test-token",
                    timeoutMillis = 200,
                )
                fail("A stalled visual fetch must time out")
            } catch (_: InterruptedIOException) {
                // OkHttp's call deadline also bounds response-body reads.
            }
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            server.enqueue(MockResponse().setBody("retry bytes"))
            assertEquals(
                200 to "retry bytes",
                session.post(server.url("/").toString().trimEnd('/'),
                    "/api/app/writing-room/visual-assets/fetch", "{}", "Bearer test-token",
                    timeoutMillis = 2_000,
                ),
            )
            assertEquals("Bearer test-token", server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }
}
