package com.jarvis.android.ui.writing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.jarvis.android.transport.live.VisualCharacterDirectionProfile
import com.jarvis.android.transport.live.VisualProjectStyleProfile
import com.jarvis.android.transport.live.VisualStudioAsset
import com.jarvis.android.transport.live.VisualStudioReferencePack
import com.jarvis.android.transport.live.WritingWikiEntity
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.shared.rememberAttachmentThumb
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.JarvisRed
import com.jarvis.android.ui.theme.JarvisViolet
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors

@Composable
fun CharacterStudioScreen(
    vm: JarvisViewModel,
    projectId: String,
    characters: List<WritingWikiEntity>,
    onEditInImageStudio: (String, String, VisualStudioAsset) -> Unit = { _, _, _ -> },
    headerContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by vm.characterStudio.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val accents = LocalJarvisAccents.current

    val characterOptions = remember(characters) {
        characters
            .filter { it.id.isNotBlank() && it.type.equals("character", ignoreCase = true) }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }
    var selectedCharacterId by rememberSaveable(projectId) {
        mutableStateOf(characterOptions.firstOrNull()?.id.orEmpty())
    }
    var masterPrompt by rememberSaveable(projectId, selectedCharacterId) { mutableStateOf("") }
    var turnaroundPrompt by rememberSaveable(projectId, selectedCharacterId) { mutableStateOf("") }
    var bulkMasterConfirmation by rememberSaveable(projectId) { mutableStateOf(false) }
    var selectedPerspective by rememberSaveable(projectId) { mutableStateOf("left_profile") }
    var showProjectStyleEditor by rememberSaveable(projectId) { mutableStateOf(false) }
    var showCharacterAppearanceEditor by rememberSaveable(projectId, selectedCharacterId) {
        mutableStateOf(false)
    }
    var projectStyleDraft by remember(projectId) {
        mutableStateOf(VisualProjectStyleProfile())
    }
    var characterDirectionDraft by remember(projectId, selectedCharacterId) {
        mutableStateOf(VisualCharacterDirectionProfile(character_id = selectedCharacterId))
    }

    LaunchedEffect(state.projectStyle?.revision, state.projectStyle?.source, projectId) {
        state.projectStyle?.let { projectStyleDraft = it }
    }
    LaunchedEffect(
        state.characterDirection?.revision,
        state.characterDirection?.source,
        selectedCharacterId,
    ) {
        state.characterDirection?.let { characterDirectionDraft = it }
    }

    LaunchedEffect(characterOptions) {
        if (selectedCharacterId.isBlank() || characterOptions.none { it.id == selectedCharacterId }) {
            selectedCharacterId = characterOptions.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(projectId, selectedCharacterId) {
        if (selectedCharacterId.isNotBlank()) {
            vm.openCharacterStudio(projectId, selectedCharacterId)
        }
    }

    val selectedCharacter = characterOptions.firstOrNull { it.id == selectedCharacterId }
    val selectedCharacterName = selectedCharacter?.let { character ->
        character.canonical_name.ifBlank { character.name.ifBlank { character.id } }
    }.orEmpty()
    val needsOwnerVisualDesign =
        characterDirectionDraft.design_status == "NEEDS_OWNER_INPUT"
    val characterDirectionLabel = when (characterDirectionDraft.design_status) {
        "READY_FROM_CANON" -> "Base canónica"
        "READY_FROM_EXTERNAL_CANON" -> "Base externa"
        "USER_CUSTOMIZED" -> "Personalizado"
        "NEEDS_OWNER_INPUT" -> "Requiere diseño"
        else -> characterDirectionDraft.source.ifBlank { "Sin definir" }
    }
    val projectStyleLabel = when {
        projectStyleDraft.seed_equivalent -> "Base Alexander"
        projectStyleDraft.source == "SAVED" -> "Personalizado"
        projectStyleDraft.source == "DEFAULT" -> "Predeterminado"
        else -> projectStyleDraft.source.ifBlank { "Sin guardar" }
    }
    val master = latestAsset(
        state.assets,
        kind = "PRIMARY_REFERENCE",
        status = "APPROVED",
        perspective = "front",
    )
    val masterCandidate = latestAsset(
        state.assets,
        kind = "PRIMARY_REFERENCE",
        status = "CANDIDATE",
        perspective = "front",
    )
    val approvedViews = state.assets
        .filter { it.kind == "IDENTITY_PACK" && it.status == "APPROVED" }
        .sortedWith(
            compareBy<VisualStudioAsset> { it.perspective }
                .thenByDescending { it.visual_revision },
        )
    val candidateViews = state.assets
        .filter { it.kind == "IDENTITY_PACK" && it.status == "CANDIDATE" }
        .sortedByDescending { it.created_utc }
    val activePack = state.detail?.active_reference_pack
    val pendingPack = state.detail?.reference_packs
        .orEmpty()
        .filter { it.state == "DRAFT" || it.state == "READY_FOR_APPROVAL" }
        .maxByOrNull { it.revision }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null && selectedCharacterId.isNotBlank()) {
            vm.uploadCharacterMaster(projectId, selectedCharacterId, uri)
        }
    }
    val turnaroundUploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null && selectedCharacterId.isNotBlank()) {
            vm.uploadCharacterTurnaround(
                projectId = projectId,
                characterId = selectedCharacterId,
                perspective = selectedPerspective,
                uri = uri,
            )
        }
    }

    if (bulkMasterConfirmation) {
        val count = state.masterRoster?.items.orEmpty().count {
            it.state == "READY" && it.character_id.isNotBlank()
        }
        AlertDialog(
            onDismissRequest = { bulkMasterConfirmation = false },
            title = { Text("Confirmar generación de masters") },
            text = {
                Text(
                    "Se solicitarán $count imágenes de pago con GPT Image 2 Medium. " +
                        "Cada master será una referencia frontal de cuerpo completo; " +
                        "el estilo visual del proyecto será el mismo para todos. " +
                        "Las imágenes se guardan como candidatas y requieren aprobación."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = count > 0 && !state.busy && settings.isOwner,
                    onClick = {
                        bulkMasterConfirmation = false
                        vm.generateMissingCharacterMasters(projectId, count)
                    },
                ) { Text("Generar $count candidatos") }
            },
            dismissButton = {
                TextButton(onClick = { bulkMasterConfirmation = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(accents.backdrop),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "visual_studio_navigation") { headerContent() }
        item {
            StudioPanel(
                title = "CHARACTER STUDIO",
                accent = accents.orbGlow,
            ) {
                Text(
                    "Master, vistas de identidad y Reference Pack del proyecto. Generar no aprueba; aprobar una imagen tampoco aprueba el pack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7C7DC),
                )
                Spacer(Modifier.height(10.dp))

                state.masterRoster?.let { roster ->
                    val readyNames = roster.items
                        .filter { it.state == "READY" }
                        .map { item -> item.canonical_name.ifBlank { item.character_id } }
                    val blockedNames = roster.items
                        .filter { it.state == "BLOCKED" }
                        .map { item -> item.canonical_name.ifBlank { item.character_id } }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x66101B2E),
                        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "BASES CANÓNICAS DEL ELENCO",
                                style = HudTextStyle,
                                color = JarvisCyan,
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Aprobados ${roster.counts.approved} · Candidatos ${roster.counts.candidate} · Por generar ${roster.counts.ready} · Bloqueados ${roster.counts.blocked}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB7C7DC),
                            )
                            if (readyNames.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Listos desde canon: " + readyNames.take(6).joinToString(", ") +
                                        if (readyNames.size > 6) "…" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisGreen,
                                )
                            }
                            if (blockedNames.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Sin evidencia visual suficiente: " + blockedNames.take(4).joinToString(", ") +
                                        if (blockedNames.size > 4) "…" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisAmber,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { bulkMasterConfirmation = true },
                                enabled = settings.isOwner && !state.busy && roster.counts.ready > 0,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (roster.counts.ready > 0) {
                                        "Generar ${roster.counts.ready} masters faltantes"
                                    } else {
                                        "Masters preparados"
                                    },
                                )
                            }
                            Text(
                                "JARVIS usa Wiki/canon y pasajes existentes. Primero confirmas la cantidad de generaciones de pago. Cada imagen sigue siendo candidata.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8BA2BE),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (characterOptions.isEmpty()) {
                    Text(
                        "No hay personajes disponibles en la Wiki de este proyecto.",
                        color = JarvisAmber,
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        characterOptions.forEach { character ->
                            FilterChip(
                                selected = character.id == selectedCharacterId,
                                onClick = { selectedCharacterId = character.id },
                                label = {
                                    Text(
                                        character.canonical_name.ifBlank {
                                            character.name.ifBlank { character.id }
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
                if (selectedCharacter != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        selectedCharacter.canonical_name.ifBlank {
                            selectedCharacter.name.ifBlank { selectedCharacter.id }
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF8FAFC),
                    )
                    Text(
                        selectedCharacter.role.ifBlank { selectedCharacter.summary }.take(180),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                    )
                }
            }
        }

        if (state.busy) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xEE101B2E),
                    border = BorderStroke(1.dp, accents.orbGlow.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = accents.orbGlow,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            state.busyLabel.ifBlank { "Procesando…" },
                            color = Color(0xFFE2E8F0),
                        )
                    }
                }
            }
        }

        state.notice?.let { notice ->
            item {
                StudioMessageCard(
                    text = notice,
                    color = JarvisGreen,
                    onDismiss = vm::clearCharacterStudioMessage,
                )
            }
        }
        state.error?.let { error ->
            item {
                StudioMessageCard(
                    text = error,
                    color = JarvisRed,
                    onDismiss = vm::clearCharacterStudioMessage,
                )
            }
        }

        if (selectedCharacterId.isNotBlank()) {
            item {
                StudioPanel("ESTILO VISUAL DEL PROYECTO", JarvisViolet) {
                    Text(
                        "Se aplica automáticamente a nuevos masters, vistas y escenas. No cambia el canon ni reemplaza imágenes aprobadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB7C7DC),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        projectStyleDraft.style_name.ifBlank { "Estilo predeterminado" },
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFF1F5F9),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showProjectStyleEditor = !showProjectStyleEditor },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (showProjectStyleEditor) "Cerrar editor" else "Editar estilo")
                    }
                    if (showProjectStyleEditor) {
                        Spacer(Modifier.height(8.dp))
                    DirectionTextField("Nombre del estilo", projectStyleDraft.style_name) {
                        projectStyleDraft = projectStyleDraft.copy(style_name = it)
                    }
                    DirectionTextField("Medio / acabado", projectStyleDraft.medium) {
                        projectStyleDraft = projectStyleDraft.copy(medium = it)
                    }
                    DirectionTextField("Nivel de realismo", projectStyleDraft.realism) {
                        projectStyleDraft = projectStyleDraft.copy(realism = it)
                    }
                    DirectionTextField("Paleta", projectStyleDraft.palette) {
                        projectStyleDraft = projectStyleDraft.copy(palette = it)
                    }
                    DirectionTextField("Iluminación", projectStyleDraft.lighting) {
                        projectStyleDraft = projectStyleDraft.copy(lighting = it)
                    }
                    DirectionTextField("Render / detalle", projectStyleDraft.rendering) {
                        projectStyleDraft = projectStyleDraft.copy(rendering = it)
                    }
                    DirectionTextField(
                        "Reglas obligatorias (separadas por coma)",
                        projectStyleDraft.positive_rules.joinToString(", "),
                    ) { value ->
                        projectStyleDraft = projectStyleDraft.copy(
                            positive_rules = splitVisualRules(value),
                        )
                    }
                    DirectionTextField(
                        "Evitar (separado por coma)",
                        projectStyleDraft.negative_rules.joinToString(", "),
                    ) { value ->
                        projectStyleDraft = projectStyleDraft.copy(
                            negative_rules = splitVisualRules(value),
                        )
                    }
                    Text(
                        "Estado: " + projectStyleLabel + " · revisión " +
                            projectStyleDraft.revision,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (projectStyleDraft.seed_equivalent) {
                            JarvisGreen
                        } else {
                            Color(0xFF94A3B8)
                        },
                    )
                    if (settings.isOwner) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                vm.saveProjectVisualStyle(projectId, projectStyleDraft)
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Guardar estilo del proyecto")
                        }
                        if (projectStyleDraft.revision > 0) {
                            Spacer(Modifier.height(7.dp))
                            OutlinedButton(
                                onClick = {
                                    vm.restoreProjectVisualStyle(
                                        projectId,
                                        projectStyleDraft.revision,
                                    )
                                },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Restaurar estilo Alexander")
                            }
                        }
                    }
                    }
                }
            }

            item {
                StudioPanel("APARIENCIA DEL PERSONAJE", JarvisCyan) {
                    Text(
                        "Dirección visual editable para " + selectedCharacterName +
                            ". El canon escrito conserva prioridad ante cualquier contradicción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB7C7DC),
                    )
                    if (!selectedCharacter?.appearance.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "CANON BASE",
                            style = HudTextStyle,
                            color = JarvisGreen,
                        )
                        Text(
                            selectedCharacter?.appearance.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Estado visual: " + characterDirectionLabel,
                        style = HudTextStyle,
                        color = if (needsOwnerVisualDesign) JarvisAmber else JarvisGreen,
                    )
                    if (needsOwnerVisualDesign) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "No existe un visual canon bloqueado suficiente para este personaje. Define y guarda su apariencia antes de generar un master.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisAmber,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showCharacterAppearanceEditor = !showCharacterAppearanceEditor
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (showCharacterAppearanceEditor) "Cerrar editor"
                            else "Editar apariencia",
                        )
                    }
                    if (showCharacterAppearanceEditor) {
                        Spacer(Modifier.height(8.dp))
                    DirectionTextField("Edad aparente", characterDirectionDraft.apparent_age) {
                        characterDirectionDraft = characterDirectionDraft.copy(apparent_age = it)
                    }
                    DirectionTextField("Complexión", characterDirectionDraft.build) {
                        characterDirectionDraft = characterDirectionDraft.copy(build = it)
                    }
                    DirectionTextField("Altura / proporciones", characterDirectionDraft.height) {
                        characterDirectionDraft = characterDirectionDraft.copy(height = it)
                    }
                    DirectionTextField("Piel", characterDirectionDraft.skin) {
                        characterDirectionDraft = characterDirectionDraft.copy(skin = it)
                    }
                    DirectionTextField("Rostro", characterDirectionDraft.face) {
                        characterDirectionDraft = characterDirectionDraft.copy(face = it)
                    }
                    DirectionTextField("Ojos", characterDirectionDraft.eyes) {
                        characterDirectionDraft = characterDirectionDraft.copy(eyes = it)
                    }
                    DirectionTextField("Cabello", characterDirectionDraft.hair) {
                        characterDirectionDraft = characterDirectionDraft.copy(hair = it)
                    }
                    DirectionTextField("Ropa base", characterDirectionDraft.base_outfit) {
                        characterDirectionDraft = characterDirectionDraft.copy(base_outfit = it)
                    }
                    DirectionTextField("Armadura", characterDirectionDraft.armor) {
                        characterDirectionDraft = characterDirectionDraft.copy(armor = it)
                    }
                    DirectionTextField("Accesorios", characterDirectionDraft.accessories) {
                        characterDirectionDraft = characterDirectionDraft.copy(accessories = it)
                    }
                    DirectionTextField("Armas", characterDirectionDraft.weapons) {
                        characterDirectionDraft = characterDirectionDraft.copy(weapons = it)
                    }
                    DirectionTextField("Colores dominantes", characterDirectionDraft.dominant_colors) {
                        characterDirectionDraft = characterDirectionDraft.copy(dominant_colors = it)
                    }
                    DirectionTextField("Aura / energía visual", characterDirectionDraft.aura) {
                        characterDirectionDraft = characterDirectionDraft.copy(aura = it)
                    }
                    DirectionTextField(
                        "Rasgos obligatorios (separados por coma)",
                        characterDirectionDraft.required_traits.joinToString(", "),
                    ) { value ->
                        characterDirectionDraft = characterDirectionDraft.copy(
                            required_traits = splitVisualRules(value),
                        )
                    }
                    DirectionTextField(
                        "Rasgos prohibidos (separados por coma)",
                        characterDirectionDraft.forbidden_traits.joinToString(", "),
                    ) { value ->
                        characterDirectionDraft = characterDirectionDraft.copy(
                            forbidden_traits = splitVisualRules(value),
                        )
                    }
                    DirectionTextField("Notas visuales", characterDirectionDraft.notes, minLines = 2) {
                        characterDirectionDraft = characterDirectionDraft.copy(notes = it)
                    }
                    Text(
                        "Estado: " + characterDirectionLabel + " · revisión " +
                            characterDirectionDraft.revision,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (needsOwnerVisualDesign) {
                            JarvisAmber
                        } else {
                            Color(0xFF94A3B8)
                        },
                    )
                    if (settings.isOwner) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                vm.saveCharacterVisualDirection(
                                    projectId,
                                    selectedCharacterId,
                                    characterDirectionDraft.copy(
                                        character_id = selectedCharacterId,
                                    ),
                                )
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Guardar apariencia")
                        }
                        if (
                            characterDirectionDraft.seed_available &&
                            characterDirectionDraft.revision > 0
                        ) {
                            Spacer(Modifier.height(7.dp))
                            OutlinedButton(
                                onClick = {
                                    vm.restoreCharacterVisualDirection(
                                        projectId,
                                        selectedCharacterId,
                                        characterDirectionDraft.revision,
                                    )
                                },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (characterDirectionDraft.external_seed_available) {
                                        "Restaurar apariencia de referencia"
                                    } else {
                                        "Restaurar apariencia desde canon"
                                    },
                                )
                            }
                        }
                    }
                    }
                }
            }

            item {
                StudioPanel("MASTER APROBADO", JarvisCyan) {
                    if (master == null) {
                        EmptyVisualState("Aún no existe un master aprobado.")
                    } else {
                        CharacterAssetCard(
                            asset = master,
                            projectId = projectId,
                            state = state,
                            vm = vm,
                            showApprove = false,
                            showRetry = false,
                            onEditInImageStudio = if (settings.isOwner) {
                                { attachmentId, asset ->
                                    onEditInImageStudio(attachmentId, selectedCharacterId, asset)
                                }
                            } else {
                                null
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "El master aprobado es inmutable. Regenerar o editar crea un candidato hijo; nunca reemplaza este asset.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                        )
                    }

                    if (settings.isOwner) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = masterPrompt,
                            onValueChange = { if (it.length <= 4000) masterPrompt = it },
                            label = {
                                Text(
                                    if (master == null) "Ajustes opcionales del primer master"
                                    else "Ajustes opcionales de la nueva versión",
                                )
                            },
                            placeholder = { Text("JARVIS usará la descripción canónica y el texto aprobado del personaje") },
                            minLines = 2,
                            maxLines = 5,
                            colors = jarvisTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    vm.generateCharacterVisual(
                                        projectId = projectId,
                                        characterId = selectedCharacterId,
                                        prompt = masterPrompt,
                                        kind = "PRIMARY_REFERENCE",
                                        perspective = "front",
                                        parentAsset = master,
                                    )
                                },
                                enabled = !state.busy && !needsOwnerVisualDesign,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (master == null) "Generar desde canon" else "Regenerar hijo")
                            }
                            OutlinedButton(
                                onClick = { uploadLauncher.launch("image/*") },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Subir")
                            }
                        }
                    }
                }
            }

            masterCandidate?.let { candidate ->
                item {
                    StudioPanel("CANDIDATO DE MASTER", JarvisAmber) {
                        CharacterAssetCard(
                            asset = candidate,
                            projectId = projectId,
                            state = state,
                            vm = vm,
                            showApprove = settings.isOwner,
                            showRetry = settings.isOwner,
                        )
                        Text(
                            "CANDIDATE no modifica el master actual hasta que apruebes explícitamente su hash exacto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                        )
                    }
                }
            }

            item {
                StudioPanel("TURNAROUND / IDENTITY PACK", JarvisGreen) {
                    Text(
                        "Cada vista usa las referencias aprobadas del personaje. Una vista generada sigue siendo CANDIDATE hasta aprobación humana.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        TURNAROUND_PERSPECTIVES.forEach { perspective ->
                            FilterChip(
                                selected = selectedPerspective == perspective,
                                onClick = { selectedPerspective = perspective },
                                label = { Text(prettyPerspective(perspective)) },
                            )
                        }
                    }
                    if (settings.isOwner) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                vm.completeCharacterViews(
                                    projectId = projectId,
                                    characterId = selectedCharacterId,
                                    perspectives = TURNAROUND_PERSPECTIVES,
                                    adjustment = turnaroundPrompt,
                                )
                            },
                            enabled = master != null &&
                                !state.busy &&
                                state.batch?.status != "RUNNING",
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.batch?.status == "RUNNING") {
                                    "JARVIS trabajando en segundo plano"
                                } else {
                                    "Completar vistas faltantes"
                                },
                            )
                        }
                        Text(
                            "JARVIS reutiliza vistas existentes, genera solo las faltantes, las evalúa contra canon + master y aplica como máximo dos correcciones automáticas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                        )

                        state.batch?.let { batch ->
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x99101B2E),
                                border = BorderStroke(1.dp, JarvisGreen.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        "LOTE · " + batch.status,
                                        style = HudTextStyle,
                                        color = JarvisGreen,
                                    )
                                    Text(
                                        batch.approved_count.toString() + " aprobadas · " +
                                            batch.candidate_count.toString() + " candidatas · " +
                                            batch.pending_count.toString() + " pendientes · " +
                                            batch.blocked_count.toString() + " bloqueadas",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFCBD5E1),
                                    )
                                    Text(
                                        "Generaciones: " + batch.generation_used.toString() +
                                            " / " + batch.generation_budget.toString() +
                                            " · máximo " + batch.max_corrections.toString() +
                                            " correcciones por vista",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8),
                                    )
                                    batch.items.forEach { item ->
                                        val evaluation = when {
                                            item.evaluation_status == "PASS" ->
                                                " · eval PASS " +
                                                    (item.evaluation_score?.toString() ?: "—")
                                            item.evaluation_status == "CORRECT" ->
                                                " · eval REVISAR " +
                                                    (item.evaluation_score?.toString() ?: "—")
                                            item.evaluation_status == "UNAVAILABLE" ->
                                                " · eval no disponible"
                                            else -> ""
                                        }
                                        val attempts = if (item.generation_attempts > 0) {
                                            " · " + item.generation_attempts.toString() + " intento(s)"
                                        } else {
                                            ""
                                        }
                                        Text(
                                            prettyPerspective(item.perspective) + " · " +
                                                item.status + attempts + evaluation +
                                                if (item.candidate_sha256.isBlank()) {
                                                    ""
                                                } else {
                                                    " · " + item.candidate_sha256.take(10) + "…"
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when {
                                                item.evaluation_status == "PASS" ||
                                                    item.status.startsWith("APPROVED") -> JarvisGreen
                                                item.evaluation_status == "CORRECT" ||
                                                    item.status.contains("CANDIDATE") -> JarvisAmber
                                                else -> Color(0xFF94A3B8)
                                            },
                                        )
                                        if (item.evaluation_status.isNotBlank()) {
                                            val evidence = item.evaluation
                                            if (
                                                evidence.identity_score != null ||
                                                evidence.canon_score != null ||
                                                evidence.perspective_score != null
                                            ) {
                                                Text(
                                                    "Identidad " +
                                                        (evidence.identity_score?.toString() ?: "—") +
                                                        " · canon " +
                                                        (evidence.canon_score?.toString() ?: "—") +
                                                        " · ángulo " +
                                                        (evidence.perspective_score?.toString() ?: "—"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF94A3B8),
                                                )
                                            }
                                            evidence.issues.take(3).forEach { issue ->
                                                Text(
                                                    "• " + issue,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = JarvisAmber,
                                                )
                                            }
                                        }
                                        if (
                                            item.evaluation_status == "CORRECT" &&
                                            item.correction_instruction.isNotBlank()
                                        ) {
                                            Text(
                                                "Última corrección: " +
                                                    item.correction_instruction,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF94A3B8),
                                            )
                                        }
                                    }
                                    val approvable = batch.items.count {
                                        it.status == "CANDIDATE" || it.status == "CANDIDATE_EXISTING"
                                    }
                                    if (approvable > 0) {
                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                vm.approveCharacterViewBatch(
                                                    projectId,
                                                    selectedCharacterId,
                                                )
                                            },
                                            enabled = !state.busy,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Aprobar " + approvable.toString() + " vistas del lote")
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = turnaroundPrompt,
                            onValueChange = { if (it.length <= 4000) turnaroundPrompt = it },
                            label = { Text("Ajustes opcionales (vista o lote)") },
                            placeholder = {
                                Text("JARVIS conservará el master aprobado; describe solo cambios opcionales")
                            },
                            minLines = 2,
                            maxLines = 5,
                            colors = jarvisTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        val parentView = latestAsset(
                            state.assets,
                            kind = "IDENTITY_PACK",
                            status = "APPROVED",
                            perspective = selectedPerspective,
                        )
                        Button(
                            onClick = {
                                vm.generateCharacterVisual(
                                    projectId = projectId,
                                    characterId = selectedCharacterId,
                                    prompt = turnaroundPrompt,
                                    kind = "IDENTITY_PACK",
                                    perspective = selectedPerspective,
                                    parentAsset = parentView,
                                )
                            },
                            enabled = master != null && !state.busy,
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (parentView == null) "Generar vista"
                                else "Regenerar vista como hija",
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { turnaroundUploadLauncher.launch("image/*") },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Subir vista manual")
                        }
                    }
                }
            }

            if (candidateViews.isNotEmpty()) {
                item {
                    StudioPanel("VISTAS CANDIDATAS", JarvisAmber) {
                        candidateViews.forEachIndexed { index, asset ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            CharacterAssetCard(
                                asset = asset,
                                projectId = projectId,
                                state = state,
                                vm = vm,
                                showApprove = settings.isOwner,
                                showRetry = settings.isOwner,
                            )
                        }
                    }
                }
            }

            if (approvedViews.isNotEmpty()) {
                item {
                    StudioPanel("VISTAS APROBADAS", JarvisGreen) {
                        approvedViews.forEachIndexed { index, asset ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            CharacterAssetCard(
                                asset = asset,
                                projectId = projectId,
                                state = state,
                                vm = vm,
                                showApprove = false,
                                showRetry = false,
                                onEditInImageStudio = if (settings.isOwner) {
                                { attachmentId, asset ->
                                    onEditInImageStudio(attachmentId, selectedCharacterId, asset)
                                }
                            } else {
                                null
                            },
                            )
                        }
                    }
                }
            }

            item {
                StudioPanel("REFERENCE PACK", accents.orbGlow) {
                    if (activePack == null) {
                        EmptyVisualState("No hay un Reference Pack activo.")
                    } else {
                        PackSummary(activePack, active = true)
                    }
                    if (pendingPack != null) {
                        Spacer(Modifier.height(10.dp))
                        PackSummary(pendingPack, active = false)
                    }

                    if (settings.isOwner) {
                        Spacer(Modifier.height(10.dp))
                        if (pendingPack == null) {
                            Button(
                                onClick = {
                                    vm.createCharacterReferencePack(
                                        projectId,
                                        selectedCharacterId,
                                    )
                                },
                                enabled = !state.busy,
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Crear nueva revisión del pack")
                            }
                        } else if (pendingPack.state == "DRAFT") {
                            Button(
                                onClick = {
                                    vm.prepareCharacterReferencePack(
                                        projectId,
                                        selectedCharacterId,
                                        pendingPack.pack_id,
                                    )
                                },
                                enabled = !state.busy,
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Validar para aprobación")
                            }
                        } else if (pendingPack.state == "READY_FOR_APPROVAL") {
                            Button(
                                onClick = {
                                    vm.approveCharacterReferencePack(
                                        projectId,
                                        selectedCharacterId,
                                        pendingPack.pack_id,
                                    )
                                },
                                enabled = !state.busy,
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Aprobar Reference Pack")
                            }
                        }
                    }

                    if (state.detail?.reference_packs.orEmpty().isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "HISTORIAL DE REVISIONES",
                            style = HudTextStyle,
                            color = Color(0xFF94A3B8),
                        )
                        state.detail?.reference_packs
                            .orEmpty()
                            .sortedByDescending { it.revision }
                            .forEach { pack ->
                                Text(
                                    "r${pack.revision} · ${pack.state} · ${pack.slots.size} vistas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1),
                                )
                            }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        vm.refreshCharacterStudio(projectId, selectedCharacterId)
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Actualizar Character Studio")
                }
            }
        }
    }
}

private fun splitVisualRules(value: String): List<String> =
    value.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(20)

@Composable
private fun DirectionTextField(
    label: String,
    value: String,
    minLines: Int = 1,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 1200) onValueChange(it) },
        label = { Text(label) },
        minLines = minLines,
        maxLines = if (minLines > 1) 5 else 3,
        colors = jarvisTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun CharacterAssetCard(
    asset: VisualStudioAsset,
    projectId: String,
    state: JarvisViewModel.CharacterStudioState,
    vm: JarvisViewModel,
    showApprove: Boolean,
    showRetry: Boolean,
    onEditInImageStudio: ((String, VisualStudioAsset) -> Unit)? = null,
) {
    val attachmentId = state.attachmentIds[asset.asset_id]
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xB30B1528),
        border = BorderStroke(1.dp, statusColor(asset.status).copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            AssetPreview(
                projectId = projectId,
                asset = asset,
                attachmentId = attachmentId,
                vm = vm,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(asset.status, statusColor(asset.status))
                Spacer(Modifier.width(7.dp))
                Text(
                    prettyPerspective(asset.perspective),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF1F5F9),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "r${asset.visual_revision}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "SHA · ${asset.sha256.take(12)}…",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7DD3FC),
            )
            Text(
                "Storage · ${asset.storage.state.ifBlank { "unknown" }}",
                style = MaterialTheme.typography.labelSmall,
                color = if (asset.storage.state == "stored") JarvisGreen else JarvisAmber,
            )
            if (asset.parent_asset_id.isNotBlank()) {
                Text(
                    "Hijo de ${asset.parent_asset_id.take(18)} · ${asset.derivation.ifBlank { "DERIVED" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisViolet,
                )
            }
            if (
                showApprove ||
                (showRetry && asset.storage.state != "stored") ||
                (onEditInImageStudio != null && asset.status == "APPROVED")
            ) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showApprove) {
                        Button(
                            onClick = { vm.approveCharacterVisual(asset) },
                            enabled = !state.busy && asset.storage.state == "stored",
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Aprobar hash")
                        }
                    }
                    if (showRetry && asset.storage.state != "stored") {
                        OutlinedButton(
                            onClick = { vm.retryCharacterVisualStorage(asset) },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Reintentar Drive")
                        }
                    }
                    if (onEditInImageStudio != null && asset.status == "APPROVED") {
                        OutlinedButton(
                            onClick = {
                                val id = attachmentId
                                if (!id.isNullOrBlank()) onEditInImageStudio(id, asset)
                            },
                            enabled = !state.busy && !attachmentId.isNullOrBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Editar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetPreview(
    projectId: String,
    asset: VisualStudioAsset,
    attachmentId: String?,
    vm: JarvisViewModel,
) {
    val state by vm.characterStudio.collectAsStateWithLifecycle()
    LaunchedEffect(projectId, state.characterId, asset.asset_id, asset.storage.state) {
        if (attachmentId.isNullOrBlank() && asset.storage.state == "stored") {
            vm.loadCharacterStudioAsset(projectId, asset.asset_id)
        }
    }
    VisualAssetPreview(
        attachmentId = attachmentId,
        store = vm.attachmentStore,
        stored = asset.storage.state == "stored",
        loading = asset.asset_id in state.loadingAssetIds,
        error = state.assetErrors[asset.asset_id],
        description = "Referencia visual del personaje",
        height = 320.dp,
        onRetry = { vm.loadCharacterStudioAsset(projectId, asset.asset_id, retry = true) },
    )
}

@Composable
private fun PackSummary(
    pack: VisualStudioReferencePack,
    active: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) JarvisGreen.copy(alpha = 0.08f) else JarvisAmber.copy(alpha = 0.08f),
        border = BorderStroke(
            1.dp,
            if (active) JarvisGreen.copy(alpha = 0.45f) else JarvisAmber.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(pack.state, if (active) JarvisGreen else JarvisAmber)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Revisión ${pack.revision}",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "Master · ${pack.master_asset_id}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1),
            )
            pack.slots.forEach { slot ->
                Text(
                    "${prettyPerspective(slot.perspective)} · ${slot.asset_id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                )
            }
        }
    }
}

@Composable
private fun StudioPanel(
    title: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xEE0E182A),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = HudTextStyle,
                color = accent,
            )
            Spacer(Modifier.height(9.dp))
            content()
        }
    }
}

@Composable
private fun StudioMessageCard(
    text: String,
    color: Color,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f)),
        modifier = Modifier.fillMaxWidth(),
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
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    }
}

@Composable
private fun EmptyVisualState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Color(0xFF07101E), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f)),
    ) {
        Text(
            text.ifBlank { "UNKNOWN" },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun latestAsset(
    assets: List<VisualStudioAsset>,
    kind: String,
    status: String,
    perspective: String? = null,
): VisualStudioAsset? =
    assets
        .asSequence()
        .filter { it.kind == kind && it.status == status }
        .filter { perspective == null || it.perspective == perspective }
        .maxWithOrNull(
            compareBy<VisualStudioAsset>({ it.visual_revision }, { it.approved_utc }, { it.created_utc }),
        )

private fun statusColor(status: String): Color = when (status) {
    "APPROVED", "CANON_LOCKED" -> JarvisGreen
    "CANDIDATE", "READY_FOR_APPROVAL" -> JarvisAmber
    "DEPRECATED" -> Color(0xFF64748B)
    "DRAFT" -> JarvisViolet
    else -> JarvisCyan
}

private fun prettyPerspective(value: String): String =
    value
        .ifBlank { "custom" }
        .replace('_', ' ')
        .split(' ')
        .joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

private val TURNAROUND_PERSPECTIVES = listOf(
    "left_three_quarter",
    "left_profile",
    "right_three_quarter",
    "right_profile",
    "back",
    "full_body",
)
