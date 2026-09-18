package com.jarvis.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.BuildConfig
import com.jarvis.android.data.local.ConversationListItem
import com.jarvis.android.data.projects.ProjectSummary
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.TopLevelDestination
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.home.FixtureProjectsSection
import com.jarvis.android.ui.home.HomeSnapshot
import com.jarvis.android.ui.home.hudLabel
import com.jarvis.android.ui.theme.AdaptiveContent
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisVisualSystem
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.LocalUiDensity
import com.jarvis.android.ui.theme.rememberAdaptiveLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: JarvisViewModel,
    onNavigate: (String) -> Unit,
) {
    val home by vm.home.collectAsStateWithLifecycle()
    val accents = LocalJarvisAccents.current
    val density = LocalUiDensity.current
    val layout = rememberAdaptiveLayout()
    val pagePad = JarvisVisualSystem.pagePaddingDp(density).dp
    val sectionGap = JarvisVisualSystem.sectionGapDp(density).dp

    LaunchedEffect(Unit) { vm.refreshChatAccess() }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color(0xFFE8EEF8),
                    navigationIconContentColor = Color(0xFFE8EEF8),
                    actionIconContentColor = Color(0xFFE8EEF8),
                ),
                windowInsets = WindowInsets(0),
            )
        },
    ) { pad ->
        AdaptiveContent(Modifier.padding(pad)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pagePad, vertical = 8.dp)
                .semantics { contentDescription = HomeA11y.SCREEN },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            JarvisOrb(
                size = layout.orbHeroDp.dp,
                visualState = home.visualState,
                contentDescription = HomeA11y.visualState(home.visualState.hudLabel()),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                home.visualState.hudLabel(),
                style = HudTextStyle,
                color = accents.orbGlow,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                connectionLabel(home.connection),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(sectionGap))
            StatusRow(home)
            Spacer(Modifier.height(sectionGap))
            QuickNavRow(onNavigate = onNavigate)
            Spacer(Modifier.height(sectionGap))
            RecentConversations(
                items = home.recentConversations,
                onOpenList = { onNavigate(TopLevelDestination.Conversations.route) },
                onOpenChat = { id ->
                    vm.openConversationAndShowChat(id)
                    onNavigate(TopLevelDestination.Conversations.route)
                },
            )
            Spacer(Modifier.height(14.dp))
            FixtureProjectsCard(
                section = home.fixtureProjects,
                onOpen = { onNavigate(TopLevelDestination.Projects.route) },
            )
            Spacer(Modifier.height(14.dp))
            CompactDiagnosticsCard(home)
            Spacer(Modifier.height(20.dp))
        }
        }
    }
}

@Composable
private fun StatusRow(home: HomeSnapshot) {
    val accents = LocalJarvisAccents.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusTile(
            modifier = Modifier.weight(1f),
            title = "MODEL",
            value = home.profile.label ?: "UNKNOWN",
            caption = when {
                home.profile.label == null -> HomeA11y.PROFILE_UNKNOWN
                home.profile.model != null -> home.profile.model
                else -> "Server catalog"
            },
            tint = if (home.profile.label == null) accents.degraded else accents.orbGlow,
        )
        StatusTile(
            modifier = Modifier.weight(1f),
            title = "TASKS",
            value = home.runningTaskCount.toString(),
            caption = if (home.runningTaskCount == 0) HomeA11y.TASKS_EMPTY else HomeA11y.runningTasks(home.runningTaskCount),
            tint = accents.orbGlow,
        )
        val pending = home.pendingApprovalCount
        StatusTile(
            modifier = Modifier.weight(1f),
            title = "APPROVALS",
            value = pending?.toString() ?: "—",
            caption = if (pending == null) HomeA11y.APPROVALS_OWNER_ONLY else HomeA11y.pendingApprovals(pending),
            tint = if (pending == null) accents.offline else accents.degraded,
        )
    }
}

@Composable
private fun StatusTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    caption: String,
    tint: Color,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(JarvisVisualSystem.cardRadiusDp(LocalUiDensity.current).dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Column(Modifier.padding(JarvisVisualSystem.cardPaddingDp(LocalUiDensity.current).dp)) {
            Text(title, style = HudTextStyle, color = tint)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun QuickNavRow(onNavigate: (String) -> Unit) {
    val destinations = buildList {
        add(TopLevelDestination.Conversations to HomeA11y.QUICK_CHAT)
        add(TopLevelDestination.Tasks to HomeA11y.QUICK_TASKS)
        add(TopLevelDestination.Approvals to HomeA11y.QUICK_APPROVALS)
        if (BuildConfig.DEBUG) add(TopLevelDestination.Projects to HomeA11y.QUICK_PROJECTS)
        add(TopLevelDestination.Settings to HomeA11y.QUICK_SETTINGS)
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(destinations, key = { it.first.route }) { (dest, a11y) ->
            Surface(
                onClick = { onNavigate(dest.route) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.semantics { contentDescription = a11y },
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(quickNavIcon(dest), contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(dest.label, style = HudTextStyle)
                }
            }
        }
    }
}

private fun quickNavIcon(dest: TopLevelDestination): ImageVector = when (dest) {
    TopLevelDestination.Home -> Icons.AutoMirrored.Filled.Chat
    TopLevelDestination.Conversations -> Icons.AutoMirrored.Filled.Chat
    TopLevelDestination.Tasks -> Icons.AutoMirrored.Filled.List
    TopLevelDestination.Approvals -> Icons.Filled.Person
    TopLevelDestination.Projects -> Icons.Filled.Folder
    TopLevelDestination.Settings -> Icons.Filled.Settings
}

@Composable
private fun RecentConversations(
    items: List<ConversationListItem>,
    onOpenList: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(JarvisVisualSystem.cardRadiusDp(LocalUiDensity.current).dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(JarvisVisualSystem.cardPaddingDp(LocalUiDensity.current).dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("RECENT CHATS", style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
                TextButton(onClick = onOpenList) { Text("All") }
            }
            if (items.isEmpty()) {
                Text(
                    HomeA11y.CONVERSATIONS_EMPTY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                items.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(item.conversationId) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.displayTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            if (item.pinned) {
                                Text(
                                    ConversationA11y.LOCAL_ONLY_HINT,
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
}

@Composable
private fun FixtureProjectsCard(
    section: FixtureProjectsSection,
    onOpen: () -> Unit,
) {
    if (section is FixtureProjectsSection.Hidden) return
    Surface(
        shape = RoundedCornerShape(JarvisVisualSystem.cardRadiusDp(LocalUiDensity.current).dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(JarvisVisualSystem.cardPaddingDp(LocalUiDensity.current).dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PROJECTS", style = HudTextStyle, color = LocalJarvisAccents.current.degraded)
                TextButton(onClick = onOpen) { Text("Open") }
            }
            Text(
                HomeA11y.PROJECTS_NOT_LIVE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            when (section) {
                FixtureProjectsSection.Loading -> Text("Loading local fixtures…", style = MaterialTheme.typography.bodyMedium)
                FixtureProjectsSection.Empty -> Text("No local fixtures on this device.", style = MaterialTheme.typography.bodyMedium)
                FixtureProjectsSection.Unavailable -> Text("Local fixtures unavailable.", style = MaterialTheme.typography.bodyMedium)
                is FixtureProjectsSection.Fixtures -> section.projects.forEach { p: ProjectSummary ->
                    Text(p.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
                }
                FixtureProjectsSection.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun CompactDiagnosticsCard(home: HomeSnapshot) {
    val d = home.diagnostics
    Surface(
        shape = RoundedCornerShape(JarvisVisualSystem.cardRadiusDp(LocalUiDensity.current).dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(JarvisVisualSystem.cardPaddingDp(LocalUiDensity.current).dp)) {
            Text("DIAGNOSTICS", style = HudTextStyle, color = LocalJarvisAccents.current.orbGlow)
            Spacer(Modifier.height(8.dp))
            Text("PHASE ${d.sessionPhase}", style = HudTextStyle)
            Text("PROTO ${d.protocolVersion ?: "UNKNOWN"}", style = HudTextStyle)
            Text("ACTIVE ${d.activeRequestCount}", style = HudTextStyle)
            Text("LAST ${d.lastKind?.name ?: "NONE"}", style = HudTextStyle)
            if (d.protocolVersion == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    HomeA11y.DIAGNOSTICS_UNKNOWN_PROTOCOL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.ONLINE -> "Link online"
    ConnectionState.DEGRADED -> "Link degraded"
    ConnectionState.CONNECTING -> "Connecting"
    ConnectionState.RECONNECTING -> "Reconnecting"
    ConnectionState.OFFLINE -> "Offline"
    ConnectionState.DISCONNECTED -> "Disconnected"
    ConnectionState.AUTH_EXPIRED -> "Auth required"
    ConnectionState.DEVICE_REVOKED -> "Device revoked"
    ConnectionState.PROTOCOL_MISMATCH -> "Update required"
}
