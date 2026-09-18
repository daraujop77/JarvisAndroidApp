package com.jarvis.android.ui.format

import com.jarvis.android.data.state.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every connection state must say something, and only the unhealthy ones explain why. */
class ConnectionCopyTest {

    @Test
    fun everyStateHasALabel() {
        ConnectionState.entries.forEach { state ->
            assertTrue("$state has no label", ConnectionCopy.label(state).isNotBlank())
        }
    }

    @Test
    fun healthyAndInProgressStatesStaySingleLine() {
        assertNull(ConnectionCopy.detail(ConnectionState.ONLINE))
        assertNull(ConnectionCopy.detail(ConnectionState.CONNECTING))
    }

    @Test
    fun unhealthyStatesExplainThemselves() {
        val explained = ConnectionState.entries.filter { ConnectionCopy.detail(it) != null }
        assertEquals(
            setOf(
                ConnectionState.DEGRADED,
                ConnectionState.RECONNECTING,
                ConnectionState.OFFLINE,
                ConnectionState.DISCONNECTED,
                ConnectionState.AUTH_EXPIRED,
                ConnectionState.DEVICE_REVOKED,
                ConnectionState.PROTOCOL_MISMATCH,
            ),
            explained.toSet(),
        )
    }

    @Test
    fun authFailurePointsAtPairing() {
        assertTrue(ConnectionCopy.detail(ConnectionState.AUTH_EXPIRED)!!.contains("pair again"))
    }
}
