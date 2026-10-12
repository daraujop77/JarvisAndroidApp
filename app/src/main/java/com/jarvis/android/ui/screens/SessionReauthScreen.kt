package com.jarvis.android.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.jarvis.android.ui.i18n.LocalAppStrings
import com.jarvis.android.ui.shared.authenticateDeviceOwner
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
    val strings = LocalAppStrings.current
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
                title = strings.unlockAction,
                subtitle = strings.reauthDescription,
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
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(24.dp),
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
            Text(strings.sessionLocked, style = MaterialTheme.typography.labelLarge, color = accents.orbGlow)
            Spacer(Modifier.height(10.dp))
            Text(
                strings.sessionExpired,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                strings.reauthDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            if (authState is JarvisViewModel.LiveAuthState.Busy) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(strings.renewingSession)
            } else {
                Button(onClick = { prompt() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.unlockAction)
                }
            }

            val error = (authState as? JarvisViewModel.LiveAuthState.Error)?.message
            if (promptDenied || error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    error ?: strings.authenticationUnavailable,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = vm::pairAgain,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(strings.pairAgain)
            }
        }
    }
}
