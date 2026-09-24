package com.jarvis.android.transport.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderUsageSessionTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/api/app/status") return MockResponse().setResponseCode(404)
                if (request.getHeader("Authorization") != "Bearer test-token") {
                    return MockResponse().setResponseCode(401)
                }
                return MockResponse().setBody(
                    """
                    {
                      "schema":"jarvis.app.status.v1",
                      "usage":{
                        "schema":"jarvis.provider-usage.v1",
                        "source":"jarvis_observed",
                        "tracking_started_utc":"2026-09-23T00:00:00Z",
                        "updated_utc":"2026-09-23T01:00:00Z",
                        "providers":[
                          {
                            "id":"nous",
                            "label":"Nous",
                            "model":"deepseek/deepseek-v4-flash",
                            "source":"jarvis_observed",
                            "periods":{
                              "today":{
                                "requests":3,
                                "successful_requests":2,
                                "failed_requests":1,
                                "duration_ms":900,
                                "input_tokens":null,
                                "output_tokens":null,
                                "total_tokens":null
                              }
                            },
                            "lifetime":{
                              "requests":3,
                              "successful_requests":2,
                              "failed_requests":1,
                              "duration_ms":900,
                              "input_tokens":null,
                              "output_tokens":null,
                              "total_tokens":null
                            },
                            "last_used_utc":"2026-09-23T01:00:00Z",
                            "quota":{
                              "status":"not_reported",
                              "remaining_percent":null,
                              "resets_at":null
                            }
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun session(): JarvisAppSession {
        val store = JarvisAppSession.MemoryStore()
        store.put(
            mapOf(
                JarvisAppSession.KEY_TOKEN to "test-token",
                JarvisAppSession.KEY_BASE to server.url("/").toString().trimEnd('/'),
                JarvisAppSession.KEY_USER_ID to "u1",
                JarvisAppSession.KEY_USERNAME to "owner",
                JarvisAppSession.KEY_ROLE to "owner",
                JarvisAppSession.KEY_EXPIRES to "2099-01-01T00:00:00Z",
            ),
        )
        return JarvisAppSession(store)
    }

    @Test
    fun providerUsageParsesNousAndKeepsUnknownQuotaAndTokensNull() = runBlocking(Dispatchers.IO) {
        val result = session().fetchProviderUsage()

        assertTrue(result.isSuccess)
        val snapshot = result.getOrThrow()
        val nous = snapshot.providers.single()
        assertEquals("nous", nous.id)
        assertEquals("Nous", nous.label)
        assertEquals("deepseek/deepseek-v4-flash", nous.model)
        assertEquals(3L, nous.periods.getValue("today").requests)
        assertNull(nous.periods.getValue("today").totalTokens)
        assertEquals("not_reported", nous.quotaStatus)
        assertNull(nous.quotaRemainingPercent)
    }
}
