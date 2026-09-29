package com.jarvis.android.ui.screens

import com.jarvis.android.transport.live.KnowledgeEdge
import com.jarvis.android.transport.live.KnowledgeNode
import com.jarvis.android.transport.live.KnowledgeTimelineEntry
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal const val ATLAS_GRAPH_NODE_LIMIT = 24
internal const val ATLAS_FOCUS_NEIGHBOR_LIMIT = 8
internal const val ATLAS_OVERVIEW_NODE_LIMIT = 9

internal data class AtlasGraphNeighborhood(
    val center: KnowledgeNode?,
    val nodes: List<KnowledgeNode>,
    val edges: List<KnowledgeEdge>,
    val overview: Boolean,
)

internal data class AtlasTimelineGroup(
    val lane: String,
    val label: String,
    val entries: List<KnowledgeTimelineEntry>,
)

/**
 * Legacy cap helper retained for compatibility with the original C10 tests.
 */
internal fun atlasVisibleNodes(
    nodes: List<KnowledgeNode>,
    selectedNodeId: String?,
    limit: Int = ATLAS_GRAPH_NODE_LIMIT,
): List<KnowledgeNode> {
    if (limit <= 0 || nodes.isEmpty()) return emptyList()
    val visible = nodes.take(limit).toMutableList()
    val selected = selectedNodeId
        ?.takeIf { id -> visible.none { it.node_id == id } }
        ?.let { id -> nodes.firstOrNull { it.node_id == id } }
    if (selected != null) {
        if (visible.size >= limit && visible.isNotEmpty()) visible.removeAt(visible.lastIndex)
        visible += selected
    }
    return visible.distinctBy { it.node_id }
}

internal fun atlasNodeCategory(node: KnowledgeNode): String {
    val types = node.type_ids.map { it.lowercase() }
    return when {
        types.any { it == "character" || it.contains("character") } -> "character"
        types.any { it == "location" || it.contains("place") } -> "location"
        types.any { it == "event" || it.contains("event") } -> "event"
        types.any { it == "chapter" || it.contains("chapter") } -> "chapter"
        types.any { it == "arc" || it.contains("arc") } -> "arc"
        types.any { it.contains("lore") || it.contains("mystery") || it.contains("thread") } -> "lore"
        else -> "other"
    }
}

internal fun atlasNodeCategoryLabel(category: String): String = when (category) {
    "character" -> "Personajes"
    "location" -> "Lugares"
    "event" -> "Eventos"
    "chapter" -> "Capítulos"
    "arc" -> "Arcos"
    "lore" -> "Lore"
    else -> "Otros"
}

internal fun atlasPredicateLabel(predicateId: String): String {
    val raw = predicateId.trim()
    if (raw.isBlank()) return "Relación"
    return when (raw.lowercase()) {
        "appears_in" -> "Aparece en"
        "mentioned_in" -> "Mencionado en"
        "planned_for" -> "Planeado para"
        else -> raw
            .removePrefix("relation_")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

internal fun atlasTimelineChapterNumber(entry: KnowledgeTimelineEntry): Int? {
    entry.chapter_ids.forEach { id ->
        Regex("""(\d+)""").find(id)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
    }
    return entry.position["sequence"]?.jsonPrimitive?.intOrNull
}

internal fun atlasTimelineGroups(entries: List<KnowledgeTimelineEntry>): List<AtlasTimelineGroup> {
    val order = listOf(
        "OCCURRED" to "HISTORIA OCURRIDA",
        "FUTURE" to "FUTURO APROBADO",
        "UNKNOWN" to "SIN ANCLA TEMPORAL",
    )
    return order.mapNotNull { (lane, label) ->
        val items = entries
            .filter { it.lane.uppercase().ifBlank { "UNKNOWN" } == lane }
            .sortedWith(
                compareBy<KnowledgeTimelineEntry> { atlasTimelineChapterNumber(it) == null }
                    .thenBy { atlasTimelineChapterNumber(it) ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() },
            )
        items.takeIf { it.isNotEmpty() }?.let {
            AtlasTimelineGroup(lane = lane, label = label, entries = it)
        }
    }
}

internal fun atlasGraphNeighborhood(
    nodes: List<KnowledgeNode>,
    edges: List<KnowledgeEdge>,
    selectedNodeId: String?,
    includeChapters: Boolean,
    neighborLimit: Int = ATLAS_FOCUS_NEIGHBOR_LIMIT,
    overviewLimit: Int = ATLAS_OVERVIEW_NODE_LIMIT,
): AtlasGraphNeighborhood {
    if (nodes.isEmpty()) {
        return AtlasGraphNeighborhood(null, emptyList(), emptyList(), overview = selectedNodeId == null)
    }
    val byId = nodes.associateBy { it.node_id }
    val degrees = mutableMapOf<String, Int>()
    edges.forEach { edge ->
        degrees[edge.source_id] = (degrees[edge.source_id] ?: 0) + 1
        degrees[edge.target_id] = (degrees[edge.target_id] ?: 0) + 1
    }

    fun visible(node: KnowledgeNode): Boolean =
        includeChapters || atlasNodeCategory(node) != "chapter" || node.node_id == selectedNodeId

    val selected = selectedNodeId?.let(byId::get)
    if (selected != null) {
        val neighborIds = edges.asSequence()
            .filter { it.source_id == selected.node_id || it.target_id == selected.node_id }
            .map { if (it.source_id == selected.node_id) it.target_id else it.source_id }
            .distinct()
            .mapNotNull(byId::get)
            .filter(::visible)
            .sortedWith(
                compareByDescending<KnowledgeNode> { degrees[it.node_id] ?: 0 }
                    .thenBy { it.label.lowercase() },
            )
            .take(neighborLimit.coerceAtLeast(1))
            .map { it.node_id }
            .toSet()

        val focusedNodes = buildList {
            add(selected)
            neighborIds.mapNotNull(byId::get).forEach(::add)
        }.distinctBy { it.node_id }
        val focusedIds = focusedNodes.map { it.node_id }.toSet()
        val focusedEdges = edges.filter {
            it.source_id in focusedIds &&
                it.target_id in focusedIds &&
                (it.source_id == selected.node_id || it.target_id == selected.node_id)
        }
        return AtlasGraphNeighborhood(
            center = selected,
            nodes = focusedNodes,
            edges = focusedEdges,
            overview = false,
        )
    }

    val overviewNodes = nodes.asSequence()
        .filter(::visible)
        .sortedWith(
            compareByDescending<KnowledgeNode> { degrees[it.node_id] ?: 0 }
                .thenBy { it.label.lowercase() },
        )
        .take(overviewLimit.coerceAtLeast(1))
        .toList()
    val overviewIds = overviewNodes.map { it.node_id }.toSet()
    return AtlasGraphNeighborhood(
        center = null,
        nodes = overviewNodes,
        edges = edges.filter { it.source_id in overviewIds && it.target_id in overviewIds },
        overview = true,
    )
}
