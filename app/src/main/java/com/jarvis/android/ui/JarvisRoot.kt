package com.jarvis.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.jarvis.android.ui.screens.SessionReauthScreen
import com.jarvis.android.ui.screens.TasksScreen
import com.jarvis.android.ui.screens.UsageScreen
import com.jarvis.android.ui.screens.WelcomeScreen
import com.jarvis.android.ui.shared.ConnectionBanner
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisCyanBright
import com.jarvis.android.ui.theme.LocalReducedMotion

sealed class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Conversations : TopLevelDestination("conversations", "Chat", Icons.Outlined.ChatBubbleOutline)
    data object Approvals : TopLevelDestination("approvals", "Approvals", Icons.Outlined.VerifiedUser)
    data object Tasks : TopLevelDestination("tasks", "Tasks", Icons.Outlined.Checklist)
    data object Settings : TopLevelDestination("settings", "Settings", Icons.Outlined.Settings)

    /** Projects is the production entry point for Writing Room and other live workspaces. */
    data object Projects : TopLevelDestination("projects", "Projects", Icons.Outlined.MenuBook)

    fun localizedLabel(strings: AppStrings): String = when (this) {
        Conversations -> strings.navChat
        Projects -> strings.navProjects
        Approvals -> strings.navApprovals
        Tasks -> strings.navTasks
        Settings -> strings.navSettings
    }

    companion object {
        /**
         * Production navigation must expose Projects because Writing Room is
         * routed through ProjectsScreen. Keep this list build-type invariant:
         * hiding Projects in release makes the signed app lose Writing Room.
         */
        val all: List<TopLevelDestination>
            get() = listOf(Conversations, Projects, Approvals, Tasks, Settings)
    }
}

/** Which full-screen surface owns the window right now. */
private enum class Shell { BOOT, LOCKED, REAUTH, PAIRING, MAIN }

@Composable
fun JarvisRoot(
    app: JarvisApp,
    openConversationRequest: kotlinx.coroutines.flow.StateFlow<Long>? = null,
    onSensitiveScreenChanged: (Boolean) -> Unit = {},
) {
    val vm: JarvisViewModel = viewModel(factory = JarvisViewModelFactory(app))

    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val liveAuth by vm.liveAuth.collectAsStateWithLifecycle()
    val bootShown by vm.bootShown.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val relock by app.relockRequested.collectAsStateWithLifecycle()

    LaunchedEffect(relock) {
        if (relock) {
            vm.relock()
            app.consumeRelock()
        }
    }

    // Revocation/protocol mismatch still fail closed. Ordinary access-token
    // expiry is recoverable only when this install has a device credential.
    val hardInvalid = snapshot.phase == SessionPhase.REVOKED ||
        snapshot.phase == SessionPhase.MISMATCH
    val authExpired = snapshot.phase == SessionPhase.AUTH_EXPIRED ||
        !vm.liveAuthenticated
    val reauthRecovered = liveAuth is JarvisViewModel.LiveAuthState.Authed &&
        vm.liveAuthenticated
    val canReauth = settings.paired &&
        !hardInvalid &&
        authExpired &&
        !reauthRecovered &&
        vm.canBiometricReauth

    val shell = when {
        !bootShown -> Shell.BOOT
        settings.appLockEnabled && !unlocked -> Shell.LOCKED
        canReauth -> Shell.REAUTH
        !settings.paired || !vm.liveAuthenticated || hardInvalid -> Shell.PAIRING
        else -> Shell.MAIN
    }

    LaunchedEffect(shell) {
        onSensitiveScreenChanged(shell != Shell.MAIN)
    }

    val currentStrings = resolveAppStrings(settings.appLanguage)
    val shellTransitionMs = if (settings.reducedMotion) 0 else 180

    CompositionLocalProvider(
        LocalReducedMotion provides settings.reducedMotion,
        LocalAppStrings provides currentStrings,
    ) {
        AnimatedContent(
            targetState = shell,
            transitionSpec = {
                (fadeIn(tween(shellTransitionMs)) + scaleIn(
                    tween(shellTransitionMs),
                    initialScale = if (settings.reducedMotion) 1f else 0.99f,
                )).togetherWith(fadeOut(tween(shellTransitionMs)))
            },
            label = "shell",
        ) { target ->
            when (target) {
                Shell.BOOT -> WelcomeScreen(
                    ownerName = settings.ownerName.takeIf { it.isNotBlank() },
                    onFinished = vm::onBootFinished,
                )

                Shell.LOCKED -> LockScreen(onUnlocked = vm::onUnlocked)

                Shell.REAUTH -> SessionReauthScreen(vm)

                Shell.PAIRING -> PairingScreen(
                    vm = vm,
                    lockedReason = when (snapshot.phase) {
                        SessionPhase.REVOKED -> currentStrings.revokedReason
                        SessionPhase.AUTH_EXPIRED -> currentStrings.expiredSessionReason
                        SessionPhase.MISMATCH -> currentStrings.protocolMismatchReason
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
    val screenTransitionMs = if (settings.reducedMotion) 0 else 160
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
                if (!imeVisible) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                    ) {
                        TopLevelDestination.all.forEach { dest ->
                            val badge = when (dest) {
                                TopLevelDestination.Approvals -> pendingApprovals
                                TopLevelDestination.Tasks -> runningTasks
                                else -> 0
                            }
                            val destinationLabel = dest.localizedLabel(strings)
                            val isSelected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    BadgedBox(badge = {
                                        if (badge > 0) Badge { Text(badge.toString()) }
                                    }) {
                                        Icon(dest.icon, contentDescription = null)
                                    }
                                },
                                label = { Text(destinationLabel, maxLines = 1) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = JarvisCyanBright,
                                    selectedTextColor = JarvisCyanBright,
                                    indicatorColor = JarvisCyan.copy(alpha = 0.15f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Conversations.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(tween(screenTransitionMs)) },
                exitTransition = { fadeOut(tween(screenTransitionMs)) },
            ) {
                composable(TopLevelDestination.Conversations.route) {
                    ConversationsScreen(vm, openConversationRequest = openConversationNonce)
                }
                composable(TopLevelDestination.Projects.route) { ProjectsScreen(vm) }
                composable(TopLevelDestination.Approvals.route) { ApprovalsScreen(vm, isOwner = isOwner) }
                composable(TopLevelDestination.Tasks.route) { TasksScreen(vm) }
                composable(TopLevelDestination.Settings.route) {
                    SettingsScreen(
                        vm = vm,
                        onOpenUsage = { navController.navigate("settings/usage") },
                    )
                }
                composable("settings/usage") {
                    UsageScreen(vm = vm, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
