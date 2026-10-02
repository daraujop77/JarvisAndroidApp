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
import com.jarvis.android.transport.live.VisualStudioAsset
import com.jarvis.android.transport.live.VisualStudioLocationReferencePack
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
fun LocationStudioScreen(
    vm: JarvisViewModel,
    projectId: String,
    locations: List<WritingWikiEntity>,
    modifier: Modifier = Modifier,
) {
    val state by vm.locationStudio.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val accents = LocalJarvisAccents.current

    val locationOptions = remember(locations) {
        locations
            .filter { it.id.isNotBlank() && it.type.equals("location", ignoreCase = true) }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }
    var selectedLocationId by rememberSaveable(projectId) {
        mutableStateOf(locationOptions.firstOrNull()?.id.orEmpty())
    }
    var masterPrompt by rememberSaveable(projectId) { mutableStateOf("") }
    var variantPrompt by rememberSaveable(projectId) { mutableStateOf("") }
    var editInstruction by rememberSaveable(projectId) { mutableStateOf("") }
    var selectedPerspective by rememberSaveable(projectId) {
        mutableStateOf("exterior")
    }

    LaunchedEffect(locationOptions) {
        if (
            selectedLocationId.isBlank() ||
            locationOptions.none { it.id == selectedLocationId }
        ) {
            selectedLocationId = locationOptions.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(projectId, selectedLocationId) {
        if (selectedLocationId.isNotBlank()) {
            vm.openLocationStudio(projectId, selectedLocationId)
        }
    }

    val selectedLocation = locationOptions.firstOrNull {
        it.id == selectedLocationId
    }
    val master = latestLocationAsset(
        state.assets,
        kind = "LOCATION_REFERENCE",
        status = "APPROVED",
        perspective = "establishing",
    )
    val masterCandidate = latestLocationAsset(
        state.assets,
        kind = "LOCATION_REFERENCE",
        status = "CANDIDATE",
        perspective = "establishing",
    )
    val approvedVariants = state.assets
        .filter { it.kind == "LOCATION_VARIANT" && it.status == "APPROVED" }
        .sortedWith(
            compareBy<VisualStudioAsset> { it.perspective }
                .thenByDescending { it.visual_revision },
        )
    val candidateVariants = state.assets
        .filter { it.kind == "LOCATION_VARIANT" && it.status == "CANDIDATE" }
        .sortedByDescending { it.created_utc }
    val activePack = state.detail?.active_reference_pack
    val pendingPack = state.detail?.reference_packs
        .orEmpty()
        .filter { it.state == "DRAFT" || it.state == "READY_FOR_APPROVAL" }
        .maxByOrNull { it.revision }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null && selectedLocationId.isNotBlank()) {
            vm.uploadLocationMaster(projectId, selectedLocationId, uri)
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
            LocationStudioPanel(
                title = "LOCATION STUDIO",
                accent = accents.orbGlow,
            ) {
                Text(
                    "Referencia visual de lugares del proyecto. Las imágenes aprobadas no cambian el canon textual; sirven como referencia visual para variantes y escenas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7C7DC),
                )
                Spacer(Modifier.height(10.dp))
                if (locationOptions.isEmpty()) {
                    Text(
                        "No hay locaciones disponibles en la Wiki de este proyecto.",
                        color = JarvisAmber,
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        locationOptions.forEach { location ->
                            FilterChip(
                                selected = location.id == selectedLocationId,
                                onClick = { selectedLocationId = location.id },
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
                }
                if (selectedLocation != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        selectedLocation.canonical_name.ifBlank {
                            selectedLocation.name.ifBlank { selectedLocation.id }
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color(0xFFF8FAFC),
                    )
                    Text(
                        selectedLocation.summary.take(220),
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
                    border = BorderStroke(
                        1.dp,
                        accents.orbGlow.copy(alpha = 0.35f),
                    ),
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
                LocationStudioMessage(
                    text = notice,
                    color = JarvisGreen,
                    onDismiss = vm::clearLocationStudioMessage,
                )
            }
        }
        state.error?.let { error ->
            item {
                LocationStudioMessage(
                    text = error,
                    color = JarvisRed,
                    onDismiss = vm::clearLocationStudioMessage,
                )
            }
        }

        if (selectedLocationId.isNotBlank()) {
            item {
                LocationStudioPanel("MASTER APROBADO", JarvisCyan) {
                    if (master == null) {
                        EmptyLocationVisualState(
                            "Aún no existe un master aprobado para esta locación.",
                        )
                    } else {
                        LocationAssetCard(
                            asset = master,
                            projectId = projectId,
                            state = state,
                            vm = vm,
                            showApprove = false,
                            showRetry = false,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "El master aprobado queda inmutable. Editar o regenerar crea un candidato hijo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                        )
                    }

                    if (settings.isOwner) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = masterPrompt,
                            onValueChange = {
                                if (it.length <= 4000) masterPrompt = it
                            },
                            label = {
                                Text(
                                    if (master == null) {
                                        "Prompt del primer master"
                                    } else {
                                        "Prompt para regenerar como hijo"
                                    },
                                )
                            },
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
                                    vm.generateLocationVisual(
                                        projectId = projectId,
                                        locationId = selectedLocationId,
                                        prompt = masterPrompt,
                                        kind = "LOCATION_REFERENCE",
                                        perspective = "establishing",
                                        parentAsset = master,
                                    )
                                },
                                enabled = masterPrompt.isNotBlank() && !state.busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (master == null) "Generar"
                                    else "Regenerar hijo",
                                )
                            }
                            OutlinedButton(
                                onClick = { uploadLauncher.launch("image/*") },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Subir")
                            }
                        }
                    }
                }
            }

            masterCandidate?.let { candidate ->
                item {
                    LocationStudioPanel("CANDIDATO DE MASTER", JarvisAmber) {
                        LocationAssetCard(
                            asset = candidate,
                            projectId = projectId,
                            state = state,
                            vm = vm,
                            showApprove = settings.isOwner,
                            showRetry = settings.isOwner,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "CANDIDATE no sustituye el master aprobado hasta aprobar explícitamente su hash.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                        )
                    }
                }
            }

            if (settings.isOwner && master != null) {
                item {
                    LocationStudioPanel("EDICIÓN NO DESTRUCTIVA", JarvisViolet) {
                        OutlinedTextField(
                            value = editInstruction,
                            onValueChange = {
                                if (it.length <= 4000) editInstruction = it
                            },
                            label = { Text("Qué debe cambiar") },
                            minLines = 2,
                            maxLines = 5,
                            colors = jarvisTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                vm.editLocationVisual(
                                    projectId = projectId,
                                    locationId = selectedLocationId,
                                    parentAsset = master,
                                    instruction = editInstruction,
                                )
                            },
                            enabled = editInstruction.isNotBlank() && !state.busy,
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Crear edición hija")
                        }
                    }
                }
            }

            item {
                LocationStudioPanel("VARIANTES APROBADAS POR REFERENCIA", JarvisGreen) {
                    Text(
                        "Las variantes se generan usando referencias aprobadas de esta misma locación.",
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
                        LOCATION_VARIANT_PERSPECTIVES.forEach { perspective ->
                            FilterChip(
                                selected = selectedPerspective == perspective,
                                onClick = {
                                    selectedPerspective = perspective
                                },
                                label = {
                                    Text(prettyLocationPerspective(perspective))
                                },
                            )
                        }
                    }
                    if (settings.isOwner) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = variantPrompt,
                            onValueChange = {
                                if (it.length <= 4000) variantPrompt = it
                            },
                            label = { Text("Instrucción de la variante") },
                            placeholder = {
                                Text(
                                    "Mantén arquitectura, materiales, escala y rasgos reconocibles del master…",
                                )
                            },
                            minLines = 2,
                            maxLines = 5,
                            colors = jarvisTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        val parentVariant = latestLocationAsset(
                            state.assets,
                            kind = "LOCATION_VARIANT",
                            status = "APPROVED",
                            perspective = selectedPerspective,
                        )
                        Button(
                            onClick = {
                                vm.generateLocationVisual(
                                    projectId = projectId,
                                    locationId = selectedLocationId,
                                    prompt = variantPrompt,
                                    kind = "LOCATION_VARIANT",
                                    perspective = selectedPerspective,
                                    parentAsset = parentVariant,
                                )
                            },
                            enabled = (
                                master != null &&
                                    variantPrompt.isNotBlank() &&
                                    !state.busy
                                ),
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (parentVariant == null) "Generar variante"
                                else "Regenerar variante como hija",
                            )
                        }
                    }
                }
            }

            if (candidateVariants.isNotEmpty()) {
                item {
                    LocationStudioPanel("VARIANTES CANDIDATAS", JarvisAmber) {
                        candidateVariants.forEachIndexed { index, asset ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            LocationAssetCard(
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

            if (approvedVariants.isNotEmpty()) {
                item {
                    LocationStudioPanel("VARIANTES APROBADAS", JarvisGreen) {
                        approvedVariants.forEachIndexed { index, asset ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            LocationAssetCard(
                                asset = asset,
                                projectId = projectId,
                                state = state,
                                vm = vm,
                                showApprove = false,
                                showRetry = false,
                            )
                        }
                    }
                }
            }

            item {
                LocationStudioPanel("LOCATION REFERENCE PACK", accents.orbGlow) {
                    if (activePack == null) {
                        EmptyLocationVisualState(
                            "No hay un Location Reference Pack activo.",
                        )
                    } else {
                        LocationPackSummary(activePack, active = true)
                    }
                    if (pendingPack != null) {
                        Spacer(Modifier.height(10.dp))
                        LocationPackSummary(pendingPack, active = false)
                    }

                    if (settings.isOwner) {
                        Spacer(Modifier.height(10.dp))
                        if (pendingPack == null) {
                            Button(
                                onClick = {
                                    vm.createLocationReferencePack(
                                        projectId,
                                        selectedLocationId,
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
                                    vm.prepareLocationReferencePack(
                                        projectId,
                                        selectedLocationId,
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
                                    vm.approveLocationReferencePack(
                                        projectId,
                                        selectedLocationId,
                                        pendingPack.pack_id,
                                    )
                                },
                                enabled = !state.busy,
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Aprobar Location Pack")
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
                                    "r${pack.revision} · ${pack.state} · ${pack.slots.size} variantes",
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
                        vm.refreshLocationStudio(
                            projectId,
                            selectedLocationId,
                        )
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Actualizar Location Studio")
                }
            }
        }
    }
}

@Composable
private fun LocationAssetCard(
    asset: VisualStudioAsset,
    projectId: String,
    state: JarvisViewModel.LocationStudioState,
    vm: JarvisViewModel,
    showApprove: Boolean,
    showRetry: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xB30B1528),
        border = BorderStroke(
            1.dp,
            locationStatusColor(asset.status).copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            LocationAssetPreview(
                projectId = projectId,
                asset = asset,
                attachmentId = state.attachmentIds[asset.asset_id],
                vm = vm,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LocationStatusPill(
                    asset.status,
                    locationStatusColor(asset.status),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    prettyLocationPerspective(asset.perspective),
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
                color = if (asset.storage.state == "stored") {
                    JarvisGreen
                } else {
                    JarvisAmber
                },
            )
            if (asset.parent_asset_id.isNotBlank()) {
                Text(
                    "Hijo de ${asset.parent_asset_id.take(18)} · ${asset.derivation.ifBlank { "DERIVED" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisViolet,
                )
            }
            if (showApprove || (showRetry && asset.storage.state != "stored")) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showApprove) {
                        Button(
                            onClick = { vm.approveLocationVisual(asset) },
                            enabled = (
                                !state.busy &&
                                    asset.storage.state == "stored"
                                ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Aprobar hash")
                        }
                    }
                    if (showRetry && asset.storage.state != "stored") {
                        OutlinedButton(
                            onClick = {
                                vm.retryLocationVisualStorage(asset)
                            },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Reintentar Drive")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationAssetPreview(
    projectId: String,
    asset: VisualStudioAsset,
    attachmentId: String?,
    vm: JarvisViewModel,
) {
    LaunchedEffect(projectId, asset.asset_id, asset.storage.state) {
        if (
            attachmentId.isNullOrBlank() &&
            asset.storage.state == "stored"
        ) {
            vm.loadLocationStudioAsset(projectId, asset.asset_id)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(
                Color(0xFF050B14),
                RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (attachmentId.isNullOrBlank()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (asset.storage.state == "stored") {
                        "Cargando imagen…"
                    } else {
                        "Pendiente de Drive"
                    },
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            val bitmap by rememberAttachmentThumb(
                attachmentId,
                vm.attachmentStore,
                maxSize = 2048,
            )
            if (bitmap == null) {
                CircularProgressIndicator(
                    color = LocalJarvisAccents.current.orbGlow,
                )
            } else {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Referencia visual de locación",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun LocationPackSummary(
    pack: VisualStudioLocationReferencePack,
    active: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) {
            JarvisGreen.copy(alpha = 0.08f)
        } else {
            JarvisAmber.copy(alpha = 0.08f)
        },
        border = BorderStroke(
            1.dp,
            if (active) {
                JarvisGreen.copy(alpha = 0.45f)
            } else {
                JarvisAmber.copy(alpha = 0.45f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LocationStatusPill(
                    pack.state,
                    if (active) JarvisGreen else JarvisAmber,
                )
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
                    "${prettyLocationPerspective(slot.perspective)} · ${slot.asset_id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                )
            }
        }
    }
}

@Composable
private fun LocationStudioPanel(
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
private fun LocationStudioMessage(
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
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp,
            ),
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
private fun EmptyLocationVisualState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(
                Color(0xFF07101E),
                RoundedCornerShape(14.dp),
            ),
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
private fun LocationStatusPill(
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
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 3.dp,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun latestLocationAsset(
    assets: List<VisualStudioAsset>,
    kind: String,
    status: String,
    perspective: String? = null,
): VisualStudioAsset? =
    assets
        .asSequence()
        .filter { it.kind == kind && it.status == status }
        .filter {
            perspective == null || it.perspective == perspective
        }
        .maxWithOrNull(
            compareBy<VisualStudioAsset>(
                { it.visual_revision },
                { it.approved_utc },
                { it.created_utc },
            ),
        )

private fun locationStatusColor(status: String): Color = when (status) {
    "APPROVED", "CANON_LOCKED" -> JarvisGreen
    "CANDIDATE", "READY_FOR_APPROVAL" -> JarvisAmber
    "DEPRECATED" -> Color(0xFF64748B)
    "DRAFT" -> JarvisViolet
    else -> JarvisCyan
}

private fun prettyLocationPerspective(value: String): String =
    value
        .ifBlank { "custom" }
        .replace('_', ' ')
        .split(' ')
        .joinToString(" ") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase()
                else it.toString()
            }
        }

private val LOCATION_VARIANT_PERSPECTIVES = listOf(
    "exterior",
    "interior",
    "aerial",
    "detail",
)
