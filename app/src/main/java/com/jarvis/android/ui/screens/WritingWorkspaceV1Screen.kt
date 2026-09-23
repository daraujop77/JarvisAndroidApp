package com.jarvis.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.remember
import com.jarvis.android.data.story.CANON_CHARACTERS
import com.jarvis.android.data.story.CANON_FACTIONS
import com.jarvis.android.data.story.CANON_MILESTONES
import com.jarvis.android.data.story.CharacterLifeStatus
import com.jarvis.android.data.story.StoryCharacter
import com.jarvis.android.data.story.StoryFaction
import com.jarvis.android.data.story.StoryMilestone
import com.jarvis.android.data.story.findCharacter
import com.jarvis.android.data.story.searchCharacters
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.transport.live.WritingChapter
import com.jarvis.android.transport.live.WritingEngineReviewEnvelope
import com.jarvis.android.transport.live.WritingPlanItem
import com.jarvis.android.transport.live.WritingWikiCategory
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.JarvisRed
import com.jarvis.android.ui.theme.JarvisViolet
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors

/**
 * Product Writing Room v1 surface.
 *
 * The six sections mirror the server-owned workspace contract:
 * Overview / Chat / Write / Plan / Wiki / Library.
 * SQLite stores workflow state only. Story canon remains human-authoritative.
 */
@Composable
fun WritingWorkspaceV1Screen(
    vm: JarvisViewModel,
    projectId: String,
    title: String,
    modifier: Modifier = Modifier,
) {
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
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xEE0E182A),
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        accents.orbGlow.copy(alpha = 0.35f),
                        Color(0x228B5CF6),
                        accents.orbGlow.copy(alpha = 0.20f),
                    )
                )
            ),
            shadowElevation = 4.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JarvisOrb(
                    size = 70.dp,
                    activity = if (state.busy) OrbActivity.THINKING else OrbActivity.IDLE,
                    intensity = if (state.streamingText.isNotBlank()) 0.9f else 0.15f,
                    contentDescription = "Writing Room status",
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF8FAFC),
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
                    ) {
                        val chapter = overview?.latest_official_chapter?.chapter_number
                        MiniPill(
                            if (chapter != null) "CANON · CH $chapter" else "CANON",
                            JarvisGreen,
                        )
                        MiniPill(
                            if (state.busy) state.busyLabel.ifBlank { "WORKING" } else "READY",
                            if (state.busy) JarvisAmber else accents.online,
                        )
                    }
                }
            }
        }
        if (state.error != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.error.orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = vm::clearWritingWorkspaceError) { Text("Dismiss") }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkspaceTab.entries.forEach { item ->
                FilterChip(
                    selected = tab == item,
                    onClick = { tab = item },
                    label = {
                        Text(
                            item.label,
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

        Spacer(Modifier.height(6.dp))
        when (tab) {
            WorkspaceTab.OVERVIEW -> OverviewSection(state, projectId, vm)
            WorkspaceTab.CHAT -> ChatSection(state, projectId, title, vm)
            WorkspaceTab.WRITE -> WriteSection(state, projectId, vm)
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
    val overview = state.overview
    val counts = overview?.sources?.by_status.orEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            WorkspaceCard("Current story state", JarvisCyan) {
                if (overview == null) {
                    Text("No workspace snapshot loaded.")
                } else {
                    val latest = overview.latest_official_chapter
                    Text(
                        latest?.title ?: "Latest official chapter unavailable",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (latest?.chapter_number != null) "Official timeline through chapter ${latest.chapter_number}" else "Official timeline loaded",
                        color = Color(0xFFCBD5E1),
                    )
                }
            }
        }
        item {
            Text(
                "Authority map",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFF8FAFC),
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthorityStat("Canon", counts["OFFICIAL_CANON"] ?: 0, "established", JarvisGreen, Modifier.weight(1f))
                AuthorityStat("Reference", counts["REFERENCE"] ?: 0, "context only", JarvisCyan, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthorityStat("Approved", counts["APPROVED_PLAN"] ?: 0, "future plan", JarvisAmber, Modifier.weight(1f))
                AuthorityStat("Proposed", counts["PROPOSED"] ?: 0, "candidate", JarvisViolet, Modifier.weight(1f))
            }
        }
        item {
            WorkspaceCard("Workspace", JarvisCyan) {
                Text("Planning items: ${overview?.workflow?.planning_items ?: 0}", color = Color(0xFFF1F5F9))
                Text("Chapter sessions: ${overview?.workflow?.chapter_sessions ?: 0}", color = Color(0xFFF1F5F9))
                Text("Versioned sources: ${overview?.sources?.total ?: 0}", color = Color(0xFFF1F5F9))
                Spacer(Modifier.height(6.dp))
                Text(
                    "A document being present in Drive or RAG does not make it canon. Its explicit authority status controls how JARVIS may use it.",
                    color = Color(0xFF94A3B8),
                )
            }
        }
        item {
            OutlinedButton(
                onClick = { vm.refreshWritingWorkspace(projectId) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Refresh story state") }
        }
    }
}

@Composable
private fun ChatSection(
    state: JarvisViewModel.WritingWorkspaceState,
    projectId: String,
    title: String,
    vm: JarvisViewModel,
) {
    var prompt by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            WorkspaceCard("JARVIS Auto Routing", JarvisCyan) {
                Text(
                    "Ask naturally. The Task Classifier chooses the Writing Room seat and the reply streams as it is generated.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = { if (it.length <= 6000) prompt = it },
                label = { Text("What do you want to work on?") },
                supportingText = { Text("${prompt.length}/6000") },
                minLines = 3,
                colors = jarvisTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = { vm.runWritingRoomAutoChat(projectId, title, prompt) },
                enabled = prompt.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send to Writing Room") }
        }

        if (state.streamingText.isNotBlank()) {
            item {
                WorkspaceCard("JARVIS · STREAMING", JarvisAmber) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("Response arriving live", style = HudTextStyle, color = JarvisAmber)
                    }
                    Spacer(Modifier.height(10.dp))
                    RichModelText(state.streamingText)
                }
            }
        }

        state.chat?.let { chat ->
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MiniPill(chat.classification.task_class.ifBlank { "NORMAL" }, JarvisCyan)
                    MiniPill(chat.selected_participant.uppercase(), JarvisViolet)
                    MiniPill(chat.turn.routing.model, JarvisGreen)
                }
            }
            item {
                WorkspaceCard(chat.turn.participant.label.ifBlank { "JARVIS" }, JarvisCyan) {
                    Text(
                        "${chat.turn.routing.provider} · ${chat.turn.routing.model}",
                        style = HudTextStyle,
                        color = JarvisCyan,
                    )
                    Spacer(Modifier.height(9.dp))
                    RichModelText(chat.turn.response.text)
                }
            }

            val grouped = chat.turn.canon.sources.groupBy { it.canon_status.ifBlank { "REFERENCE" } }
            listOf("OFFICIAL_CANON", "REFERENCE", "APPROVED_PLAN", "PROPOSED").forEach { authority ->
                val entries = grouped[authority].orEmpty()
                if (entries.isNotEmpty()) {
                    item {
                        Text(
                            authorityLabel(authority),
                            style = MaterialTheme.typography.titleSmall,
                            color = authorityColor(authority),
                        )
                    }
                    items(entries.take(5)) { source ->
                        WorkspaceCard(source.title, authorityColor(authority)) {
                            MiniPill(authorityLabel(authority), authorityColor(authority))
                            if (!source.heading.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(source.heading.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WriteSection(
    state: JarvisViewModel.WritingWorkspaceState,
    projectId: String,
    vm: JarvisViewModel,
) {
    var chapterTitle by rememberSaveable { mutableStateOf("") }
    var objective by rememberSaveable { mutableStateOf("") }
    var storyPoint by rememberSaveable { mutableStateOf("") }
    var characters by rememberSaveable { mutableStateOf("") }
    var mustHave by rememberSaveable { mutableStateOf("") }
    var mustAvoid by rememberSaveable { mutableStateOf("") }
    var tone by rememberSaveable { mutableStateOf("") }
    var desiredEnd by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    val active = state.activeChapter

    LaunchedEffect(active?.chapter_id, active?.draft_text) {
        if (active != null) draft = active.draft_text
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Start with Author Intent. The server builds the Story State Brief before any prose is generated.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            WorkspaceCard("JARVIS Writing Engine · WR-1–5", JarvisCyan) {
                Text(
                    "The engine builds a frozen evidence pack, keeps future plans separate from occurred canon, and runs advisory editorial + continuity checks before human approval.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MiniPill("CONTEXT", JarvisCyan)
                    MiniPill("WRITER", JarvisGreen)
                    MiniPill("STYLE", JarvisViolet)
                    MiniPill("CANON", JarvisGreen)
                    MiniPill("TIMELINE", JarvisAmber)
                    MiniPill("KNOWLEDGE", JarvisCyan)
                    MiniPill("POWER", JarvisAmber)
                    MiniPill("COUNCIL", JarvisViolet)
                }
                if (!active?.context_pack_id.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Frozen Context Pack: ${active?.context_pack_id}",
                        style = HudTextStyle,
                        color = JarvisCyan,
                    )
                }
            }
        }
        item { SimpleField(chapterTitle, { chapterTitle = it }, "Chapter title") }
        item { SimpleField(objective, { objective = it }, "Author objective (required)", 4) }
        item { SimpleField(storyPoint, { storyPoint = it }, "Story point / where this begins") }
        item { SimpleField(characters, { characters = it }, "Characters, comma separated") }
        item { SimpleField(mustHave, { mustHave = it }, "Must have", 3) }
        item { SimpleField(mustAvoid, { mustAvoid = it }, "Must avoid", 3) }
        item { SimpleField(tone, { tone = it }, "Tone") }
        item { SimpleField(desiredEnd, { desiredEnd = it }, "Desired end state", 3) }
        item {
            Button(
                onClick = {
                    vm.startWritingChapter(
                        projectId = projectId,
                        title = chapterTitle,
                        objective = objective,
                        storyPoint = storyPoint,
                        characters = characters.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        mustHave = mustHave,
                        mustAvoid = mustAvoid,
                        tone = tone,
                        desiredEnd = desiredEnd,
                    )
                },
                enabled = objective.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create Story State Brief") }
        }

        if (state.chapters.isNotEmpty()) {
            item { Text("Chapter sessions", style = MaterialTheme.typography.titleMedium) }
            items(state.chapters) { chapter ->
                WorkspaceCard("${chapter.title} · ${chapter.status}") {
                    Text(chapter.objective)
                    if (chapter.story_point.isNotBlank()) {
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
                            enabled = !state.busy,
                        ) { Text("Write") }
                        TextButton(
                            onClick = { vm.runWritingChapterStep(projectId, chapter.chapter_id, "review") },
                            enabled = !state.busy,
                        ) { Text("Review") }
                    }
                }
            }
        }

        if (active != null) {
            item { ActiveChapterCard(active) }
            state.engineReview?.let { review ->
                item { WritingEngineReviewCard(review) }
            }
            if (active.draft_text.isNotBlank() || active.status == "DRAFT" || active.status == "REVIEW") {
                item {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("Working draft") },
                        minLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { vm.saveWritingChapterDraft(projectId, active.chapter_id, draft) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save draft") }
                }
            }
        }
    }
}

@Composable
private fun ActiveChapterCard(chapter: WritingChapter) {
    WorkspaceCard("${chapter.title} · ${chapter.status}") {
        if (chapter.showrunner_brief.isNotBlank()) {
            Text("Showrunner brief", style = MaterialTheme.typography.titleSmall)
            RichModelText(chapter.showrunner_brief)
            Spacer(Modifier.height(8.dp))
        }
        if (chapter.reviewer_text.isNotBlank()) {
            Text("Reviewer", style = MaterialTheme.typography.titleSmall)
            RichModelText(chapter.reviewer_text)
            Spacer(Modifier.height(8.dp))
        }
        if (chapter.canon_review_text.isNotBlank()) {
            Text("Canon audit", style = MaterialTheme.typography.titleSmall)
            RichModelText(chapter.canon_review_text)
        }
        if (chapter.context_pack_id.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Frozen context: ${chapter.context_pack_id}",
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

    WorkspaceCard("Writing Engine · Structured Review", statusColor) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniPill(result.status.ifBlank { "UNKNOWN" }.uppercase(), statusColor)
            MiniPill("$blocking BLOCK", if (blocking > 0) MaterialTheme.colorScheme.error else JarvisGreen)
            MiniPill("$warnings WARN", if (warnings > 0) JarvisAmber else JarvisGreen)
            MiniPill("$info INFO", JarvisCyan)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Context Pack: ${review.context_pack_id.ifBlank { result.context_pack_id }}",
            style = HudTextStyle,
            color = JarvisCyan,
        )
        Text(
            "Read-only review · no auto-rewrite · no canon promotion · human decision required",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val checks = result.check_order.ifEmpty { result.check_status.keys.toList() }
        if (checks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Checks", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                "Review incomplete: required factual checks are missing or failed. Do not treat this as approval.",
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
            Text("Findings", style = MaterialTheme.typography.titleSmall)
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
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MiniPill(finding.severity.uppercase(), findingColor)
                            MiniPill(reviewCheckLabel(finding.check), JarvisCyan)
                            if (finding.evidence_bound == true) {
                                MiniPill("EVIDENCE BOUND", JarvisGreen)
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
                                "Evidence: ${refs.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (finding.evidence_required) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "No bound evidence in this finding.",
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
                "No structured findings were returned by the completed checks.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun reviewCheckLabel(check: String): String = when (check) {
    "grammar" -> "GRAMMAR"
    "prose" -> "PROSE"
    "style" -> "STYLE"
    "canon" -> "CANON"
    "timeline" -> "TIMELINE"
    "character_knowledge" -> "KNOWLEDGE"
    "power_cost" -> "POWER"
    "open_threads" -> "THREADS"
    "reviewer" -> "REVIEWER"
    else -> check.uppercase()
}

@Composable
private fun PlanSection(
    state: JarvisViewModel.WritingWorkspaceState,
    projectId: String,
    vm: JarvisViewModel,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Planning is persistent workflow state, not canon. New items start as PROPOSED.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { SimpleField(title, { title = it }, "Plan title") }
        item { SimpleField(body, { body = it }, "Future direction / planning note", 5) }
        item {
            Button(
                onClick = {
                    vm.createWritingPlan(projectId, title, body)
                    title = ""
                    body = ""
                },
                enabled = body.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add proposed plan") }
        }
        items(state.plans) { item ->
            PlanningCard(item, state.busy) { status ->
                vm.setWritingPlanStatus(projectId, item.item_id, status)
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
    WorkspaceCard("${item.title} · ${item.status}", authorityColor(item.status)) {
        MiniPill(authorityLabel(item.status), authorityColor(item.status))
        Spacer(Modifier.height(7.dp))
        RichModelText(item.body)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = { onStatus("HUMAN_SELECTED") }, enabled = !busy) { Text("Select") }
            TextButton(onClick = { onStatus("APPROVED_PLAN") }, enabled = !busy) { Text("Approve plan") }
            TextButton(onClick = { onStatus("DEFERRED") }, enabled = !busy) { Text("Defer") }
            TextButton(onClick = { onStatus("REJECTED_FOR_CURRENT_ARC") }, enabled = !busy) { Text("Reject arc") }
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

    if (selectedCharacterId != null) {
        val character = findCharacter(selectedCharacterId!!)
        if (character != null) {
            CharacterDetailWiki(
                character = character,
                onBack = { selectedCharacterId = null },
                onSelectCharacter = { newCharId -> selectedCharacterId = newCharId },
            )
            return
        }
    }

    if (viewingCharactersDirectory) {
        CharactersDirectory(
            onSelectCharacter = { character ->
                selectedCharacterId = character.id
            },
            onBack = {
                viewingCharactersDirectory = false
            },
        )
        return
    }

    val matchingCharacters = remember(query) {
        val clean = query.trim()
        if (clean.isNotEmpty()) searchCharacters(clean) else emptyList()
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
                        placeholder = { Text("Search story canon, characters, lore…", color = Color(0xFF64748B)) },
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
                            label = { Text("Personajes (${CANON_CHARACTERS.size})") },
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
                                Text("Back to Explore")
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
                        Text("Ver directorio (${CANON_CHARACTERS.size})", style = HudTextStyle, color = JarvisCyan)
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
                    source_count = CANON_CHARACTERS.size,
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
                    "Explore by Category",
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
                        source_count = CANON_CHARACTERS.size,
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
                            milestone.keyCharacterIds.mapNotNull { findCharacter(it) }.forEach { char ->
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
                            faction.memberIds.mapNotNull { findCharacter(it) }.forEach { member ->
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
    onSelectCharacter: (StoryCharacter) -> Unit,
    onBack: () -> Unit,
) {
    var searchFilter by rememberSaveable { mutableStateOf("") }
    var selectedStatusFilter by rememberSaveable { mutableStateOf<CharacterLifeStatus?>(null) }

    val characters = remember(searchFilter, selectedStatusFilter) {
        val base = if (searchFilter.trim().isEmpty()) CANON_CHARACTERS else searchCharacters(searchFilter)
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
                    label = { Text("Todos (${CANON_CHARACTERS.size})") },
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
                "${characters.size} PERSONAJES CANÓNICOS",
                style = HudTextStyle,
                color = LocalJarvisAccents.current.orbGlow,
            )
        }

        if (characters.isEmpty()) {
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
            items(characters, key = { it.id }) { character ->
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
                    "Ver expediente canónico →",
                    style = HudTextStyle,
                    color = character.themeColor,
                )
            }
        }
    }
}

private enum class DossierSubTab(val label: String) {
    GENERAL("General"),
    PODERES("Poderes"),
    HISTORIA("Historia"),
    VINCULOS("Vínculos"),
    TODOS("Todos"),
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
    onBack: () -> Unit,
    onSelectCharacter: (String) -> Unit,
) {
    var currentSubTab by rememberSaveable(character.id) { mutableStateOf(DossierSubTab.GENERAL) }

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
                    Text("Back to Characters", color = Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                MiniPill("CANON OFICIAL · CAP 37", JarvisGreen)
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
                                        .size(76.dp)
                                        .background(character.themeColor.copy(alpha = 0.2f), CircleShape)
                                        .border(2.dp, character.themeColor, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        character.avatarInitial,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        color = character.themeColor,
                                    )
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
                        }
                    }
                }
            }

            // Status & Vital Condition
            item {
                WorkspaceCard("Estado & Condición Vital", accent = character.status.color) {
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
                WorkspaceCard("Esencia del Personaje", accent = character.themeColor) {
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
                WorkspaceCard("Apariencia & Notas Visuales", accent = Color(0xFF94A3B8)) {
                    Text(
                        character.appearance,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = Color(0xFFF1F5F9),
                    )
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
                WorkspaceCard("Poderes & Estilo de Combate", accent = JarvisCyan) {
                    Text(
                        character.powersOverview,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = Color(0xFFF1F5F9),
                    )
                    if (character.signatureTechniques.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "TÉCNICAS DISTINTIVAS & LÍMITES",
                            style = HudTextStyle,
                            color = JarvisCyan,
                        )
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
                WorkspaceCard("Trayectoria Narrativa Cronológica (Capítulos 1–37)", accent = JarvisGreen) {
                    Text(
                        character.storyHistory,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = Color(0xFFF1F5F9),
                    )
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
            val relatedChars = character.relatedCharacterIds.mapNotNull { findCharacter(it) }
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
                Text("← Back to Characters", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            WorkspaceCard("Publish / export") {
                val exports = library?.exports.orEmpty()
                Text("PDF: ${exports["pdf"] ?: "not available"}")
                Text("DOCX: ${exports["docx"] ?: "not available"}")
                Text("EPUB: ${exports["epub"] ?: "not available"}")
                Text("Markdown: ${exports["markdown"] ?: "not available"}")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Exports are generated on demand from official chapter sources. " +
                        "Saving a file does not change canon or write story content back to the VPS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.exportMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.exportMessage.orEmpty(), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (library != null) {
            items(library.items) { item ->
                WorkspaceCard(item.title, JarvisGreen) {
                    MiniPill("CANON OFICIAL", JarvisGreen)
                    val range = when {
                        item.chapter_min != null && item.chapter_max != null && item.chapter_min != item.chapter_max ->
                            "Chapters ${item.chapter_min}–${item.chapter_max}"
                        item.chapter_max != null -> "Chapter ${item.chapter_max}"
                        else -> ""
                    }
                    if (range.isNotBlank()) Text(range)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextButton(
                            onClick = { vm.readWritingLibraryDocument(projectId, item.document_id) },
                            enabled = !state.busy,
                        ) { Text("Open") }
                        TextButton(
                            onClick = { vm.requestWritingLibraryExport(projectId, item.document_id, "pdf") },
                            enabled = !state.busy && library.exports["pdf"] == "ready",
                        ) { Text("PDF") }
                        TextButton(
                            onClick = { vm.requestWritingLibraryExport(projectId, item.document_id, "docx") },
                            enabled = !state.busy && library.exports["docx"] == "ready",
                        ) { Text("DOCX") }
                        TextButton(
                            onClick = { vm.requestWritingLibraryExport(projectId, item.document_id, "epub") },
                            enabled = !state.busy && library.exports["epub"] == "ready",
                        ) { Text("EPUB") }
                        TextButton(
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

@Composable
private fun RichModelText(text: String) {
    val lines = text.replace("\r\n", "\n").split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        lines.forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line.startsWith("### ") -> Text(
                    line.removePrefix("### "),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF67E8F9),
                )
                line.startsWith("## ") -> Text(
                    line.removePrefix("## "),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF67E8F9),
                )
                line.startsWith("# ") -> Text(
                    line.removePrefix("# "),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFE0F2FE),
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row(
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("•", color = JarvisCyan, fontSize = 16.sp, modifier = Modifier.padding(top = 1.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        inlineMarkdown(line.drop(2)),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color(0xFFE2E8F0),
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
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
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                        Spacer(Modifier.size(8.dp))
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
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                    ),
                )
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

private fun authorityColor(status: String): Color = when (status.uppercase()) {
    "OFFICIAL_CANON" -> JarvisGreen
    "APPROVED_PLAN", "LOCKED_FUTURE" -> JarvisAmber
    "PROPOSED", "HUMAN_SELECTED" -> JarvisViolet
    "REFERENCE" -> JarvisCyan
    else -> Color(0xFF9CA3AF)
}

private fun authorityLabel(status: String): String = when (status.uppercase()) {
    "OFFICIAL_CANON" -> "CANON OFICIAL"
    "REFERENCE" -> "REFERENCIA"
    "APPROVED_PLAN" -> "PLAN APROBADO · FUTURO"
    "LOCKED_FUTURE" -> "FUTURO BLOQUEADO"
    "PROPOSED" -> "PROPUESTA"
    "HUMAN_SELECTED" -> "SELECCIONADO"
    else -> status.ifBlank { "SOURCE" }
}

private enum class WorkspaceTab(val label: String) {
    OVERVIEW("Overview"),
    CHAT("Chat"),
    WRITE("Write"),
    PLAN("Plan"),
    WIKI("Wiki"),
    LIBRARY("Library"),
}
