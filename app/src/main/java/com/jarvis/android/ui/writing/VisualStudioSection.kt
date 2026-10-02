package com.jarvis.android.ui.writing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.jarvis.android.transport.live.WritingVisualReferencePack
import com.jarvis.android.transport.live.WritingWikiEntity
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.shared.rememberAttachmentThumb
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.JarvisViolet
import com.jarvis.android.ui.theme.LocalJarvisAccents

private enum class VisualStudioPane(val label: String) {
    CHARACTERS("Personajes"),
    LOCATIONS("Locaciones"),
    SCENES("Escenas"),
    CANON("Canon Gallery"),
}

@Composable
fun VisualStudioSection(
    vm: JarvisViewModel,
    projectId: String,
    characters: List<WritingWikiEntity>,
) {
    val state by vm.visualCharacterStudio.collectAsStateWithLifecycle()
    val visualAttachments by vm.wikiVisualAttachments.collectAsStateWithLifecycle()
    var pane by rememberSaveable(projectId) { mutableStateOf(VisualStudioPane.CHARACTERS) }
    var selectedCharacterId by rememberSaveable(projectId) { mutableStateOf("") }

    LaunchedEffect(projectId, characters) {
        if (
            selectedCharacterId.isBlank() ||
            characters.none { canonicalCharacterId(it) == selectedCharacterId }
        ) {
            selectedCharacterId = characters.firstOrNull()?.let(::canonicalCharacterId).orEmpty()
        }
    }

    val selected = remember(selectedCharacterId, characters) {
        characters.firstOrNull { canonicalCharacterId(it) == selectedCharacterId }
    }

    LaunchedEffect(projectId, selectedCharacterId) {
        if (selectedCharacterId.isNotBlank()) {
            vm.loadVisualCharacter(projectId, selectedCharacterId)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalJarvisAccents.current.backdrop),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VisualStudioPane.entries.forEach { item ->
                FilterChip(
                    selected = pane == item,
                    onClick = { pane = item },
                    label = { Text(item.label) },
                )
            }
        }

        when (pane) {
            VisualStudioPane.CHARACTERS -> CharacterStudioPane(
                vm = vm,
                projectId = projectId,
                characters = characters,
                selected = selected,
                selectedCharacterId = selectedCharacterId,
                onSelectCharacter = { selectedCharacterId = canonicalCharacterId(it) },
                state = state,
                visualAttachments = visualAttachments,
            )
            VisualStudioPane.LOCATIONS -> PendingVisualDomain(
                "LOCATION STUDIO",
                "Master de locación, establishing views, variantes y World Map llegarán en el bloque V2.",
            )
            VisualStudioPane.SCENES -> PendingVisualDomain(
                "SCENE BUILDER",
                "Escenas usarán referencias CANON_LOCKED/APPROVED y contexto del capítulo/evento en el bloque V3.",
            )
            VisualStudioPane.CANON -> PendingVisualDomain(
                "CANON GALLERY",
                "La galería canónica se construirá sobre los mismos asset IDs y hashes aprobados; no habrá una segunda base de imágenes.",
            )
        }
    }
}

@Composable
private fun CharacterStudioPane(
    vm: JarvisViewModel,
    projectId: String,
    characters: List<WritingWikiEntity>,
    selected: WritingWikiEntity?,
    selectedCharacterId: String,
    onSelectCharacter: (WritingWikiEntity) -> Unit,
    state: JarvisViewModel.VisualCharacterStudioState,
    visualAttachments: Map<String, String>,
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val character = selected ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            vm.stageVisualMasterFromUri(
                uri = uri,
                projectId = projectId,
                characterId = selectedCharacterId,
                characterName = character.name.ifBlank { character.canonical_name },
            )
        }
    }

    val detail = state.detail.takeIf {
        it?.character_id == selectedCharacterId && state.projectId == projectId
    }
    val candidate = state.candidate.takeIf {
        it?.character_ids?.any { id -> id.equals(selectedCharacterId, ignoreCase = true) } == true
    }
    val masterAssetId = candidate?.asset_id
        ?: detail?.gallery?.primary?.asset_id
        ?: detail?.active_reference_pack?.master_asset_id
        ?: ""
    val masterAttachmentId = visualAttachments[masterAssetId]
    val masterBitmap by rememberAttachmentThumb(
        masterAttachmentId.orEmpty(),
        vm.attachmentStore,
        maxSize = 1536,
    )
    val newestPack = detail?.reference_packs
        ?.maxByOrNull { it.revision }
        ?: detail?.active_reference_pack
    var masterPrompt by rememberSaveable(projectId, selectedCharacterId) {
        mutableStateOf("")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            VisualCard("CHARACTER STUDIO", JarvisCyan) {
                Text(
                    "Elige un personaje canónico. El master, sus vistas y el pack se guardan por project_id; aprobar una imagen no aprueba automáticamente el pack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7C7DC),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    characters.forEach { character ->
                        val id = canonicalCharacterId(character)
                        FilterChip(
                            selected = id == selectedCharacterId,
                            onClick = { onSelectCharacter(character) },
                            label = { Text(character.name.ifBlank { character.canonical_name.ifBlank { id } }) },
                        )
                    }
                }
            }
        }

        if (selected == null) {
            item {
                VisualCard("SIN PERSONAJES", JarvisAmber) {
                    Text(
                        "Este proyecto aún no expone personajes en la Wiki estructurada.",
                        color = Color(0xFFCBD5E1),
                    )
                }
            }
            return@LazyColumn
        }

        item {
            VisualCard("MASTER · " + selected.name.uppercase(), JarvisGreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(390.dp)
                        .background(Color(0xFF050B14), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        masterBitmap != null -> Image(
                            bitmap = masterBitmap!!.asImageBitmap(),
                            contentDescription = "Master visual de " + selected.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        state.busy -> CircularProgressIndicator(color = JarvisCyan)
                        else -> Text(
                            if (masterAssetId.isBlank()) "SIN MASTER APROBADO" else "CARGANDO MASTER",
                            style = HudTextStyle,
                            color = Color(0xFF94A3B8),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (candidate != null) {
                    val stored = candidate.storage.state == "stored"
                    Text(
                        "CANDIDATO · " + candidate.sha256.take(12) + "… · " +
                            if (stored) "STORED" else "STORAGE PENDIENTE",
                        style = HudTextStyle,
                        color = if (stored) JarvisAmber else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (stored) {
                        Button(
                            onClick = {
                                vm.approveVisualMasterCandidate(projectId, selectedCharacterId)
                            },
                            enabled = !state.busy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisGreen,
                                contentColor = Color(0xFF02101F),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("APROBAR ESTE HASH COMO MASTER", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                vm.retryVisualMasterUpload(
                                    selected.name.ifBlank { selected.canonical_name },
                                )
                            },
                            enabled = !state.busy && !state.pendingAssetId.isNullOrBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("REINTENTAR STORAGE · NO REGENERAR")
                        }
                    }
                } else {
                    val primary = detail?.gallery?.primary
                    Text(
                        if (primary != null) {
                            "MASTER APROBADO · REV " + primary.visual_revision + " · " + primary.sha256.take(12) + "…"
                        } else {
                            "Primero sube o genera un candidato; no se promueve nada automáticamente."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (primary != null) JarvisGreen else Color(0xFF94A3B8),
                    )
                }
                Spacer(Modifier.height(9.dp))
                OutlinedButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (masterAssetId.isBlank()) "SUBIR MASTER" else "PROPONER NUEVO MASTER")
                }

                if (!state.pendingAssetId.isNullOrBlank() && candidate == null && state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            vm.retryVisualMasterUpload(
                                selected.name.ifBlank { selected.canonical_name },
                            )
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("REINTENTAR STORAGE · MISMO ASSET ID")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("GENERAR MASTER CLOUD", style = HudTextStyle, color = JarvisViolet)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = masterPrompt,
                    onValueChange = { masterPrompt = it.take(4000) },
                    label = { Text("Describe la apariencia canónica") },
                    minLines = 3,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        vm.generateVisualMasterCandidate(
                            projectId = projectId,
                            characterId = selectedCharacterId,
                            characterName = selected.name.ifBlank { selected.canonical_name },
                            prompt = masterPrompt,
                        )
                    },
                    enabled = masterPrompt.isNotBlank() && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("GENERAR CANDIDATO CLOUD")
                }
            }
        }

        state.message?.let { message ->
            item {
                VisualNotice(message, JarvisGreen) { vm.clearVisualCharacterStudioMessage() }
            }
        }
        state.error?.let { error ->
            item {
                VisualNotice(error, MaterialTheme.colorScheme.error) {
                    vm.clearVisualCharacterStudioMessage()
                }
            }
        }

        item {
            ReferencePackCard(
                pack = newestPack,
                activePackId = detail?.active_reference_pack?.pack_id,
                visualAttachments = visualAttachments,
                vm = vm,
                onRefresh = { vm.loadVisualCharacter(projectId, selectedCharacterId) },
            )
        }

        if (newestPack != null && newestPack.state != "APPROVED") {
            item {
                TurnaroundBuilderCard(
                    vm = vm,
                    pack = newestPack,
                    state = state,
                    characterName = selected.name.ifBlank { selected.canonical_name },
                    visualAttachments = visualAttachments,
                )
            }
        }

    }
}

private data class TurnaroundSpec(
    val slotKey: String,
    val label: String,
    val perspective: String,
)

private val TURNAROUND_SPECS = listOf(
    TurnaroundSpec("left_profile", "Perfil izquierdo", "left_profile"),
    TurnaroundSpec("right_profile", "Perfil derecho", "right_profile"),
    TurnaroundSpec("back", "Espalda", "back"),
)

@Composable
private fun TurnaroundBuilderCard(
    vm: JarvisViewModel,
    pack: WritingVisualReferencePack,
    state: JarvisViewModel.VisualCharacterStudioState,
    characterName: String,
    visualAttachments: Map<String, String>,
) {
    VisualCard("TURNAROUND · REV " + pack.revision, JarvisCyan) {
        Text(
            "Cada vista entra como CANDIDATE, se aprueba por su hash exacto y sólo entonces se enlaza al pack. El pack se aprueba aparte.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB7C7DC),
        )
        Spacer(Modifier.height(9.dp))

        TURNAROUND_SPECS.forEach { spec ->
            TurnaroundSlotRow(
                vm = vm,
                spec = spec,
                pack = pack,
                state = state,
                characterName = characterName,
                visualAttachments = visualAttachments,
            )
            Spacer(Modifier.height(8.dp))
        }

        val requiredBound = TURNAROUND_SPECS.all { spec ->
            pack.slots.any { it.required && it.slot_key == spec.slotKey }
        }
        when (pack.state) {
            "DRAFT" -> Button(
                onClick = { vm.prepareVisualReferencePack(pack.pack_id) },
                enabled = requiredBound && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(
                    if (requiredBound) "PREPARAR PACK PARA APROBACIÓN"
                    else "COMPLETA Y APRUEBA LAS 3 VISTAS",
                )
            }
            "READY_FOR_APPROVAL" -> Button(
                onClick = { vm.approveVisualReferencePack(pack.pack_id) },
                enabled = !state.busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisGreen,
                    contentColor = Color(0xFF02101F),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("APROBAR Y ACTIVAR PACK", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TurnaroundSlotRow(
    vm: JarvisViewModel,
    spec: TurnaroundSpec,
    pack: WritingVisualReferencePack,
    state: JarvisViewModel.VisualCharacterStudioState,
    characterName: String,
    visualAttachments: Map<String, String>,
) {
    val bound = pack.slots.firstOrNull { it.slot_key == spec.slotKey }
    val candidate = state.viewCandidates[spec.slotKey]
    val pending = state.pendingViewUploads[spec.slotKey]
    val assetId = candidate?.asset_id ?: bound?.asset_id.orEmpty()
    val bitmap by rememberAttachmentThumb(
        visualAttachments[assetId].orEmpty(),
        vm.attachmentStore,
        maxSize = 512,
    )
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            vm.stageVisualPackSlotFromUri(
                uri = uri,
                slotKey = spec.slotKey,
                perspective = spec.perspective,
                required = true,
                characterName = characterName,
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0x66101B2E),
        border = BorderStroke(
            1.dp,
            if (bound != null) JarvisGreen.copy(alpha = 0.45f) else JarvisCyan.copy(alpha = 0.24f),
        ),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF050B14),
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = spec.label,
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("VISTA", style = HudTextStyle, color = Color(0xFF64748B))
                        }
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(spec.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            bound != null -> "APROBADA · " + bound.asset_sha256.take(12) + "…"
                            candidate != null -> "CANDIDATA · " + candidate.sha256.take(12) + "…"
                            pending != null -> "STORAGE PENDIENTE · mismo asset_id"
                            else -> "SIN VISTA"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            bound != null -> JarvisGreen
                            candidate != null -> JarvisAmber
                            else -> Color(0xFF94A3B8)
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                bound != null -> OutlinedButton(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = pack.state == "DRAFT" && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("PROPONER REEMPLAZO")
                }
                candidate != null && candidate.storage.state != "stored" -> OutlinedButton(
                    onClick = {
                        vm.retryVisualPackSlotUpload(spec.slotKey, characterName)
                    },
                    enabled = pending != null && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.size(5.dp))
                    Text("REINTENTAR STORAGE · NO REGENERAR")
                }
                candidate != null -> Button(
                    onClick = { vm.approveVisualPackSlot(spec.slotKey) },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisGreen,
                        contentColor = Color(0xFF02101F),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("APROBAR VISTA Y ENLAZAR")
                }
                pending != null && state.error != null -> OutlinedButton(
                    onClick = {
                        vm.retryVisualPackSlotUpload(spec.slotKey, characterName)
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.size(5.dp))
                    Text("REINTENTAR STORAGE")
                }
                else -> {
                    Button(
                        onClick = {
                            vm.generateVisualPackSlotCandidate(
                                slotKey = spec.slotKey,
                                perspective = spec.perspective,
                                characterName = characterName,
                            )
                        },
                        enabled = pack.state == "DRAFT" && !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.size(5.dp))
                        Text("GENERAR CON MASTER")
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = pack.state == "DRAFT" && !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.size(5.dp))
                        Text("SUBIR VISTA")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferencePackCard(
    pack: WritingVisualReferencePack?,
    activePackId: String?,
    visualAttachments: Map<String, String>,
    vm: JarvisViewModel,
    onRefresh: () -> Unit,
) {
    VisualCard("VISUAL REFERENCE PACK", JarvisViolet) {
        if (pack == null) {
            Text(
                "Sin pack todavía. Al aprobar un master se crea una revisión DRAFT antes de generar sus vistas.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
            )
            return@VisualCard
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "REV " + pack.revision + " · " + pack.state,
                style = HudTextStyle,
                color = if (pack.state == "APPROVED") JarvisGreen else JarvisAmber,
            )
            Spacer(Modifier.weight(1f))
            if (pack.pack_id == activePackId) {
                Text("ACTIVO", style = HudTextStyle, color = JarvisGreen)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (pack.slots.isEmpty()) {
            Text(
                "Turnaround pendiente. El pack DRAFT no puede convertirse en referencia activa sin vistas requeridas.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB7C7DC),
            )
        } else {
            pack.slots.forEach { slot ->
                val attachmentId = visualAttachments[slot.asset_id].orEmpty()
                val bitmap by rememberAttachmentThumb(
                    attachmentId,
                    vm.attachmentStore,
                    maxSize = 512,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(58.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF07111F),
                        border = BorderStroke(1.dp, JarvisViolet.copy(alpha = 0.35f)),
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = slot.perspective,
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text("IMG", style = HudTextStyle, color = Color(0xFF64748B))
                            }
                        }
                    }
                    Spacer(Modifier.size(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(slot.slot_key.replace("_", " ").uppercase(), fontWeight = FontWeight.SemiBold)
                        Text(
                            slot.asset_sha256.take(12) + "… · " + if (slot.required) "REQUERIDA" else "OPCIONAL",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.size(5.dp))
            Text("Actualizar")
        }
    }
}

@Composable
private fun VisualCard(
    title: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xCC0E182A),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = HudTextStyle, color = accent)
            Spacer(Modifier.height(9.dp))
            content()
        }
    }
}

@Composable
private fun VisualNotice(
    text: String,
    accent: Color,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    }
}

@Composable
private fun PendingVisualDomain(
    title: String,
    body: String,
) {
    Box(
        Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        VisualCard(title, JarvisViolet) {
            Text(body, color = Color(0xFFB7C7DC), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun canonicalCharacterId(character: WritingWikiEntity): String {
    val raw = character.id.ifBlank { character.canonical_name.ifBlank { character.name } }.trim()
    if (raw.startsWith("character:", ignoreCase = true)) return raw.lowercase()
    return "character:" + raw.lowercase().replace(" ", "-")
}
