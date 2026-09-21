package com.jarvis.android.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.jarvis.android.ui.theme.LocalJarvisAccents

/**
 * Daily v1 Writing Room for Alexander History, preview only.
 *
 * Names and seats come from the Story Workspace design. Facts do not: no
 * manuscript is in this repo, M3 has not published a project contract, and M4
 * has not chosen where canon lives. So a page says what is *not* loaded rather
 * than inventing a biography. Nothing here is retrieved, embedded, or stored,
 * and nothing can write canon.
 *
 * The brain stays [OrbActivity.IDLE]: present, not thinking, because there is
 * no session to think about.
 */
@Composable
fun WritingRoomPreview(title: String, modifier: Modifier = Modifier) {
    var openCharacter by rememberSaveable { mutableStateOf<String?>(null) }
    val character = CHARACTERS.firstOrNull { it.name == openCharacter }
    if (character != null) {
        CharacterPage(character, onBack = { openCharacter = null })
        return
    }

    val accents = LocalJarvisAccents.current
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            JarvisBrain(
                size = 148.dp,
                activity = OrbActivity.IDLE,
                contentDescription = "Jarvis, waiting. The writing room is not connected.",
            )
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text("PREVIEW — NOT CONNECTED", style = HudTextStyle, color = JarvisAmber)
        }
        item { SectionLabel("Council", "Seats are reserved. Nobody is in session.") }
        items(COUNCIL.size) { i -> CouncilSeat(COUNCIL[i]) }
        item { SectionLabel("Characters", "Open a page. It shows what the room does not know yet.") }
        items(CHARACTERS.size) { i ->
            CharacterRow(CHARACTERS[i]) { openCharacter = CHARACTERS[i].name }
        }
        item { SectionLabel("Canon", "No source is loaded, so nothing here is authoritative.") }
        item { EmptyCanon(accents.orbGlow) }
        item {
            Text(
                "A character only ever knows what the story has shown them. " +
                    "Author secrets and locked future events stay out of a character page. " +
                    "Retrieval and the story database arrive with the control plane.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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
private fun roomCardColor() = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)

@Composable
private fun roomCardBorder() = BorderStroke(1.dp, LocalJarvisAccents.current.orbGlow.copy(alpha = 0.16f))

@Composable
private fun FactBlock(label: String, body: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = roomCardColor(),
        border = roomCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionLabel(title: String, caption: String) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(title.uppercase(), style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
        Spacer(Modifier.height(2.dp))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CouncilSeat(seat: Seat) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = roomCardColor(),
        border = roomCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(seat.role, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(seat.brief, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("EMPTY", style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CharacterRow(character: CharacterEntry, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = roomCardColor(),
        border = roomCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(character.name, style = MaterialTheme.typography.titleMedium)
                Text(character.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("OPEN", style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
        }
    }
}

@Composable
private fun EmptyCanon(accent: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = roomCardColor(),
        border = roomCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("NO CANON LOADED", style = HudTextStyle, color = accent)
            Spacer(Modifier.height(4.dp))
            Text(
                "Facts appear here only after a source is imported and marked authoritative. A draft can never outrank one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
