package com.jarvis.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.transport.live.WritingChapter
import com.jarvis.android.transport.live.WritingPlanItem
import com.jarvis.android.ui.JarvisViewModel

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

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            val overview = state.overview
            Text(
                when {
                    state.busy -> state.busyLabel.ifBlank { "Working" }
                    overview != null -> {
                        val chapter = overview.latest_official_chapter?.chapter_number?.let { "Chapter $it" } ?: "Canon loaded"
                        "$chapter · ${overview.sources.total} sources · human authority"
                    }
                    else -> "Writing Room v1"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.busy) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            state.error.orEmpty(),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = vm::clearWritingWorkspaceError) { Text("Dismiss") }
                    }
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
                    label = { Text(item.label) },
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            WorkspaceCard("Current story state") {
                if (overview == null) {
                    Text("No workspace snapshot loaded.")
                } else {
                    Text(overview.project.title, style = MaterialTheme.typography.titleMedium)
                    Text("Story: ${overview.project.story_id}")
                    Text("Authority: ${overview.project.authority}")
                    val latest = overview.latest_official_chapter
                    Text(
                        if (latest?.chapter_number != null) {
                            "Latest official chapter: ${latest.chapter_number} · ${latest.title}"
                        } else {
                            "Latest official chapter: unavailable"
                        },
                    )
                }
            }
        }
        item {
            WorkspaceCard("Workspace") {
                Text("Planning items: ${overview?.workflow?.planning_items ?: 0}")
                Text("Chapter sessions: ${overview?.workflow?.chapter_sessions ?: 0}")
                Text("Versioned story sources: ${overview?.sources?.total ?: 0}")
            }
        }
        item {
            WorkspaceCard("Authority rules") {
                Text("OFFICIAL_CANON is established fact.")
                Text("APPROVED_PLAN is approved future direction, not an occurred event.")
                Text("PROPOSED material does not become canon automatically.")
                Text("Only a human approval can promote story authority.")
            }
        }
        item {
            OutlinedButton(
                onClick = { vm.refreshWritingWorkspace(projectId) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Refresh workspace") }
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "General story chat uses the Task Classifier to choose the right Writing Room seat automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = { if (it.length <= 6000) prompt = it },
                label = { Text("Ask about the story") },
                supportingText = { Text("${prompt.length}/6000") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = { vm.runWritingRoomAutoChat(projectId, title, prompt) },
                enabled = prompt.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send") }
        }
        state.chat?.let { chat ->
            item {
                WorkspaceCard("Task routing") {
                    Text("Class: ${chat.classification.task_class}")
                    Text("Seat: ${chat.selected_participant}")
                    if (chat.classification.depth.isNotBlank()) Text("Depth: ${chat.classification.depth}")
                    Text("${chat.turn.routing.provider} · ${chat.turn.routing.model}")
                }
            }
            item {
                WorkspaceCard(chat.turn.participant.label.ifBlank { "Response" }) {
                    Text(chat.turn.response.text)
                }
            }
            if (chat.turn.canon.sources.isNotEmpty()) {
                item { Text("Grounding", style = MaterialTheme.typography.titleMedium) }
                items(chat.turn.canon.sources) { source ->
                    WorkspaceCard(source.canon_status.ifBlank { "Source" }) {
                        Text(source.title)
                        if (!source.heading.isNullOrBlank()) {
                            Text(source.heading.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(chapter.showrunner_brief)
            Spacer(Modifier.height(8.dp))
        }
        if (chapter.reviewer_text.isNotBlank()) {
            Text("Reviewer", style = MaterialTheme.typography.titleSmall)
            Text(chapter.reviewer_text)
            Spacer(Modifier.height(8.dp))
        }
        if (chapter.canon_review_text.isNotBlank()) {
            Text("Canon audit", style = MaterialTheme.typography.titleSmall)
            Text(chapter.canon_review_text)
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
    WorkspaceCard("${item.title} · ${item.status}") {
        Text(item.body)
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SimpleField(query, { query = it }, "Search canon / lore / continuity", 3) }
        item {
            Button(
                onClick = { vm.searchWritingWiki(projectId, query) },
                enabled = query.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Search Wiki") }
        }
        val wiki = state.wiki
        if (wiki != null) {
            item {
                Text(
                    if (wiki.connected) "${wiki.results.size} grounded results · human authority" else "RAG unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(wiki.results) { source ->
                WorkspaceCard(source.canon_status.ifBlank { "Source" }) {
                    Text(source.title, style = MaterialTheme.typography.titleSmall)
                    if (!source.heading.isNullOrBlank()) Text(source.heading.orEmpty())
                    if (source.excerpt.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(source.excerpt, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
                WorkspaceCard(item.title) {
                    Text(item.canon_status)
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
                WorkspaceCard(document.title) {
                    Text(document.canon_status)
                    Spacer(Modifier.height(8.dp))
                    Text(document.text)
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
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WorkspaceCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

private enum class WorkspaceTab(val label: String) {
    OVERVIEW("Overview"),
    CHAT("Chat"),
    WRITE("Write"),
    PLAN("Plan"),
    WIKI("Wiki"),
    LIBRARY("Library"),
}
