package com.jarvis.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.data.state.DiagnosticEntry
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.LocalJarvisAccents

/**
 * Sanitized client diagnostics (plan §17, AND-W4). Shows reducer/transport
 * notes only — never payload bodies, tokens, or pairing material.
 */
@Composable
fun DiagnosticsCard(vm: JarvisViewModel, diagnostics: List<DiagnosticEntry>) {
    var expanded by remember { mutableStateOf(false) }
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val accents = LocalJarvisAccents.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC0E182A),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    accents.orbGlow.copy(alpha = 0.28f),
                    Color(0x228B5CF6),
                    accents.orbGlow.copy(alpha = 0.18f),
                ),
            ),
        ),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                accents.orbGlow.copy(alpha = 0.8f),
                                Color(0x338B5CF6),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accents.orbGlow.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = accents.orbGlow,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "DIAGNOSTICS",
                            style = HudTextStyle.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                            color = accents.orbGlow,
                        )
                    }
                    TextButton(
                        onClick = { expanded = !expanded },
                        colors = ButtonDefaults.textButtonColors(contentColor = accents.orbGlow),
                    ) {
                        Text(
                            if (expanded) "HIDE" else "SHOW (${diagnostics.size})",
                            style = HudTextStyle,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF08101E),
                    border = BorderStroke(1.dp, Color(0x3322D3EE)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "CURSOR: ${snapshot.session.lastCursorToken.ifBlank { snapshot.session.lastCursor.toString() }}",
                                style = HudTextStyle,
                                color = Color(0xFF8BA2BE),
                            )
                            Text(
                                "ACTIVE: ${snapshot.session.activeRequestCount}",
                                style = HudTextStyle,
                                color = JarvisCyan,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "PHASE: ${snapshot.phase.name}",
                                style = HudTextStyle,
                                color = Color(0xFF8BA2BE),
                            )
                            Text(
                                "TRANSPORT: ${vm.transportMode.name}",
                                style = HudTextStyle,
                                color = JarvisCyan,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "PROTO: ${snapshot.session.negotiatedProtocolVersion.ifBlank { "—" }}",
                                style = HudTextStyle,
                                color = Color(0xFF8BA2BE),
                            )
                            Text(
                                "FP: ${snapshot.session.negotiatedFingerprint.ifBlank { "—" }}",
                                style = HudTextStyle,
                                color = Color(0xFF8BA2BE),
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    LazyColumn(
                        Modifier.fillMaxWidth().height(220.dp).padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(diagnostics.takeLast(60).reversed()) { d ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF091220),
                                border = BorderStroke(1.dp, Color(0x1A22D3EE)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "${d.kind}  ${d.detail}",
                                    style = HudTextStyle,
                                    color = when (d.kind) {
                                        DiagnosticEntry.Kind.PROTOCOL_MISMATCH,
                                        DiagnosticEntry.Kind.MALFORMED_EVENT -> MaterialTheme.colorScheme.error
                                        DiagnosticEntry.Kind.UNKNOWN_EVENT,
                                        DiagnosticEntry.Kind.DUPLICATE_EVENT -> accents.degraded
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
