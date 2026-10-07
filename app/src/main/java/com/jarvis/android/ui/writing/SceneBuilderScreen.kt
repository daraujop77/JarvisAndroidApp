package com.jarvis.android.ui.writing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.transport.live.VisualSceneReference
import com.jarvis.android.transport.live.VisualStudioAsset
import com.jarvis.android.transport.live.WritingWikiEntity
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.shared.rememberAttachmentThumb
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.LocalJarvisAccents

@Composable
fun SceneBuilderScreen(
    vm: JarvisViewModel,
    projectId: String,
    characters: List<WritingWikiEntity>,
    locations: List<WritingWikiEntity>,
    onUseInChapter: (String) -> Unit = {},
    onEditScene: (String, VisualStudioAsset) -> Unit = { _, _ -> },
    headerContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val writing by vm.writingWorkspace.collectAsStateWithLifecycle()
    val builder by vm.sceneBuilder.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val accents = LocalJarvisAccents.current
    val activeChapterId = writing.activeChapter?.chapter_id.orEmpty()
    val chapterId = writing.planningV2ChapterId.orEmpty().ifBlank { activeChapterId }
    val direction = writing.planningV2Direction ?: writing.draftV2?.direction
    val draftId = writing.draftV2?.aggregate?.current_draft_revision_id.orEmpty()
    val briefId = direction?.brief?.brief_revision_id.orEmpty()
    val aggregateVersion = maxOf(
        writing.planningV2AggregateVersion,
        direction?.aggregate_version ?: 0,
        writing.draftV2?.aggregate?.version ?: 0,
    )
    val useDraft = draftId.isNotBlank()
    val selectedBriefId = if (useDraft) "" else briefId
    val selectedDraftId = if (useDraft) draftId else ""

    var sceneId by rememberSaveable(projectId, chapterId) { mutableStateOf("scene:1") }
    var selectedCharactersCsv by rememberSaveable(projectId, chapterId) { mutableStateOf("") }
    var selectedLocationId by rememberSaveable(projectId, chapterId) { mutableStateOf("") }
    var era by rememberSaveable(projectId, chapterId) { mutableStateOf("") }
    var stateText by rememberSaveable(projectId, chapterId) { mutableStateOf("") }
    var time by rememberSaveable(projectId, chapterId) { mutableStateOf("") }
    var weather by rememberSaveable(projectId, chapterId) { mutableStateOf("") }
    var composition by rememberSaveable(projectId, chapterId) { mutableStateOf("") }
    var instruction by rememberSaveable(projectId, chapterId) { mutableStateOf("") }
    var directorRequest by rememberSaveable(projectId) { mutableStateOf("") }
    var showManual by rememberSaveable(projectId) { mutableStateOf(false) }
    var generationEngine by rememberSaveable(projectId) { mutableStateOf("cloud") }
    var generationMode by rememberSaveable(projectId) { mutableStateOf("quality") }

    val selectedCharacterIds = selectedCharactersCsv
        .split("|")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    LaunchedEffect(projectId) {
        if (builder.projectId.isNotBlank() && builder.projectId != projectId) {
            vm.clearSceneBuilderContext(projectId)
        }
        vm.refreshSceneGenerationCapabilities(projectId)
    }

    LaunchedEffect(chapterId, writing.activeChapter?.characters) {
        if (selectedCharactersCsv.isBlank()) {
            val names = writing.activeChapter?.characters.orEmpty()
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .toSet()
            val matches = characters.filter { entry ->
                entry.id.trim().lowercase() in names ||
                    entry.name.trim().lowercase() in names ||
                    entry.canonical_name.trim().lowercase() in names
            }.map { it.id }.filter { it.isNotBlank() }
            if (matches.isNotEmpty()) selectedCharactersCsv = matches.joinToString("|")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "visual_studio_navigation") { headerContent() }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xCC0E182A),
                border = BorderStroke(1.dp, accents.orbGlow.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("SCENE BUILDER", style = MaterialTheme.typography.titleMedium, color = Color(0xFFF8FAFC))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (chapterId.isBlank()) {
                            "El Director puede buscar en la historia completa; abre un capítulo solo si quieres usar el modo manual."
                        } else {
                            "Capítulo: " + chapterId + " · " + if (useDraft) "Draft" else "Brief"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                    )
                    if (selectedDraftId.isNotBlank() || selectedBriefId.isNotBlank()) {
                        Text(
                            "Revisión: " + selectedDraftId.ifBlank { selectedBriefId }.take(28),
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisCyan,
                        )
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xCC0B2233),
                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.48f)),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "DIRECTOR DE ESCENA",
                        color = JarvisCyan,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Describe qué momento quieres ver. JARVIS busca el pasaje exacto, identifica participantes y locación, elige referencias aprobadas y evalúa la imagen antes de entregártela.",
                        color = Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = directorRequest,
                        onValueChange = { directorRequest = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ej. batalla de Doom contra el Guardián y Soren") },
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = settings.isOwner &&
                            !builder.busy &&
                            directorRequest.isNotBlank(),
                        onClick = {
                            vm.directSceneVisual(
                                projectId = projectId,
                                requestText = directorRequest,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (builder.busy && builder.directorStatus == "RESOLVING") {
                                "Buscando y verificando…"
                            } else {
                                "Generar desde texto y canon"
                            },
                        )
                    }
                    if (!settings.isOwner) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Solo el owner puede iniciar generación visual.",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (builder.directorStatus == "AMBIGUOUS" && builder.directorCandidates.isNotEmpty()) {
            item {
                Text(
                    "Encontré varios pasajes posibles",
                    color = JarvisAmber,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(
                builder.directorCandidates,
                key = { "director:" + it.evidence_id },
            ) { candidate ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xCC171C29),
                    border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.35f)),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            candidate.title.ifBlank {
                                candidate.heading.ifBlank { candidate.kind }
                            },
                            color = Color(0xFFF8FAFC),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            listOfNotNull(
                                candidate.chapter_number?.let { "Cap. $it" },
                                candidate.canon_status.takeIf { it.isNotBlank() },
                                candidate.kind.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            color = JarvisCyan,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            candidate.excerpt,
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 6,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            enabled = !builder.busy,
                            onClick = {
                                vm.directSceneVisual(
                                    projectId = projectId,
                                    requestText = directorRequest,
                                    selectedEvidenceId = candidate.evidence_id,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Usar este pasaje")
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showManual = !showManual },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (showManual) {
                        "Ocultar modo manual"
                    } else {
                        "Modo manual / avanzado"
                    },
                )
            }
        }

        if (showManual) {
        item {
            OutlinedTextField(
                value = sceneId,
                onValueChange = { sceneId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ID estable de escena/beat") },
                singleLine = true,
            )
        }

        if (characters.isNotEmpty()) {
            item {
                Text("Personajes", color = Color(0xFFF8FAFC))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    characters.forEach { entry ->
                        val selected = entry.id in selectedCharacterIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = selectedCharacterIds.toMutableList()
                                if (selected) next.remove(entry.id) else next.add(entry.id)
                                selectedCharactersCsv = next.distinct().joinToString("|")
                            },
                            label = { Text(entry.name.ifBlank { entry.id }) },
                        )
                    }
                }
            }
        }

        if (locations.isNotEmpty()) {
            item {
                Text("Locación", color = Color(0xFFF8FAFC))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    FilterChip(
                        selected = selectedLocationId.isBlank(),
                        onClick = { selectedLocationId = "" },
                        label = { Text("Sin locación") },
                    )
                    locations.forEach { entry ->
                        FilterChip(
                            selected = selectedLocationId == entry.id,
                            onClick = { selectedLocationId = entry.id },
                            label = { Text(entry.name.ifBlank { entry.id }) },
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = era,
                    onValueChange = { era = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Era") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = stateText,
                    onValueChange = { stateText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Estado") },
                    singleLine = true,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Hora/momento") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = weather,
                    onValueChange = { weather = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Clima") },
                    singleLine = true,
                )
            }
        }

        item {
            OutlinedTextField(
                value = composition,
                onValueChange = { composition = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Composición") },
                minLines = 2,
            )
        }

        item {
            OutlinedTextField(
                value = instruction,
                onValueChange = { instruction = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Instrucción visual para generar") },
                minLines = 2,
            )
        }

        item {
            val canPreview =
                !builder.busy &&
                    chapterId.isNotBlank() &&
                    aggregateVersion > 0 &&
                    (selectedBriefId.isNotBlank() || selectedDraftId.isNotBlank()) &&
                    sceneId.isNotBlank() &&
                    (selectedCharacterIds.isNotEmpty() || selectedLocationId.isNotBlank())
            Button(
                enabled = canPreview,
                onClick = {
                    vm.previewSceneVisualContext(
                        projectId = projectId,
                        chapterId = chapterId,
                        sceneId = sceneId.trim(),
                        expectedAggregateVersion = aggregateVersion,
                        briefRevisionId = selectedBriefId,
                        draftRevisionId = selectedDraftId,
                        characterIds = selectedCharacterIds,
                        locationId = selectedLocationId,
                        era = era,
                        state = stateText,
                        time = time,
                        weather = weather,
                        composition = composition,
                        instruction = instruction,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (builder.busy) "Preparando…" else "Preparar referencias")
            }
        }

        }

        builder.error?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        builder.notice?.let { notice ->
            item {
                Text(
                    notice,
                    color = if (builder.context?.generation_ready == true) JarvisGreen else JarvisAmber,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        builder.context?.let { context ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xCC101B2E),
                    border = BorderStroke(
                        1.dp,
                        if (context.generation_ready) JarvisGreen.copy(alpha = 0.45f)
                        else JarvisAmber.copy(alpha = 0.45f),
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            if (context.generation_ready) "CONTEXTO LISTO" else "SELECCIÓN PENDIENTE",
                            color = if (context.generation_ready) JarvisGreen else JarvisAmber,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            "Contexto " + context.context_id.take(28) + " · " + context.reference_manifest.approval_policy,
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Snapshot " + context.source.snapshot_id.take(20) + " · " +
                                context.reference_manifest.references.size + " referencia(s)",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (context.narrative_evidence.text.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "PASAJE CONGELADO · " +
                                    context.narrative_evidence.kind.ifBlank {
                                        context.narrative_evidence.canon_status
                                    },
                                color = JarvisCyan,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                context.narrative_evidence.text,
                                color = Color(0xFFCBD5E1),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 8,
                            )
                            Text(
                                "SHA " + context.narrative_evidence.sha256.take(12) + "… · " +
                                    context.narrative_evidence.canon_status,
                                color = Color(0xFF94A3B8),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            if (context.reference_manifest.references.isNotEmpty()) {
                item {
                    Text("Referencias exactas", color = Color(0xFFF8FAFC), style = MaterialTheme.typography.titleSmall)
                }
                items(
                    context.reference_manifest.references,
                    key = { it.asset_id + ":" + it.role },
                ) { reference ->
                    SceneReferenceCard(reference)
                }
            }

            if (context.reference_manifest.unresolved_roles.isNotEmpty()) {
                item {
                    Text("Requieren selección explícita", color = JarvisAmber, style = MaterialTheme.typography.titleSmall)
                }
                items(context.reference_manifest.unresolved_roles) { unresolved ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xAA261B0E),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(unresolved.role, color = Color(0xFFF8FAFC))
                            Text(unresolved.reason, color = JarvisAmber, style = MaterialTheme.typography.bodySmall)
                            unresolved.candidates.take(3).forEach { candidate ->
                                Text(
                                    "• " + candidate.perspective.ifBlank { candidate.kind } +
                                        " · " + candidate.applicability.status,
                                    color = Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            if (context.generation_ready && !context.exploratory) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xCC101B2E),
                        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.35f)),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "GENERACIÓN DE ESCENA",
                                color = JarvisCyan,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(Modifier.height(8.dp))
                            val cloudCapability = builder.capabilities
                                ?.engines
                                ?.get("cloud")
                            val localCapability = builder.capabilities
                                ?.engines
                                ?.get("local")
                            val cloudReady = cloudCapability == null ||
                                cloudCapability.state == "ready"
                            val localReady = localCapability?.state == "ready" &&
                                (
                                    localCapability.exact_reference_count <= 0 ||
                                        context.reference_manifest.references.size ==
                                        localCapability.exact_reference_count
                                )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = generationEngine == "cloud",
                                    onClick = { generationEngine = "cloud" },
                                    enabled = cloudReady,
                                    label = { Text("Cloud") },
                                )
                                FilterChip(
                                    selected = generationEngine == "local",
                                    onClick = { generationEngine = "local" },
                                    enabled = localReady,
                                    label = { Text("Local") },
                                )
                            }
                            localCapability?.let { local ->
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    when {
                                        local.state == "ready" ->
                                            "Local · " + local.model +
                                                " · " + local.exact_reference_count +
                                                " referencias exactas"
                                        local.reason == "reference_materialization_unavailable" ->
                                            "Local pendiente: falta materializar las referencias Drive en el PC. No habrá fallback a Cloud."
                                        local.reason == "pc_upstream_unavailable" ->
                                            "Local no disponible: el nodo PC no está conectado."
                                        else ->
                                            "Local no disponible. JARVIS no cambiará a Cloud automáticamente."
                                    },
                                    color = if (localReady) JarvisGreen else JarvisAmber,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = generationMode == "quality",
                                    onClick = { generationMode = "quality" },
                                    enabled = generationEngine == "cloud",
                                    label = { Text("Quality") },
                                )
                                FilterChip(
                                    selected = generationMode == "speed",
                                    onClick = { generationMode = "speed" },
                                    enabled = generationEngine == "cloud",
                                    label = { Text("Speed") },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    vm.generateSceneVisual(
                                        projectId = projectId,
                                        engine = generationEngine,
                                        mode = generationMode,
                                        aspectRatio = "landscape",
                                    )
                                },
                                enabled = (
                                    settings.isOwner &&
                                        !builder.busy &&
                                        context.selection.instruction.isNotBlank() &&
                                        (
                                            generationEngine == "cloud" &&
                                                cloudReady ||
                                                generationEngine == "local" &&
                                                localReady
                                        )
                                    ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (builder.busy) {
                                        builder.busyLabel.ifBlank { "Generando…" }
                                    } else {
                                        "Generar escena con referencias"
                                    },
                                )
                            }
                            if (!settings.isOwner) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "Solo el owner puede generar o aprobar arte visual.",
                                    color = Color(0xFF94A3B8),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (context.selection.instruction.isBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "Agrega una instrucción visual y vuelve a preparar referencias antes de generar.",
                                    color = JarvisAmber,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        builder.generated?.let { generated ->
            val asset = generated.visual_asset
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xCC0E182A),
                    border = BorderStroke(
                        1.dp,
                        if (asset?.status == "APPROVED") {
                            JarvisGreen.copy(alpha = 0.50f)
                        } else {
                            JarvisAmber.copy(alpha = 0.50f)
                        },
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            if (asset?.status == "APPROVED") {
                                "ESCENA VISUAL APROBADA"
                            } else {
                                "ESCENA CANDIDATA"
                            },
                            color = if (asset?.status == "APPROVED") JarvisGreen else JarvisAmber,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        SceneGeneratedPreview(
                            attachmentId = builder.generatedAttachmentId,
                            vm = vm,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            generated.provider + " · " + generated.model,
                            color = JarvisCyan,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Refs exactas: " + generated.reference_count +
                                " · SHA " + (asset?.sha256?.take(12) ?: "—") + "…",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Storage: " + (asset?.storage?.state ?: "unknown"),
                            color = if (asset?.storage?.state == "stored") JarvisGreen else JarvisAmber,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (generated.scene_evaluation.verdict.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            val evaluation = generated.scene_evaluation
                            Text(
                                "EVALUACIÓN · " + evaluation.verdict +
                                    " · correcciones " + generated.scene_correction_count,
                                color = if (evaluation.verdict == "PASS") JarvisGreen else JarvisAmber,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            if (
                                evaluation.narrative_score != null ||
                                evaluation.identity_score != null ||
                                evaluation.composition_score != null
                            ) {
                                Text(
                                    "Pasaje " + (evaluation.narrative_score?.toString() ?: "—") +
                                        " · identidad " + (evaluation.identity_score?.toString() ?: "—") +
                                        " · composición " + (evaluation.composition_score?.toString() ?: "—"),
                                    color = Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            evaluation.issues.take(4).forEach { issue ->
                                Text(
                                    "• " + issue,
                                    color = JarvisAmber,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (generated.scene_attempts.size > 1) {
                                Text(
                                    generated.scene_attempts.size.toString() +
                                        " candidatos conservados en el historial de generación.",
                                    color = Color(0xFF94A3B8),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (asset != null && settings.isOwner) {
                            Spacer(Modifier.height(10.dp))
                            if (asset.storage.state != "stored") {
                                OutlinedButton(
                                    onClick = vm::retrySceneVisualStorage,
                                    enabled = !builder.busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Reintentar Drive sin regenerar")
                                }
                            } else if (asset.status == "CANDIDATE") {
                                Button(
                                    onClick = vm::approveSceneVisual,
                                    enabled = !builder.busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Aprobar escena visual")
                                }
                            }
                            if (asset.status == "APPROVED") {
                                val attachmentId = builder.generatedAttachmentId
                                if (!attachmentId.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            onEditScene(attachmentId, asset)
                                        },
                                        enabled = !builder.busy,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Editar en Image Studio")
                                    }
                                }
                                if (asset.chapter_ids.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            onUseInChapter(
                                                asset.chapter_ids.first(),
                                            )
                                        },
                                        enabled = !builder.busy,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Usar en capítulo")
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Una imagen candidata o aprobada no altera hechos, timeline ni canon narrativo.",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneGeneratedPreview(
    attachmentId: String?,
    vm: JarvisViewModel,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(Color(0xFF050B14), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (attachmentId.isNullOrBlank()) {
            CircularProgressIndicator(color = LocalJarvisAccents.current.orbGlow)
        } else {
            val bitmap by rememberAttachmentThumb(
                attachmentId,
                vm.attachmentStore,
                maxSize = 2048,
            )
            if (bitmap == null) {
                CircularProgressIndicator(color = LocalJarvisAccents.current.orbGlow)
            } else {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Escena generada",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun SceneReferenceCard(reference: VisualSceneReference) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xCC0E182A),
        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(reference.role, color = Color(0xFFF8FAFC), style = MaterialTheme.typography.titleSmall)
            Text(
                reference.authority + " · " + reference.perspective.ifBlank { reference.kind },
                color = JarvisCyan,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Asset " + reference.asset_id.take(24) + " · SHA " + reference.asset_sha256.take(12) + "…",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall,
            )
            if (reference.applicability.status != "MATCH") {
                Text(
                    "Aplicabilidad: " + reference.applicability.status,
                    color = JarvisAmber,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
