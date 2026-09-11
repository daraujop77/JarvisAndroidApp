package com.jarvis.android.di

import android.content.Context
import com.jarvis.android.data.local.JarvisDao
import com.jarvis.android.data.local.JarvisDatabase
import com.jarvis.android.data.prefs.SettingsStore
import com.jarvis.android.data.repo.ConversationRepository
import com.jarvis.android.data.repo.JarvisSessionRepository
import com.jarvis.android.security.DeviceIdentityStore
import com.jarvis.android.transport.GatewayTransport
import com.jarvis.android.transport.fake.FakeGateway
import com.jarvis.android.transport.wss.WssGatewayTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manual DI container (no Hilt, keeps the build lean per AND-W0).
 * Chooses Fake vs WSS transport from settings (plan §23 abstraction).
 */
class AppContainer(private val context: Context) {

    enum class TransportMode { FAKE, WSS }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val db: JarvisDatabase by lazy { JarvisDatabase.get(context) }
    val dao: JarvisDao by lazy { db.dao() }

    val settings: SettingsStore by lazy { SettingsStore(context) }

    val deviceIdentity: DeviceIdentityStore by lazy { DeviceIdentityStore(context) }

    val attachmentStore: com.jarvis.android.data.media.AttachmentStore by lazy {
        com.jarvis.android.data.media.AttachmentStore(context)
    }

    private val fake = FakeGateway(scope)
    private val wss by lazy { WssGatewayTransport(context, scope) }

    /** Resolved at start(); defaults to Fake until settings are read. */
    @Volatile
    var transportMode: TransportMode = TransportMode.FAKE
        private set

    /**
     * Swappable transport seam. Lazy so [start] resolves the mode from settings
     * first; changing the mode requires an app restart (documented V1 limitation).
     */
    val transport: GatewayTransport by lazy {
        when (transportMode) {
            TransportMode.FAKE -> fake
            TransportMode.WSS -> wss
        }
    }

    val fakeGateway: FakeGateway get() = fake

    val session: JarvisSessionRepository by lazy {
        JarvisSessionRepository(transport, scope)
    }

    val conversations: ConversationRepository by lazy {
        ConversationRepository(dao, session, scope)
    }

    /**
     * Called from Application.onCreate, so nothing here may block the main
     * thread: the transport mode is resolved on a background coroutine before
     * the session (and its lazy transport) is first touched.
     */
    fun start() {
        scope.launch {
            transportMode = if (settings.settings.first().useFakeGateway) TransportMode.FAKE else TransportMode.WSS
            session.start()
            runCatching { conversations.recover() }
        }
    }
}
