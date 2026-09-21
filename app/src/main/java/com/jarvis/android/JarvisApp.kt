package com.jarvis.android

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jarvis.android.di.AppContainer
import com.jarvis.android.notify.NotificationCoordinator
import kotlinx.coroutines.launch

class JarvisApp : Application() {

    lateinit var container: AppContainer
        private set

    lateinit var notifications: NotificationCoordinator
        private set

    /** Set when the app backgrounds; the UI re-locks on the next composition. */
    val relockRequested = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun lockOnBackground() { relockRequested.value = true }

    fun consumeRelock() { relockRequested.value = false }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        notifications = NotificationCoordinator(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                notifications.setForeground(true)
                container.session.setForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                notifications.setForeground(false)
                container.session.setForeground(false)
            }
        })

        container.start()

        // Bring the floating brain back if the owner left it on. No-op without
        // the overlay grant, so a revoked permission never resurrects it.
        com.jarvis.android.overlay.FloatingBubbleService.restoreIfEnabled(this)

        // Emit notification-worthy transitions off every snapshot.
        container.scope.launch {
            container.session.snapshot.collect(notifications::onSnapshot)
        }
    }
}
