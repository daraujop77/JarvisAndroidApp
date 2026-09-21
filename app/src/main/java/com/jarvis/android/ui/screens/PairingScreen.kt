package com.jarvis.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.components.JarvisBrain
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pairing UX shell (plan AND-W3). QR scanning / handshake waits for the frozen
 * pairing contract (plan §11); this screen provides manual-code intake plus a
 * dev identity path so the rest of the app is reachable during Fake Gateway
 * development.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PairingScreen(
    vm: JarvisViewModel,
    modifier: Modifier = Modifier,
    lockedReason: String? = null,
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf<String?>(null) }
    // Daily v1: one normal entry point, the VPS. Start from the last front door
    // that actually authenticated. Never a hard-coded PC — that is what made a
    // fresh install walk CONNECTING -> DISCONNECTED against the home machine.
    val rememberedDoor by vm.settings.collectAsStateWithLifecycle()
    var baseUrl by remember(rememberedDoor.lastControlPlaneUrl) {
        mutableStateOf(rememberedDoor.lastControlPlaneUrl)
    }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val accents = LocalJarvisAccents.current
    val liveAuth by vm.liveAuth.collectAsStateWithLifecycle()

    AmbientBackdrop(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            JarvisBrain(
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
                modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                shape = RoundedCornerShape(16.dp),
                colors = jarvisTextFieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Pairing code (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                shape = RoundedCornerShape(16.dp),
                colors = jarvisTextFieldColors(),
            )

            Spacer(Modifier.height(20.dp))

            if (vm.developerOptionsEnabled) {
                Button(
                    onClick = {
                        if (name.isNotBlank()) vm.setOwnerName(name)
                        vm.pairDemoDevice { deviceId = it }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text("Continue with Fake Gateway") }
            }

            AnimatedVisibility(visible = deviceId != null, enter = fadeIn()) {
                Text(
                    "IDENTITY ${deviceId?.take(18)?.uppercase() ?: ""}",
                    style = HudTextStyle,
                    color = accents.orbGlow,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            // PCB-LIVE-1: PC-A authenticated app session over the private front door.
            Text(
                "OR CONNECT TO JARVIS",
                style = HudTextStyle,
                color = accents.orbGlow,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "One front door: the JARVIS control plane on your private Tailscale network. " +
                    "It routes to the cloud or to your PC — this phone does not choose. " +
                    "Only the short-lived token is stored on this device, never the password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Control plane URL") },
                placeholder = { Text("https://jarvis.your-tailnet.ts.net") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                shape = RoundedCornerShape(16.dp),
                colors = jarvisTextFieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                shape = RoundedCornerShape(16.dp),
                colors = jarvisTextFieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                shape = RoundedCornerShape(16.dp),
                colors = jarvisTextFieldColors(),
            )
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = { vm.liveLogin(baseUrl, user, password) },
                enabled = liveAuth !is JarvisViewModel.LiveAuthState.Busy &&
                    baseUrl.isNotBlank() && user.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    when (liveAuth) {
                        JarvisViewModel.LiveAuthState.Busy -> "Signing in…"
                        else -> "Connect"
                    },
                )
            }
            AnimatedVisibility(visible = liveAuth is JarvisViewModel.LiveAuthState.Error) {
                Text(
                    (liveAuth as? JarvisViewModel.LiveAuthState.Error)?.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
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
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.scrollIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent { state ->
            if (state.isFocused) {
                scope.launch {
                    delay(280)
                    requester.bringIntoView()
                }
            }
        }
}
