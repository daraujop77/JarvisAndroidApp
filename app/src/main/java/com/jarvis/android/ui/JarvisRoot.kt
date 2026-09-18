package com.jarvis.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvis.android.JarvisApp
import com.jarvis.android.data.repo.SessionPhase
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.screens.ApprovalsScreen
import com.jarvis.android.ui.screens.ConversationsScreen
import com.jarvis.android.ui.screens.HomeScreen
import com.jarvis.android.ui.screens.LockScreen
import com.jarvis.android.ui.screens.PairingScreen
import com.jarvis.android.ui.screens.ProjectsScreen
import com.jarvis.android.ui.screens.SettingsScreen
import com.jarvis.android.ui.screens.TasksScreen
import com.jarvis.android.ui.screens.WelcomeScreen
import com.jarvis.android.ui.shared.ConnectionBanner
import com.jarvis.android.ui.theme.HudInk
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisTokens
import com.jarvis.android.ui.theme.JarvisVisualSystem
import com.jarvis.android.ui.theme.LocalAnimationIntensity
import com.jarvis.android.ui.theme.LocalReducedMotion
import com.jarvis.android.ui.theme.LocalUiDensity
import com.jarvis.android.ui.theme.rememberAdaptiveLayout
import com.jarvis.android.ui.theme.tokenColor

sealed class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : TopLevelDestination("home", "Home", Icons.Filled.Home)
    data object Conversations : TopLevelDestination("conversations", "Chat", Icons.AutoMirrored.Filled.Chat)
    data object Approvals : TopLevelDestination("approvals", "Approvals", Icons.Filled.Person)
    data object Tasks : TopLevelDestination("tasks", "Tasks", Icons.AutoMirrored.Filled.List)
    data object Settings : TopLevelDestination("settings", "Settings", Icons.Filled.Settings)

    /** AND-W9 shell: fake repository until PC-A publishes the projects contract. */
    data object Projects : TopLevelDestination("projects", "Projects", Icons.Filled.Folder)

    companion object {
        /**
         * Getter, not a val: a companion val can capture nulls before the
         * nested objects exist. Projects is a debug-only preview until PC-A
         * publishes the projects contract (the shell runs on fixture data, so
         * release users must not see it as if it were real).
         */
        val all: List<TopLevelDestination>
            get() = if (com.jarvis.android.BuildConfig.DEBUG)
                listOf(Home, Conversations, Projects, Approvals, Tasks, Settings)
            else listOf(Home, Conversations, Approvals, Tasks, Settings)
    }
}

/** Which full-screen surface owns the window right now. */
private enum class Shell { BOOT, LOCKED, PAIRING, MAIN }

@Composable
fun JarvisRoot(app: JarvisApp) {
    val vm: JarvisViewModel = viewModel(factory = JarvisViewModelFactory(app))

    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bootShown by vm.bootShown.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val relock by app.relockRequested.collectAsStateWithLifecycle()
    val voiceStop by app.voiceStopRequested.collectAsStateWithLifecycle()
    val appForeground by app.appInForeground.collectAsStateWithLifecycle()

    LaunchedEffect(appForeground) {
        vm.setVoiceAppForeground(appForeground)
        if (!appForeground) vm.stopVoice()
    }

    LaunchedEffect(voiceStop) {
        if (voiceStop > 0) vm.stopVoice()
    }

    LaunchedEffect(relock) {
        if (relock) {
            vm.stopVoice()
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
        !settings.paired || credentialsInvalid -> Shell.PAIRING
        else -> Shell.MAIN
    }

    val intensity = JarvisVisualSystem.intensityOf(settings.animationIntensity)
    val density = JarvisVisualSystem.densityOf(settings.uiDensity)
    val reduced = settings.reducedMotion
    val enterMs = JarvisVisualSystem.durationMs(reduced, intensity, 420)
    val exitMs = JarvisVisualSystem.durationMs(reduced, intensity, 240)

    CompositionLocalProvider(
        LocalReducedMotion provides reduced,
        LocalAnimationIntensity provides intensity,
        LocalUiDensity provides density,
    ) {
        AnimatedContent(
            targetState = shell,
            transitionSpec = {
                (fadeIn(tween(enterMs)) + scaleIn(tween(enterMs), initialScale = if (reduced) 1f else 0.96f))
                    .togetherWith(fadeOut(tween(exitMs)))
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

                Shell.MAIN -> MainShell(vm)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainShell(vm: JarvisViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isOwner = settings.isOwner
    val imeVisible = WindowInsets.isImeVisible
    val layout = rememberAdaptiveLayout()
    val reduced = LocalReducedMotion.current
    val intensity = LocalAnimationIntensity.current
    val navEnter = JarvisVisualSystem.durationMs(reduced, intensity, 220)
    val navExit = JarvisVisualSystem.durationMs(reduced, intensity, 160)

    LaunchedEffect(Unit) { vm.seedDemoIfEmpty() }

    val home by vm.home.collectAsStateWithLifecycle()
    // Non-owners never see pending-approval counts (or the cards themselves).
    val pendingApprovals = home.pendingApprovalCount ?: 0
    val runningTasks = home.runningTaskCount
    val navUnselected = tokenColor(JarvisTokens.NAV_UNSELECTED)
    val onNavigate: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    AmbientBackdrop(visualState = home.visualState) {
        if (layout.useNavigationRail) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = Color.Transparent, modifier = Modifier.fillMaxHeight()) {
                    TopLevelDestination.all.forEach { dest ->
                        val badge = when (dest) {
                            TopLevelDestination.Approvals -> pendingApprovals
                            TopLevelDestination.Tasks -> runningTasks
                            else -> 0
                        }
                        NavigationRailItem(
                            selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                            onClick = { onNavigate(dest.route) },
                            icon = {
                                BadgedBox(badge = {
                                    if (badge > 0) Badge { Text(badge.toString()) }
                                }) {
                                    Icon(dest.icon, contentDescription = dest.label)
                                }
                            },
                            label = { Text(dest.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = JarvisCyan,
                                selectedTextColor = HudInk,
                                unselectedIconColor = navUnselected,
                                unselectedTextColor = navUnselected,
                                indicatorColor = JarvisCyan.copy(alpha = 0.20f),
                            ),
                        )
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    topBar = { ConnectionBanner(snapshot, onReconnect = vm::reconnect) },
                ) { innerPadding ->
                    MainNavHost(
                        navController = navController,
                        vm = vm,
                        isOwner = isOwner,
                        modifier = Modifier.padding(innerPadding),
                        enterMs = navEnter,
                        exitMs = navExit,
                    )
                }
            }
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = { ConnectionBanner(snapshot, onReconnect = vm::reconnect) },
                bottomBar = {
                    AnimatedVisibility(
                        visible = !imeVisible,
                        enter = fadeIn(tween(navEnter)),
                        exit = fadeOut(tween(navExit)),
                    ) {
                        NavigationBar(containerColor = Color.Transparent) {
                            TopLevelDestination.all.forEach { dest ->
                                val badge = when (dest) {
                                    TopLevelDestination.Approvals -> pendingApprovals
                                    TopLevelDestination.Tasks -> runningTasks
                                    else -> 0
                                }
                                NavigationBarItem(
                                    selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                                    onClick = { onNavigate(dest.route) },
                                    icon = {
                                        BadgedBox(badge = {
                                            if (badge > 0) Badge { Text(badge.toString()) }
                                        }) {
                                            Icon(dest.icon, contentDescription = dest.label)
                                        }
                                    },
                                    label = { Text(dest.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = JarvisCyan,
                                        selectedTextColor = HudInk,
                                        unselectedIconColor = navUnselected,
                                        unselectedTextColor = navUnselected,
                                        indicatorColor = JarvisCyan.copy(alpha = 0.20f),
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                MainNavHost(
                    navController = navController,
                    vm = vm,
                    isOwner = isOwner,
                    modifier = Modifier.padding(innerPadding),
                    enterMs = navEnter,
                    exitMs = navExit,
                )
            }
        }
    }
}

@Composable
private fun MainNavHost(
    navController: NavHostController,
    vm: JarvisViewModel,
    isOwner: Boolean,
    modifier: Modifier,
    enterMs: Int,
    exitMs: Int,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(tween(enterMs)) },
        exitTransition = { fadeOut(tween(exitMs)) },
    ) {
        composable(TopLevelDestination.Home.route) {
            HomeScreen(
                vm = vm,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(TopLevelDestination.Conversations.route) { ConversationsScreen(vm) }
        composable(TopLevelDestination.Projects.route) { ProjectsScreen(vm) }
        composable(TopLevelDestination.Approvals.route) { ApprovalsScreen(vm, isOwner = isOwner) }
        composable(TopLevelDestination.Tasks.route) { TasksScreen(vm) }
        composable(TopLevelDestination.Settings.route) { SettingsScreen(vm) }
    }
}
