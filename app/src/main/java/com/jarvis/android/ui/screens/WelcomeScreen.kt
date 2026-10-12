package com.jarvis.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.android.BuildConfig
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.components.JarvisBrain
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.i18n.LocalAppStrings
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisCyanBright
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

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
    val strings = LocalAppStrings.current
    val bootLines = listOf(
        strings.bootInitializing,
        strings.bootLoading,
        strings.bootRestoring,
        strings.bootConnecting,
    )
    var stage by remember { mutableIntStateOf(if (reduced) bootLines.size else 0) }
    var greet by remember { mutableStateOf(reduced) }

    val progress by animateFloatAsState(
        targetValue = stage / bootLines.size.toFloat(),
        animationSpec = tween(if (reduced) 0 else 420),
        label = "boot",
    )

    LaunchedEffect(Unit) {
        if (!reduced) {
            delay(280)
            while (stage < bootLines.size) {
                delay(340)
                stage += 1
            }
            greet = true
            delay(900)
        }
        onFinished()
    }

    AmbientBackdrop {
        // Short/landscape windows must not let the centred stack collide with the
        // footer, so the orb scales down and everything stays in one flow.
        val compactHeight = maxHeight < 560.dp
        val orbSize = if (compactHeight) 110.dp else 170.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            JarvisBrain(
                size = orbSize,
                activity = if (greet) OrbActivity.IDLE else OrbActivity.THINKING,
                intensity = progress,
            )

            Spacer(Modifier.height(if (compactHeight) 18.dp else 32.dp))

            Text(
                "J A R V I S",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp,
                ),
                color = JarvisCyanBright,
            )

            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ) {
                Text(
                    "${strings.appDescriptor} · v${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(if (compactHeight) 20.dp else 36.dp))

            Box(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                this@Column.AnimatedVisibility(
                    visible = !greet,
                    enter = fadeIn(tween(if (reduced) 0 else 300)),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(JarvisCyan),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        bootLines.getOrElse(stage.coerceAtMost(bootLines.lastIndex)) { "" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = JarvisCyan,
                                trackColor = Color(0x3316223A),
                            )
                        }
                    }
                }

                this@Column.AnimatedVisibility(
                    visible = greet,
                    enter = fadeIn(tween(if (reduced) 0 else 500)) +
                        slideInVertically(tween(if (reduced) 0 else 500)) { it / 3 },
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(JarvisGreen.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = JarvisGreen,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    strings.welcomeBack(ownerName),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                strings.allSystemsReady,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (!compactHeight) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(JarvisCyan.copy(alpha = 0.7f)),
                )
                Text(
                    strings.secureClientVersion(BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
