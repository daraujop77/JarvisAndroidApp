package com.jarvis.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.home.JarvisVisualState
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisVisualSystem
import com.jarvis.android.ui.theme.LocalAnimationIntensity
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.LocalReducedMotion
import com.jarvis.android.ui.theme.rememberAdaptiveLayout
import kotlinx.coroutines.delay

private val BootLines = listOf(
    "initialising core",
    "loading contracts",
    "restoring conversations",
    "linking gateway",
)

/**
 * Boot / welcome screen. Runs a short staged sequence, then calls [onFinished].
 * Under reduced motion the sequence collapses to a single frame so the app is
 * usable immediately.
 */
@Composable
fun WelcomeScreen(
    ownerName: String? = null,
    onFinished: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val intensity = LocalAnimationIntensity.current
    val accents = LocalJarvisAccents.current
    val layout = rememberAdaptiveLayout()
    var stage by remember { mutableIntStateOf(if (reduced) BootLines.size else 0) }
    var greet by remember { androidx.compose.runtime.mutableStateOf(reduced) }
    val bootMs = JarvisVisualSystem.durationMs(reduced, intensity, 420)

    val progress by animateFloatAsState(
        targetValue = stage / BootLines.size.toFloat(),
        animationSpec = tween(bootMs),
        label = "boot",
    )

    LaunchedEffect(Unit) {
        if (!reduced) {
            delay(280)
            while (stage < BootLines.size) {
                delay(340)
                stage += 1
            }
            greet = true
            delay(900)
        }
        onFinished()
    }

    AmbientBackdrop(visualState = if (greet) JarvisVisualState.IDLE else JarvisVisualState.BOOT) {
        // Short/landscape windows must not let the centred stack collide with the
        // footer, so the orb scales down and everything stays in one flow.
        val compactHeight = maxHeight < 560.dp
        val orbSize = layout.orbHeroDp.dp.let { if (!compactHeight && layout.orbHeroDp < 140) 168.dp else it }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            JarvisOrb(
                size = orbSize,
                visualState = if (greet) JarvisVisualState.IDLE else JarvisVisualState.BOOT,
                intensity = progress,
            )

            Spacer(Modifier.height(if (compactHeight) 18.dp else 36.dp))

            Text(
                "J A R V I S",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(10.dp))

            AnimatedVisibility(
                visible = greet,
                enter = fadeIn(tween(bootMs)) + slideInVertically(tween(bootMs)) { it / 3 },
            ) {
                Text(
                    if (ownerName.isNullOrBlank()) "Welcome back." else "Welcome back, $ownerName.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(if (compactHeight) 20.dp else 44.dp))

            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.TopCenter) {
                if (!greet) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.width(180.dp),
                            color = accents.orbGlow,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row {
                            Text(
                                text = BootLines.getOrElse(stage.coerceAtMost(BootLines.lastIndex)) { "" }
                                    .uppercase(),
                                style = HudTextStyle,
                                color = accents.orbGlow.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
        }

        if (!compactHeight) {
            Text(
                "PRIVATE LINK · FAKE GATEWAY BUILD",
                style = HudTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .alpha(0.6f),
            )
        }
    }
}
