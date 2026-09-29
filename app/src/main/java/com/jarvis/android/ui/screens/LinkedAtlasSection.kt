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
import com.jarvis.android.transport.live.KnowledgeNode
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
    onOpenChapter: () -> Unit,
) {
    var view by rememberSaveable { mutableStateOf("timeline") }
    var query by rememberSaveable { mutableStateOf("") }
    var lane by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTimelineEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var graphCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var includeChapterNodes by rememberSaveable { mutableStateOf(false) }
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
                        Text("ATLAS", style = HudTextStyle, color = JarvisGreen)
                        Text(
                            when (view) {
                                "graph" -> "Entidades y relaciones. Toca un nodo para centrar su vecindario."
                                "evidence" -> "Fuentes y procedencia de la selección actual."
                                else -> "La historia en orden narrativo, sin convertir cada evento en una tarjeta."
                            },
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
                        "timeline" to "Historia",
                        "graph" to "Relaciones",
                        "evidence" to "Fuentes",
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = view == id,
                            onClick = { view = id },
                            label = { Text(label) },
                        )
                    }
                }
                if (caps.coverage.partial || caps.coverage.incomplete) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Cobertura parcial: ${caps.coverage.reasons.joinToString().ifBlank { "hay fuentes pendientes" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisAmber,
                    )
                }
                if (state.knowledgeSnapshotChanged) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "La historia tiene una versión de conocimiento más reciente.",
                        color = JarvisCyan,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                state.knowledgeAtlasError?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(it, color = JarvisAmber, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (view != "evidence") {
            item {
                AtlasCard(JarvisCyan) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (view == "graph") "Buscar entidad" else "Buscar en la historia") },
                    )
                    Spacer(Modifier.height(7.dp))
                    if (view == "timeline") {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                null to "Todo",
                                "OCCURRED" to "Ocurrido",
                                "FUTURE" to "Futuro",
                                "UNKNOWN" to "Sin ancla",
                            ).forEach { (id, label) ->
                                FilterChip(
                                    selected = lane == id,
                                    onClick = { lane = id },
                                    label = { Text(label) },
                                )
                            }
                        }
                    } else {
                        val graph = state.knowledgeGraphV2
                        val categories = graph?.data?.nodes.orEmpty()
                            .map(::atlasNodeCategory)
                            .filter { it != "chapter" }
                            .distinct()
                            .sorted()
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = graphCategory == null,
                                onClick = { graphCategory = null },
                                label = { Text("Todo") },
                            )
                            categories.forEach { category ->
                                FilterChip(
                                    selected = graphCategory == category,
                                    onClick = { graphCategory = category },
                                    label = { Text(atlasNodeCategoryLabel(category)) },
                                )
                            }
                            FilterChip(
                                selected = includeChapterNodes,
                                onClick = { includeChapterNodes = !includeChapterNodes },
                                label = { Text("Capítulos") },
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(onClick = {
                            selectedNodeId = null
                            selectedTimelineEntryId = null
                            vm.clearKnowledgeSelection()
                            vm.refreshKnowledgeAtlas(
                                projectId = projectId,
                                query = query,
                                lane = if (view == "timeline") lane else null,
                                refreshSnapshot = false,
                            )
                        }) { Text("Aplicar") }
                        TextButton(onClick = {
                            selectedNodeId = null
                            selectedTimelineEntryId = null
                            vm.clearKnowledgeSelection()
                            vm.refreshKnowledgeAtlas(
                                projectId = projectId,
                                query = query,
                                lane = if (view == "timeline") lane else null,
                                refreshSnapshot = true,
                            )
                        }) { Text("Sincronizar") }
                    }
                }
            }
        }

        when (view) {
            "timeline" -> {
                val entries = state.knowledgeTimelineV2?.data?.entries.orEmpty()
                val groups = atlasTimelineGroups(entries)
                if (groups.isEmpty() && !state.knowledgeAtlasLoading) {
                    item { AtlasEmpty("No hay eventos visibles para estos filtros.") }
                }
                groups.forEach { group ->
                    item(key = "kv2_group_${group.lane}") {
                        AtlasTimelineRail(
                            group = group,
                            selectedEntryId = selectedTimelineEntryId,
                            onEntry = { entry ->
                                selectedTimelineEntryId = entry.entry_id
                                entry.entity_ids.firstOrNull()?.let(::focusNode)
                            },
                            onOpenRelations = { entry ->
                                selectedTimelineEntryId = entry.entry_id
                                entry.entity_ids.firstOrNull()?.let(::focusNode)
                                view = "graph"
                            },
                        )
                    }
                }
            }

            "graph" -> {
                val graph = state.knowledgeGraphV2
                if (graph != null && graph.data.nodes.isNotEmpty()) {
                    val filteredNodes = graph.data.nodes.filter { node ->
                        graphCategory == null ||
                            atlasNodeCategory(node) == graphCategory ||
                            node.node_id == selectedNodeId
                    }
                    val filteredIds = filteredNodes.map { it.node_id }.toSet()
                    val filteredGraph = graph.copy(
                        data = graph.data.copy(
                            nodes = filteredNodes,
                            edges = graph.data.edges.filter {
                                it.source_id in filteredIds && it.target_id in filteredIds
                            },
                        ),
                    )
                    item {
                        AtlasFocusedGraph(
                            graph = filteredGraph,
                            selectedNodeId = selectedNodeId,
                            includeChapterNodes = includeChapterNodes,
                            onNode = ::focusNode,
                            onEdge = { edgeId -> vm.focusKnowledgeEdge(projectId, edgeId) },
                        )
                    }
                } else if (!state.knowledgeAtlasLoading) {
                    item { AtlasEmpty("No hay entidades visibles para estos filtros.") }
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
                    onResolveEvidence = { evidenceId ->
                        vm.resolveKnowledgeEvidence(projectId, evidenceId)
                    },
                    onOpenSourceDocument = { documentId ->
                        vm.readWritingLibraryDocument(projectId, documentId)
                        onOpenChapter()
                    },
                )
            }
        }

        if (view != "evidence" && (state.knowledgeSelectedNode != null || state.knowledgeSelectedEdge != null)) {
            item {
                AtlasCard(JarvisGreen) {
                    Text("SELECCIÓN", style = HudTextStyle, color = JarvisGreen)
                    selectedNode?.let {
                        Text(
                            it.label.ifBlank { it.node_id },
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            atlasNodeCategoryLabel(atlasNodeCategory(it)),
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    state.knowledgeSelectedEdge?.data?.edge?.let {
                        Text(
                            atlasPredicateLabel(it.predicate_id),
                            color = JarvisViolet,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { view = "evidence" }) { Text("Ver fuentes") }
                        if (selectedNode != null) {
                            TextButton(onClick = {
                                vm.searchWritingWiki(projectId, selectedNode.label)
                                onOpenWiki()
                            }) { Text("Abrir Wiki") }
                        }
                        TextButton(onClick = {
                            selectedNodeId = null
                            selectedTimelineEntryId = null
                            vm.clearKnowledgeSelection()
                        }) { Text("Quitar selección") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtlasTimelineRail(
    group: AtlasTimelineGroup,
    selectedEntryId: String?,
    onEntry: (KnowledgeTimelineEntry) -> Unit,
    onOpenRelations: (KnowledgeTimelineEntry) -> Unit,
) {
    val accent = when (group.lane) {
        "FUTURE" -> JarvisViolet
        "UNKNOWN" -> JarvisAmber
        else -> JarvisCyan
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(group.label, style = HudTextStyle, color = accent)
            Spacer(Modifier.weight(1f))
            Text(
                "${group.entries.size} hitos",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF71839C),
            )
        }

        group.entries.forEachIndexed { index, entry ->
            val selected = selectedEntryId == entry.entry_id
            val chapter = atlasTimelineChapterNumber(entry)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onEntry(entry) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    Modifier.width(48.dp).padding(top = 5.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        chapter?.let { "CAP $it" } ?: "—",
                        style = HudTextStyle,
                        color = if (selected) accent else Color(0xFF7F93AE),
                    )
                }
                Canvas(
                    Modifier
                        .width(34.dp)
                        .height(if (selected) 82.dp else 62.dp),
                ) {
                    val x = size.width / 2f
                    val dotY = 15.dp.toPx()
                    if (index > 0) {
                        drawLine(
                            color = accent.copy(alpha = 0.32f),
                            start = Offset(x, 0f),
                            end = Offset(x, dotY),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                    if (index < group.entries.lastIndex) {
                        drawLine(
                            color = accent.copy(alpha = 0.32f),
                            start = Offset(x, dotY),
                            end = Offset(x, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                    drawCircle(
                        color = if (selected) accent else Color(0xFF142238),
                        radius = if (selected) 7.dp.toPx() else 5.dp.toPx(),
                        center = Offset(x, dotY),
                    )
                    drawCircle(
                        color = accent,
                        radius = if (selected) 8.dp.toPx() else 6.dp.toPx(),
                        center = Offset(x, dotY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = if (selected) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        ),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 3.dp, end = 4.dp, top = 3.dp, bottom = 8.dp),
                ) {
                    Text(
                        entry.title.ifBlank { entry.entry_id },
                        color = Color.White,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = if (selected) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append("${entry.entity_ids.size} entidades")
                            if (entry.assertion_ids.isNotEmpty()) {
                                append(" · ${entry.assertion_ids.size} hechos enlazados")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7F93AE),
                    )
                    if (selected && entry.entity_ids.isNotEmpty()) {
                        TextButton(
                            onClick = { onOpenRelations(entry) },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Text("Ver relaciones", color = accent)
                        }
                    }
                }
            }
        }
    }
}

private fun atlasCategoryColor(node: KnowledgeNode): Color = when (atlasNodeCategory(node)) {
    "character" -> JarvisCyan
    "location" -> JarvisGreen
    "event" -> JarvisAmber
    "chapter" -> Color(0xFF94A3B8)
    "arc" -> JarvisViolet
    "lore" -> Color(0xFF67E8F9)
    else -> Color(0xFFCBD5E1)
}

@Composable
private fun AtlasFocusedGraph(
    graph: KnowledgeGraphResponse,
    selectedNodeId: String?,
    includeChapterNodes: Boolean,
    onNode: (String) -> Unit,
    onEdge: (String) -> Unit,
) {
    val neighborhood = atlasGraphNeighborhood(
        nodes = graph.data.nodes,
        edges = graph.data.edges,
        selectedNodeId = selectedNodeId,
        includeChapters = includeChapterNodes,
    )
    val nodes = neighborhood.nodes
    if (nodes.isEmpty()) {
        AtlasEmpty("No hay entidades para esta vista.")
        return
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            if (neighborhood.overview) "RESUMEN POR CONECTIVIDAD" else "VECINDARIO DIRECTO",
            style = HudTextStyle,
            color = if (neighborhood.overview) JarvisCyan else JarvisGreen,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
        )
        Text(
            if (neighborhood.overview) {
                "Se muestran pocas entidades con más conexiones visibles. No implica importancia canónica."
            } else {
                "Solo se muestran relaciones directas del nodo seleccionado."
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF7F93AE),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(6.dp))

        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xC90B1322),
            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.18f)),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(if (neighborhood.overview) 360.dp else 390.dp)
                    .background(Color(0xAA08111F)),
            ) {
                val cx = maxWidth / 2
                val cy = if (neighborhood.overview) 178.dp else 194.dp
                val rx = ((maxWidth.value / 2f) - 56f).coerceAtLeast(84f)
                val ry = if (neighborhood.overview) 126f else 142f
                val centerId = neighborhood.center?.node_id
                val peripheral = nodes.filter { it.node_id != centerId }
                val pos = buildMap<String, Pair<Dp, Dp>> {
                    neighborhood.center?.let { put(it.node_id, Pair(cx, cy)) }
                    if (neighborhood.center == null) {
                        nodes.forEachIndexed { index, node ->
                            val angle = 2.0 * PI * index.toDouble() / nodes.size.coerceAtLeast(1) - PI / 2.0
                            put(
                                node.node_id,
                                Pair(
                                    cx + (rx * cos(angle)).toFloat().dp,
                                    cy + (ry * sin(angle)).toFloat().dp,
                                ),
                            )
                        }
                    } else {
                        peripheral.forEachIndexed { index, node ->
                            val angle = 2.0 * PI * index.toDouble() / peripheral.size.coerceAtLeast(1) - PI / 2.0
                            put(
                                node.node_id,
                                Pair(
                                    cx + (rx * cos(angle)).toFloat().dp,
                                    cy + (ry * sin(angle)).toFloat().dp,
                                ),
                            )
                        }
                    }
                }

                Canvas(Modifier.fillMaxSize()) {
                    neighborhood.edges.forEach { edge ->
                        val a = pos[edge.source_id] ?: return@forEach
                        val b = pos[edge.target_id] ?: return@forEach
                        val hot = centerId != null &&
                            (edge.source_id == centerId || edge.target_id == centerId)
                        drawLine(
                            color = if (hot) JarvisCyan.copy(alpha = 0.80f)
                            else Color(0xFF3F536E).copy(alpha = 0.48f),
                            start = Offset(a.first.toPx(), a.second.toPx()),
                            end = Offset(b.first.toPx(), b.second.toPx()),
                            strokeWidth = if (hot) 2.3.dp.toPx() else 1.2.dp.toPx(),
                        )
                    }
                }

                if (centerId != null) {
                    neighborhood.edges.take(8).forEach { edge ->
                        val a = pos[edge.source_id] ?: return@forEach
                        val b = pos[edge.target_id] ?: return@forEach
                        val mx = (a.first.value + b.first.value) / 2f
                        val my = (a.second.value + b.second.value) / 2f
                        Surface(
                            Modifier
                                .offset(mx.dp - 36.dp, my.dp - 9.dp)
                                .width(72.dp)
                                .clickable { onEdge(edge.edge_id) },
                            shape = RoundedCornerShape(50),
                            color = Color(0xE6111C2C),
                            border = BorderStroke(1.dp, JarvisViolet.copy(alpha = 0.28f)),
                        ) {
                            Text(
                                atlasPredicateLabel(edge.predicate_id),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD9E5F5),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                nodes.forEach { node ->
                    val p = pos[node.node_id] ?: return@forEach
                    val selected = node.node_id == centerId
                    val accent = atlasCategoryColor(node)
                    val nodeWidth = if (selected) 118.dp else 82.dp
                    val nodeHeight = if (selected) 58.dp else 48.dp
                    Surface(
                        Modifier
                            .offset(p.first - nodeWidth / 2, p.second - nodeHeight / 2)
                            .width(nodeWidth)
                            .heightIn(min = nodeHeight)
                            .clickable { onNode(node.node_id) },
                        shape = RoundedCornerShape(if (selected) 15.dp else 12.dp),
                        color = if (selected) accent.copy(alpha = 0.24f) else Color(0xFF142238),
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            accent.copy(alpha = if (selected) 0.95f else 0.38f),
                        ),
                        shadowElevation = if (selected) 8.dp else 2.dp,
                    ) {
                        Column(
                            Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                node.label.ifBlank { node.node_id },
                                style = if (selected) MaterialTheme.typography.labelLarge
                                else MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) {
                                Text(
                                    atlasNodeCategoryLabel(atlasNodeCategory(node)),
                                    style = HudTextStyle,
                                    color = accent,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            if (neighborhood.overview) {
                "Toca una entidad para convertirla en el centro del mapa."
            } else {
                "Toca otro nodo para reenfocar. Toca una etiqueta de relación para ver sus fuentes."
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF7F93AE),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun AtlasEvidence(
    state: JarvisViewModel.WritingWorkspaceState,
    onOpenWiki: () -> Unit,
    onResolveEvidence: (String) -> Unit,
    onOpenSourceDocument: (String) -> Unit,
) {
    AtlasCard(JarvisAmber) {
        Text("FUENTES Y PROCEDENCIA", style = HudTextStyle, color = JarvisAmber)
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
            response.data.assertions.take(20).forEach { AtlasAssertion(it, onResolveEvidence) }
            OutlinedButton(onClick = onOpenWiki) { Text("Abrir articulo Wiki") }
        }
        edge?.let { response ->
            Spacer(Modifier.height(8.dp))
            Text("Relación: ${atlasPredicateLabel(response.data.edge.predicate_id)}", color = JarvisViolet)
            response.data.assertions.take(20).forEach { AtlasAssertion(it, onResolveEvidence) }
        }
        state.knowledgeResolvedSource?.let { resolved ->
            Spacer(Modifier.height(10.dp))
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = JarvisCyan.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.28f)),
            ) {
                Column(Modifier.padding(9.dp)) {
                    Text("FUENTE RESUELTA", style = HudTextStyle, color = JarvisCyan)
                    val source = resolved.data.source
                    Text(
                        source.source_document_id.ifBlank { source.source_id },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        listOf(source.role, source.authority, source.provider)
                            .filter { it.isNotBlank() }
                            .joinToString(" / "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1),
                    )
                    val evidence = resolved.data.evidence
                    val locator = buildList {
                        evidence.chapter_number?.let { add("cap. $it") }
                        evidence.section?.takeIf { it.isNotBlank() }?.let(::add)
                        evidence.source_line_start?.let { start ->
                            add("lineas $start-${evidence.source_line_end ?: start}")
                        }
                    }.joinToString(" / ")
                    if (locator.isNotBlank()) {
                        Text(locator, style = MaterialTheme.typography.labelSmall, color = JarvisGreen)
                    }
                    if (source.source_document_id.isNotBlank()) {
                        TextButton(onClick = { onOpenSourceDocument(source.source_document_id) }) {
                            Text("Abrir documento fuente")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtlasAssertion(assertion: KnowledgeAssertion, onResolveEvidence: (String) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(9.dp),
        color = Color(0x551E293B),
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(atlasPredicateLabel(assertion.predicate_id).ifBlank { assertion.assertion_id }, color = Color.White)
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
                    Column {
                        Text(
                            "- ${evidence.support.ifBlank { "EVIDENCE" }} / ${evidence.locator_precision.ifBlank { "UNKNOWN" }}" +
                                if (parts.isEmpty()) "" else " / ${parts.joinToString(" / ")}",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (evidence.evidence_id.isNotBlank()) {
                            TextButton(
                                onClick = { onResolveEvidence(evidence.evidence_id) },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            ) {
                                Text("Resolver fuente", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
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
