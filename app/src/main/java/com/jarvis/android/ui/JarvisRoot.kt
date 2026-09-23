package com.jarvis.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvis.android.JarvisApp
import com.jarvis.android.data.repo.SessionPhase
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.i18n.AppStrings
import com.jarvis.android.ui.i18n.LocalAppStrings
import com.jarvis.android.ui.i18n.resolveAppStrings
import com.jarvis.android.ui.screens.ApprovalsScreen
import com.jarvis.android.ui.screens.ConversationsScreen
import com.jarvis.android.ui.screens.LockScreen
import com.jarvis.android.ui.screens.PairingScreen
import com.jarvis.android.ui.screens.ProjectsScreen
import com.jarvis.android.ui.screens.SettingsScreen
import com.jarvis.android.ui.screens.TasksScreen
import com.jarvis.android.ui.screens.WelcomeScreen
import com.jarvis.android.ui.shared.ConnectionBanner
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisCyanBright
import com.jarvis.android.ui.theme.LocalReducedMotion

sealed class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Conversations : TopLevelDestination("conversations", "Chat", Icons.AutoMirrored.Filled.Chat)
    data object Approvals : TopLevelDestination("approvals", "Approvals", Icons.Filled.VerifiedUser)
    data object Tasks : TopLevelDestination("tasks", "Tasks", Icons.Filled.Checklist)
    data object Settings : TopLevelDestination("settings", "Settings", Icons.Filled.Tune)

    /** AND-W9 shell: fake repository until PC-A publishes the projects contract. */
    data object Projects : TopLevelDestination("projects", "Projects", Icons.Filled.AutoStories)

    fun localizedLabel(strings: AppStrings): String = when (this) {
        Conversations -> strings.navChat
        Projects -> strings.navProjects
        Approvals -> strings.navApprovals
        Tasks -> strings.navTasks
        Settings -> strings.navSettings
    }

    companion object {
        /**
         * Getter, not a val: a companion val can capture nulls before the
         * nested objects exist. Projects is a debug-only preview until PC-A
         * publishes the projects contract (the shell runs on fixture data, so
         * release users must not see it as if it were real).
         */
        val all: List<TopLevelDestination>
            get() = if (com.jarvis.android.BuildConfig.DEBUG)
                listOf(Conversations, Projects, Approvals, Tasks, Settings)
            else listOf(Conversations, Approvals, Tasks, Settings)
    }
}

/** Which full-screen surface owns the window right now. */
private enum class Shell { BOOT, LOCKED, PAIRING, MAIN }

@Composable
fun JarvisRoot(
    app: JarvisApp,
    openConversationRequest: kotlinx.coroutines.flow.StateFlow<Long>? = null,
    onSensitiveScreenChanged: (Boolean) -> Unit = {},
) {
    val vm: JarvisViewModel = viewModel(factory = JarvisViewModelFactory(app))

    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bootShown by vm.bootShown.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val relock by app.relockRequested.collectAsStateWithLifecycle()

    LaunchedEffect(relock) {
        if (relock) {
            vm.relock()
            app.consumeRelock()
        }
    }

    // Fail closed: revoked / expired / protocol-mismatch lose the chat
    // surface. Re-pair (or update + re-pair) is the only recovery — never a
    // silent reconnect from MAIN.
    val credentialsInvalid = snapshot.phase == SessionPhase.REVOKED ||
        snapshot.phase == SessionPhase.AUTH_EXPIRED ||
        snapshot.phase == SessionPhase.MISMATCH

    val shell = when {
        !bootShown -> Shell.BOOT
        settings.appLockEnabled && !unlocked -> Shell.LOCKED
        !settings.paired || !vm.liveAuthenticated || credentialsInvalid -> Shell.PAIRING
        else -> Shell.MAIN
    }

    LaunchedEffect(shell) {
        onSensitiveScreenChanged(shell != Shell.MAIN)
    }

    val currentStrings = resolveAppStrings(settings.appLanguage)

    CompositionLocalProvider(
        LocalReducedMotion provides settings.reducedMotion,
        LocalAppStrings provides currentStrings,
    ) {
        AnimatedContent(
            targetState = shell,
            transitionSpec = {
                (fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 0.96f))
                    .togetherWith(fadeOut(tween(240)))
            },
            label = "shell",
        ) { target ->
            when (target) {
                Shell.BOOT -> WelcomeScreen(
                    ownerName = settings.ownerName.takeIf { it.isNotBlank() },
                    onFinished = vm::onBootFinished,
                )

                Shell.LOCKED -> LockScreen(onUnlocked = vm::onUnlocked)

                Shell.PAIRING -> PairingScreen(
                    vm = vm,
                    lockedReason = when (snapshot.phase) {
                        SessionPhase.REVOKED -> "This device was revoked on the Jarvis PC. Pair again to restore access."
                        SessionPhase.AUTH_EXPIRED -> "Your session expired. Pair again to restore access."
                        SessionPhase.MISMATCH -> "This app is out of date for the Jarvis PC protocol. Update, then pair again."
                        else -> null
                    },
                )

                Shell.MAIN -> MainShell(vm, openConversationRequest)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainShell(
    vm: JarvisViewModel,
    openConversationRequest: kotlinx.coroutines.flow.StateFlow<Long>? = null,
) {
    val strings = LocalAppStrings.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isOwner = settings.isOwner
    val imeVisible = WindowInsets.isImeVisible
    val openConversationNonce by (openConversationRequest
        ?: kotlinx.coroutines.flow.MutableStateFlow(0L)).collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.seedDemoIfEmpty() }
    LaunchedEffect(openConversationNonce) {
        if (openConversationNonce > 0L) {
            navController.navigate(TopLevelDestination.Conversations.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Non-owners never see pending-approval counts (or the cards themselves).
    val pendingApprovals = if (isOwner) {
        snapshot.session.approvals.values.count { it.outcome == null }
    } else 0
    val runningTasks = snapshot.session.tasks.values.count {
        it.status == com.jarvis.android.contract.TaskStatus.RUNNING ||
            it.status == com.jarvis.android.contract.TaskStatus.STARTED ||
            it.status == com.jarvis.android.contract.TaskStatus.QUEUED
    }

    AmbientBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { ConnectionBanner(snapshot, onReconnect = vm::reconnect) },
            bottomBar = {
                AnimatedVisibility(
                    visible = !imeVisible,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
                    exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Color(0xDD0D1626),
                            border = BorderStroke(
                                1.dp,
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0x3322D3EE),
                                        Color(0x228B5CF6),
                                        Color(0x3322D3EE),
                                    )
                                )
                            ),
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TopLevelDestination.all.forEach { dest ->
                                    val badge = when (dest) {
                                        TopLevelDestination.Approvals -> pendingApprovals
                                        TopLevelDestination.Tasks -> runningTasks
                                        else -> 0
                                    }
                                    val destinationLabel = dest.localizedLabel(strings)
                                    val isSelected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                    val reduced = LocalReducedMotion.current
                                    val pulse by rememberInfiniteTransition(label = "navGlow").animateFloat(
                                        initialValue = 0.45f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            tween(1100, easing = LinearEasing),
                                            RepeatMode.Reverse,
                                        ),
                                        label = "pulse",
                                    )
                                    val glow = if (isSelected && !reduced) pulse else if (isSelected) 0.85f else 0f
                                    val tint = if (isSelected) JarvisCyanBright else JarvisCyan.copy(alpha = 0.72f)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    navController.navigate(dest.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                    ) {
                                        BadgedBox(badge = {
                                            if (badge > 0) Badge(
                                                containerColor = Color(0xFF22D3EE),
                                                contentColor = Color(0xFF041018),
                                            ) { Text(badge.toString()) }
                                        }) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .drawBehind {
                                                        if (glow <= 0f) return@drawBehind
                                                        drawCircle(
                                                            color = JarvisCyan.copy(alpha = 0.45f * glow),
                                                            radius = size.minDimension * 0.62f,
                                                        )
                                                    }
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) JarvisCyan.copy(alpha = 0.22f)
                                                        else JarvisCyan.copy(alpha = 0.08f),
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) JarvisCyanBright.copy(alpha = 0.55f + 0.45f * glow)
                                                        else JarvisCyan.copy(alpha = 0.35f),
                                                        CircleShape,
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    dest.icon,
                                                    contentDescription = destinationLabel,
                                                    tint = tint,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            destinationLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                letterSpacing = 0.1.sp,
                                            ),
                                            color = tint,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Conversations.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(160)) },
            ) {
                composable(TopLevelDestination.Conversations.route) {
                    ConversationsScreen(vm, openConversationRequest = openConversationNonce)
                }
                composable(TopLevelDestination.Projects.route) { ProjectsScreen(vm) }
                composable(TopLevelDestination.Approvals.route) { ApprovalsScreen(vm, isOwner = isOwner) }
                composable(TopLevelDestination.Tasks.route) { TasksScreen(vm) }
                composable(TopLevelDestination.Settings.route) { SettingsScreen(vm) }
            }
        }
    }
}
