package com.jarvis.android.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.data.projects.ProjectKind
import com.jarvis.android.data.projects.ProjectState
import com.jarvis.android.data.projects.ProjectSummary
import com.jarvis.android.data.projects.ProjectsResult
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents

/**
 * AND-W9 Projects shell (Lane F). Backend is NOT_CONNECTED; the data comes from
 * a fake repository behind the [com.jarvis.android.data.projects.ProjectsRepository]
 * seam, so the list/detail/loading/empty/error UI is real while the source is a
 * placeholder. No server behavior is asserted.
 */
@Composable
fun ProjectsScreen(vm: JarvisViewModel) {
    val result by vm.projects.collectAsStateWithLifecycle()
    var openProject by remember { mutableStateOf<ProjectSummary?>(null) }

    val project = openProject
    if (project != null) {
        ProjectDetailView(vm, project, onBack = { openProject = null })
        return
    }

    ProjectsScaffold(title = "PROJECTS", onRefresh = vm::refreshProjects) {
        when (val r = result) {
            ProjectsResult.Loading -> Centered { CircularProgressIndicator(Modifier.size(28.dp)) }
            ProjectsResult.Empty -> Centered {
                Text(
                    "No projects yet.\nProjects appear when your Jarvis PC organizes conversations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
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
            is ProjectsResult.Loaded -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(r.projects, key = { it.id.value }) { p ->
                    ProjectRow(p, onClick = { openProject = p })
                }
            }
        }
    }
}

@Composable
private fun ProjectDetailView(vm: JarvisViewModel, project: ProjectSummary, onBack: () -> Unit) {
    val conversations by vm.conversationsFor(project.id).collectAsStateWithLifecycle(initialValue = null)
    if (project.kind == ProjectKind.WRITING_ROOM) {
        ProjectsScaffold(title = project.title.uppercase(), onBack = onBack) {
            WritingRoomPreview(project.title)
        }
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
private fun ProjectRow(project: ProjectSummary, onClick: () -> Unit) {
    val accents = LocalJarvisAccents.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .padding(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = accents.orbGlow.copy(alpha = 0.14f),
                    modifier = Modifier.size(38.dp),
                ) {}
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(project.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        project.kind == ProjectKind.WRITING_ROOM -> "WRITING ROOM · PREVIEW"
                        project.state == ProjectState.ACTIVE -> "ACTIVE"
                        else -> "ARCHIVED"
                    },
                    style = HudTextStyle,
                    color = if (project.state == ProjectState.ACTIVE) accents.online
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectsScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AmbientBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
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
