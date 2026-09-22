package com.jarvis.android.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.android.ui.components.JarvisBrain
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisMotion
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors
import com.jarvis.android.ui.writing.LoreSearch

/**
 * Daily v1 Writing Room for Alexander History, preview only.
 *
 * Seats and names come from the Story Workspace design. Facts do not: no
 * manuscript is in this repo, M3 has not published a project contract, and M4
 * has not chosen where canon lives. Every page says what is *not* loaded
 * rather than inventing a biography. Nothing here retrieves, embeds, stores,
 * or writes canon.
 */
@Composable
fun WritingRoomPreview(title: String, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(RoomTab.LORE) }
    var openCharacter by rememberSaveable { mutableStateOf<String?>(null) }

    val character = CHARACTERS.firstOrNull { it.name == openCharacter }
    if (character != null) {
        CharacterPage(character, onBack = { openCharacter = null })
        return
    }

    Column(modifier.fillMaxSize()) {
        RoomHeader(title)
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
                RoomTab.COUNCIL -> CouncilTab()
                RoomTab.CANON -> CanonTab()
                RoomTab.STUDIO -> StudioTab()
            }
        }
    }
}

@Composable
private fun RoomHeader(title: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JarvisBrain(
            size = 120.dp,
            activity = OrbActivity.IDLE,
            contentDescription = "Jarvis, waiting. The writing room is not connected.",
        )
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text("PREVIEW — NOT CONNECTED", style = HudTextStyle, color = JarvisAmber)
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

/** The interactive wiki. Search filters names and roles only — no canon is loaded. */
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
private fun CouncilTab() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Caption("Seats are reserved. Nobody is in session.") }
        items(COUNCIL.size) { i ->
            val seat = COUNCIL[i]
            RoomCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(seat.role, style = MaterialTheme.typography.titleMedium)
                        Text(
                            seat.brief,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("EMPTY", style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CanonTab() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            RoomCard {
                Column {
                    Text("NO CANON LOADED", style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Facts appear only after a source is imported and marked authoritative. " +
                            "A draft can never outrank one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Caption(
                "A character only ever knows what the story has shown them. Author secrets and " +
                    "locked future events stay out of a character page.",
            )
        }
    }
}

/**
 * The production surface, preview only. The draft lives in this composition
 * and nowhere else: it is not saved, not sent, and not eligible to become
 * canon. That promotion stays a human action in the control plane.
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
        item { FactBlock("State", "Not loaded. Current condition connects with the story state.") }
        item { FactBlock("Knows", "Nothing yet. A character page never receives author secrets.") }
        item { FactBlock("Relationships", "Not loaded.") }
        item { FactBlock("Open threads", "Not loaded.") }
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

private enum class RoomTab(val label: String) {
    LORE("Lore"),
    COUNCIL("Council"),
    CANON("Canon"),
    STUDIO("Studio"),
}

private data class Seat(val role: String, val brief: String)

private data class CharacterEntry(val name: String, val role: String)

private val COUNCIL = listOf(
    Seat("Moderator", "Keeps the room to one question at a time."),
    Seat("Showrunner", "Holds the shape of the story, not its facts."),
    Seat("Canon Keeper", "Says what is established. Cannot change it."),
    Seat("Challenger", "Asks what breaks if the scene is true."),
)

/** The three simulations the Council MVP names. Roles, not biographies. */
private val CHARACTERS = listOf(
    CharacterEntry("Alexander", "Lead"),
    CharacterEntry("William", "Character"),
    CharacterEntry("Melody", "Character"),
)
