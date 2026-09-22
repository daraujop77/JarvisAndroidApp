package com.jarvis.android.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.JarvisBrain
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisMotion
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors
import com.jarvis.android.ui.writing.LoreSearch

/**
 * Grounded Writing Room for Alexander History.
 *
 * Council turns use the authenticated JARVIS app session. The backend retrieves
 * human-authored Story RAG evidence and keeps canon authority human-only.
 */
@Composable
fun WritingRoomPreview(
    vm: JarvisViewModel,
    projectId: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(RoomTab.COUNCIL) }
    var openCharacter by rememberSaveable { mutableStateOf<String?>(null) }
    val state by vm.writingRoomState.collectAsStateWithLifecycle()

    val character = CHARACTERS.firstOrNull { it.name == openCharacter }
    if (character != null) {
        CharacterPage(character, onBack = { openCharacter = null })
        return
    }

    Column(modifier.fillMaxSize()) {
        RoomHeader(title, state)
        RoomTabs(tab, onSelect = { tab = it })
        val tabIn = JarvisMotion.standard<Float>(180)
        val tabOut = JarvisMotion.standard<Float>(120)
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tabIn) togetherWith fadeOut(tabOut) },
            label = "room-tab",
        ) { target ->
            when (target) {
                RoomTab.LORE -> LoreTab(onOpenCharacter = { openCharacter = it })
                RoomTab.COUNCIL -> CouncilTab(
                    projectId = projectId,
                    projectTitle = title,
                    state = state,
                    onRun = vm::runWritingRoomTurn,
                    onClear = vm::clearWritingRoomTurn,
                )
                RoomTab.CANON -> CanonTab(state)
                RoomTab.STUDIO -> StudioTab()
            }
        }
    }
}

@Composable
private fun RoomHeader(title: String, state: JarvisViewModel.WritingRoomState) {
    val success = state as? JarvisViewModel.WritingRoomState.Success
    val connected = success?.turn?.canon?.connected == true
    val documents = success?.turn?.canon?.documents ?: 0
    val activity = if (state is JarvisViewModel.WritingRoomState.Running) OrbActivity.THINKING else OrbActivity.IDLE

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JarvisBrain(
            size = 120.dp,
            activity = activity,
            contentDescription = if (connected) "Jarvis Writing Room, canon connected." else "Jarvis Writing Room.",
        )
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            if (connected) "CANON CONNECTED · $documents DOCS" else "LIVE COUNCIL",
            style = HudTextStyle,
            color = if (connected) LocalJarvisAccents.current.online else JarvisAmber,
        )
    }
}

@Composable
private fun RoomTabs(selected: RoomTab, onSelect: (RoomTab) -> Unit) {
    val accents = LocalJarvisAccents.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoomTab.entries.forEach { entry ->
            FilterChip(
                selected = entry == selected,
                onClick = { onSelect(entry) },
                label = { Text(entry.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accents.orbGlow.copy(alpha = 0.18f),
                    selectedLabelColor = accents.orbGlow,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = entry == selected,
                    borderColor = accents.orbGlow.copy(alpha = 0.22f),
                    selectedBorderColor = accents.orbGlow.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

/** Search remains a compact character index; factual character pages will be wired next. */
@Composable
private fun LoreTab(onOpenCharacter: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = LoreSearch.filter(CHARACTERS.map { it.name to it.role }, query)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search the lore") },
                colors = jarvisTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (results.isEmpty()) {
            item {
                Text(
                    "Nothing matches \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(results.size) { i ->
            val (name, role) = results[i]
            RoomCard(onClick = { onOpenCharacter(name) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            role,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("OPEN", style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
                }
            }
        }
    }
}

@Composable
private fun CouncilTab(
    projectId: String,
    projectTitle: String,
    state: JarvisViewModel.WritingRoomState,
    onRun: (String, String, String, String) -> Unit,
    onClear: () -> Unit,
) {
    var participant by rememberSaveable { mutableStateOf("showrunner") }
    var prompt by rememberSaveable { mutableStateOf("") }
    val running = state is JarvisViewModel.WritingRoomState.Running

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Caption(
                "Choose one independent Council role. Every turn is grounded against the Story RAG; " +
                    "models can propose or challenge, but only you can approve canon.",
            )
        }

        items(COUNCIL.size) { i ->
            val seat = COUNCIL[i]
            val selected = participant == seat.id
            RoomCard(onClick = { if (!running) participant = seat.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(seat.role, style = MaterialTheme.typography.titleMedium)
                        Text(
                            seat.brief,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (selected) "SELECTED" else "SELECT",
                        style = HudTextStyle,
                        color = if (selected) LocalJarvisAccents.current.orbGlow
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = { if (it.length <= 6000) prompt = it },
                enabled = !running,
                minLines = 3,
                label = { Text("Question or scene to discuss") },
                supportingText = { Text("${prompt.length}/6000") },
                colors = jarvisTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onRun(projectId, projectTitle, participant, prompt) },
                    enabled = prompt.isNotBlank() && !running,
                    modifier = Modifier.weight(1f),
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("Thinking…")
                    } else {
                        Text("Ask ${COUNCIL.first { it.id == participant }.role}")
                    }
                }
                if (state !is JarvisViewModel.WritingRoomState.Idle && !running) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
        }

        when (state) {
            JarvisViewModel.WritingRoomState.Idle -> Unit
            is JarvisViewModel.WritingRoomState.Running -> item {
                Caption("${seatLabel(state.participant)} is working with the grounded story context.")
            }
            is JarvisViewModel.WritingRoomState.Error -> item {
                RoomCard {
                    Text("REQUEST FAILED", style = HudTextStyle, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is JarvisViewModel.WritingRoomState.Success -> {
                val turn = state.turn
                item {
                    RoomCard {
                        Text(
                            turn.participant.label.uppercase(),
                            style = HudTextStyle,
                            color = LocalJarvisAccents.current.orbGlow,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(turn.response.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    RoomCard {
                        Text("GROUNDING", style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (turn.canon.connected) {
                                "${turn.canon.documents} documents · ${turn.canon.chunks} chunks · " +
                                    "${turn.metrics.rag_source_count} retrieved sources"
                            } else {
                                "Canon/RAG not connected for this turn"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${turn.routing.provider} · ${turn.routing.model} · ${turn.metrics.total_ms} ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(turn.canon.sources.size) { i ->
                    val source = turn.canon.sources[i]
                    RoomCard {
                        Text(source.canon_status.ifBlank { "SOURCE" }, style = HudTextStyle)
                        Spacer(Modifier.height(3.dp))
                        Text(source.title, style = MaterialTheme.typography.bodyMedium)
                        if (!source.heading.isNullOrBlank()) {
                            Text(
                                source.heading,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CanonTab(state: JarvisViewModel.WritingRoomState) {
    val success = state as? JarvisViewModel.WritingRoomState.Success
    val canon = success?.turn?.canon

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            RoomCard {
                Column {
                    Text(
                        if (canon?.connected == true) "CANON CONNECTED" else "NO TURN LOADED",
                        style = HudTextStyle,
                        color = LocalJarvisAccents.current.orbGlow,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (canon?.connected == true) {
                            "Story ${canon.story_id ?: ""} · ${canon.documents} documents · " +
                                "${canon.chunks} chunks. Authority: human only."
                        } else {
                            "Run a Council turn to inspect the exact sources retrieved for that question."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (canon != null) {
            items(canon.sources.size) { i ->
                val source = canon.sources[i]
                RoomCard {
                    Text(source.canon_status.ifBlank { "SOURCE" }, style = HudTextStyle)
                    Spacer(Modifier.height(4.dp))
                    Text(source.title, style = MaterialTheme.typography.bodyMedium)
                    if (!source.heading.isNullOrBlank()) {
                        Text(
                            source.heading,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Caption(
                "OFFICIAL_CANON is established material. APPROVED_PLAN is approved future direction, " +
                    "REFERENCE can support but not override canon, and PROPOSED remains candidate material.",
            )
        }
    }
}

/**
 * Local draft studio. Draft text stays in this composition and is not sent,
 * saved, or eligible to become canon.
 */
@Composable
private fun StudioTab() {
    var draft by rememberSaveable { mutableStateOf("") }
    val words = draft.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Caption("A local draft. Nothing here is saved or can become canon.") }
        item {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Scene draft") },
                minLines = 8,
                colors = jarvisTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                "$words WORDS · NOT SAVED",
                style = HudTextStyle,
                color = JarvisAmber,
            )
        }
    }
}

@Composable
private fun CharacterPage(character: CharacterEntry, onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the writing room")
                }
                Column {
                    Text(character.name, style = MaterialTheme.typography.headlineSmall)
                    Text(character.role, style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
                }
            }
        }
        item { FactBlock("State", "Character-specific grounded pages are the next Writing Room slice.") }
        item { FactBlock("Knows", "No author-only secrets are shown on character pages.") }
        item { FactBlock("Relationships", "Use Council with Canon Keeper to inspect established relationships.") }
        item { FactBlock("Open threads", "Use Council with Showrunner or Challenger for grounded proposals.") }
    }
}

@Composable
private fun FactBlock(label: String, body: String) {
    RoomCard {
        Column {
            Text(label.uppercase(), style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@Composable
private fun RoomCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    val border = BorderStroke(1.dp, LocalJarvisAccents.current.orbGlow.copy(alpha = 0.16f))
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, border = border, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    } else {
        Surface(shape = shape, color = color, border = border, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

private fun seatLabel(id: String): String = COUNCIL.firstOrNull { it.id == id }?.role ?: id

private enum class RoomTab(val label: String) {
    LORE("Lore"),
    COUNCIL("Council"),
    CANON("Canon"),
    STUDIO("Studio"),
}

private data class Seat(val id: String, val role: String, val brief: String)

private data class CharacterEntry(val name: String, val role: String)

private val COUNCIL = listOf(
    Seat("moderator", "Moderator", "Keeps the room to one question at a time and separates facts from proposals."),
    Seat("showrunner", "Showrunner", "Explores structure, pacing, consequences, pressure, and concrete story options."),
    Seat("canon_keeper", "Canon Keeper", "Checks canon, continuity, timeline, and what characters are allowed to know."),
    Seat("challenger", "Challenger", "Stress-tests assumptions and looks for failure modes or stronger alternatives."),
)

private val CHARACTERS = listOf(
    CharacterEntry("Alexander", "Lead"),
    CharacterEntry("William", "Character"),
    CharacterEntry("Melody", "Character"),
)
