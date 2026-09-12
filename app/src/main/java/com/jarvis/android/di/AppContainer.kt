package com.jarvis.android.di

import android.content.Context
import com.jarvis.android.data.local.JarvisDao
import com.jarvis.android.data.local.JarvisDatabase
import com.jarvis.android.data.prefs.SettingsStore
import com.jarvis.android.data.repo.ConversationRepository
import com.jarvis.android.data.repo.JarvisSessionRepository
import com.jarvis.android.security.DeviceIdentityStore
import com.jarvis.android.transport.GatewayTransport
import com.jarvis.android.transport.NoAuthProvider
import com.jarvis.android.transport.fake.FakeGateway
import com.jarvis.android.transport.http.HttpGatewayTransport
import com.jarvis.android.transport.wss.WssGatewayTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manual DI container (no Hilt, keeps the build lean per AND-W0).
 * Chooses Fake vs HTTP Web V1 transport from settings (plan §23 abstraction).
 * Legacy WSS stays on the seam but is not the production path.
 */
class AppContainer(private val context: Context) {

    enum class TransportMode { FAKE, HTTP, WSS }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val db: JarvisDatabase by lazy { JarvisDatabase.get(context) }
    val dao: JarvisDao by lazy { db.dao() }

    val settings: SettingsStore by lazy { SettingsStore(context) }

    val deviceIdentity: DeviceIdentityStore by lazy { DeviceIdentityStore(context) }

    val attachmentStore: com.jarvis.android.data.media.AttachmentStore by lazy {
        com.jarvis.android.data.media.AttachmentStore(context)
    }

    private val fake = FakeGateway(scope)
    private val http by lazy {
        HttpGatewayTransport(
            scope = scope,
            baseUrlProvider = {
                kotlinx.coroutines.runBlocking { settings.settings.first().gatewayBaseUrl }
            },
            auth = NoAuthProvider,
        )
    }
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
            TransportMode.HTTP -> http
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
            transportMode = if (settings.settings.first().useFakeGateway) TransportMode.FAKE else TransportMode.HTTP
            session.start()
            runCatching { conversations.recover() }
        }
    }
}
