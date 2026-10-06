package com.jarvis.android.transport.live

import com.jarvis.android.contract.EventEnvelope
import com.jarvis.android.contract.GatewayEvent
import com.jarvis.android.contract.JarvisJson
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFrameConcurrencyTest {
    @Test fun parallelEmittersKeepUniqueIdsAndOrderedCursors() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val transport = LiveAppGatewayTransport(JarvisAppSession(JarvisAppSession.MemoryStore()), scope)
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val workers = (0 until 8).map { worker ->
                pool.submit {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    repeat(200) { index ->
                        transport.emitFrame(GatewayEvent.MessageCompleted("request_$worker", "message_$index"))
                    }
                }
            }
            start.countDown()
            workers.forEach { it.get(10, TimeUnit.SECONDS) }
            val frames = runBlocking {
                withTimeout(5_000) { transport.frames.take(1_600).toList() }
            }.map { JarvisJson.default.decodeFromString(EventEnvelope.serializer(), it) }
            assertEquals((1L..1_600L).toList(), frames.map { it.cursor })
            assertEquals(1_600, frames.map { it.eventId }.toSet().size)
        } finally {
            pool.shutdownNow()
            scope.cancel()
        }
    }
}
