package com.jarvis.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.shared.rememberAttachmentThumb
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors

/**
 * Dedicated image-editing workspace.
 *
 * V1 exposes only controls that the authenticated backend can honor across the
 * current Grok/GPT edit routes. Seed/CFG/steps remain out until the server
 * advertises provider-specific support.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImageStudioScreen(
    vm: JarvisViewModel,
    initialAttachmentId: String,
    onBack: () -> Unit,
) {
    val accents = LocalJarvisAccents.current
    val access by vm.chatAccess.collectAsStateWithLifecycle()
    val editState by vm.imageEditState.collectAsStateWithLifecycle()
    val editDetails by vm.lastImageEditDetails.collectAsStateWithLifecycle()

    var currentAttachmentId by rememberSaveable(initialAttachmentId) {
        mutableStateOf(initialAttachmentId)
    }
    var versions by rememberSaveable(initialAttachmentId) {
        mutableStateOf(listOf(initialAttachmentId))
    }
    var instruction by rememberSaveable(initialAttachmentId) { mutableStateOf("") }
    var preserveIdentity by rememberSaveable(initialAttachmentId) { mutableStateOf("high") }
    var selectedMode by rememberSaveable(initialAttachmentId) { mutableStateOf("quality") }
    var selectedModel by rememberSaveable(initialAttachmentId) { mutableStateOf("auto") }

    val bitmap by rememberAttachmentThumb(
        currentAttachmentId,
        vm.attachmentStore,
        maxSize = 2048,
    )
    val aspectRatio = remember(bitmap) {
        val image = bitmap
        if (image == null || image.width <= 0 || image.height <= 0) {
            "square"
        } else {
            val ratio = image.width.toFloat() / image.height.toFloat()
            when {
                ratio > 1.12f -> "landscape"
                ratio < 0.89f -> "portrait"
                else -> "square"
            }
        }
    }

    val editAccess = access?.imageEdit
    val editReady = access?.capabilityStates?.get("image_edit") == "ready"

    LaunchedEffect(Unit) {
        vm.resetImageEditState()
        vm.refreshChatAccess()
    }

    LaunchedEffect(editAccess) {
        val catalog = editAccess ?: return@LaunchedEffect
        if (catalog.modes.none { it.id == selectedMode }) {
            selectedMode = catalog.defaultMode.takeIf { mode ->
                catalog.modes.any { it.id == mode }
            } ?: catalog.modes.firstOrNull()?.id ?: "quality"
        }
        if (
            selectedMode == "model_select" &&
            catalog.models.none { it.id == selectedModel && it.state == "ready" }
        ) {
            selectedModel = catalog.models.firstOrNull { it.id == "auto" }?.id
                ?: catalog.models.firstOrNull { it.state == "ready" }?.id
                ?: "auto"
        }
    }

    LaunchedEffect(editState) {
        val success = editState as? JarvisViewModel.ImageEditState.Success
            ?: return@LaunchedEffect
        if (success.attachmentId !in versions) {
            versions = versions + success.attachmentId
        }
        currentAttachmentId = success.attachmentId
        instruction = ""
        vm.resetImageEditState()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xF509111F),
                    titleContentColor = Color(0xFFF8FAFC),
                    navigationIconContentColor = accents.orbGlow,
                ),
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = {
                    Column {
                        Text(
                            "IMAGE STUDIO",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            "EDICIÓN CON REFERENCIA · JARVIS",
                            style = HudTextStyle.copy(fontSize = 10.sp),
                            color = accents.orbGlow,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalJarvisAccents.current.backdrop)
                .padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xEE0B1528),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                accents.orbGlow.copy(alpha = 0.52f),
                                Color(0x338B5CF6),
                                accents.orbGlow.copy(alpha = 0.25f),
                            ),
                        ),
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = null,
                                tint = accents.orbGlow,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "REFERENCIA ACTIVA",
                                style = HudTextStyle,
                                color = accents.orbGlow,
                            )
                            Spacer(Modifier.weight(1f))
                            StudioPill("AUTO-REFERENCE", JarvisGreen)
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(390.dp)
                                .background(Color(0xFF050B14), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap!!.asImageBitmap(),
                                    contentDescription = "Imagen de referencia actual",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                CircularProgressIndicator(color = accents.orbGlow)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Cada resultado nuevo se convierte automáticamente en la referencia de la siguiente edición. Puedes volver a cualquier versión de abajo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB7C7DC),
                        )
                    }
                }
            }

            if (versions.size > 1) {
                item {
                    StudioCard("HISTORIAL DE VERSIONES", accents.orbGlow) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            versions.forEachIndexed { index, id ->
                                val active = id == currentAttachmentId
                                Surface(
                                    onClick = { currentAttachmentId = id },
                                    shape = RoundedCornerShape(50),
                                    color = if (active) accents.orbGlow.copy(alpha = 0.20f)
                                    else Color(0x66101B2E),
                                    border = BorderStroke(
                                        1.dp,
                                        if (active) accents.orbGlow else Color(0xFF30435F),
                                    ),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (active) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = accents.orbGlow,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            if (index == 0) "BASE" else "V" + (index + 1),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                StudioCard("MODIFICACIÓN", JarvisCyan) {
                    Text(
                        "Describe exactamente qué debe cambiar. JARVIS enviará esta imagen como referencia real al modelo de edición, no sólo como texto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = instruction,
                        onValueChange = { if (it.length <= 4000) instruction = it },
                        placeholder = {
                            Text("Ej. Conserva el personaje y cambia únicamente la armadura a negro mate…")
                        },
                        minLines = 3,
                        maxLines = 6,
                        colors = jarvisTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("ATAJOS", style = HudTextStyle.copy(fontSize = 10.sp), color = Color(0xFF94A3B8))
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        StudioPromptChip("Ropa") {
                            instruction = "Conserva el mismo personaje y modifica únicamente la ropa: "
                        }
                        StudioPromptChip("Fondo") {
                            instruction = "Conserva el mismo personaje y modifica únicamente el fondo: "
                        }
                        StudioPromptChip("Expresión") {
                            instruction = "Conserva el mismo personaje y modifica únicamente la expresión facial: "
                        }
                        StudioPromptChip("Variación") {
                            instruction = "Crea una variación de esta imagen manteniendo al mismo personaje reconocible y la intención visual principal."
                            preserveIdentity = "medium"
                        }
                    }
                }
            }

            item {
                StudioCard("CONSISTENCIA DEL PERSONAJE", JarvisGreen) {
                    Text(
                        "Este control ajusta las instrucciones de JARVIS. No simula un parámetro nativo que el proveedor no exponga.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "high" to "Alta",
                            "medium" to "Media",
                            "low" to "Libre",
                        ).forEach { pair ->
                            FilterChip(
                                selected = preserveIdentity == pair.first,
                                onClick = { preserveIdentity = pair.first },
                                label = { Text(pair.second) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JarvisGreen.copy(alpha = 0.18f),
                                    selectedLabelColor = Color(0xFFE2FBEA),
                                ),
                            )
                        }
                    }
                }
            }

            item {
                StudioCard("RUTA DE EDICIÓN", JarvisAmber) {
                    if (!editReady) {
                        Text(
                            "La edición por referencia todavía no está disponible en el servidor conectado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            editAccess?.modes.orEmpty().forEach { mode ->
                                FilterChip(
                                    selected = selectedMode == mode.id,
                                    onClick = { selectedMode = mode.id },
                                    label = { Text(mode.label) },
                                )
                            }
                        }

                        if (selectedMode == "model_select") {
                            Spacer(Modifier.height(8.dp))
                            Text("MODELO · OWNER", style = HudTextStyle, color = JarvisAmber)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                editAccess?.models.orEmpty().forEach { model ->
                                    val ready = model.state == "ready"
                                    FilterChip(
                                        selected = selectedModel == model.id,
                                        onClick = { if (ready) selectedModel = model.id },
                                        enabled = ready,
                                        label = { Text(model.label) },
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = null,
                                tint = JarvisAmber,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Formato detectado: " + aspectRatio.uppercase(),
                                style = HudTextStyle.copy(fontSize = 10.sp),
                                color = Color(0xFFB7C7DC),
                            )
                        }
                    }
                }
            }

            when (val state = editState) {
                is JarvisViewModel.ImageEditState.Error -> item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f),
                    ) {
                        Text(
                            state.message,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                else -> Unit
            }

            editDetails?.let { details ->
                item {
                    val fallback = if (details.fallbackUsed) {
                        " · fallback " + details.attemptCount
                    } else ""
                    Text(
                        "ÚLTIMA EDICIÓN · " +
                            details.model.ifBlank { details.provider } +
                            " · " + details.durationMs + " ms" + fallback,
                        style = HudTextStyle.copy(fontSize = 10.sp),
                        color = accents.orbGlow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                val busy = editState is JarvisViewModel.ImageEditState.Busy
                val exactReady = selectedMode != "model_select" ||
                    editAccess?.models?.any { it.id == selectedModel && it.state == "ready" } == true
                Button(
                    onClick = {
                        vm.editImage(
                            referenceAttachmentId = currentAttachmentId,
                            instruction = instruction,
                            mode = selectedMode,
                            model = selectedModel.takeIf { selectedMode == "model_select" },
                            preserveIdentity = preserveIdentity,
                            aspectRatio = aspectRatio,
                        )
                    },
                    enabled = editReady && instruction.isNotBlank() && !busy && exactReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accents.orbGlow,
                        contentColor = Color(0xFF02101F),
                    ),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF02101F),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Editando…")
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("GENERAR EDICIÓN", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StudioCard(
    title: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xCC0E182A),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = HudTextStyle, color = accent)
            Spacer(Modifier.height(9.dp))
            content()
        }
    }
}

@Composable
private fun StudioPromptChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0x6610233D),
        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.32f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFE2E8F0),
        )
    }
}

@Composable
private fun StudioPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = HudTextStyle.copy(fontSize = 9.sp),
            color = color,
        )
    }
}
