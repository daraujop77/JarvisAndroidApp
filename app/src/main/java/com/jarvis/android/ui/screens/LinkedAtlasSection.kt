package com.jarvis.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.android.transport.live.KnowledgeAssertion
import com.jarvis.android.transport.live.KnowledgeEdge
import com.jarvis.android.transport.live.KnowledgeGraphResponse
import com.jarvis.android.transport.live.KnowledgeTimelineEntry
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.JarvisViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
internal fun LinkedAtlasSection(
    state: JarvisViewModel.WritingWorkspaceState,
    projectId: String,
    vm: JarvisViewModel,
    onOpenWiki: () -> Unit,
) {
    var view by rememberSaveable { mutableStateOf("timeline") }
    var query by rememberSaveable { mutableStateOf("") }
    var lane by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    val caps = state.knowledgeCapabilities ?: return
    val selectedNode = state.knowledgeSelectedNode?.data?.node

    fun focusNode(id: String) {
        selectedNodeId = id
        vm.focusKnowledgeNode(projectId, id)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            AtlasCard(JarvisGreen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ATLAS VINCULADO", style = HudTextStyle, color = JarvisGreen)
                        Text(
                            "Timeline y relaciones comparten seleccion, filtros y snapshot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                        )
                    }
                    if (state.knowledgeAtlasLoading) {
                        CircularProgressIndicator(
                            Modifier.width(22.dp).height(22.dp),
                            strokeWidth = 2.dp,
                            color = JarvisCyan,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "timeline" to "Linea de tiempo",
                        "graph" to "Mapa de relaciones",
                        "evidence" to "Evidencia",
                    ).forEach { (id, label) ->
                        FilterChip(selected = view == id, onClick = { view = id }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "Snapshot ${caps.snapshot_id.take(16)}...  |  ${caps.data.counts.nodes} nodos  |  ${caps.data.counts.edges} relaciones  |  ${caps.data.counts.assertions} afirmaciones",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                )
                if (state.knowledgeSnapshotChanged) {
                    Text("Atlas actualizado a una nueva snapshot.", color = JarvisCyan, style = MaterialTheme.typography.labelSmall)
                }
                state.knowledgeAtlasError?.let {
                    Text(it, color = JarvisAmber, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item {
            AtlasCard(JarvisCyan) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Buscar en canon") },
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        null to "Todo",
                        "OCCURRED" to "Ocurrido",
                        "FUTURE" to "Futuro",
                        "UNKNOWN" to "Sin fecha",
                    ).forEach { (id, label) ->
                        FilterChip(selected = lane == id, onClick = { lane = id }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onClick = {
                        selectedNodeId = null
                        vm.clearKnowledgeSelection()
                        vm.refreshKnowledgeAtlas(projectId, query, lane, refreshSnapshot = false)
                    }) { Text("Aplicar") }
                    OutlinedButton(onClick = {
                        selectedNodeId = null
                        vm.clearKnowledgeSelection()
                        vm.refreshKnowledgeAtlas(projectId, query, lane, refreshSnapshot = true)
                    }) { Text("Revisar version") }
                }
            }
        }

        when (view) {
            "timeline" -> {
                val entries = state.knowledgeTimelineV2?.data?.entries.orEmpty()
                if (entries.isEmpty() && !state.knowledgeAtlasLoading) {
                    item { AtlasEmpty("No hay eventos visibles para estos filtros.") }
                }
                items(entries, key = { "kv2_t_${it.entry_id}" }) { entry ->
                    AtlasTimelineCard(entry, selectedNodeId) {
                        entry.entity_ids.firstOrNull()?.let(::focusNode)
                    }
                }
            }
            "graph" -> {
                val graph = state.knowledgeGraphV2
                if (graph != null && graph.data.nodes.isNotEmpty()) {
                    item { AtlasGraph(graph, selectedNodeId, ::focusNode) }
                    val edges = graph.data.edges
                        .filter { selectedNodeId == null || it.source_id == selectedNodeId || it.target_id == selectedNodeId }
                        .take(30)
                    items(edges, key = { "kv2_e_${it.edge_id}" }) { edge ->
                        AtlasEdgeRow(
                            edge = edge,
                            label = { id -> graph.data.nodes.firstOrNull { it.node_id == id }?.label ?: id },
                            selected = state.knowledgeSelectedEdge?.data?.edge?.edge_id == edge.edge_id,
                        ) {
                            vm.focusKnowledgeEdge(projectId, edge.edge_id)
                        }
                    }
                } else if (!state.knowledgeAtlasLoading) {
                    item { AtlasEmpty("No hay nodos visibles para estos filtros.") }
                }
            }
            else -> item {
                AtlasEvidence(
                    state = state,
                    onOpenWiki = {
                        val label = selectedNode?.label.orEmpty()
                        if (label.isNotBlank()) {
                            vm.searchWritingWiki(projectId, label)
                            onOpenWiki()
                        }
                    },
                )
            }
        }

        if (view != "evidence" && (state.knowledgeSelectedNode != null || state.knowledgeSelectedEdge != null)) {
            item {
                AtlasCard(JarvisGreen) {
                    Text("SELECCION VINCULADA", style = HudTextStyle, color = JarvisGreen)
                    selectedNode?.let {
                        Text(it.label.ifBlank { it.node_id }, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    state.knowledgeSelectedEdge?.data?.edge?.let {
                        Text("Relacion: ${it.predicate_id}", color = JarvisViolet, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { view = "evidence" }) { Text("Ver evidencia") }
                        if (selectedNode != null) TextButton(onClick = {
                            vm.searchWritingWiki(projectId, selectedNode.label)
                            onOpenWiki()
                        }) { Text("Abrir Wiki") }
                        TextButton(onClick = {
                            selectedNodeId = null
                            vm.clearKnowledgeSelection()
                            vm.refreshKnowledgeAtlas(projectId, query, lane, refreshSnapshot = false)
                        }) { Text("Quitar seleccion") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtlasTimelineCard(entry: KnowledgeTimelineEntry, selectedNodeId: String?, onClick: () -> Unit) {
    val selected = selectedNodeId != null && selectedNodeId in entry.entity_ids
    val accent = when (entry.lane.uppercase()) {
        "FUTURE" -> JarvisViolet
        "UNKNOWN" -> JarvisAmber
        else -> JarvisCyan
    }
    Surface(
        Modifier.fillMaxWidth().clickable(enabled = entry.entity_ids.isNotEmpty(), onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) accent.copy(alpha = 0.17f) else Color(0xC90B1322),
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.lane.ifBlank { "UNKNOWN" }, style = HudTextStyle, color = accent)
                Spacer(Modifier.weight(1f))
                Text("${entry.assertion_ids.size} afirm.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
            }
            Text(entry.title.ifBlank { entry.entry_id }, color = Color.White, fontWeight = FontWeight.SemiBold)
            if (entry.chapter_ids.isNotEmpty()) {
                Text(
                    entry.chapter_ids.joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.entity_ids.isNotEmpty()) {
                Text("Toca para vincular con el mapa.", style = MaterialTheme.typography.labelSmall, color = JarvisGreen)
            }
        }
    }
}

@Composable
private fun AtlasGraph(graph: KnowledgeGraphResponse, selectedNodeId: String?, onNode: (String) -> Unit) {
    val nodes = graph.data.nodes.take(24)
    val ids = nodes.map { it.node_id }.toSet()
    val edges = graph.data.edges.filter { it.source_id in ids && it.target_id in ids }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), color = Color(0xC90B1322)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(420.dp).background(Color(0xAA08111F))) {
            val cx = maxWidth / 2
            val cy = 205.dp
            val rx = ((maxWidth.value / 2f) - 48f).coerceAtLeast(82f)
            val ry = 148f
            val pos: Map<String, Pair<Dp, Dp>> = nodes.mapIndexed { index, node ->
                val angle = 2.0 * PI * index.toDouble() / nodes.size.coerceAtLeast(1) - PI / 2.0
                node.node_id to Pair(
                    cx + (rx * cos(angle)).toFloat().dp,
                    cy + (ry * sin(angle)).toFloat().dp,
                )
            }.toMap()
            Canvas(Modifier.fillMaxSize()) {
                edges.forEach { edge ->
                    val a = pos[edge.source_id] ?: return@forEach
                    val b = pos[edge.target_id] ?: return@forEach
                    val hot = selectedNodeId != null && (edge.source_id == selectedNodeId || edge.target_id == selectedNodeId)
                    drawLine(
                        color = if (hot) JarvisCyan.copy(alpha = 0.9f) else Color(0xFF475569).copy(alpha = 0.55f),
                        start = Offset(a.first.toPx(), a.second.toPx()),
                        end = Offset(b.first.toPx(), b.second.toPx()),
                        strokeWidth = if (hot) 3.dp.toPx() else 1.4.dp.toPx(),
                    )
                }
            }
            nodes.forEach { node ->
                val p = pos[node.node_id] ?: return@forEach
                val selected = node.node_id == selectedNodeId
                Surface(
                    Modifier
                        .offset(p.first - 36.dp, p.second - 23.dp)
                        .width(72.dp)
                        .heightIn(min = 46.dp)
                        .clickable { onNode(node.node_id) },
                    shape = RoundedCornerShape(11.dp),
                    color = if (selected) JarvisGreen else Color(0xFF142238),
                    shadowElevation = if (selected) 7.dp else 2.dp,
                ) {
                    Text(
                        node.label.ifBlank { node.node_id },
                        modifier = Modifier.padding(5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color(0xFF07131B) else Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AtlasEdgeRow(edge: KnowledgeEdge, label: (String) -> String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = if (selected) JarvisViolet.copy(alpha = 0.18f) else Color(0xC90B1322),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("${label(edge.source_id)} -> ${label(edge.target_id)}", color = Color.White)
            Text(edge.predicate_id, color = JarvisViolet, style = MaterialTheme.typography.bodySmall)
            Text("${edge.assertion_ids.size} afirmacion(es)", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AtlasEvidence(state: JarvisViewModel.WritingWorkspaceState, onOpenWiki: () -> Unit) {
    AtlasCard(JarvisAmber) {
        Text("EVIDENCIA Y PROCEDENCIA", style = HudTextStyle, color = JarvisAmber)
        val node = state.knowledgeSelectedNode
        val edge = state.knowledgeSelectedEdge
        if (node == null && edge == null) {
            Text(
                "Selecciona un nodo o una relacion para ver afirmaciones y fuentes.",
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.bodySmall,
            )
            return@AtlasCard
        }
        node?.let { response ->
            Text(response.data.node.label, color = Color.White, fontWeight = FontWeight.SemiBold)
            response.data.assertions.take(20).forEach { AtlasAssertion(it) }
            OutlinedButton(onClick = onOpenWiki) { Text("Abrir articulo Wiki") }
        }
        edge?.let { response ->
            Spacer(Modifier.height(8.dp))
            Text("Relacion: ${response.data.edge.predicate_id}", color = JarvisViolet)
            response.data.assertions.take(20).forEach { AtlasAssertion(it) }
        }
    }
}

@Composable
private fun AtlasAssertion(assertion: KnowledgeAssertion) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(9.dp),
        color = Color(0x551E293B),
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(assertion.predicate_id.ifBlank { assertion.assertion_id }, color = Color.White)
            val authority = listOf("status", "authority", "level", "kind")
                .firstNotNullOfOrNull { key ->
                    assertion.authority[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                }
            authority?.let { Text(it, color = JarvisCyan, style = MaterialTheme.typography.labelSmall) }
            if (assertion.evidence_refs.isEmpty()) {
                Text("Sin evidencia materializada", color = JarvisAmber, style = MaterialTheme.typography.labelSmall)
            } else {
                assertion.evidence_refs.take(6).forEach { evidence ->
                    val parts = mutableListOf<String>()
                    evidence.source_document_id?.takeIf(String::isNotBlank)?.let(parts::add)
                    evidence.chapter_number?.let { parts.add("cap. $it") }
                    evidence.source_line_start?.let { start ->
                        parts.add("lineas $start-${evidence.source_line_end ?: start}")
                    }
                    Text(
                        "- ${evidence.support.ifBlank { "EVIDENCE" }} / ${evidence.locator_precision.ifBlank { "UNKNOWN" }}" +
                            if (parts.isEmpty()) "" else " / ${parts.joinToString(" / ")}",
                        color = Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AtlasEmpty(text: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color(0xC90B1322)) {
        Text(text, Modifier.padding(13.dp), color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AtlasCard(accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = Color(0xC90B1322),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}
