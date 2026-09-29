package com.jarvis.android.ui.screens

import com.jarvis.android.transport.live.KnowledgeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedAtlasModelTest {
    private fun node(index: Int) = KnowledgeNode(node_id = "n$index", label = "Node $index")

    @Test
    fun visibleNodesRespectCap() {
        val visible = atlasVisibleNodes((1..40).map(::node), selectedNodeId = null, limit = 24)
        assertEquals(24, visible.size)
        assertEquals("n1", visible.first().node_id)
        assertEquals("n24", visible.last().node_id)
    }

    @Test
    fun selectedNodeOutsideCapIsStillVisible() {
        val visible = atlasVisibleNodes((1..40).map(::node), selectedNodeId = "n40", limit = 24)
        assertEquals(24, visible.size)
        assertTrue(visible.any { it.node_id == "n40" })
    }
}
