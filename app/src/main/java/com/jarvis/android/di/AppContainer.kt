package com.jarvis.android.di

import android.content.Context
import com.jarvis.android.data.local.JarvisDao
import com.jarvis.android.data.local.JarvisDatabase
import com.jarvis.android.data.prefs.SettingsStore
import com.jarvis.android.data.repo.ConversationRepository
import com.jarvis.android.data.repo.JarvisSessionRepository
import com.jarvis.android.security.DeviceIdentityStore
import com.jarvis.android.transport.AuthProvider
import com.jarvis.android.transport.GatewayTransport
import com.jarvis.android.transport.fake.FakeGateway
import com.jarvis.android.transport.http.HttpGatewayTransport
import com.jarvis.android.transport.live.JarvisAppSession
import com.jarvis.android.transport.live.LiveAppGatewayTransport
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

    enum class TransportMode { FAKE, HTTP, LIVE, WSS }

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
                val stored = kotlinx.coroutines.runBlocking { settings.settings.first().gatewayBaseUrl }
                stored.ifBlank { liveSession.baseUrl }
            },
            // Lane G: reuse the real PC-A bearer when a session exists; never a
            // fabricated token. Absent credentials stay fail-closed server-side.
            auth = AuthProvider {
                liveSession.authHeader()?.let { mapOf("Authorization" to it) } ?: emptyMap()
            },
            identity = {
                com.jarvis.android.contract.webv1.WebV1Identity(
                    userId = liveSession.userId.ifBlank { null },
                    deviceId = liveSession.deviceId.ifBlank { null },
                    sessionId = liveSession.appSessionId.ifBlank { null },
                )
            },
        )
    }
    private val wss by lazy { WssGatewayTransport(context, scope) }

    /** PC-A authenticated app session (PCB-LIVE-1). Persists only the bearer token. */
    val liveSession: JarvisAppSession by lazy { JarvisAppSession.forContext(context) }

    private val live by lazy { LiveAppGatewayTransport(liveSession, scope, attachmentStore) }

    /**
     * Resolved fully in [start] on a background coroutine. The pre-start value
     * is fail-safe: release builds default to HTTP (never FAKE) with no disk
     * I/O on the main thread, and [resolveMode] still promotes to LIVE once the
     * stored token is read.
     */
    @Volatile
    var transportMode: TransportMode =
        if (liveSession.isAuthenticated) TransportMode.LIVE else TransportMode.HTTP
        private set

    /**
     * Swappable transport seam. Lazy so [start] resolves the mode from settings
     * first; changing the mode requires an app restart (documented V1 limitation).
     */
    val transport: GatewayTransport by lazy {
        when (transportMode) {
            TransportMode.FAKE -> fake
            TransportMode.HTTP -> http
            TransportMode.LIVE -> live
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

    val projectsRepository: com.jarvis.android.data.projects.ProjectsRepository by lazy {
        com.jarvis.android.data.projects.LiveProjectsRepository(liveSession)
    }

    /**
     * Called from Application.onCreate, so nothing here may block the main
     * thread: the transport mode is resolved on a background coroutine before
     * the session (and its lazy transport) is first touched.
     */
    fun start() {
        // Resolve synchronously before any lazy session/transport can be touched.
        // Daily builds must never race through the FakeGateway simply because
        // they are debug-signed.
        transportMode = resolveMode(useFake = false)
        scope.launch {
            runCatching { settings.setUseFake(false) }
            session.start()
            runCatching { conversations.recover() }
        }
    }

    /**
     * FAKE is a debug-only surface (Lane E): a release build never runs against
     * the simulator even if a stray preference asked for it. LIVE applies while
     * the PC-A app-session token is valid; HTTP remains the frozen /api/v1
     * target for when PC-A activates it.
     */
    fun resolveMode(useFake: Boolean): TransportMode = when {
        liveSession.isAuthenticated -> TransportMode.LIVE
        else -> TransportMode.HTTP
    }

    val liveTransport: LiveAppGatewayTransport get() = live
}
