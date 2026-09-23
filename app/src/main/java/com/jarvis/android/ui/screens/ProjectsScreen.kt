package com.jarvis.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.data.projects.ProjectKind
import com.jarvis.android.data.projects.ProjectState
import com.jarvis.android.data.projects.ProjectSummary
import com.jarvis.android.data.projects.ProjectsResult
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.JarvisViolet
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors

private enum class ProjectFilter(val label: String) {
    ALL("All"),
    WRITING("Writing Studios"),
    ACTIVE("Active"),
    ARCHIVED("Archived"),
}

@Composable
fun ProjectsScreen(vm: JarvisViewModel) {
    val result by vm.projects.collectAsStateWithLifecycle()
    val actionMessage by vm.projectsMessage.collectAsStateWithLifecycle()
    var openProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var selectedFilter by rememberSaveable { mutableStateOf(ProjectFilter.ALL) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var draftTitle by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }

    val project = openProject
    if (project != null) {
        ProjectDetailView(
            vm,
            project,
            onBack = { openProject = null },
            onEdit = {
                draftTitle = project.title
                editing = project
            },
            onDelete = { deleting = project },
        )
    } else {

    ProjectsScaffold(
        title = "WORKSPACE REGISTRY",
        onRefresh = vm::refreshProjects,
        onCreate = {
            draftTitle = ""
            creating = true
        },
    ) {
        Column(Modifier.fillMaxSize()) {
        if (!actionMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        actionMessage.orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = vm::clearProjectsMessage) { Text("Dismiss") }
                }
            }
        }
        Box(Modifier.weight(1f)) {
        when (val r = result) {
            ProjectsResult.Loading -> Centered { CircularProgressIndicator(Modifier.size(28.dp)) }
            ProjectsResult.Empty -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = LocalJarvisAccents.current.orbGlow.copy(alpha = 0.12f),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.HistoryEdu,
                                contentDescription = null,
                                tint = LocalJarvisAccents.current.orbGlow,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No Workspaces Yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF1F5F9),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Projects appear when your JARVIS PC organizes active narratives, stories, and conversations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            is ProjectsResult.Error -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        r.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = vm::refreshProjects) { Text("Retry") }
                }
            }
            is ProjectsResult.Loaded -> {
                val filteredProjects = remember(r.projects, selectedFilter) {
                    when (selectedFilter) {
                        ProjectFilter.ALL -> r.projects
                        ProjectFilter.WRITING -> r.projects.filter { it.kind == ProjectKind.WRITING_ROOM }
                        ProjectFilter.ACTIVE -> r.projects.filter { it.state == ProjectState.ACTIVE }
                        ProjectFilter.ARCHIVED -> r.projects.filter { it.state == ProjectState.ARCHIVED }
                    }
                }
                val writingCount = remember(r.projects) {
                    r.projects.count { it.kind == ProjectKind.WRITING_ROOM }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0x990E182A),
                            border = BorderStroke(1.dp, LocalJarvisAccents.current.orbGlow.copy(alpha = 0.20f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        "CONNECTED WORKSPACES",
                                        style = HudTextStyle,
                                        color = LocalJarvisAccents.current.orbGlow,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${r.projects.size} total · $writingCount story room${if (writingCount != 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFCBD5E1),
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = JarvisGreen.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, JarvisGreen.copy(alpha = 0.4f)),
                                ) {
                                    Text(
                                        "LIVE SYNC",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = HudTextStyle.copy(fontSize = 10.sp),
                                        color = JarvisGreen,
                                    )
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
                            ProjectFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = {
                                        Text(
                                            filter.label,
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

                    if (filteredProjects.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No projects matching \"${selectedFilter.label}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(filteredProjects, key = { it.id.value }) { p ->
                            ProjectRow(
                                p,
                                onClick = { openProject = p },
                                onEdit = {
                                    draftTitle = p.title
                                    editing = p
                                },
                                onDelete = { deleting = p },
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

    if (creating || editing != null) {
        val target = editing
        AlertDialog(
            onDismissRequest = {
                creating = false
                editing = null
            },
            title = { Text(if (target == null) "New project" else "Rename project") },
            text = {
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = jarvisTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (target == null) vm.createProject(draftTitle)
                        else vm.renameProject(target.id, draftTitle)
                        creating = false
                        editing = null
                    },
                    enabled = draftTitle.isNotBlank(),
                ) { Text(if (target == null) "Create" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    creating = false
                    editing = null
                }) { Text("Cancel") }
            },
        )
    }
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete project") },
            text = {
                Text("Ask the server to delete ${target.title}? Canon stays unchanged unless the server accepts the delete.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProject(target.id)
                    if (openProject?.id == target.id) openProject = null
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProjectDetailView(
    vm: JarvisViewModel,
    project: ProjectSummary,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val conversations by vm.conversationsFor(project.id).collectAsStateWithLifecycle(initialValue = null)
    if (project.kind == ProjectKind.WRITING_ROOM) {
        WritingWorkspaceV1Screen(
            vm = vm,
            projectId = project.id.value,
            title = project.title,
            onBack = onBack,
            onEdit = onEdit,
            onDelete = onDelete,
        )
        return
    }
    ProjectsScaffold(title = project.title.uppercase(), onBack = onBack) {
        val list = conversations
        if (list == null) {
            Centered { CircularProgressIndicator(Modifier.size(28.dp)) }
        } else if (list.isEmpty()) {
            Centered {
                Text(
                    "No conversations linked to this project yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.conversationId }) { c ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                c.conversationId,
                                style = HudTextStyle,
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
private fun ProjectRow(
    project: ProjectSummary,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accents = LocalJarvisAccents.current
    val isWritingRoom = project.kind == ProjectKind.WRITING_ROOM

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC111E33),
        border = BorderStroke(
            1.dp,
            if (isWritingRoom) Brush.horizontalGradient(
                listOf(
                    accents.orbGlow.copy(alpha = 0.45f),
                    JarvisViolet.copy(alpha = 0.35f),
                )
            ) else Brush.horizontalGradient(
                listOf(
                    accents.orbGlow.copy(alpha = 0.16f),
                    Color(0x112A3B57),
                )
            )
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (isWritingRoom) JarvisViolet.copy(alpha = 0.22f) else accents.orbGlow.copy(alpha = 0.14f),
                border = BorderStroke(
                    1.dp,
                    if (isWritingRoom) JarvisViolet.copy(alpha = 0.5f) else accents.orbGlow.copy(alpha = 0.3f)
                ),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isWritingRoom) Icons.Filled.AutoStories else Icons.Filled.Folder,
                        contentDescription = null,
                        tint = if (isWritingRoom) Color(0xFFC084FC) else accents.orbGlow,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        project.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF8FAFC),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isWritingRoom) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = JarvisGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, JarvisGreen.copy(alpha = 0.4f)),
                        ) {
                            Text(
                                "STUDIO",
                                style = HudTextStyle.copy(fontSize = 9.sp),
                                color = JarvisGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            isWritingRoom -> buildString {
                                append(project.storyId.ifBlank { "WRITING ROOM" })
                                project.chapterNumber?.let { append(" · CH $it") }
                                project.snapshotDate?.let { append(" · $it") }
                            }
                            project.state == ProjectState.ACTIVE -> "ACTIVE WORKSPACE"
                            else -> "ARCHIVED"
                        },
                        style = HudTextStyle.copy(fontSize = 11.sp),
                        color = when {
                            isWritingRoom -> JarvisCyan
                            project.state == ProjectState.ACTIVE -> accents.online
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename project", tint = JarvisCyan)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete project", tint = MaterialTheme.colorScheme.error)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectsScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onCreate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AmbientBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (onEdit != null) IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = "Rename project")
                        }
                        if (onDelete != null) IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete project")
                        }
                        if (onCreate != null) IconButton(onClick = onCreate) {
                            Icon(Icons.Filled.Add, contentDescription = "New project")
                        }
                        if (onRefresh != null) TextButton(onClick = onRefresh) { Text("Refresh") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color(0xFFE8EEF8),
                        navigationIconContentColor = Color(0xFFE8EEF8),
                        actionIconContentColor = Color(0xFF22D3EE),
                    ),
                    windowInsets = WindowInsets(0),
                )
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) { content() }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}
