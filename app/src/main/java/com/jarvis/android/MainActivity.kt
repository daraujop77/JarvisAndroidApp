package com.jarvis.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.data.learning.LearningCatalog
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
        // Lane E: recents/task-switcher and screenshots must not show chat,
        // approvals or tokens. FLAG_SECURE blanks the preview and blocks
        // capture; the lock screen still re-arms on onStop.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val app = application as JarvisApp
            val settings by app.container.settings.settings.collectAsStateWithLifecycle(
                initialValue = com.jarvis.android.data.prefs.SettingsStore.Settings(),
            )
            JarvisTheme(
                theme = LearningCatalog.themeFor(
                    com.jarvis.android.data.learning.LessonProgress
                        .completedFor(settings.lessonProgress, settings.activeLearnerId),
                ),
            ) {
                JarvisRoot(app = app)
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
