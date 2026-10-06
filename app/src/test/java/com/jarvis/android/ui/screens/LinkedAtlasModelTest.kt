package com.jarvis.android.ui.screens

import com.jarvis.android.transport.live.KnowledgeEdge
import com.jarvis.android.transport.live.KnowledgeNode
import com.jarvis.android.transport.live.KnowledgeTimelineEntry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedAtlasModelTest {
    private fun node(index: Int, type: String = "character") =
        KnowledgeNode(node_id = "n$index", label = "Node $index", type_ids = listOf(type))

    private fun edge(index: Int, source: String, target: String, predicate: String = "relation_knows") =
        KnowledgeEdge(edge_id = "e$index", source_id = source, target_id = target, predicate_id = predicate)

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

    @Test
    fun overviewHidesChapterNodesByDefault() {
        val nodes = listOf(
            node(1),
            node(2),
            node(3, "chapter"),
        )
        val graph = atlasGraphNeighborhood(
            nodes = nodes,
            edges = listOf(edge(1, "n1", "n2"), edge(2, "n1", "n3")),
            selectedNodeId = null,
            includeChapters = false,
        )
        assertTrue(graph.overview)
        assertFalse(graph.nodes.any { atlasNodeCategory(it) == "chapter" })
    }

    @Test
    fun selectedNodeBecomesCenterAndOnlyDirectNeighborsRemain() {
        val nodes = (1..5).map(::node)
        val edges = listOf(
            edge(1, "n1", "n2"),
            edge(2, "n1", "n3"),
            edge(3, "n3", "n4"),
            edge(4, "n4", "n5"),
        )
        val graph = atlasGraphNeighborhood(
            nodes = nodes,
            edges = edges,
            selectedNodeId = "n1",
            includeChapters = false,
        )
        assertEquals("n1", graph.center?.node_id)
        assertEquals(setOf("n1", "n2", "n3"), graph.nodes.map { it.node_id }.toSet())
        assertEquals(setOf("e1", "e2"), graph.edges.map { it.edge_id }.toSet())
        assertFalse(graph.overview)
    }

    @Test
    fun selectedChapterRemainsVisibleEvenWhenChaptersAreHidden() {
        val chapter = node(8, "chapter")
        val graph = atlasGraphNeighborhood(
            nodes = listOf(node(1), chapter),
            edges = listOf(edge(1, "n1", "n8", "appears_in")),
            selectedNodeId = "n8",
            includeChapters = false,
        )
        assertEquals("n8", graph.center?.node_id)
        assertTrue(graph.nodes.any { it.node_id == "n8" })
    }

    @Test
    fun timelineGroupsUseNarrativeLanesAndChapterOrder() {
        val entries = listOf(
            KnowledgeTimelineEntry(
                entry_id = "future",
                title = "Future",
                lane = "FUTURE",
                chapter_ids = listOf("chapter:40"),
            ),
            KnowledgeTimelineEntry(
                entry_id = "two",
                title = "Second",
                lane = "OCCURRED",
                chapter_ids = listOf("chapter:2"),
            ),
            KnowledgeTimelineEntry(
                entry_id = "one",
                title = "First",
                lane = "OCCURRED",
                position = buildJsonObject { put("sequence", 1) },
            ),
        )

        val groups = atlasTimelineGroups(entries)
        assertEquals(listOf("OCCURRED", "FUTURE"), groups.map { it.lane })
        assertEquals(listOf("one", "two"), groups.first().entries.map { it.entry_id })
        assertEquals(40, atlasTimelineChapterNumber(groups[1].entries.first()))
    }

    @Test
    fun predicateLabelsAreHumanReadable() {
        assertEquals("Aparece en", atlasPredicateLabel("appears_in"))
        assertEquals("Hermano mayor", atlasPredicateLabel("relation_hermano_mayor"))
    }
}
