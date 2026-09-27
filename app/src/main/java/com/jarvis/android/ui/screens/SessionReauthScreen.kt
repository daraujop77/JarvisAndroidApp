package com.jarvis.android.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.components.JarvisBrain
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.shared.authenticateDeviceOwner
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import kotlinx.coroutines.launch

/**
 * Passwordless recovery surface for an expired short-lived app session.
 * BiometricPrompt (or the device credential fallback) gates use of the
 * Keystore-encrypted device refresh credential.
 */
@Composable
fun SessionReauthScreen(vm: JarvisViewModel) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val accents = LocalJarvisAccents.current
    val scope = rememberCoroutineScope()
    val authState by vm.liveAuth.collectAsStateWithLifecycle()
    var promptDenied by remember { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }

    val canAuthenticate = remember {
        BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt() {
        if (prompting || authState is JarvisViewModel.LiveAuthState.Busy) return
        val host = activity
        if (host == null || !canAuthenticate) {
            promptDenied = true
            return
        }
        prompting = true
        promptDenied = false
        scope.launch {
            val verified = authenticateDeviceOwner(
                activity = host,
                title = "Unlock JARVIS",
                subtitle = "Confirm it's you to renew this device session",
            )
            prompting = false
            if (verified) {
                vm.biometricReauth()
            } else {
                promptDenied = true
            }
        }
    }

    LaunchedEffect(Unit) { prompt() }

    AmbientBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            JarvisBrain(
                size = 132.dp,
                activity = if (authState is JarvisViewModel.LiveAuthState.Busy) {
                    OrbActivity.THINKING
                } else {
                    OrbActivity.LISTENING
                },
            )

            Spacer(Modifier.height(30.dp))
            Text("SESSION LOCKED", style = HudTextStyle, color = accents.orbGlow)
            Spacer(Modifier.height(10.dp))
            Text(
                "Your JARVIS session expired",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Use your fingerprint, face, or device unlock. No JARVIS password is needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            if (authState is JarvisViewModel.LiveAuthState.Busy) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Renewing secure session…")
            } else {
                Button(onClick = { prompt() }) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock JARVIS")
                }
            }

            val error = (authState as? JarvisViewModel.LiveAuthState.Error)?.message
            if (promptDenied || error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    error ?: "Authentication was cancelled or is unavailable on this device.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = vm::pairAgain) {
                Text("Pair again instead")
            }
        }
    }
}
