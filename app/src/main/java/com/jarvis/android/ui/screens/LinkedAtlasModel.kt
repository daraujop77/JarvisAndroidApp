package com.jarvis.android.ui.screens

import com.jarvis.android.transport.live.KnowledgeNode

internal const val ATLAS_GRAPH_NODE_LIMIT = 24

/**
 * Keeps the graph readable while guaranteeing that a selected node is never
 * silently dropped by the visual cap.
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
