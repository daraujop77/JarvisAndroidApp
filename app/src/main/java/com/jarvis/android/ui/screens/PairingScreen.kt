package com.jarvis.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors

/**
 * Pairing UX shell (plan AND-W3). QR scanning / handshake waits for the frozen
 * pairing contract (plan §11); this screen provides manual-code intake plus a
 * dev identity path so the rest of the app is reachable during Fake Gateway
 * development.
 */
@Composable
fun PairingScreen(
    vm: JarvisViewModel,
    modifier: Modifier = Modifier,
    lockedReason: String? = null,
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf<String?>(null) }
    val accents = LocalJarvisAccents.current

    AmbientBackdrop(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            JarvisOrb(
                size = 128.dp,
                activity = if (lockedReason != null) OrbActivity.OFFLINE else OrbActivity.LISTENING,
            )
            Spacer(Modifier.height(26.dp))

            Text(
                if (lockedReason != null) "REAUTHORISE" else "PAIR DEVICE",
                style = HudTextStyle,
                color = if (lockedReason != null) MaterialTheme.colorScheme.error else accents.orbGlow,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Connect this phone to JARVIS",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )

            lockedReason?.let {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Scanning the QR code on your Jarvis PC will pair this device. " +
                    "Pairing security is gated on the PC-A contract freeze — while developing " +
                    "against the Fake Gateway you can provision a local device identity instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(26.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("What should I call you?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = jarvisTextFieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Pairing code (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = jarvisTextFieldColors(),
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) vm.setOwnerName(name)
                    vm.pairDemoDevice { deviceId = it }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("Continue with Fake Gateway") }

            AnimatedVisibility(visible = deviceId != null, enter = fadeIn()) {
                Text(
                    "IDENTITY ${deviceId?.take(18)?.uppercase() ?: ""}",
                    style = HudTextStyle,
                    color = accents.orbGlow,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Text("  Scan QR — locked until contract freeze")
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "NOTHING LEAVES THIS DEVICE IN FAKE MODE",
                style = HudTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
