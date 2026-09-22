package com.jarvis.android

import android.os.Bundle
import android.view.WindowManager
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
        // Manual screenshots are intentionally allowed while JARVIS is in the
        // foreground so the owner can capture UI/debug evidence. Keep the task
        // switcher private without applying FLAG_SECURE to the whole activity.
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
        setContent {
            JarvisTheme {
                JarvisRoot(app = application as JarvisApp)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT < 33) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onPause() {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            // Older Android releases do not expose the dedicated Recents
            // screenshot API. Protect the task snapshot only while leaving.
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        super.onPause()
    }

    /**
     * Re-arm the biometric lock as soon as the app leaves the foreground.
     */
    override fun onStop() {
        super.onStop()
        (application as? JarvisApp)?.lockOnBackground()
    }
}
