package com.jarvis.android

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import com.jarvis.android.ui.JarvisRoot
import com.jarvis.android.ui.theme.JarvisTheme

/**
 * FragmentActivity (not ComponentActivity) so BiometricPrompt's
 * FragmentActivity overload is available for sensitive approvals (AND-W5).
 */
class MainActivity : FragmentActivity() {
    private val openConversationRequest = MutableStateFlow(0L)
    private val notificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Start fail-closed until Compose resolves which shell owns the window.
        // MAIN later clears FLAG_SECURE so the owner can take intentional
        // screenshots for support/debugging. Login/lock keep it enabled.
        setSensitiveScreen(true)

        // Manual screenshots in MAIN are allowed, but the Android recents/task
        // switcher should still not retain a visual snapshot of JARVIS.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            setRecentsScreenshotEnabled(false)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_CONVERSATION, false) == true) {
            openConversationRequest.value += 1
        }
        setContent {
            JarvisTheme {
                JarvisRoot(
                    app = application as JarvisApp,
                    openConversationRequest = openConversationRequest,
                    onSensitiveScreenChanged = ::setSensitiveScreen,
                )
            }
        }
    }

    private fun setSensitiveScreen(sensitive: Boolean) {
        if (sensitive) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CONVERSATION, false)) {
            openConversationRequest.value += 1
        }
    }

    /**
     * Re-arm the biometric lock as soon as the app leaves the foreground, so a
     * task-switcher peek can't expose conversations.
     */
    override fun onStop() {
        super.onStop()
        (application as? JarvisApp)?.lockOnBackground()
    }
    companion object {
        const val EXTRA_OPEN_CONVERSATION = "com.jarvis.android.extra.OPEN_CONVERSATION"
    }
}
