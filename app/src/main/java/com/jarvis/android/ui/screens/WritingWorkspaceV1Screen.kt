package com.jarvis.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import com.jarvis.android.data.story.CANON_FACTIONS
import com.jarvis.android.data.story.CANON_MILESTONES
import com.jarvis.android.data.story.CharacterChapterActivity
import com.jarvis.android.data.story.CharacterJarvisAnalysis
import com.jarvis.android.data.story.CharacterLifeStatus
import com.jarvis.android.data.story.StoryCharacter
import com.jarvis.android.data.story.StoryFaction
import com.jarvis.android.data.story.StoryMilestone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.transport.live.WritingChapter
import com.jarvis.android.transport.live.WritingEngineReviewEnvelope
import com.jarvis.android.transport.live.WritingPlanItem
import com.jarvis.android.transport.live.WritingPlanningCouncilMessage
import com.jarvis.android.transport.live.WritingRoomAutoChat
import com.jarvis.android.transport.live.WritingWikiCategory
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.i18n.AppStrings
import com.jarvis.android.ui.i18n.LocalAppStrings
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.JarvisRed
import com.jarvis.android.ui.theme.JarvisViolet
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors
import com.jarvis.android.ui.writing.findStructuredCharacterByReference
import com.jarvis.android.ui.writing.mergeStructuredCharacters
import com.jarvis.android.ui.writing.searchStructuredCharacters

/**
 * Product Writing Room v1 surface.
 *
 * The six sections mirror the server-owned workspace contract:
 * Overview / Write / Chat / Plan / Wiki / Library.
 * SQLite stores workflow state only. Story canon remains human-authoritative.
 */
@Composable
fun WritingWorkspaceV1Screen(
    vm: JarvisViewModel,
    projectId: String,
    title: String,
    onBack: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val state by vm.writingWorkspace.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(WorkspaceTab.OVERVIEW) }

    LaunchedEffect(projectId) {
        vm.refreshWritingWorkspace(projectId)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(LocalJarvisAccents.current.backdrop),
    ) {
        val accents = LocalJarvisAccents.current
        val overview = state.overview

        // Barra de navegación y acciones superiores
        if (onBack != null || onEdit != null || onDelete != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            tint = LocalJarvisAccents.current.orbGlow,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(strings.back, color = Color(0xFFF1F5F9), style = HudTextStyle)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Renombrar",
                            tint = JarvisCyan,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Eliminar",
                            tint = Color(0xFFEF4444).copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Hero Card de Jarvis Core con el Orbe animado y estado del proyecto
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xEE0E182A),
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        accents.orbGlow.copy(alpha = 0.38f),
                        Color(0x228B5CF6),
                        accents.orbGlow.copy(alpha = 0.20f),
                    )
                ),
            ),
            shadowElevation = 4.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JarvisOrb(
                    size = 62.dp,
                    activity = if (state.busy) OrbActivity.THINKING else OrbActivity.IDLE,
                    intensity = if (state.streamingText.isNotBlank()) 0.9f else 0.2f,
                    contentDescription = "Writing Room status",
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF8FAFC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "WRITING ROOM · STORY-001",
                        style = HudTextStyle,
                        color = accents.orbGlow,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val chapter = overview?.latest_official_chapter?.chapter_number ?: 37
                        MiniPill("CANON · CAP $chapter", JarvisGreen)
                        MiniPill(
                            if (state.busy) writingRoomActivityLabel(state, strings) else strings.statusOnline,
                            if (state.busy) JarvisAmber else accents.online,
                        )
                    }
                }
            }
        }

        if (state.error != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.error.orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = vm::clearWritingWorkspaceError) { Text(strings.dismiss) }
                }
            }
        }

        // Horizontal tab bar with icons for high touch affordance
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkspaceTab.entries.forEach { item ->
                FilterChip(
                    selected = tab == item,
                    onClick = { tab = item },
                    leadingIcon = {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (tab == item) LocalJarvisAccents.current.orbGlow else Color(0xFF94A3B8),
                        )
                    },
                    label = {
                        Text(
                            item.label(strings),
                            fontWeight = if (tab == item) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0x33101B2E),
                        labelColor = Color(0xFF94A3B8),
                        selectedContainerColor = LocalJarvisAccents.current.orbGlow.copy(alpha = 0.22f),
                        selectedLabelColor = Color(0xFF67E8F9),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = tab == item,
                        borderColor = Color(0x332A3B57),
                        selectedBorderColor = LocalJarvisAccents.current.orbGlow.copy(alpha = 0.65f),
                    ),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        when (tab) {
            WorkspaceTab.OVERVIEW -> OverviewSection(state, projectId, vm)
            WorkspaceTab.WRITE -> WriteSection(state, projectId, vm)
            WorkspaceTab.CHAT -> ChatSection(state, projectId, title, vm)
            WorkspaceTab.PLAN -> PlanSection(state, projectId, vm)
            WorkspaceTab.WIKI -> WikiSection(state, projectId, vm)
            WorkspaceTab.LIBRARY -> LibrarySection(state, projectId, vm)
        }
    }
}

@Composable
private fun OverviewSection(
    state: JarvisViewModel.WritingWorkspaceState,
    projectId: String,
    vm: JarvisViewModel,
) {
    val strings = LocalAppStrings.current
    val overview = state.overview
    val counts = overview?.sources?.by_status.orEmpty()
    val accents = LocalJarvisAccents.current
    val latest = overview?.latest_official_chapter

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // TARJETA PRINCIPAL: NÚCLEO NARRATIVO JARVIS (JARVIS CORE)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xEE0B1528),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            accents.orbGlow.copy(alpha = 0.50f),
                            Color(0x338B5CF6),
                            accents.orbGlow.copy(alpha = 0.25f),
                        )
                    )
                ),
                shadowElevation = 6.dp,
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JarvisOrb(
                            size = 54.dp,
                            activity = if (state.busy) OrbActivity.THINKING else OrbActivity.IDLE,
                            intensity = if (state.streamingText.isNotBlank()) 0.9f else 0.2f,
                            contentDescription = "Núcleo JARVIS",
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Memory, contentDescription = null, tint = accents.orbGlow, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "JARVIS CORE · NÚCLEO NARRATIVO",
                                    style = HudTextStyle.copy(fontSize = 11.sp),
                                    color = accents.orbGlow,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                overview?.project?.title ?: "Alexander History",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF8FAFC),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Línea temporal oficial consolidada · Canon humano exclusivo",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Fila de chips de estado del núcleo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MiniPill("CANON SINCRONIZADO", JarvisGreen)
                        MiniPill("${overview?.sources?.total ?: 23} FUENTES ACTIVAS", JarvisCyan)
                        MiniPill("RAG CONECTADO", JarvisCyan)
                        overview?.project?.snapshot_date?.let {
                            MiniPill("SNAPSHOT · $it", JarvisViolet)
                        }
                    }
                }
            }
        }

        // HITO CLAVE: ÚLTIMO CAPÍTULO OFICIAL
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xCC0E231C),
                border = BorderStroke(1.dp, JarvisGreen.copy(alpha = 0.45f)),
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoStories, contentDescription = null, tint = JarvisGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "ÚLTIMO CAPÍTULO OFICIAL ESTABLECIDO",
                                style = HudTextStyle.copy(fontSize = 10.sp),
                                color = JarvisGreen,
                            )
                        }
                        MiniPill(
                            if (latest?.chapter_number != null) "CAPÍTULO ${latest.chapter_number}" else "VIGENTE",
                            JarvisGreen,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        latest?.title ?: "Capítulo 37: La contingencia",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF8FAFC),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Los hechos de este capítulo son verdad canónica inmutable. Todo el razonamiento de JARVIS protege la continuidad a partir de este punto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                    )
                }
            }
        }

        // MAPA DE AUTORIDAD NARRATIVA
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "DISTRIBUCIÓN DE AUTORIDAD DEL CONOCIMIENTO",
                    style = HudTextStyle,
                    color = Color(0xFF94A3B8),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthorityStat(
                    title = strings.officialCanon,
                    count = counts["OFFICIAL_CANON"] ?: 8,
                    subtitle = strings.officialCanonSub,
                    color = JarvisGreen,
                    modifier = Modifier.weight(1f),
                )
                AuthorityStat(
                    title = strings.reference,
                    count = counts["REFERENCE"] ?: 7,
                    subtitle = strings.referenceSub,
                    color = JarvisCyan,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthorityStat(
                    title = strings.approvedPlans,
                    count = counts["APPROVED_PLAN"] ?: 6,
                    subtitle = strings.approvedPlansSub,
                    color = JarvisAmber,
                    modifier = Modifier.weight(1f),
                )
                AuthorityStat(
                    title = strings.proposals,
                    count = counts["PROPOSED"] ?: 2,
                    subtitle = strings.proposalsSub,
                    color = JarvisViolet,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // CAPAS DEL MOTOR DE ESCRITURA JARVIS (WR-1 a WR-5)
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xCC0E182A),
                border = BorderStroke(1.dp, Color(0x332A3B57)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "ARQUITECTURA DE VALIDACIÓN CANÓNICA (WR-1–5)",
                        style = HudTextStyle,
                        color = JarvisCyan,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "JARVIS aísla los planes futuros de los hechos históricos y audita contradicciones antes de solicitar la aprobación del autor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MiniPill("WR-1 BRIEF CONGELADO", JarvisCyan)
                        MiniPill("WR-2 BORRADOR CONTEXTUAL", JarvisGreen)
                        MiniPill("WR-3 CONTINUIDAD TEMPORAL", JarvisAmber)
                        MiniPill("WR-4 LÍMITES DE PODER", JarvisViolet)
                        MiniPill("WR-5 AUDITORÍA CANON", JarvisGreen)
                    }
                }
            }
        }

        // BOTÓN DE SINCRONIZACIÓN
        item {
            Button(
                onClick = { vm.refreshWritingWorkspace(projectId) },
                enabled = !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10233D), contentColor = JarvisCyan),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sincronizar Núcleo de Historia", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Copiloto Narrativo como un chat regular.
 *
 * Muestra el flujo de conversación (usuario vs JARVIS) y una barra inferior fija
 * de mensajería con sugerencias rápidas sobre el canon de Alexander History.
 */
@Composable
private fun ChatSection(
    state: JarvisViewModel.WritingWorkspaceState,
    projectId: String,
    title: String,
    vm: JarvisViewModel,
) {
    val strings = LocalAppStrings.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var prompt by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val completedTurns = state.chatHistory.count { it.response != null }

    val sendPrompt: () -> Unit = {
        val clean = prompt.trim()
        if (clean.isNotEmpty() && !state.busy) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            vm.runWritingRoomAutoChat(projectId, title, clean)
            prompt = ""
        }
    }

    LaunchedEffect(state.chatHistory.size, completedTurns, state.streamingText.isNotBlank()) {
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Historial completo del copiloto. Cada turno conserva pregunta y respuesta.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.chatHistory.isEmpty() && state.streamingText.isBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xEE0E1B2E),
                        border = BorderStroke(1.dp, LocalJarvisAccents.current.orbGlow.copy(alpha = 0.35f)),
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                JarvisOrb(
                                    size = 36.dp,
                                    activity = OrbActivity.IDLE,
                                    contentDescription = "Copiloto JARVIS",
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Copiloto Narrativo JARVIS",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFF8FAFC),
                                    )
                                    Text(
                                        "Conectado al Canon de Alexander History",
                                        style = HudTextStyle.copy(fontSize = 11.sp),
                                        color = LocalJarvisAccents.current.orbGlow,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Pregúntame sobre hechos ocurridos, motivaciones de personajes, dilemas estratégicos o propón giros para los próximos capítulos. Todas las respuestas se fundamentan en el canon oficial.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCBD5E1),
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "CONSULTAS RÁPIDAS",
                                style = HudTextStyle.copy(fontSize = 10.sp),
                                color = Color(0xFF94A3B8),
                            )
                            Spacer(Modifier.height(6.dp))
                            val suggestions = listOf(
                                "¿Cuál es el estado de Alexander tras el Cap. 37?",
                                "¿Qué facciones y generales tienen tensiones activas?",
                                "Sugiere un punto de partida para el siguiente capítulo",
                            )
                            suggestions.forEach { suggestion ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x3310233D),
                                    border = BorderStroke(1.dp, Color(0x332A4B7C)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { prompt = suggestion },
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = JarvisCyan,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            suggestion,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFE2E8F0),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.chatHistory.forEach { turn ->
                item {
                    CopilotUserMessage(turn.prompt)
                }
                turn.response?.let { response ->
                    item {
                        CopilotAssistantMessage(response)
                    }
                }
            }

            if (state.busy || state.streamingText.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                            color = Color(0xEE0E182A),
                            border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.45f)),
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth(0.98f),
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = JarvisAmber,
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        writingRoomActivityLabel(state, strings),
                                        style = HudTextStyle.copy(fontSize = 10.sp),
                                        color = JarvisAmber,
                                    )
                                }
                                if (state.streamingText.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    RichModelText(state.streamingText)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Composer fijo sobre el teclado. La acción IME Send envía con Enter.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFA0E182A),
            border = BorderStroke(1.dp, LocalJarvisAccents.current.orbGlow.copy(alpha = 0.40f)),
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { if (it.length <= 6000) prompt = it },
                    placeholder = {
                        Text(
                            "Escribe tu consulta a JARVIS...",
                            color = Color(0xFF64748B),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendPrompt() }),
                    colors = jarvisTextFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = sendPrompt,
                    enabled = prompt.isNotBlank() && !state.busy,
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (prompt.isNotBlank() && !state.busy) JarvisCyan else Color(0x3310233D),
                            CircleShape,
                        ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar mensaje",
                        tint = if (prompt.isNotBlank() && !state.busy) Color(0xFF02101F) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CopilotUserMessage(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            color = Color(0xFF1E3A5F),
            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(0.90f),
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text(
                    "TÚ",
                    style = HudTextStyle.copy(fontSize = 10.sp),
                    color = JarvisCyan,
                )
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun CopilotAssistantMessage(chat: WritingRoomAutoChat) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember(chat.turn.response.text) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = Color(0xEE0E182A),
            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.35f)),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(0.98f),
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        JarvisOrb(
                            size = 20.dp,
                            activity = OrbActivity.IDLE,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            chat.turn.participant.label.ifBlank { "JARVIS" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFF1F5F9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(chat.turn.response.text))
                            copied = true
                        },
                    ) {
                        Icon(
                            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (copied) JarvisGreen else JarvisCyan,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (copied) "Copiado" else "Copiar",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (copied) JarvisGreen else JarvisCyan,
                        )
                    }
                }

                val model = chat.turn.routing.model.substringAfterLast('/')
                if (model.isNotBlank()) {
                    Text(
                        model,
                        style = HudTextStyle.copy(fontSize = 9.sp),
                        color = JarvisCyan,
                    )
                }
                Spacer(Modifier.height(5.dp))
                RichModelText(chat.turn.response.text)

                val grouped = chat.turn.canon.sources.groupBy { it.canon_status.ifBlank { "REFERENCE" } }
                if (grouped.values.any { it.isNotEmpty() }) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "FUENTES CANÓNICAS CONSULTADAS",
                        style = HudTextStyle.copy(fontSize = 9.sp),
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("OFFICIAL_CANON", "REFERENCE", "APPROVED_PLAN", "PROPOSED").forEach { authority ->
                            grouped[authority].orEmpty().forEach { source ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = authorityColor(authority).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, authorityColor(authority).copy(alpha = 0.35f)),
                                ) {
                                    Text(
                                        source.title,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = Color(0xFFE2E8F0),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Estudio de Escritura Simplificado.
 *
 * En lugar de 8 campos rígidos, ofrece un flujo simple con un brief corto
 * de lo que se desea en el capítulo, y un editor directo con métricas cuando hay borrador activo.
 */
@Composable
private fun WriteSection(
    state: JarvisViewModel.WritingWorkspaceState,
    projectId: String,
    vm: JarvisViewModel,
) {
    var selectedChapterTitle by rememberSaveable { mutableStateOf("") }
    var chapterBrief by rememberSaveable { mutableStateOf("") }
    var extraDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var characters by rememberSaveable { mutableStateOf("") }
    var tone by rememberSaveable { mutableStateOf("") }
    var mustAvoid by rememberSaveable { mutableStateOf("") }

    var draft by rememberSaveable { mutableStateOf("") }
    val active = state.activeChapter

    LaunchedEffect(active?.chapter_id, active?.draft_text, active?.title) {
        if (active != null) {
            draft = active.draft_text
            selectedChapterTitle = if (active.title == "Nuevo capítulo") "" else active.title
        }
    }

    val wordCount = remember(draft) {
        if (draft.isBlank()) 0 else draft.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }
    val charCount = draft.length
    val readingTimeMin = remember(wordCount) { maxOf(1, wordCount / 200) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // El Showrunner analiza primero el brief. El título y la dirección siguen siendo propuesta
        // hasta que el usuario los aprueba explícitamente.
        if (active != null && active.title == "Nuevo capítulo") {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xEE0E182A),
                    border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.45f)),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            JarvisOrb(
                                size = 28.dp,
                                activity = if (state.busy) OrbActivity.THINKING else OrbActivity.IDLE,
                                contentDescription = "Showrunner",
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "SHOWRUNNER · DIRECCIÓN DEL CAPÍTULO",
                                    style = HudTextStyle.copy(fontSize = 10.sp),
                                    color = JarvisAmber,
                                )
                                Text(
                                    if (active.showrunner_brief.isBlank()) "Analizando tu brief" else "Propuesta lista para tu decisión",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF8FAFC),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tu brief",
                            style = HudTextStyle.copy(fontSize = 9.sp),
                            color = Color(0xFF94A3B8),
                        )
                        Text(
                            active.objective,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                        )

                        if (active.showrunner_brief.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            RichModelText(active.showrunner_brief)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "TÍTULO ELEGIDO",
                                style = HudTextStyle,
                                color = JarvisCyan,
                            )
                            Spacer(Modifier.height(4.dp))
                            SimpleField(
                                value = selectedChapterTitle,
                                onValueChange = { selectedChapterTitle = it },
                                label = "Escribe o usa uno de los títulos sugeridos por el Showrunner",
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    vm.approveWritingChapterPlan(
                                        projectId = projectId,
                                        chapterId = active.chapter_id,
                                        title = selectedChapterTitle,
                                    )
                                },
                                enabled = selectedChapterTitle.isNotBlank() && !state.busy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JarvisGreen,
                                    contentColor = Color(0xFF02101F),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(7.dp))
                                Text("Aprobar Dirección y Escribir Capítulo", fontWeight = FontWeight.Bold)
                            }
                        } else if (!state.busy) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { vm.runWritingChapterStep(projectId, active.chapter_id, "showrunner") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reintentar análisis con Showrunner")
                            }
                        }
                    }
                }
            }
        }

        // Redacción de capítulo activo sólo después de seleccionar el título/dirección.
        if (active != null && active.title != "Nuevo capítulo") {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xEE0E182A),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.40f)),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    active.title.ifBlank { "Capítulo en redacción" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF8FAFC),
                                )
                                Spacer(Modifier.height(3.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "$wordCount palabras",
                                        style = HudTextStyle.copy(fontSize = 11.sp),
                                        color = JarvisGreen,
                                    )
                                    Text("•", color = Color(0xFF64748B), fontSize = 11.sp)
                                    Text(
                                        "$charCount caracteres",
                                        style = HudTextStyle.copy(fontSize = 11.sp),
                                        color = Color(0xFF94A3B8),
                                    )
                                    Text("•", color = Color(0xFF64748B), fontSize = 11.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Timer,
                                            contentDescription = null,
                                            tint = JarvisAmber,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            "~$readingTimeMin min",
                                            style = HudTextStyle.copy(fontSize = 11.sp),
                                            color = JarvisAmber,
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { vm.saveWritingChapterDraft(projectId, active.chapter_id, draft) },
                                enabled = !state.busy,
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color(0xFF02101F)),
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Guardar", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Botones de acción del flujo narrativo de IA
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            OutlinedButton(
                                onClick = { vm.runWritingChapterStep(projectId, active.chapter_id, "showrunner") },
                                enabled = !state.busy,
                            ) { Text("Ver Brief") }
                            OutlinedButton(
                                onClick = { vm.runWritingChapterStep(projectId, active.chapter_id, "write") },
                                enabled = !state.busy,
                            ) { Text("Borrador IA") }
                            OutlinedButton(
                                onClick = { vm.runWritingChapterStep(projectId, active.chapter_id, "review") },
                                enabled = !state.busy,
                            ) { Text("Auditar Continuidad") }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Borrador del manuscrito") },
                    placeholder = { Text("Escribe o perfecciona el texto de la escena aquí...") },
                    minLines = 14,
                    colors = jarvisTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.engineReview?.let { review ->
                item { WritingEngineReviewCard(review) }
            }

            item { ActiveChapterCard(active) }
        }

        // Modo de creación: sólo el brief es obligatorio. El Showrunner propone título y dirección.
        if (active == null || active.title != "Nuevo capítulo") {
            item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xEE0E182A),
                border = BorderStroke(1.dp, Color(0x332A3B57)),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EditNote, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                if (active == null) "Nuevo Capítulo" else "Planear Otro Capítulo",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF1F5F9),
                            )
                            Text(
                                "Cuéntale al Showrunner qué quieres que ocurra. Él propondrá títulos, tono y dirección.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text("BRIEF DE LO QUE QUIERES EN EL CAPÍTULO", style = HudTextStyle, color = JarvisCyan)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = chapterBrief,
                        onValueChange = { chapterBrief = it },
                        label = { Text("Brief o resumen de la escena (Requerido)") },
                        placeholder = { Text("Ej: El protagonista reúne a su equipo tras la batalla para decidir el siguiente movimiento y resolver una tensión interna...") },
                        minLines = 4,
                        colors = jarvisTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(10.dp))

                    // Acordeón opcional colapsable para afinar detalles
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { extraDetailsExpanded = !extraDetailsExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Opciones avanzadas opcionales (personajes, tono)",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalJarvisAccents.current.orbGlow,
                        )
                        Icon(
                            if (extraDetailsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = LocalJarvisAccents.current.orbGlow,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    if (extraDetailsExpanded) {
                        Spacer(Modifier.height(8.dp))
                        SimpleField(characters, { characters = it }, "Personajes presentes (separados por coma)")
                        Spacer(Modifier.height(6.dp))
                        SimpleField(tone, { tone = it }, "Tono o atmósfera (ej: tenso, solemne, heroico)")
                        Spacer(Modifier.height(6.dp))
                        SimpleField(mustAvoid, { mustAvoid = it }, "Elementos o decisiones a evitar", 2)
                    }

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = {
                            vm.startWritingChapter(
                                projectId = projectId,
                                title = "",
                                objective = chapterBrief,
                                storyPoint = "",
                                characters = characters.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                mustHave = "",
                                mustAvoid = mustAvoid,
                                tone = tone,
                                desiredEnd = "",
                            )
                            selectedChapterTitle = ""
                        },
                        enabled = chapterBrief.isNotBlank() && !state.busy,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisGreen, contentColor = Color(0xFF02101F)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Analizar Brief con Showrunner", fontWeight = FontWeight.Bold)
                    }
                }
            }
            }
        }

        // Historial de sesiones de capítulos anteriores
        if (state.chapters.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                    Text(
                        "Sesiones de Capítulos",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF1F5F9),
                    )
                }
            }
            items(state.chapters) { chapter ->
                WorkspaceCard("${chapter.title} · ${chapter.status}") {
                    Text(chapter.objective, color = Color(0xFFE2E8F0))
                    if (chapter.story_point.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(chapter.story_point, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(
                            onClick = { vm.runWritingChapterStep(projectId, chapter.chapter_id, "showrunner") },
                            enabled = !state.busy,
                        ) { Text("Brief") }
                        TextButton(
                            onClick = { vm.runWritingChapterStep(projectId, chapter.chapter_id, "write") },
                            enabled = !state.busy && chapter.title != "Nuevo capítulo",
                        ) { Text("Escribir") }
                        TextButton(
                            onClick = { vm.runWritingChapterStep(projectId, chapter.chapter_id, "review") },
                            enabled = !state.busy,
                        ) { Text("Revisar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveChapterCard(chapter: WritingChapter) {
    WorkspaceCard("${chapter.title} · ${chapter.status}") {
        if (chapter.showrunner_brief.isNotBlank()) {
            Text("Brief del showrunner", style = MaterialTheme.typography.titleSmall)
            RichModelText(chapter.showrunner_brief)
            Spacer(Modifier.height(8.dp))
        }
        if (chapter.reviewer_text.isNotBlank()) {
            Text("Notas del revisor", style = MaterialTheme.typography.titleSmall)
            RichModelText(chapter.reviewer_text)
            Spacer(Modifier.height(8.dp))
        }
        if (chapter.canon_review_text.isNotBlank()) {
            Text("Auditoría de canon", style = MaterialTheme.typography.titleSmall)
            RichModelText(chapter.canon_review_text)
        }
        if (chapter.context_pack_id.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Contexto congelado: ${chapter.context_pack_id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WritingEngineReviewCard(review: WritingEngineReviewEnvelope) {
    val result = review.result
    val blocking = result.severity_counts["blocking"] ?: 0
    val warnings = result.severity_counts["warning"] ?: 0
    val info = result.severity_counts["info"] ?: 0
    val statusColor = when {
        result.status == "reviewed" && blocking == 0 -> JarvisGreen
        result.status == "incomplete" -> JarvisViolet
        blocking > 0 -> MaterialTheme.colorScheme.error
        warnings > 0 -> JarvisAmber
        else -> JarvisCyan
    }

    WorkspaceCard("Motor de Escritura · Revisión Estructurada", statusColor) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniPill(result.status.ifBlank { "DESCONOCIDO" }.uppercase(), statusColor)
            MiniPill("$blocking BLOQUEANTE", if (blocking > 0) MaterialTheme.colorScheme.error else JarvisGreen)
            MiniPill("$warnings AVISO", if (warnings > 0) JarvisAmber else JarvisGreen)
            MiniPill("$info INFO", JarvisCyan)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Pack de contexto: ${review.context_pack_id.ifBlank { result.context_pack_id }}",
            style = HudTextStyle,
            color = JarvisCyan,
        )
        Text(
            "Revisión de solo lectura · sin reescritura automática · sin alteración de canon · decisión del autor requerida",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val checks = result.check_order.ifEmpty { result.check_status.keys.toList() }
        if (checks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Comprobaciones", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                checks.forEach { check ->
                    val status = result.check_status[check] ?: "unknown"
                    val color = when (status) {
                        "ok" -> JarvisGreen
                        "missing_required", "error" -> MaterialTheme.colorScheme.error
                        "unavailable" -> JarvisViolet
                        else -> JarvisCyan
                    }
                    MiniPill("${reviewCheckLabel(check)} · ${status.uppercase()}", color)
                }
            }
        }

        if (result.missing_required_checks.isNotEmpty() || result.failed_required_checks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Revisión incompleta: comprobaciones requeridas han fallado. No trate esto como aprobación.",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }

        val prioritized = result.findings
            .sortedBy {
                when (it.severity) {
                    "blocking" -> 0
                    "warning" -> 1
                    else -> 2
                }
            }
        if (prioritized.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Hallazgos", style = MaterialTheme.typography.titleSmall)
            prioritized.forEach { finding ->
                Spacer(Modifier.height(8.dp))
                val findingColor = when (finding.severity) {
                    "blocking" -> MaterialTheme.colorScheme.error
                    "warning" -> JarvisAmber
                    else -> JarvisCyan
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = findingColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, findingColor.copy(alpha = 0.30f)),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MiniPill(finding.severity.uppercase(), findingColor)
                            MiniPill(reviewCheckLabel(finding.check), JarvisCyan)
                            if (finding.evidence_bound == true) {
                                MiniPill("EVIDENCIA VINCULADA", JarvisGreen)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(finding.message)
                        val refs = finding.evidence
                            .mapNotNull { it.source_ref?.takeIf(String::isNotBlank) }
                            .distinct()
                        if (refs.isNotEmpty()) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Evidencia: ${refs.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (finding.evidence_required) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Sin evidencia vinculada en este hallazgo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (finding.suggestions.isNotEmpty()) {
                            Spacer(Modifier.height(5.dp))
                            finding.suggestions.take(3).forEach { suggestion ->
                                Text("• $suggestion", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text(
                "No se encontraron anomalías en las comprobaciones completadas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun reviewCheckLabel(check: String): String = when (check) {
    "grammar" -> "GRAMÁTICA"
    "prose" -> "PROSA"
    "style" -> "ESTILO"
    "canon" -> "CANON"
    "timeline" -> "LÍNEA TEMPORAL"
    "character_knowledge" -> "CONOCIMIENTO"
    "power_cost" -> "COSTE DE PODER"
    "open_threads" -> "HILOS ABIERTOS"
    "reviewer" -> "REVISOR"
    else -> check.uppercase()
}

private enum class PlanFilter(val label: String, val statusKey: String?) {
    ALL("Todos", null),
    APPROVED("Aprobados", "APPROVED_PLAN"),
    PROPOSED("Propuestas", "PROPOSED"),
    SELECTED("Seleccionados", "HUMAN_SELECTED"),
    DEFERRED("Postergados", "DEFERRED"),
}

@Composable
private fun PlanSection(
    state: JarvisViewModel.WritingWorkspaceState,
    projectId: String,
    vm: JarvisViewModel,
) {
    var councilTitle by rememberSaveable { mutableStateOf("") }
    var councilPrompt by rememberSaveable { mutableStateOf("") }
    var councilReply by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(PlanFilter.ALL) }

    val council = state.planningCouncil
    val filteredPlans = remember(state.plans, selectedFilter) {
        if (selectedFilter.statusKey == null) state.plans
        else state.plans.filter { it.status == selectedFilter.statusKey }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xEE0E182A),
                border = BorderStroke(1.dp, JarvisViolet.copy(alpha = 0.45f)),
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            JarvisOrb(
                                size = 28.dp,
                                activity = if (state.busy && council != null) OrbActivity.THINKING else OrbActivity.IDLE,
                                contentDescription = "Sala de Planificación",
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "SALA DE PLANIFICACIÓN",
                                    style = HudTextStyle.copy(fontSize = 10.sp),
                                    color = JarvisViolet,
                                )
                                Text(
                                    "Consejo visible · futuro no ocurrido",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF8FAFC),
                                )
                            }
                        }
                        if (council != null) {
                            TextButton(
                                onClick = { vm.clearPlanningCouncil() },
                                enabled = !state.busy,
                            ) { Text("Nueva sala") }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Showrunner propone · Lore Keeper protege lo establecido · Challenger tensiona la idea · Story Architect entra cuando hay impacto de largo plazo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MiniPill("SHOWRUNNER", JarvisCyan)
                        MiniPill("LORE KEEPER", JarvisGreen)
                        MiniPill("CHALLENGER", JarvisAmber)
                        MiniPill("STORY ARCHITECT · SEGÚN APLIQUE", JarvisViolet)
                    }
                }
            }
        }

        if (state.planningCouncilSessions.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "SALAS RECIENTES",
                        style = HudTextStyle.copy(fontSize = 9.sp),
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.planningCouncilSessions.take(12).forEach { session ->
                            FilterChip(
                                selected = council?.session?.session_id == session.session_id,
                                onClick = { vm.openPlanningCouncil(projectId, session.session_id) },
                                enabled = !state.busy,
                                label = {
                                    Text(
                                        session.title.ifBlank { "Sala" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        if (council == null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x990E182A),
                    border = BorderStroke(1.dp, Color(0x332A3B57)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "¿QUÉ IDEA QUIERES EXPLORAR?",
                            style = HudTextStyle,
                            color = JarvisCyan,
                        )
                        Spacer(Modifier.height(6.dp))
                        SimpleField(
                            value = councilTitle,
                            onValueChange = { councilTitle = it },
                            label = "Título opcional de la sala",
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = councilPrompt,
                            onValueChange = { councilPrompt = it },
                            label = { Text("Idea, evento futuro o dirección narrativa") },
                            placeholder = {
                                Text("Ej: Quiero explorar qué consecuencias tendría que Alexander tome esta decisión dentro de varios capítulos...")
                            },
                            minLines = 5,
                            colors = jarvisTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Nada de esta conversación se vuelve canon automáticamente. Las intervenciones pueden guardarse como PROPUESTA y sólo tú cambias su autoridad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                vm.startPlanningCouncil(projectId, councilTitle, councilPrompt)
                                councilPrompt = ""
                                councilTitle = ""
                            },
                            enabled = councilPrompt.isNotBlank() && !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisViolet,
                                contentColor = Color(0xFFF8FAFC),
                            ),
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Abrir Consejo en Vivo", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x66101B2E),
                    border = BorderStroke(1.dp, JarvisViolet.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            council.session.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFF8FAFC),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            council.session.seed_prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                        )
                        Spacer(Modifier.height(6.dp))
                        MiniPill("NO CANON · EN PLANIFICACIÓN", JarvisViolet)
                    }
                }
            }

            items(council.messages, key = { it.message_id }) { message ->
                PlanningCouncilMessageCard(
                    message = message,
                    busy = state.busy,
                    onSaveIdea = { vm.savePlanningCouncilIdea(projectId, message.message_id) },
                )
            }

            if (state.busy) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = JarvisAmber.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = JarvisAmber,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                state.busyLabel.ifBlank { "CONSEJO TRABAJANDO" },
                                style = HudTextStyle.copy(fontSize = 10.sp),
                                color = JarvisAmber,
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x990E182A),
                    border = BorderStroke(1.dp, Color(0x332A3B57)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "INTERVENIR EN LA SALA",
                            style = HudTextStyle.copy(fontSize = 9.sp),
                            color = JarvisCyan,
                        )
                        Spacer(Modifier.height(5.dp))
                        OutlinedTextField(
                            value = councilReply,
                            onValueChange = { councilReply = it },
                            placeholder = {
                                Text("Ej: Me gusta la alternativa del Challenger, pero no quiero retrasar la revelación.")
                            },
                            minLines = 3,
                            colors = jarvisTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (councilReply.isNotBlank() && !state.busy) {
                                        vm.continuePlanningCouncil(projectId, councilReply)
                                        councilReply = ""
                                    }
                                },
                            ),
                        )
                        Spacer(Modifier.height(7.dp))
                        Button(
                            onClick = {
                                vm.continuePlanningCouncil(projectId, councilReply)
                                councilReply = ""
                            },
                            enabled = councilReply.isNotBlank() && !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Responder al Consejo")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlanFilter.entries.forEach { filter ->
                    val count = if (filter.statusKey == null) state.plans.size
                    else state.plans.count { it.status == filter.statusKey }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                "${filter.label} ($count)",
                                fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0x33101B2E),
                            labelColor = Color(0xFF94A3B8),
                            selectedContainerColor = LocalJarvisAccents.current.orbGlow.copy(alpha = 0.22f),
                            selectedLabelColor = Color(0xFF67E8F9),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == filter,
                            borderColor = Color(0x332A3B57),
                            selectedBorderColor = LocalJarvisAccents.current.orbGlow.copy(alpha = 0.65f),
                        ),
                    )
                }
            }
        }

        item {
            Text(
                "IDEAS Y PLANES GUARDADOS",
                style = HudTextStyle,
                color = JarvisCyan,
            )
        }

        if (filteredPlans.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No hay planes bajo \"${selectedFilter.label}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(filteredPlans) { item ->
                PlanningCard(item, state.busy) { status ->
                    vm.setWritingPlanStatus(projectId, item.item_id, status)
                }
            }
        }
    }
}

@Composable
private fun PlanningCouncilMessageCard(
    message: WritingPlanningCouncilMessage,
    busy: Boolean,
    onSaveIdea: () -> Unit,
) {
    val isUser = message.role == "user"
    val accent = when (message.role) {
        "showrunner" -> JarvisCyan
        "lore_keeper" -> JarvisGreen
        "challenger" -> JarvisAmber
        "story_architect" -> JarvisViolet
        else -> Color(0xFF94A3B8)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
            else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = if (isUser) Color(0xFF1E3A5F) else Color(0xEE0E182A),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.90f else 0.98f),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        message.role_label.ifBlank { message.role.uppercase() },
                        style = HudTextStyle.copy(fontSize = 9.sp),
                        color = accent,
                    )
                    if (!isUser) {
                        if (message.saved_plan_item_id.isNotBlank()) {
                            MiniPill("IDEA GUARDADA", JarvisGreen)
                        } else {
                            TextButton(
                                onClick = onSaveIdea,
                                enabled = !busy,
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Guardar idea", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                if (isUser) {
                    Text(
                        message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFF8FAFC),
                    )
                } else {
                    RichModelText(message.body)
                }
            }
        }
    }
}

@Composable
private fun PlanningCard(
    item: WritingPlanItem,
    busy: Boolean,
    onStatus: (String) -> Unit,
) {
    val accent = authorityColor(item.status)
    WorkspaceCard("${item.title} · ${item.status}", accent) {
        MiniPill(authorityLabel(item.status), accent)
        Spacer(Modifier.height(7.dp))
        RichModelText(item.body)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = { onStatus("HUMAN_SELECTED") }, enabled = !busy) { Text("Seleccionar") }
            TextButton(onClick = { onStatus("APPROVED_PLAN") }, enabled = !busy) { Text("Aprobar plan") }
            TextButton(onClick = { onStatus("DEFERRED") }, enabled = !busy) { Text("Postergar") }
            TextButton(onClick = { onStatus("REJECTED_FOR_CURRENT_ARC") }, enabled = !busy) { Text("Rechazar") }
        }
    }
}

@Composable
private fun WikiSection(
    state: JarvisViewModel.WritingWorkspaceState,
    projectId: String,
    vm: JarvisViewModel,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCharacterId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingCharactersDirectory by rememberSaveable { mutableStateOf(false) }
    var selectedMilestoneEra by rememberSaveable { mutableStateOf<String?>(null) }

    val home = state.wikiHome
    val wiki = state.wiki
    val settings by vm.settings.collectAsStateWithLifecycle()
    val liveCharacters = remember(state.wikiCharacters) {
        mergeStructuredCharacters(state.wikiCharacters)
    }

    if (selectedCharacterId != null) {
        val character = findStructuredCharacterByReference(liveCharacters, selectedCharacterId.orEmpty())
        if (character != null) {
            CharacterDetailWiki(
                character = character,
                allCharacters = liveCharacters,
                projectId = projectId,
                vm = vm,
                isOwner = settings.isOwner,
                onBack = { selectedCharacterId = null },
                onSelectCharacter = { newCharId -> selectedCharacterId = newCharId },
            )
            return
        }
    }

    if (viewingCharactersDirectory) {
        CharactersDirectory(
            characters = liveCharacters,
            onSelectCharacter = { character ->
                selectedCharacterId = character.id
            },
            onBack = {
                viewingCharactersDirectory = false
            },
        )
        return
    }

    val matchingCharacters = remember(query, liveCharacters) {
        val clean = query.trim()
        if (clean.isNotEmpty()) searchStructuredCharacters(liveCharacters, clean) else emptyList()
    }
    val strings = LocalAppStrings.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEE0E182A),
                border = BorderStroke(1.dp, LocalJarvisAccents.current.orbGlow.copy(alpha = 0.32f)),
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Story Knowledge Wiki",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFF8FAFC),
                        )
                        Spacer(Modifier.weight(1f))
                        val canonChapter = home?.latest_official_chapter?.chapter_number ?: 37
                        MiniPill("CANON · CH $canonChapter", JarvisGreen)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Search canon dossiers, character arcs, rules, and timelines, or tap a category below.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(strings.searchLorePlaceholder, color = Color(0xFF64748B)) },
                        colors = jarvisTextFieldColors(),
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = ""; vm.clearWritingWikiSearch() }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { viewingCharactersDirectory = true },
                            label = { Text("Personajes (${liveCharacters.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0x33101B2E),
                                labelColor = JarvisCyan,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = JarvisCyan.copy(alpha = 0.45f),
                            ),
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                query = "facciones y vinculos"
                                vm.searchWritingWiki(projectId, "facciones y vinculos")
                            },
                            label = { Text("Facciones (${CANON_FACTIONS.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0x33101B2E),
                                labelColor = JarvisGreen,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = JarvisGreen.copy(alpha = 0.45f),
                            ),
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                query = "hitos cronologicos"
                                vm.searchWritingWiki(projectId, "hitos cronologicos")
                            },
                            label = { Text("Hitos Cap 1–37 (${CANON_MILESTONES.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0x33101B2E),
                                labelColor = JarvisAmber,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = JarvisAmber.copy(alpha = 0.45f),
                            ),
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                query = "jutsus y poderes"
                                vm.searchWritingWiki(projectId, "jutsus y poderes")
                            },
                            label = { Text("Poderes & Jutsus") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0x33101B2E),
                                labelColor = Color(0xFFCBD5E1),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = Color(0x332A3B57),
                            ),
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                query = "canon y reglas"
                                vm.searchWritingWiki(projectId, "canon y reglas")
                            },
                            label = { Text("Reglas & Continuidad") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0x33101B2E),
                                labelColor = Color(0xFFCBD5E1),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = Color(0x332A3B57),
                            ),
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                query = "arcos y capitulos"
                                vm.searchWritingWiki(projectId, "arcos y capitulos")
                            },
                            label = { Text("Arcos & Capítulos") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0x33101B2E),
                                labelColor = Color(0xFFCBD5E1),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = Color(0x332A3B57),
                            ),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                val clean = query.trim().lowercase()
                                if (clean == "personajes" || clean == "characters" || clean == "personaje" || clean == "character") {
                                    viewingCharactersDirectory = true
                                } else {
                                    vm.searchWritingWiki(projectId, query)
                                }
                            },
                            enabled = query.isNotBlank() && !state.busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalJarvisAccents.current.orbGlow,
                                contentColor = Color(0xFF041018),
                                disabledContainerColor = LocalJarvisAccents.current.orbGlow.copy(alpha = 0.3f),
                                disabledContentColor = Color(0xFF041018).copy(alpha = 0.4f),
                            ),
                        ) {
                            Text("Search Wiki", fontWeight = FontWeight.Bold)
                        }
                        if (wiki != null) {
                            OutlinedButton(
                                onClick = { vm.clearWritingWikiSearch(); query = "" },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(strings.backToExplore)
                            }
                        }
                    }
                }
            }
        }

        if (state.busy) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x3322D3EE),
                    border = BorderStroke(1.dp, LocalJarvisAccents.current.orbGlow.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalJarvisAccents.current.orbGlow)
                        Spacer(Modifier.width(10.dp))
                        Text("Searching story canon & references…", style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
                    }
                }
            }
        }

        // Show matching characters at top of results whenever user searches or types
        if (matchingCharacters.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "CANON CHARACTERS (${matchingCharacters.size})",
                        style = HudTextStyle,
                        color = LocalJarvisAccents.current.orbGlow,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewingCharactersDirectory = true }) {
                        Text("Ver directorio (${liveCharacters.size})", style = HudTextStyle, color = JarvisCyan)
                    }
                }
            }
            items(matchingCharacters, key = { "match_${it.id}" }) { char ->
                CharacterDirectoryCard(
                    character = char,
                    onClick = { selectedCharacterId = char.id },
                )
            }
        }

        if (wiki != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${wiki.results.size} GROUNDED RESULTS",
                        style = HudTextStyle,
                        color = LocalJarvisAccents.current.orbGlow,
                    )
                    Spacer(Modifier.weight(1f))
                    MiniPill(if (wiki.connected) "CANON CONNECTED" else "OFFLINE", if (wiki.connected) JarvisGreen else JarvisAmber)
                }
            }
            if (wiki.results.isEmpty() && matchingCharacters.isEmpty()) {
                item {
                    WorkspaceCard("No results found") {
                        Text(
                            "No matching canon excerpts for \"$query\". Try searching character names or timeline events.",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                items(wiki.results) { source ->
                    WorkspaceCard(source.title, authorityColor(source.canon_status)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MiniPill(authorityLabel(source.canon_status), authorityColor(source.canon_status))
                            source.heading?.let { h ->
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    h,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF1F5F9),
                                )
                            }
                        }
                        if (source.excerpt.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            RichModelText(source.excerpt)
                        }
                    }
                }
            }
        } else {
            val categories = home?.categories?.ifEmpty { null } ?: listOf(
                WritingWikiCategory(
                    id = "characters",
                    label = "Personajes",
                    subtitle = "Expedientes canónicos, estado vital, poderes y trayectoria",
                    query = "characters",
                    source_count = liveCharacters.size,
                ),
                WritingWikiCategory(
                    id = "canon",
                    label = "Canon y Continuidad",
                    subtitle = "Reglas fundamentales del mundo y líneas temporales",
                    query = "canon",
                    source_count = 6,
                ),
                WritingWikiCategory(
                    id = "arcs",
                    label = "Arcos y Capítulos",
                    subtitle = "Estructura de la historia hasta el Capítulo 37",
                    query = "arcs",
                    source_count = 37,
                ),
            )

            item {
                Text(
                    strings.exploreByCategory,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFF8FAFC),
                )
            }
            items(categories) { category ->
                val isCharacterCategory = category.id.equals("characters", ignoreCase = true) ||
                    category.label.contains("personaje", ignoreCase = true) ||
                    category.label.contains("character", ignoreCase = true) ||
                    category.query.equals("characters", ignoreCase = true) ||
                    category.query.equals("personajes", ignoreCase = true)

                val displayCategory = if (isCharacterCategory) {
                    category.copy(
                        label = "Personajes",
                        subtitle = "Expedientes canónicos, estado vital, poderes y trayectoria",
                        source_count = liveCharacters.size,
                    )
                } else {
                    category
                }

                WikiCategoryCard(displayCategory) {
                    if (isCharacterCategory) {
                        viewingCharactersDirectory = true
                    } else {
                        query = category.query
                        vm.searchWritingWiki(projectId, category.query)
                    }
                }
            }

            // --- CANON TIMELINE MILESTONES (ERA PROGRESSION) ---
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Línea Temporal & Hitos (Capítulos 1–37)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFF8FAFC),
                )
            }
            items(CANON_MILESTONES, key = { it.id }) { milestone ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xEE0E182A),
                    border = BorderStroke(1.dp, milestone.badgeColor.copy(alpha = 0.38f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MiniPill(milestone.era, milestone.badgeColor)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                milestone.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF8FAFC),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            milestone.summary,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = Color(0xFFCBD5E1),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Personajes clave del hito:",
                            style = HudTextStyle,
                            color = Color(0xFF94A3B8),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            milestone.keyCharacterIds.mapNotNull { id ->
                                liveCharacters.firstOrNull { it.id.equals(id, ignoreCase = true) }
                            }.forEach { char ->
                                Surface(
                                    onClick = { selectedCharacterId = char.id },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x33101B2E),
                                    border = BorderStroke(1.dp, char.themeColor.copy(alpha = 0.45f)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(char.themeColor.copy(alpha = 0.25f), CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                char.avatarInitial,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = char.themeColor,
                                            )
                                        }
                                        Text(
                                            char.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color(0xFFF8FAFC),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- INTERACTIVE FACTIONS & BONDS WEB ---
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Facciones & Alianzas Canónicas",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFF8FAFC),
                )
            }
            items(CANON_FACTIONS, key = { it.id }) { faction ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xEE0E182A),
                    border = BorderStroke(1.dp, faction.accentColor.copy(alpha = 0.38f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                faction.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF8FAFC),
                            )
                            Spacer(Modifier.weight(1f))
                            MiniPill("${faction.memberIds.size} MIEMBROS", faction.accentColor)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            faction.description,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = Color(0xFF94A3B8),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            faction.memberIds.mapNotNull { id ->
                                liveCharacters.firstOrNull { it.id.equals(id, ignoreCase = true) }
                            }.forEach { member ->
                                Surface(
                                    onClick = { selectedCharacterId = member.id },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0x44060D1A),
                                    border = BorderStroke(1.dp, member.themeColor.copy(alpha = 0.45f)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .background(member.themeColor.copy(alpha = 0.25f), CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                member.avatarInitial,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = member.themeColor,
                                            )
                                        }
                                        Text(
                                            member.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color(0xFFF8FAFC),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (home != null) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Canon Authority Map",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFF8FAFC),
                    )
                }
                items(home.legend) { legend ->
                    WorkspaceCard(legend.label, authorityColor(legend.status)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MiniPill(
                                "${home.authority_counts[legend.status] ?: 0}",
                                authorityColor(legend.status),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                legend.meaning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCBD5E1),
                            )
                        }
                    }
                }

                if (home.featured.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Featured Canon Documents",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFF8FAFC),
                        )
                    }
                    items(home.featured) { source ->
                        WorkspaceCard(source.title, authorityColor(source.canon_status)) {
                            MiniPill(authorityLabel(source.canon_status), authorityColor(source.canon_status))
                            if (source.authority.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(source.authority, style = HudTextStyle, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharactersDirectory(
    characters: List<StoryCharacter>,
    onSelectCharacter: (StoryCharacter) -> Unit,
    onBack: () -> Unit,
) {
    var searchFilter by rememberSaveable { mutableStateOf("") }
    var selectedStatusFilter by rememberSaveable { mutableStateOf<CharacterLifeStatus?>(null) }

    val filteredCharacters = remember(searchFilter, selectedStatusFilter, characters) {
        val base = if (searchFilter.trim().isEmpty()) {
            characters
        } else {
            searchStructuredCharacters(characters, searchFilter)
        }
        if (selectedStatusFilter != null) {
            base.filter { it.status == selectedStatusFilter }
        } else {
            base
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEE0E182A),
                border = BorderStroke(1.dp, LocalJarvisAccents.current.orbGlow.copy(alpha = 0.32f)),
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = LocalJarvisAccents.current.orbGlow,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Volver al Wiki", color = Color(0xFFF1F5F9), style = HudTextStyle)
                        }
                        Spacer(Modifier.weight(1f))
                        MiniPill("CANON · CAP 37", JarvisGreen)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Directorio de Personajes",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF8FAFC),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Expedientes canónicos oficiales, estado vital, poderes, jutsus insignia y trayectoria histórica.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        placeholder = { Text("Filtrar por nombre, jutsu, facción…", color = Color(0xFF64748B)) },
                        colors = jarvisTextFieldColors(),
                        singleLine = true,
                        trailingIcon = {
                            if (searchFilter.isNotBlank()) {
                                IconButton(onClick = { searchFilter = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedStatusFilter == null,
                    onClick = { selectedStatusFilter = null },
                    label = { Text("Todos (${characters.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LocalJarvisAccents.current.orbGlow.copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFFF8FAFC),
                    ),
                )
                FilterChip(
                    selected = selectedStatusFilter == CharacterLifeStatus.ALIVE,
                    onClick = {
                        selectedStatusFilter = if (selectedStatusFilter == CharacterLifeStatus.ALIVE) null else CharacterLifeStatus.ALIVE
                    },
                    label = { Text("Activos") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisGreen.copy(alpha = 0.25f),
                        selectedLabelColor = JarvisGreen,
                    ),
                )
                FilterChip(
                    selected = selectedStatusFilter == CharacterLifeStatus.ALIVE_MARKED,
                    onClick = {
                        selectedStatusFilter = if (selectedStatusFilter == CharacterLifeStatus.ALIVE_MARKED) null else CharacterLifeStatus.ALIVE_MARKED
                    },
                    label = { Text("Marcados") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisAmber.copy(alpha = 0.25f),
                        selectedLabelColor = JarvisAmber,
                    ),
                )
                FilterChip(
                    selected = selectedStatusFilter == CharacterLifeStatus.CRITICAL_INJURY || selectedStatusFilter == CharacterLifeStatus.CONTROLLED,
                    onClick = {
                        selectedStatusFilter = if (selectedStatusFilter == CharacterLifeStatus.CRITICAL_INJURY) null else CharacterLifeStatus.CRITICAL_INJURY
                    },
                    label = { Text("Críticos / Controlados") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisRed.copy(alpha = 0.25f),
                        selectedLabelColor = JarvisRed,
                    ),
                )
                FilterChip(
                    selected = selectedStatusFilter == CharacterLifeStatus.IN_COMA || selectedStatusFilter == CharacterLifeStatus.DECEASED,
                    onClick = {
                        selectedStatusFilter = if (selectedStatusFilter == CharacterLifeStatus.IN_COMA) null else CharacterLifeStatus.IN_COMA
                    },
                    label = { Text("Coma / Caídos") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF94A3B8).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFFE2E8F0),
                    ),
                )
            }
        }

        item {
            Text(
                "${filteredCharacters.size} PERSONAJES CANÓNICOS",
                style = HudTextStyle,
                color = LocalJarvisAccents.current.orbGlow,
            )
        }

        if (filteredCharacters.isEmpty()) {
            item {
                WorkspaceCard("Sin resultados") {
                    Text(
                        "No se encontraron personajes para \"$searchFilter\".",
                        color = Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            items(filteredCharacters, key = { it.id }) { character ->
                CharacterDirectoryCard(
                    character = character,
                    onClick = { onSelectCharacter(character) },
                )
            }
        }
    }
}

@Composable
private fun CharacterDirectoryCard(
    character: StoryCharacter,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xEE0E182A),
        border = BorderStroke(1.dp, character.themeColor.copy(alpha = 0.35f)),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(character.themeColor.copy(alpha = 0.18f), CircleShape)
                        .border(1.5.dp, character.themeColor.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        character.avatarInitial,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = character.themeColor,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        character.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF8FAFC),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        character.epithet,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = character.themeColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniPill(character.status.label, character.status.color)
                        MiniPill(character.faction, Color(0xFF94A3B8))
                    }
                }
            }
            if (!character.essence.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x33060D1A),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "\"${character.essence}\"",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            lineHeight = 18.sp,
                        ),
                        color = Color(0xFFCBD5E1),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Abrir artículo →",
                    style = HudTextStyle,
                    color = character.themeColor,
                )
            }
        }
    }
}

private enum class DossierSubTab(val label: String) {
    TODOS("Artículo"),
    GENERAL("Resumen"),
    HISTORIA("Biografía"),
    APARICIONES("Apariciones"),
    PODERES("Poderes"),
    VINCULOS("Relaciones"),
    ANALISIS("Análisis JARVIS"),
}

@Composable
private fun CompactCharacterHeader(character: StoryCharacter) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xEE0E182A),
        border = BorderStroke(1.dp, character.themeColor.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(character.themeColor.copy(alpha = 0.2f), CircleShape)
                    .border(1.5.dp, character.themeColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    character.avatarInitial,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = character.themeColor,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    character.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFF8FAFC),
                )
                Text(
                    character.epithet,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = character.themeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MiniPill(character.status.label, character.status.color)
        }
    }
}

@Composable
private fun CharacterDetailWiki(
    character: StoryCharacter,
    allCharacters: List<StoryCharacter>,
    projectId: String,
    vm: JarvisViewModel,
    isOwner: Boolean,
    onBack: () -> Unit,
    onSelectCharacter: (String) -> Unit,
) {
    var currentSubTab by rememberSaveable(character.id) { mutableStateOf(DossierSubTab.TODOS) }
    val wikiPrimaryState by vm.wikiPrimaryState.collectAsStateWithLifecycle()
    val wikiVisualAttachments by vm.wikiVisualAttachments.collectAsStateWithLifecycle()
    val visualAttachmentId = wikiVisualAttachments[character.visualAssetId]
    val visualBitmap = remember(visualAttachmentId) {
        visualAttachmentId?.let { vm.attachmentStore.decodeThumbnail(it, 768) }
    }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            vm.setWikiPrimaryReferenceFromUri(
                uri = uri,
                character = character.wikiEntryId.ifBlank { character.id },
                projectId = projectId,
            )
        }
    }

    LaunchedEffect(character.visualAssetId) {
        if (character.visualAssetId.isNotBlank()) {
            vm.loadWikiVisual(projectId, character.visualAssetId)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = LocalJarvisAccents.current.orbGlow,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Volver a personajes", color = Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                MiniPill(
                    "CANON OFICIAL · CAP " + (character.encyclopediaProfile.latestAppearance ?: 37),
                    JarvisGreen,
                )
            }
        }

        // Internal Dossier Navigation Tabs
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xEE0E182A),
                border = BorderStroke(1.dp, LocalJarvisAccents.current.orbGlow.copy(alpha = 0.22f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DossierSubTab.values().forEach { subTab ->
                        val selected = currentSubTab == subTab
                        FilterChip(
                            selected = selected,
                            onClick = { currentSubTab = subTab },
                            label = {
                                Text(
                                    subTab.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0x33101B2E),
                                labelColor = if (selected) JarvisCyan else Color(0xFFCBD5E1),
                                selectedContainerColor = Color(0x33101B2E),
                                selectedLabelColor = JarvisCyan,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = Color(0x332A3B57),
                                selectedBorderColor = JarvisCyan,
                                borderWidth = if (selected) 1.5.dp else 1.dp,
                            ),
                            leadingIcon = if (selected) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(JarvisCyan, CircleShape),
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }
        }

        // --- GENERAL ---
        if (currentSubTab == DossierSubTab.GENERAL || currentSubTab == DossierSubTab.TODOS) {
            // Hero Portrait / Banner
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xEE0E182A),
                    border = BorderStroke(1.5.dp, character.themeColor.copy(alpha = 0.55f)),
                    shadowElevation = 6.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(character.themeColor.copy(alpha = 0.22f), Color.Transparent),
                                ),
                            ),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(character.themeColor.copy(alpha = 0.2f))
                                        .border(2.dp, character.themeColor, RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (visualBitmap != null) {
                                        Image(
                                            bitmap = visualBitmap.asImageBitmap(),
                                            contentDescription = character.visualAlt.ifBlank {
                                                "Referencia visual aprobada de " + character.name
                                            },
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Text(
                                            character.avatarInitial,
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Black,
                                            color = character.themeColor,
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        character.name,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFF8FAFC),
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        character.epithet,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = character.themeColor,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        character.faction,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8),
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MiniPill(character.status.label, character.status.color)
                                MiniPill(character.faction, Color(0xFF94A3B8))
                                MiniPill("CANON OFICIAL", JarvisGreen)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                character.role,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFFCBD5E1),
                            )

                            if (isOwner) {
                                Spacer(Modifier.height(12.dp))
                                val publishing = wikiPrimaryState is JarvisViewModel.WikiPrimaryState.Busy
                                OutlinedButton(
                                    onClick = {
                                        vm.resetWikiPrimaryState()
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    },
                                    enabled = !publishing,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    if (publishing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = character.themeColor,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.AddPhotoAlternate,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (character.visualAssetId.isBlank()) "SUBIR IMAGEN" else "CAMBIAR IMAGEN",
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                when (val visualState = wikiPrimaryState) {
                                    is JarvisViewModel.WikiPrimaryState.Success -> {
                                        val currentEntry = character.wikiEntryId.ifBlank {
                                            "character:" + character.id
                                        }
                                        if (visualState.characterId.equals(currentEntry, ignoreCase = true)) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                "REFERENCIA APROBADA · revisión " + visualState.revision,
                                                style = HudTextStyle,
                                                color = JarvisGreen,
                                            )
                                        }
                                    }
                                    is JarvisViewModel.WikiPrimaryState.Error -> {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            visualState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }

            if (
                character.encyclopediaProfile.rank.isNotBlank() ||
                character.encyclopediaProfile.age.isNotBlank() ||
                character.encyclopediaProfile.affiliations.isNotEmpty() ||
                character.encyclopediaProfile.family.isNotEmpty()
            ) {
                item {
                    CharacterEncyclopediaInfobox(
                        character = character,
                        allCharacters = allCharacters,
                        onSelectCharacter = onSelectCharacter,
                    )
                }
            }

            // Status & Vital Condition
            item {
                WorkspaceCard("Estado actual", accent = character.status.color) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiniPill(character.status.label, character.status.color)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        character.statusDetail,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = Color(0xFFF1F5F9),
                    )
                }
            }

            // Essence
            item {
                WorkspaceCard("Resumen", accent = character.themeColor) {
                    Text(
                        "\"${character.essence}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            lineHeight = 22.sp,
                        ),
                        color = Color(0xFFE2E8F0),
                    )
                }
            }

            // Appearance & Visual Notes
            item {
                WorkspaceCard("Apariencia", accent = Color(0xFF94A3B8)) {
                    SelectionContainer {
                        Text(
                            character.appearance,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = Color(0xFFF1F5F9),
                        )
                    }
                }
            }
        }

        // --- PODERES ---
        if (currentSubTab == DossierSubTab.PODERES || currentSubTab == DossierSubTab.TODOS) {
            if (currentSubTab == DossierSubTab.PODERES) {
                item {
                    CompactCharacterHeader(character)
                }
            }

            item {
                WorkspaceCard("Habilidades, Técnicas & Límites", accent = JarvisCyan) {
                    if (character.canonicalAbilities.isNotEmpty()) {
                        Text("HABILIDADES CANÓNICAS", style = HudTextStyle, color = JarvisCyan)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            character.canonicalAbilities.forEach { ability ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0x66060D1A),
                                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.28f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "• $ability",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFF1F5F9),
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            character.powersOverview,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = Color(0xFFF1F5F9),
                        )
                    }

                    if (character.signatureTechniques.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("TÉCNICAS REGISTRADAS", style = HudTextStyle, color = JarvisCyan)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            character.signatureTechniques.forEach { (name, desc) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x66060D1A),
                                    border = BorderStroke(1.dp, character.themeColor.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = character.themeColor,
                                        )
                                        if (desc.isNotBlank()) {
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                desc,
                                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                                color = Color(0xFFCBD5E1),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (character.canonicalLimitations.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("LÍMITES & COSTOS", style = HudTextStyle, color = JarvisRed)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            character.canonicalLimitations.forEach { limitation ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = JarvisRed.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, JarvisRed.copy(alpha = 0.30f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "• $limitation",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                        color = Color(0xFFF1F5F9),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- HISTORIA ---
        if (currentSubTab == DossierSubTab.HISTORIA || currentSubTab == DossierSubTab.TODOS) {
            if (currentSubTab == DossierSubTab.HISTORIA) {
                item {
                    CompactCharacterHeader(character)
                }
            }

            item {
                WorkspaceCard("Historia Canónica · Línea de Tiempo", accent = JarvisGreen) {
                    if (character.historyTimeline.isNotEmpty()) {
                        Text(
                            "Sólo se muestran hechos documentados. Las etapas sin información canónica se marcan explícitamente para no inventar datos.",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = Color(0xFF94A3B8),
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            character.historyTimeline.forEachIndexed { index, stage ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x66060D1A),
                                    border = BorderStroke(1.dp, JarvisGreen.copy(alpha = 0.30f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            MiniPill("${index + 1}", JarvisGreen)
                                            Text(
                                                stage.label.ifBlank { stage.period },
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFFF8FAFC),
                                            )
                                        }
                                        if (stage.period.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                stage.period,
                                                style = HudTextStyle,
                                                color = JarvisGreen.copy(alpha = 0.85f),
                                            )
                                        }
                                        if (stage.summary.isNotBlank()) {
                                            Spacer(Modifier.height(7.dp))
                                            Text(
                                                stage.summary,
                                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                                                color = Color(0xFFCBD5E1),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            character.storyHistory,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = Color(0xFFF1F5F9),
                        )
                    }
                }
            }

            if (
                character.chapterActivity.isEmpty() &&
                (character.canonAppearances.isNotEmpty() || character.mentionedChapters.isNotEmpty())
            ) {
                item {
                    WorkspaceCard("Apariciones & Capítulos", accent = JarvisCyan) {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            character.canonAppearances.forEach { appearance ->
                                val chapterLabel = if (appearance.chapters.isEmpty()) {
                                    "Aparición"
                                } else {
                                    "Cap. " + appearance.chapters.joinToString(", ")
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0x66060D1A),
                                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.padding(11.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                chapterLabel,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = JarvisCyan,
                                            )
                                            if (appearance.kind.isNotBlank()) {
                                                MiniPill(appearance.kind, Color(0xFF94A3B8))
                                            }
                                        }
                                        if (appearance.summary.isNotBlank()) {
                                            Spacer(Modifier.height(5.dp))
                                            Text(
                                                appearance.summary,
                                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                                color = Color(0xFFCBD5E1),
                                            )
                                        }
                                    }
                                }
                            }
                            if (character.mentionedChapters.isNotEmpty()) {
                                Text(
                                    "Menciones adicionales: capítulos ${character.mentionedChapters.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                )
                            }
                        }
                    }
                }
            }

            val relevantMilestones = CANON_MILESTONES.filter { it.keyCharacterIds.contains(character.id) }
            if (relevantMilestones.isNotEmpty()) {
                item {
                    WorkspaceCard("Hitos Canónicos Vinculados al Personaje", accent = JarvisCyan) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            relevantMilestones.forEach { milestone ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x66060D1A),
                                    border = BorderStroke(1.dp, milestone.badgeColor.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            MiniPill(milestone.era, milestone.badgeColor)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                milestone.title,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFFF8FAFC),
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            milestone.summary,
                                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                            color = Color(0xFFCBD5E1),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- APARICIONES ---
        if (currentSubTab == DossierSubTab.APARICIONES || currentSubTab == DossierSubTab.TODOS) {
            if (currentSubTab == DossierSubTab.APARICIONES) {
                item {
                    CompactCharacterHeader(character)
                }
            }

            if (character.chapterActivity.isNotEmpty()) {
                item {
                    WorkspaceCard("Apariciones por capítulo", accent = JarvisCyan) {
                        Text(
                            "Qué hizo el personaje, qué decidió y qué consecuencias dejó cada aparición. " +
                                "Cuando una fuente sólo resume varios capítulos, JARVIS conserva el bloque agrupado en vez de inventar granularidad.",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = Color(0xFF94A3B8),
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            character.chapterActivity.forEach { activity ->
                                CharacterChapterActivityCard(activity, character.themeColor)
                            }
                        }
                    }
                }
            } else if (currentSubTab == DossierSubTab.APARICIONES) {
                item {
                    WorkspaceCard("Apariciones", accent = JarvisCyan) {
                        if (character.canonAppearances.isEmpty() && character.mentionedChapters.isEmpty()) {
                            Text(
                                "Todavía no hay un índice capítulo por capítulo estructurado para este personaje.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCBD5E1),
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                character.canonAppearances.forEach { appearance ->
                                    Text(
                                        formatChapterLabel(appearance.chapters) + " · " + appearance.summary,
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                        color = Color(0xFFF1F5F9),
                                    )
                                }
                                if (character.mentionedChapters.isNotEmpty()) {
                                    Text(
                                        "Menciones: capítulos " + character.mentionedChapters.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- VÍNCULOS ---
        if (currentSubTab == DossierSubTab.VINCULOS || currentSubTab == DossierSubTab.TODOS) {
            if (currentSubTab == DossierSubTab.VINCULOS) {
                item {
                    CompactCharacterHeader(character)
                }
            }

            character.centralWound?.let { wound ->
                item {
                    WorkspaceCard("Psicología & Herida Central", accent = JarvisRed) {
                        Text(
                            wound,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = Color(0xFFF1F5F9),
                        )
                    }
                }
            }

            character.relationships?.let { rel ->
                item {
                    WorkspaceCard("Relaciones & Vínculos Directos", accent = JarvisViolet) {
                        Text(
                            rel,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = Color(0xFFF1F5F9),
                        )
                    }
                }
            }

            // Related Characters Jump Chips (Fandom style cross-linking)
            val relatedChars = character.relatedCharacterIds.mapNotNull { id ->
                allCharacters.firstOrNull { it.id.equals(id, ignoreCase = true) }
            }
            if (relatedChars.isNotEmpty()) {
                item {
                    WorkspaceCard("Personajes Vinculados (Cross-Linking Canónico)", accent = JarvisCyan) {
                        Text(
                            "Toca cualquier personaje vinculado para saltar a su expediente:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            relatedChars.forEach { relChar ->
                                Surface(
                                    onClick = { onSelectCharacter(relChar.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0x66060D1A),
                                    border = BorderStroke(1.dp, relChar.themeColor.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .background(relChar.themeColor.copy(alpha = 0.2f), CircleShape)
                                                .border(1.5.dp, relChar.themeColor, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                relChar.avatarInitial,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = relChar.themeColor,
                                            )
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    relChar.name,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFFF8FAFC),
                                                )
                                                MiniPill(relChar.status.label, relChar.status.color)
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                relChar.epithet,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = relChar.themeColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                relChar.role,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF94A3B8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            "→",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = relChar.themeColor,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- ANÁLISIS JARVIS ---
        if (currentSubTab == DossierSubTab.ANALISIS || currentSubTab == DossierSubTab.TODOS) {
            if (currentSubTab == DossierSubTab.ANALISIS) {
                item {
                    CompactCharacterHeader(character)
                }
            }

            character.jarvisAnalysis?.let { analysis ->
                item {
                    CharacterJarvisAnalysisCard(
                        character = character,
                        analysis = analysis,
                    )
                }
            } ?: run {
                if (currentSubTab == DossierSubTab.ANALISIS) {
                    item {
                        WorkspaceCard("Análisis de JARVIS", accent = JarvisViolet) {
                            Text(
                                "Este personaje todavía no tiene un análisis derivado estructurado. " +
                                    "JARVIS no rellenará motivaciones o psicología sin evidencia del canon.",
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                                color = Color(0xFFCBD5E1),
                            )
                        }
                    }
                }
            }
        }

        // Back button at bottom
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = character.themeColor,
                    contentColor = Color(0xFF041018),
                ),
            ) {
                Text("← Volver a personajes", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CharacterEncyclopediaInfobox(
    character: StoryCharacter,
    allCharacters: List<StoryCharacter>,
    onSelectCharacter: (String) -> Unit,
) {
    val profile = character.encyclopediaProfile
    WorkspaceCard("Ficha del personaje", accent = character.themeColor) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (profile.rank.isNotBlank()) {
                WikiFactRow("Rango", profile.rank)
            }
            if (profile.age.isNotBlank()) {
                WikiFactRow("Edad", profile.age)
            }
            if (profile.height.isNotBlank()) {
                WikiFactRow("Altura", profile.height)
            }
            if (profile.affiliations.isNotEmpty()) {
                WikiFactRow("Afiliación", profile.affiliations.joinToString(" · "))
            }
            WikiFactRow("Estado", character.status.label)
            profile.firstAppearance?.let { WikiFactRow("Primera aparición", "Capítulo " + it) }
            profile.latestAppearance?.let { WikiFactRow("Última aparición", "Capítulo " + it) }

            if (profile.ageNote.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = JarvisAmber.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        profile.ageNote,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = Color(0xFFCBD5E1),
                    )
                }
            }

            if (profile.family.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("FAMILIA", style = HudTextStyle, color = character.themeColor)
                Spacer(Modifier.height(7.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    profile.family.forEach { member ->
                        val related = findStructuredCharacterByReference(allCharacters, member.id)
                        Surface(
                            onClick = { related?.let { onSelectCharacter(it.id) } },
                            enabled = related != null,
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x66060D1A),
                            border = BorderStroke(1.dp, character.themeColor.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    member.label,
                                    style = HudTextStyle,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.width(74.dp),
                                )
                                Text(
                                    related?.name ?: member.id.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFF1F5F9),
                                    modifier = Modifier.weight(1f),
                                )
                                if (related != null) {
                                    Text("→", color = related.themeColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WikiFactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF94A3B8),
            modifier = Modifier.width(112.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = Color(0xFFF1F5F9),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CharacterChapterActivityCard(
    activity: CharacterChapterActivity,
    accent: Color,
) {
    val saveKey = activity.chapters.joinToString("_") + "_" + activity.title
    var expanded by rememberSaveable(saveKey) { mutableStateOf(false) }
    val presenceColor = when (activity.presence) {
        "ON_PAGE" -> JarvisGreen
        "MENTIONED_ONLY" -> Color(0xFF94A3B8)
        "RECAP_GROUPED" -> JarvisAmber
        "INDIRECT" -> JarvisViolet
        else -> accent
    }
    val presenceLabel = when (activity.presence) {
        "ON_PAGE" -> "EN ESCENA"
        "MENTIONED_ONLY" -> "MENCIÓN"
        "RECAP_GROUPED" -> "RECAP AGRUPADA"
        "INDIRECT" -> "INDIRECTO"
        else -> activity.presence.ifBlank { "REGISTRO" }
    }

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        color = Color(0x66060D1A),
        border = BorderStroke(1.dp, presenceColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    formatChapterLabel(activity.chapters),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                )
                MiniPill(presenceLabel, presenceColor)
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Ocultar detalles" else "Ver detalles",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp),
                )
            }
            if (activity.title.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    activity.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFF1F5F9),
                )
            }
            if (activity.summary.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    activity.summary,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                    color = Color(0xFFCBD5E1),
                )
            }
            if (expanded) {
                ActivityDetailGroup("QUÉ HIZO", activity.actions, JarvisCyan)
                ActivityDetailGroup("DECISIONES", activity.decisions, JarvisAmber)
                ActivityDetailGroup("TÉCNICAS", activity.techniques, characterTechniqueColor(accent))
                ActivityDetailGroup("CONSECUENCIAS", activity.consequences, JarvisGreen)
                if (activity.sourceRefs.isNotEmpty()) {
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "FUENTE VINCULADA · " + activity.sourceRefs.size +
                            if (activity.sourceRefs.size == 1) " documento" else " documentos",
                        style = HudTextStyle,
                        color = Color(0xFF64748B),
                    )
                }
            }
        }
    }
}

private fun characterTechniqueColor(accent: Color): Color = accent

@Composable
private fun ActivityDetailGroup(
    title: String,
    values: List<String>,
    accent: Color,
) {
    if (values.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Text(title, style = HudTextStyle, color = accent)
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            Text(
                "• " + value,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = Color(0xFFF1F5F9),
            )
        }
    }
}

@Composable
private fun CharacterJarvisAnalysisCard(
    character: StoryCharacter,
    analysis: CharacterJarvisAnalysis,
) {
    WorkspaceCard("Entender a " + character.name, accent = JarvisViolet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            MiniPill("ANÁLISIS JARVIS", JarvisViolet)
            MiniPill("DERIVADO · NO CANON", JarvisAmber)
        }
        Spacer(Modifier.height(9.dp))
        Text(
            analysis.disclaimer,
            style = MaterialTheme.typography.bodySmall.copy(
                fontStyle = FontStyle.Italic,
                lineHeight = 18.sp,
            ),
            color = Color(0xFF94A3B8),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            analysis.summary,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = Color(0xFFF1F5F9),
        )

        if (analysis.evolution.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text("EVOLUCIÓN", style = HudTextStyle, color = JarvisViolet)
            Spacer(Modifier.height(6.dp))
            Text(
                analysis.evolution,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                color = Color(0xFFCBD5E1),
            )
        }

        ActivityDetailGroup("MOTIVACIONES", analysis.motivations, JarvisCyan)
        ActivityDetailGroup("PATRONES DE CONDUCTA", analysis.behaviorPatterns, JarvisAmber)

        if (analysis.insights.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("LECTURAS DE JARVIS", style = HudTextStyle, color = JarvisViolet)
            Spacer(Modifier.height(7.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                analysis.insights.forEach { insight ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = JarvisViolet.copy(alpha = 0.07f),
                        border = BorderStroke(1.dp, JarvisViolet.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(11.dp)) {
                            Text(
                                insight.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF8FAFC),
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                insight.analysis,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                                color = Color(0xFFCBD5E1),
                            )
                            if (insight.evidenceChapters.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "EVIDENCIA · " + formatChapterLabel(insight.evidenceChapters),
                                    style = HudTextStyle,
                                    color = JarvisGreen,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatChapterLabel(chapters: List<Int>): String {
    if (chapters.isEmpty()) return "Aparición"
    val sorted = chapters.distinct().sorted()
    return if (sorted.size > 1 && sorted.last() - sorted.first() + 1 == sorted.size) {
        "Cap. " + sorted.first() + "–" + sorted.last()
    } else if (sorted.size == 1) {
        "Cap. " + sorted.first()
    } else {
        "Caps. " + sorted.joinToString(", ")
    }
}

@Composable
private fun WikiCategoryCard(category: WritingWikiCategory, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xEE0E182A),
        border = BorderStroke(1.dp, LocalJarvisAccents.current.orbGlow.copy(alpha = 0.28f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    category.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFF8FAFC),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    category.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = Color(0xFF94A3B8),
                )
            }
            Spacer(Modifier.size(10.dp))
            MiniPill("${category.source_count}", JarvisCyan)
        }
    }
}

/**
 * Modern Publication & Export Studio.
 */
@Composable
private fun LibrarySection(
    state: JarvisViewModel.WritingWorkspaceState,
    projectId: String,
    vm: JarvisViewModel,
) {
    val library = state.library
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> if (uri != null) vm.savePendingWritingExport(uri) else vm.cancelPendingWritingExport() }
    val docxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ),
    ) { uri -> if (uri != null) vm.savePendingWritingExport(uri) else vm.cancelPendingWritingExport() }
    val epubLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/epub+zip"),
    ) { uri -> if (uri != null) vm.savePendingWritingExport(uri) else vm.cancelPendingWritingExport() }
    val markdownLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> if (uri != null) vm.savePendingWritingExport(uri) else vm.cancelPendingWritingExport() }

    LaunchedEffect(state.pendingExport?.sha256) {
        val export = state.pendingExport ?: return@LaunchedEffect
        when (export.format) {
            "pdf" -> pdfLauncher.launch(export.filename)
            "docx" -> docxLauncher.launch(export.filename)
            "epub" -> epubLauncher.launch(export.filename)
            "markdown" -> markdownLauncher.launch(export.filename)
            else -> vm.cancelPendingWritingExport()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            WorkspaceCard("Centro de Publicación y Exportación", JarvisCyan) {
                Text(
                    "Compila capítulos oficiales en formatos de lectura y manuscrito. Las exportaciones se generan bajo demanda desde el canon oficial.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                val exports = library?.exports.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportTile(
                            title = "Libro PDF",
                            format = "pdf",
                            status = if (exports["pdf"] == "ready") "DISPONIBLE" else "PENDIENTE",
                            icon = Icons.Filled.PictureAsPdf,
                            modifier = Modifier.weight(1f),
                        )
                        ExportTile(
                            title = "Lector EPUB",
                            format = "epub",
                            status = if (exports["epub"] == "ready") "DISPONIBLE" else "PENDIENTE",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportTile(
                            title = "Word DOCX",
                            format = "docx",
                            status = if (exports["docx"] == "ready") "DISPONIBLE" else "PENDIENTE",
                            icon = Icons.Filled.Description,
                            modifier = Modifier.weight(1f),
                        )
                        ExportTile(
                            title = "Bóveda Markdown",
                            format = "markdown",
                            status = if (exports["markdown"] == "ready") "DISPONIBLE" else "PENDIENTE",
                            icon = Icons.Filled.EditNote,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (!state.exportMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.exportMessage.orEmpty(), color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (library != null) {
            item {
                Text(
                    "ARCHIVOS DEL CANON OFICIAL",
                    style = HudTextStyle,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(library.items) { item ->
                WorkspaceCard(item.title, JarvisGreen) {
                    MiniPill("CANON OFICIAL", JarvisGreen)
                    val range = when {
                        item.chapter_min != null && item.chapter_max != null && item.chapter_min != item.chapter_max ->
                            "Capítulos ${item.chapter_min} al ${item.chapter_max}"
                        item.chapter_max != null -> "Capítulo ${item.chapter_max}"
                        else -> ""
                    }
                    if (range.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(range, color = Color(0xFFCBD5E1))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = { vm.readWritingLibraryDocument(projectId, item.document_id) },
                            enabled = !state.busy,
                        ) { Text("Leer") }
                        OutlinedButton(
                            onClick = { vm.requestWritingLibraryExport(projectId, item.document_id, "pdf") },
                            enabled = !state.busy && library.exports["pdf"] == "ready",
                        ) { Text("PDF") }
                        OutlinedButton(
                            onClick = { vm.requestWritingLibraryExport(projectId, item.document_id, "docx") },
                            enabled = !state.busy && library.exports["docx"] == "ready",
                        ) { Text("DOCX") }
                        OutlinedButton(
                            onClick = { vm.requestWritingLibraryExport(projectId, item.document_id, "epub") },
                            enabled = !state.busy && library.exports["epub"] == "ready",
                        ) { Text("EPUB") }
                        OutlinedButton(
                            onClick = { vm.requestWritingLibraryExport(projectId, item.document_id, "markdown") },
                            enabled = !state.busy && library.exports["markdown"] == "ready",
                        ) { Text("MD") }
                    }
                }
            }
        }
        state.document?.let { document ->
            item {
                WorkspaceCard(document.title, JarvisGreen) {
                    MiniPill("CANON OFICIAL", JarvisGreen)
                    Spacer(Modifier.height(8.dp))
                    RichModelText(document.text)
                }
            }
        }
    }
}

@Composable
private fun ExportTile(
    title: String,
    format: String,
    status: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0x66111E33),
        border = BorderStroke(1.dp, Color(0x332A3B57)),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = JarvisCyan,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFF1F5F9),
                )
                Text(
                    status.uppercase(),
                    style = HudTextStyle.copy(fontSize = 9.sp),
                    color = if (status == "ready") JarvisGreen else JarvisAmber,
                )
            }
        }
    }
}

@Composable
private fun SimpleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = minLines,
        colors = jarvisTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WorkspaceCard(
    title: String,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val cardAccent = accent ?: LocalJarvisAccents.current.orbGlow
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xEE0E182A),
        border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.32f)),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFF8FAFC),
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MiniPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = HudTextStyle.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp),
            color = color,
        )
    }
}

@Composable
private fun AuthorityStat(
    title: String,
    count: Int,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xEE0E182A),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = color)
            Spacer(Modifier.height(2.dp))
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFFF1F5F9))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
        }
    }
}

private fun writingRoomActivityLabel(
    state: JarvisViewModel.WritingWorkspaceState,
    strings: AppStrings,
): String = when {
    state.streamingText.isNotBlank() -> strings.writingRoomResponding
    state.chatHistory.lastOrNull()?.response == null && state.chatHistory.isNotEmpty() ->
        strings.writingRoomAnalyzing
    else -> state.busyLabel.ifBlank { strings.thinking }
}

@Composable
private fun RichModelText(text: String) {
    SelectionContainer {
        val lines = text.replace("\r\n", "\n").split("\n")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                lines.forEach { raw ->
                    val line = raw.trimEnd()
                    when {
                        line.isBlank() -> Spacer(Modifier.height(3.dp))
                        line.startsWith("### ") -> Text(
                            line.removePrefix("### "),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF67E8F9),
                        )
                        line.startsWith("## ") -> Text(
                            line.removePrefix("## "),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF67E8F9),
                        )
                        line.startsWith("# ") -> Text(
                            line.removePrefix("# "),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE0F2FE),
                        )
                        line.startsWith("- ") || line.startsWith("* ") -> Row(
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text("•", color = JarvisCyan, fontSize = 14.sp, modifier = Modifier.padding(top = 1.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                inlineMarkdown(line.drop(2)),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        line.matches(Regex("^\\d+[.)]\\s+.*")) -> {
                            val split = line.indexOf(' ')
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    line.take(split),
                                    color = JarvisCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    inlineMarkdown(line.drop(split + 1)),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 15.sp,
                                        lineHeight = 24.sp,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        else -> Text(
                            inlineMarkdown(line),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                        )
                    }
                }
            }
    }
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    val regex = Regex("\\*\\*(.+?)\\*\\*")
    regex.findAll(text).forEach { match ->
        if (match.range.first > index) {
            pushStyle(SpanStyle(color = Color(0xFFE2E8F0)))
            append(text.substring(index, match.range.first))
            pop()
        }
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF67E8F9)))
        append(match.groupValues[1])
        pop()
        index = match.range.last + 1
    }
    if (index < text.length) {
        pushStyle(SpanStyle(color = Color(0xFFE2E8F0)))
        append(text.substring(index))
        pop()
    }
}

private fun authorityColor(status: String): Color = when (status) {
    "OFFICIAL_CANON" -> JarvisGreen
    "REFERENCE" -> JarvisCyan
    "APPROVED_PLAN" -> JarvisAmber
    "LOCKED_FUTURE" -> JarvisViolet
    "PROPOSED" -> JarvisViolet
    "HUMAN_SELECTED" -> JarvisGreen
    else -> Color(0xFF94A3B8)
}

private fun authorityLabel(status: String): String = when (status) {
    "OFFICIAL_CANON" -> "CANON OFICIAL"
    "REFERENCE" -> "REFERENCIA"
    "APPROVED_PLAN" -> "PLAN APROBADO"
    "LOCKED_FUTURE" -> "FUTURO BLOQUEADO"
    "PROPOSED" -> "PROPUESTA"
    "HUMAN_SELECTED" -> "SELECCIONADO"
    "DEFERRED" -> "POSTERGADO"
    "REJECTED_FOR_CURRENT_ARC" -> "RECHAZADO"
    else -> status.ifBlank { "FUENTE" }
}

private enum class WorkspaceTab(val icon: ImageVector) {
    OVERVIEW(Icons.Filled.Dashboard),
    WRITE(Icons.Filled.EditNote),
    CHAT(Icons.AutoMirrored.Filled.Chat),
    PLAN(Icons.Filled.AccountTree),
    WIKI(Icons.Filled.AutoStories),
    LIBRARY(Icons.Filled.LocalLibrary);

    fun label(strings: AppStrings): String = when (this) {
        OVERVIEW -> strings.workspaceOverview
        WRITE -> strings.workspaceWrite
        CHAT -> strings.workspaceChat
        PLAN -> strings.workspacePlan
        WIKI -> strings.workspaceWiki
        LIBRARY -> strings.workspaceLibrary
    }
}
