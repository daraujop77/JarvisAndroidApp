package com.jarvis.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.jarvis.android.ui.JarvisRoot
import com.jarvis.android.ui.theme.JarvisTheme

/**
 * FragmentActivity (not ComponentActivity) so BiometricPrompt's
 * FragmentActivity overload is available for sensitive approvals (AND-W5).
 */
class MainActivity : FragmentActivity() {
    private val notificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            JarvisTheme {
                JarvisRoot(app = application as JarvisApp)
            }
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
}
