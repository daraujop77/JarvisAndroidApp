package com.jarvis.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.android.BuildConfig
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.components.JarvisBrain
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisCyanBright
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.JarvisViolet
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

private val BootLines = listOf(
    "initialising neural core",
    "loading contracts & registry",
    "restoring memory & timeline",
    "synchronizing gateway link",
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
    val accents = LocalJarvisAccents.current
    var stage by remember { mutableIntStateOf(if (reduced) BootLines.size else 0) }
    var greet by remember { mutableStateOf(reduced) }

    val progress by animateFloatAsState(
        targetValue = stage / BootLines.size.toFloat(),
        animationSpec = tween(if (reduced) 0 else 420),
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
                shape = RoundedCornerShape(20.dp),
                color = Color(0x2222D3EE),
                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.45f)),
            ) {
                Text(
                    "AUTONOMOUS CO-PILOT // v${BuildConfig.VERSION_CODE}",
                    style = HudTextStyle.copy(letterSpacing = 1.3.sp, fontSize = 10.sp),
                    color = JarvisCyanBright,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(if (compactHeight) 20.dp else 36.dp))

            Box(
                modifier = Modifier
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
                        color = Color(0xCC0E182A),
                        border = BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    JarvisCyan.copy(alpha = 0.35f),
                                    JarvisViolet.copy(alpha = 0.25f),
                                    JarvisCyan.copy(alpha = 0.35f),
                                ),
                            ),
                        ),
                        shadowElevation = 6.dp,
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
                                        BootLines.getOrElse(stage.coerceAtMost(BootLines.lastIndex)) { "" }
                                            .uppercase(),
                                        style = HudTextStyle,
                                        color = JarvisCyanBright,
                                    )
                                }
                                Text(
                                    "[ ${(progress * 100).toInt()}% ]",
                                    style = HudTextStyle,
                                    color = JarvisCyan,
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
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xD90E182A),
                        border = BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    JarvisGreen.copy(alpha = 0.5f),
                                    JarvisCyan.copy(alpha = 0.4f),
                                    JarvisGreen.copy(alpha = 0.5f),
                                ),
                            ),
                        ),
                        shadowElevation = 6.dp,
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
                                    if (ownerName.isNullOrBlank()) "WELCOME BACK" else "WELCOME BACK, ${ownerName.uppercase()}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    color = Color(0xFFE8EEF8),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "NEURAL LINK ESTABLISHED // ALL SYSTEMS ONLINE",
                                style = HudTextStyle,
                                color = JarvisGreen,
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
                    "SECURE NEURAL CLIENT // PROTOCOL v${BuildConfig.VERSION_CODE}",
                    style = HudTextStyle,
                    color = Color(0xFF8BA2BE).copy(alpha = 0.75f),
                )
            }
        }
    }
}
