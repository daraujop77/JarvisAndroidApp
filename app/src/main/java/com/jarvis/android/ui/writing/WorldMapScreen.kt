package com.jarvis.android.ui.writing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.transport.live.WorldMapCharacterPresence
import com.jarvis.android.transport.live.WorldMapNode
import com.jarvis.android.transport.live.WritingWikiEntity
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.JarvisRed
import com.jarvis.android.ui.theme.JarvisViolet
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun WorldMapScreen(
    vm: JarvisViewModel,
    projectId: String,
    locations: List<WritingWikiEntity>,
    characters: List<WritingWikiEntity>,
    modifier: Modifier = Modifier,
) {
    val state by vm.worldMap.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val accents = LocalJarvisAccents.current

    var nodeType by rememberSaveable(projectId) { mutableStateOf("LOCATION") }
    var nodeName by rememberSaveable(projectId) { mutableStateOf("") }
    var parentNodeId by rememberSaveable(projectId) { mutableStateOf("") }
    var locationId by rememberSaveable(projectId) { mutableStateOf("") }

    LaunchedEffect(projectId) {
        vm.openWorldMap(projectId)
    }

    val map = state.map
    val draft = map?.revision?.state == "DRAFT"
    val locationOptions = remember(locations) {
        locations
            .filter { it.type.equals("location", ignoreCase = true) && it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }
    val characterNames = remember(characters) {
        characters.associate { item ->
            item.id to item.canonical_name.ifBlank {
                item.name.ifBlank { item.id }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(accents.backdrop),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WorldMapPanel("WORLD MAP", JarvisCyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Mapa espacial del proyecto",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Separado del Atlas de relaciones. Las posiciones son internas al mundo; ninguna imagen crea hechos narrativos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB7C7DC),
                        )
                    }
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(22.dp),
                            strokeWidth = 2.dp,
                            color = accents.orbGlow,
                        )
                    }
                }

                Spacer(Modifier.height(9.dp))
                if (map == null) {
                    Text(
                        "Aún no hay una revisión activa o DRAFT.",
                        color = Color(0xFF94A3B8),
                    )
                    if (settings.isOwner) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.createWorldMapDraft(projectId) },
                            enabled = !state.busy,
                        ) {
                            Icon(Icons.Filled.AddLocationAlt, contentDescription = null)
                            Spacer(Modifier.height(1.dp))
                            Text("Crear World Map")
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MapPill(
                            map.revision.state,
                            if (draft) JarvisAmber else JarvisGreen,
                        )
                        MapPill("r${map.revision.revision}", JarvisCyan)
                        MapPill("v${map.revision.version}", JarvisViolet)
                        MapPill(
                            "SHA ${map.revision.content_hash.take(8)}",
                            Color(0xFF7DD3FC),
                        )
                    }
                    if (!draft && settings.isOwner && state.status?.draft == null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { vm.createWorldMapDraft(projectId) },
                            enabled = !state.busy,
                        ) {
                            Text("Nueva revisión desde mapa activo")
                        }
                    }
                }
            }
        }

        state.conflict?.let { conflict ->
            item {
                WorldMapMessage(
                    color = JarvisAmber,
                    text = buildString {
                        append(conflict.message)
                        conflict.currentVersion?.let {
                            append(" · servidor v")
                            append(it)
                        }
                        if (!conflict.currentHash.isNullOrBlank()) {
                            append(" · ")
                            append(conflict.currentHash.take(10))
                            append("…")
                        }
                    },
                    onDismiss = vm::clearWorldMapMessage,
                )
            }
        }
        state.notice?.let {
            item {
                WorldMapMessage(
                    color = JarvisGreen,
                    text = it,
                    onDismiss = vm::clearWorldMapMessage,
                )
            }
        }
        state.error?.let {
            item {
                WorldMapMessage(
                    color = JarvisRed,
                    text = it,
                    onDismiss = vm::clearWorldMapMessage,
                )
            }
        }

        if (map != null) {
            item {
                WorldMapPanel("CANVAS ESPACIAL", JarvisGreen) {
                    Text(
                        if (draft && settings.isOwner) {
                            "Arrastra un nodo para cambiar su posición. El movimiento se guarda al soltar."
                        } else {
                            "Revisión de solo lectura. Crea una nueva revisión para mover nodos."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(Modifier.height(8.dp))
                    WorldMapCanvas(
                        nodes = map.nodes,
                        presence = map.character_presence,
                        editable = draft && settings.isOwner && !state.busy,
                        onMoveNode = { node, x, y ->
                            vm.upsertWorldMapNode(
                                projectId = projectId,
                                nodeId = node.node_id,
                                nodeType = node.node_type,
                                name = node.name,
                                parentNodeId = node.parent_node_id,
                                locationId = node.location_id,
                                x = x,
                                y = y,
                                z = node.z,
                            )
                        },
                    )
                }
            }

            item {
                WorldMapPanel("JERARQUÍA", JarvisViolet) {
                    if (map.nodes.isEmpty()) {
                        Text(
                            "La revisión está vacía.",
                            color = Color(0xFF94A3B8),
                        )
                    } else {
                        map.nodes
                            .sortedWith(
                                compareBy<WorldMapNode>(
                                    { mapNodeTypeOrder(it.node_type) },
                                    { it.name.lowercase() },
                                ),
                            )
                            .forEach { node ->
                                val parent = map.nodes.firstOrNull {
                                    it.node_id == node.parent_node_id
                                }
                                val count = map.character_presence.count {
                                    it.node_id == node.node_id
                                }
                                Text(
                                    "${mapNodeIndent(node.node_type)}${node.name} · ${node.node_type}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (node.node_type == "WORLD") {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                                Text(
                                    buildString {
                                        if (parent != null) {
                                            append("↳ ")
                                            append(parent.name)
                                            append(" · ")
                                        }
                                        append("(")
                                        append(node.x.roundToInt())
                                        append(", ")
                                        append(node.y.roundToInt())
                                        append(")")
                                        if (node.visual_asset_id.isNotBlank()) {
                                            append(" · visual aprobado")
                                        }
                                        if (count > 0) {
                                            append(" · ")
                                            append(count)
                                            append(" presencia(s)")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8),
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                    }
                }
            }

            if (draft && settings.isOwner) {
                item {
                    WorldMapPanel("AGREGAR NODO", JarvisAmber) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MAP_NODE_TYPES.forEach { type ->
                                FilterChip(
                                    selected = nodeType == type,
                                    onClick = {
                                        nodeType = type
                                        parentNodeId = ""
                                        if (type !in setOf("LOCATION", "SUBLOCATION")) {
                                            locationId = ""
                                        }
                                    },
                                    label = {
                                        Text(
                                            type.lowercase().replaceFirstChar {
                                                if (it.isLowerCase()) it.titlecase()
                                                else it.toString()
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = nodeName,
                            onValueChange = { if (it.length <= 240) nodeName = it },
                            label = { Text("Nombre") },
                            singleLine = true,
                            colors = jarvisTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val parents = allowedWorldMapParents(nodeType, map.nodes)
                        if (nodeType != "WORLD") {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Padre",
                                style = HudTextStyle,
                                color = Color(0xFF94A3B8),
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                parents.forEach { parent ->
                                    FilterChip(
                                        selected = parentNodeId == parent.node_id,
                                        onClick = { parentNodeId = parent.node_id },
                                        label = { Text(parent.name) },
                                    )
                                }
                            }
                        }

                        if (nodeType in setOf("LOCATION", "SUBLOCATION")) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Locación canónica",
                                style = HudTextStyle,
                                color = Color(0xFF94A3B8),
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                locationOptions.forEach { location ->
                                    FilterChip(
                                        selected = locationId == location.id,
                                        onClick = { locationId = location.id },
                                        label = {
                                            Text(
                                                location.canonical_name.ifBlank {
                                                    location.name.ifBlank { location.id }
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                            Text(
                                "Si esta locación tiene master APPROVED en Location Studio, JARVIS lo vincula por asset + SHA; nunca usa un CANDIDATE.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7DD3FC),
                            )
                        }

                        Spacer(Modifier.height(9.dp))
                        Button(
                            onClick = {
                                vm.upsertWorldMapNode(
                                    projectId = projectId,
                                    nodeId = if (nodeType == "WORLD") "world:main" else null,
                                    nodeType = nodeType,
                                    name = nodeName,
                                    parentNodeId = if (nodeType == "WORLD") "" else parentNodeId,
                                    locationId = if (
                                        nodeType in setOf("LOCATION", "SUBLOCATION")
                                    ) {
                                        locationId
                                    } else {
                                        ""
                                    },
                                    x = 0.0,
                                    y = 0.0,
                                )
                                nodeName = ""
                            },
                            enabled = (
                                nodeName.isNotBlank() &&
                                    !state.busy &&
                                    (
                                        nodeType == "WORLD" ||
                                            parentNodeId.isNotBlank()
                                    ) &&
                                    (
                                        nodeType !in setOf("LOCATION", "SUBLOCATION") ||
                                            locationId.isNotBlank()
                                    )
                                ),
                        ) {
                            Icon(Icons.Filled.AddLocationAlt, contentDescription = null)
                            Text(" Agregar al DRAFT")
                        }
                    }
                }
            }

            if (map.character_presence.isNotEmpty()) {
                item {
                    Text(
                        "PRESENCIA CON EVIDENCIA",
                        style = HudTextStyle,
                        color = JarvisCyan,
                    )
                }
                items(
                    map.character_presence,
                    key = { "world_presence_${it.presence_id}" },
                ) { item ->
                    WorldPresenceCard(
                        item = item,
                        node = map.nodes.firstOrNull { it.node_id == item.node_id },
                        characterLabel = characterNames[item.character_id]
                            ?: item.character_id,
                    )
                }
            }

            item {
                WorldMapPanel("REVISIÓN", accents.orbGlow) {
                    Text(
                        "La aprobación fija exactamente v${map.revision.version} · ${map.revision.content_hash.take(14)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                    )
                    if (draft && settings.isOwner) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.approveWorldMapRevision(projectId) },
                            enabled = !state.busy && map.nodes.any {
                                it.node_type == "WORLD"
                            },
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Text(" Aprobar revisión")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.refreshWorldMap(projectId) },
                        enabled = !state.busy,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text(" Actualizar")
                    }
                }
            }

            if (state.status?.revisions.orEmpty().isNotEmpty()) {
                item {
                    WorldMapPanel("HISTORIAL", Color(0xFF94A3B8)) {
                        state.status?.revisions
                            .orEmpty()
                            .sortedByDescending { it.revision }
                            .forEach { revision ->
                                Text(
                                    "r${revision.revision} · ${revision.state} · v${revision.version} · ${revision.content_hash.take(8)}…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1),
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldMapCanvas(
    nodes: List<WorldMapNode>,
    presence: List<WorldMapCharacterPresence>,
    editable: Boolean,
    onMoveNode: (WorldMapNode, Double, Double) -> Unit,
) {
    if (nodes.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color(0xAA07101E), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Agrega el nodo WORLD para comenzar.",
                color = Color(0xFF94A3B8),
            )
        }
        return
    }

    val density = LocalDensity.current
    val localPositions = remember { mutableStateMapOf<String, Offset>() }

    val minX = nodes.minOfOrNull { it.x } ?: -100.0
    val maxX = nodes.maxOfOrNull { it.x } ?: 100.0
    val minY = nodes.minOfOrNull { it.y } ?: -100.0
    val maxY = nodes.maxOfOrNull { it.y } ?: 100.0
    val centerX = (minX + maxX) / 2.0
    val centerY = (minY + maxY) / 2.0
    val rangeX = max(200.0, maxX - minX + 160.0)
    val rangeY = max(200.0, maxY - minY + 160.0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(Color(0xAA07101E), RoundedCornerShape(16.dp)),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        fun worldToPixel(x: Double, y: Double): Offset {
            val normalizedX = ((x - centerX) / rangeX + 0.5)
                .coerceIn(0.04, 0.96)
            val normalizedY = ((y - centerY) / rangeY + 0.5)
                .coerceIn(0.06, 0.94)
            return Offset(
                (normalizedX * widthPx).toFloat(),
                (normalizedY * heightPx).toFloat(),
            )
        }

        fun pixelToWorld(offset: Offset): Pair<Double, Double> {
            val normalizedX = (offset.x / widthPx).coerceIn(0.04f, 0.96f)
            val normalizedY = (offset.y / heightPx).coerceIn(0.06f, 0.94f)
            return Pair(
                centerX + (normalizedX - 0.5) * rangeX,
                centerY + (normalizedY - 0.5) * rangeY,
            )
        }

        LaunchedEffect(nodes, widthPx, heightPx) {
            val ids = nodes.map { it.node_id }.toSet()
            localPositions.keys
                .filter { it !in ids }
                .forEach { localPositions.remove(it) }
            nodes.forEach { node ->
                localPositions[node.node_id] = worldToPixel(node.x, node.y)
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            nodes.forEach { node ->
                val parentId = node.parent_node_id
                if (parentId.isBlank()) return@forEach
                val from = localPositions[parentId] ?: return@forEach
                val to = localPositions[node.node_id] ?: return@forEach
                drawLine(
                    color = Color(0x665EE7F7),
                    start = from,
                    end = to,
                    strokeWidth = 2f,
                )
            }
        }

        nodes.forEach { node ->
            val point = localPositions[node.node_id]
                ?: worldToPixel(node.x, node.y)
            val nodeWidthPx = with(density) { 118.dp.toPx() }
            val nodeHeightPx = with(density) { 54.dp.toPx() }
            val presenceCount = presence.count { it.node_id == node.node_id }
            Surface(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (point.x - nodeWidthPx / 2f).roundToInt(),
                            (point.y - nodeHeightPx / 2f).roundToInt(),
                        )
                    }
                    .widthIn(min = 96.dp, max = 128.dp)
                    .pointerInput(node.node_id, editable) {
                        if (!editable) return@pointerInput
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val previous = localPositions[node.node_id]
                                    ?: worldToPixel(node.x, node.y)
                                localPositions[node.node_id] = Offset(
                                    (previous.x + dragAmount.x)
                                        .coerceIn(8f, widthPx - 8f),
                                    (previous.y + dragAmount.y)
                                        .coerceIn(8f, heightPx - 8f),
                                )
                            },
                            onDragEnd = {
                                val moved = localPositions[node.node_id]
                                if (moved != null) {
                                    val (worldX, worldY) = pixelToWorld(moved)
                                    onMoveNode(node, worldX, worldY)
                                }
                            },
                        )
                    },
                shape = RoundedCornerShape(13.dp),
                color = mapNodeColor(node.node_type).copy(alpha = 0.16f),
                border = BorderStroke(
                    1.dp,
                    mapNodeColor(node.node_type).copy(alpha = 0.65f),
                ),
            ) {
                Column(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        node.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        buildString {
                            append(node.node_type)
                            if (presenceCount > 0) {
                                append(" · ")
                                append(presenceCount)
                                append("p")
                            }
                        },
                        color = mapNodeColor(node.node_type),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldPresenceCard(
    item: WorldMapCharacterPresence,
    node: WorldMapNode?,
    characterLabel: String,
) {
    val accent = when (item.temporal_kind) {
        "FUTURE" -> JarvisViolet
        "UNKNOWN" -> JarvisAmber
        else -> JarvisCyan
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xC90B1322),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    characterLabel,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                MapPill(item.temporal_kind, accent)
            }
            Text(
                "${node?.name ?: item.node_id} · ${item.temporal_ref.ifBlank { "sin fecha precisa" }}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1),
            )
            item.evidence.take(3).forEach { evidence ->
                Text(
                    "Evidencia · ${evidence.source_type}:${evidence.source_id}" +
                        if (evidence.lines.isNotBlank()) " · ${evidence.lines}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                )
            }
        }
    }
}

@Composable
private fun WorldMapPanel(
    title: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xEE0E182A),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = HudTextStyle, color = accent)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun WorldMapMessage(
    color: Color,
    text: String,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                color = Color(0xFFE2E8F0),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    }
}

@Composable
private fun MapPill(
    text: String,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun allowedWorldMapParents(
    type: String,
    nodes: List<WorldMapNode>,
): List<WorldMapNode> {
    val allowed = when (type) {
        "REGION" -> setOf("WORLD")
        "CITY" -> setOf("REGION")
        "LOCATION" -> setOf("CITY")
        "SUBLOCATION" -> setOf("LOCATION", "SUBLOCATION")
        else -> emptySet()
    }
    return nodes
        .filter { it.node_type in allowed }
        .sortedBy { it.name.lowercase() }
}

private fun mapNodeColor(type: String): Color = when (type) {
    "WORLD" -> JarvisCyan
    "REGION" -> JarvisGreen
    "CITY" -> JarvisViolet
    "LOCATION" -> JarvisAmber
    else -> Color(0xFF7DD3FC)
}

private fun mapNodeTypeOrder(type: String): Int = when (type) {
    "WORLD" -> 0
    "REGION" -> 1
    "CITY" -> 2
    "LOCATION" -> 3
    "SUBLOCATION" -> 4
    else -> 9
}

private fun mapNodeIndent(type: String): String = when (type) {
    "REGION" -> "  "
    "CITY" -> "    "
    "LOCATION" -> "      "
    "SUBLOCATION" -> "        "
    else -> ""
}

private val MAP_NODE_TYPES = listOf(
    "WORLD",
    "REGION",
    "CITY",
    "LOCATION",
    "SUBLOCATION",
)
