package com.jarvis.android.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.jarvis.android.ui.components.AmbientBackdrop
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.shared.authenticateDeviceOwner
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import kotlinx.coroutines.launch

/**
 * Biometric app lock (AND-W3 adjacent). Blocks the whole app surface until the
 * device owner authenticates. Accepts device credential as fallback so a user
 * without enrolled biometrics can still get in.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val accents = LocalJarvisAccents.current
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }

    val canAuth = remember {
        BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt() {
        val host = activity
        if (host == null || !canAuth) {
            onUnlocked() // nothing to authenticate against; don't strand the user
            return
        }
        if (prompting) return
        prompting = true
        scope.launch {
            val ok = authenticateDeviceOwner(
                activity = host,
                title = "Unlock JARVIS",
                subtitle = "Confirm it's you to open your assistant",
            )
            prompting = false
            if (ok) onUnlocked() else denied = true
        }
    }

    LaunchedEffect(Unit) { prompt() }

    AmbientBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            JarvisOrb(size = 132.dp, activity = if (denied) OrbActivity.OFFLINE else OrbActivity.LISTENING)

            Spacer(Modifier.height(30.dp))
            Text("LOCKED", style = HudTextStyle, color = accents.orbGlow)
            Spacer(Modifier.height(10.dp))
            Text(
                "JARVIS is locked",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Authenticate to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            Button(onClick = { denied = false; prompt() }) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  Unlock")
            }

            AnimatedVisibility(visible = denied, enter = fadeIn()) {
                Text(
                    "Authentication failed or cancelled.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
