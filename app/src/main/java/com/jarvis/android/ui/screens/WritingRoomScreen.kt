package com.jarvis.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Daily v1 Writing Room, preview only.
 *
 * M3 has not published a project contract and M4 has not chosen where canon
 * lives, so nothing here is retrieved, embedded, or stored. The council and
 * the canon list are fixtures that show the shape the room will have once the
 * control plane connects. The brain stays [OrbActivity.IDLE]: present, not
 * thinking, because there is no session to think about.
 */
@Composable
fun WritingRoomPreview(title: String, modifier: Modifier = Modifier) {
    val accents = LocalJarvisAccents.current
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            JarvisBrain(
                size = 168.dp,
                activity = OrbActivity.IDLE,
                contentDescription = "Jarvis, waiting. Writing room is not connected.",
            )
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                "PREVIEW — NOT CONNECTED",
                style = HudTextStyle,
                color = JarvisAmber,
            )
        }
        item { RoomSection("Council", "No one is in session. Seats are reserved, not occupied.") }
        items(COUNCIL.size) { i -> CouncilSeat(COUNCIL[i]) }
        item { RoomSection("Canon", "Nothing here is authoritative. The index connects at M4.") }
        items(CANON.size) { i -> CanonRow(CANON[i], accents.orbGlow) }
        item {
            Text(
                "A draft cannot outrank canon, and nothing on this screen can write it. " +
                    "Retrieval, embeddings and the story database arrive with the control plane.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun RoomSection(title: String, caption: String) {
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun CanonRow(fact: CanonFact, accent: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fact.claim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(fact.authority, style = HudTextStyle, color = accent)
            }
            Spacer(Modifier.height(2.dp))
            Text(fact.source, style = HudTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class Seat(val role: String, val brief: String)

private data class CanonFact(val claim: String, val authority: String, val source: String)

private val COUNCIL = listOf(
    Seat("Moderator", "Keeps the room to one question at a time."),
    Seat("Showrunner", "Holds the shape of the story, not its facts."),
    Seat("Canon Keeper", "Says what is established. Cannot change it."),
    Seat("Challenger", "Asks what breaks if the scene is true."),
)

private val CANON = listOf(
    CanonFact("The city floods every seventh winter.", "CANON", "book 1 · ch. 2"),
    CanonFact("Mara does not know the route home.", "OPEN", "unresolved thread"),
    CanonFact("The river was redirected by the council.", "DRAFT", "not authoritative"),
)
