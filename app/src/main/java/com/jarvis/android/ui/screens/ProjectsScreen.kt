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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.BuildConfig
import com.jarvis.android.data.projects.ProjectActivity
import com.jarvis.android.data.projects.ProjectConversation
import com.jarvis.android.data.projects.ProjectState
import com.jarvis.android.data.projects.ProjectSummary
import com.jarvis.android.data.projects.ProjectsResult
import com.jarvis.android.data.projects.ProjectsWorkspace
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.home.JarvisVisualState
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors

/**
 * AND-W9 / A5 Projects shell. Backend is NOT_CONNECTED. Data comes from
 * [com.jarvis.android.data.projects.FakeProjectsRepository] behind
 * [com.jarvis.android.data.projects.ProjectsRepository]. Release hides
 * fixture claims. No server behavior is asserted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(vm: JarvisViewModel) {
    val result by vm.projects.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var openProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var query by remember { mutableStateOf(ProjectsWorkspace.Query()) }

    if (!BuildConfig.DEBUG) {
        ProjectsScaffold(title = "PROJECTS") {
            Centered {
                JarvisOrb(size = 110.dp, visualState = JarvisVisualState.OFFLINE)
                Spacer(Modifier.height(16.dp))
                Text(
                    ProjectsA11y.RELEASE_HIDDEN,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { contentDescription = ProjectsA11y.RELEASE_HIDDEN },
                )
            }
        }
        return
    }

    val project = openProject
    if (project != null) {
        ProjectDetailView(vm, project, connection = snapshot.connection, onBack = { openProject = null })
        return
    }

    val surface = ProjectsWorkspace.surface(true, result, snapshot.connection)
    val visible = ProjectsWorkspace.visibleProjects(true, result, query)

    ProjectsScaffold(title = "PROJECTS", onRefresh = vm::refreshProjects) {
        Column(Modifier.fillMaxSize()) {
            Text(
                ProjectsA11y.NOT_LIVE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .semantics { contentDescription = ProjectsA11y.SCREEN },
            )
            OutlinedTextField(
                value = query.text,
                onValueChange = { query = query.copy(text = it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .semantics { contentDescription = ProjectsA11y.SEARCH },
                placeholder = { Text("Search local fixtures") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = jarvisTextFieldColors(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = query.filter == ProjectsWorkspace.Filter.ALL,
                    onClick = { query = query.copy(filter = ProjectsWorkspace.Filter.ALL) },
                    label = { Text("All") },
                    modifier = Modifier.semantics { contentDescription = ProjectsA11y.FILTER_ALL },
                )
                FilterChip(
                    selected = query.filter == ProjectsWorkspace.Filter.ACTIVE,
                    onClick = { query = query.copy(filter = ProjectsWorkspace.Filter.ACTIVE) },
                    label = { Text("Active") },
                    modifier = Modifier.semantics { contentDescription = ProjectsA11y.FILTER_ACTIVE },
                )
                FilterChip(
                    selected = query.filter == ProjectsWorkspace.Filter.ARCHIVED,
                    onClick = { query = query.copy(filter = ProjectsWorkspace.Filter.ARCHIVED) },
                    label = { Text("Archived") },
                    modifier = Modifier.semantics { contentDescription = ProjectsA11y.FILTER_ARCHIVED },
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        query = query.copy(
                            sort = if (query.sort == ProjectsWorkspace.Sort.UPDATED_DESC) {
                                ProjectsWorkspace.Sort.TITLE_ASC
                            } else {
                                ProjectsWorkspace.Sort.UPDATED_DESC
                            },
                        )
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (query.sort == ProjectsWorkspace.Sort.UPDATED_DESC) {
                            ProjectsA11y.SORT_UPDATED
                        } else {
                            ProjectsA11y.SORT_TITLE
                        }
                    },
                ) {
                    Text(if (query.sort == ProjectsWorkspace.Sort.UPDATED_DESC) "Updated" else "Title")
                }
            }
            when (surface) {
                ProjectsWorkspace.Surface.LOADING -> Centered {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(ProjectsA11y.LOADING, style = MaterialTheme.typography.bodyMedium)
                }
                ProjectsWorkspace.Surface.EMPTY -> Centered {
                    JarvisOrb(size = 96.dp, visualState = JarvisVisualState.IDLE)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        ProjectsA11y.EMPTY,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                ProjectsWorkspace.Surface.ERROR -> Centered {
                    JarvisOrb(size = 96.dp, visualState = JarvisVisualState.ERROR)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        ProjectsA11y.ERROR,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = vm::refreshProjects) { Text("Retry") }
                }
                ProjectsWorkspace.Surface.OFFLINE -> Centered {
                    JarvisOrb(size = 96.dp, visualState = JarvisVisualState.OFFLINE)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        ProjectsA11y.OFFLINE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = vm::refreshProjects) { Text("Retry") }
                }
                ProjectsWorkspace.Surface.LOADED -> {
                    if (visible.isEmpty()) {
                        Centered {
                            Text(
                                ProjectsA11y.EMPTY_SEARCH,
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
                            items(visible, key = { it.id.value }) { p ->
                                ProjectCard(p, onClick = { openProject = p })
                            }
                        }
                    }
                }
                ProjectsWorkspace.Surface.HIDDEN -> Unit
            }
        }
    }
}

@Composable
private fun ProjectDetailView(
    vm: JarvisViewModel,
    project: ProjectSummary,
    connection: ConnectionState,
    onBack: () -> Unit,
) {
    val conversations by vm.conversationsFor(project.id).collectAsStateWithLifecycle(initialValue = null)
    val activity by vm.activityFor(project.id).collectAsStateWithLifecycle(
        initialValue = ProjectActivity.FIXTURE_PLACEHOLDER,
    )
    var chatQuery by remember { mutableStateOf("") }
    val offline = connection == ConnectionState.OFFLINE || connection == ConnectionState.DISCONNECTED

    ProjectsScaffold(title = project.title.uppercase(), onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            Text(
                ProjectsA11y.NOT_LIVE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .semantics { contentDescription = ProjectsA11y.DETAIL },
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(project.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (project.state == ProjectState.ACTIVE) "ACTIVE" else "ARCHIVED",
                        style = HudTextStyle,
                        color = LocalJarvisAccents.current.orbGlow,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "ID · ${ProjectsWorkspace.sanitizedId(project.id.value)}",
                        style = HudTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "UPDATED · ${ProjectsWorkspace.formatTimestamp(project.updatedAtMs)}",
                        style = HudTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        ProjectsWorkspace.conversationCountLabel(project.conversationCount),
                        style = HudTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("ACTIVITY", style = HudTextStyle, color = LocalJarvisAccents.current.degraded)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        activity.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { contentDescription = ProjectsA11y.ACTIVITY_PLACEHOLDER },
                    )
                    if (offline) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            ProjectsA11y.OFFLINE,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = chatQuery,
                onValueChange = { chatQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                placeholder = { Text("Search linked chats") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = jarvisTextFieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            val loadedConversations = conversations
            when {
                loadedConversations == null -> Centered { CircularProgressIndicator(Modifier.size(28.dp)) }
                loadedConversations.isEmpty() -> Centered {
                    Text(
                        ProjectsA11y.NO_CONVERSATIONS,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    val filtered = ProjectsWorkspace.searchConversations(loadedConversations, chatQuery)
                    if (filtered.isEmpty()) {
                        Centered {
                            Text(
                                ProjectsA11y.EMPTY_SEARCH,
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
                            items(filtered, key = { it.conversationId }) { c ->
                                ProjectConversationRow(c)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectConversationRow(conversation: ProjectConversation) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = ProjectsA11y.conversation(conversation.title) },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(conversation.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "ID · ${ProjectsWorkspace.sanitizedId(conversation.conversationId)}",
                style = HudTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                ProjectsWorkspace.formatTimestamp(conversation.updatedAtMs),
                style = HudTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectSummary, onClick: () -> Unit) {
    val accents = LocalJarvisAccents.current
    val chats = ProjectsWorkspace.conversationCountLabel(project.conversationCount)
    val stateLabel = if (project.state == ProjectState.ACTIVE) "ACTIVE" else "ARCHIVED"
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = ProjectsA11y.card(project.title, stateLabel, chats) },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape),
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
                    stateLabel,
                    style = HudTextStyle,
                    color = if (project.state == ProjectState.ACTIVE) accents.online
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    chats,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                ProjectsA11y.NOT_LIVE,
                style = HudTextStyle,
                color = accents.degraded,
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) { content() }
    }
}
