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

    companion object {
        /**
         * Pure transport policy used by both startup and tests.
         *
         * A valid authenticated LIVE session always wins over developer simulation.
         * FAKE is an explicit debug-only opt-in; otherwise fail safe to HTTP.
         */
        internal fun selectTransportMode(
            debug: Boolean,
            useFake: Boolean,
            liveAuthenticated: Boolean,
        ): TransportMode = when {
            liveAuthenticated -> TransportMode.LIVE
            debug && useFake -> TransportMode.FAKE
            else -> TransportMode.HTTP
        }
    }

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

    private val live by lazy { LiveAppGatewayTransport(liveSession, scope) }

    /**
     * Safe pre-start mode.
     *
     * The old DEBUG default was FAKE, which allowed the lazy transport/session graph
     * to capture FakeGateway before the async settings read completed. A persisted
     * authenticated LIVE session is synchronously observable and must therefore be
     * honored immediately. Otherwise HTTP is the safe pre-start fallback; FAKE can
     * only be selected explicitly after settings are read.
     */
    @Volatile
    var transportMode: TransportMode =
        selectTransportMode(
            debug = com.jarvis.android.BuildConfig.DEBUG,
            useFake = false,
            liveAuthenticated = liveSession.isAuthenticated,
        )
        private set

    /**
     * Swappable transport seam.
     *
     * Re-check LIVE authentication at materialization time so an authenticated
     * session can never be captured as FakeGateway because of startup ordering.
     * Changing the non-LIVE developer mode still requires an app restart.
     */
    val transport: GatewayTransport by lazy {
        val materializedMode = if (liveSession.isAuthenticated) {
            TransportMode.LIVE
        } else {
            transportMode
        }
        transportMode = materializedMode
        when (materializedMode) {
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

    /**
     * AND-W9 (Lane F): Projects backend is NOT_CONNECTED. The shell runs on
     * this fake repository; swapping in a real PC-A projects source later is a
     * one-property change, no UI edits.
     */
    val projectsRepository: com.jarvis.android.data.projects.ProjectsRepository by lazy {
        com.jarvis.android.data.projects.FakeProjectsRepository()
    }

    /**
     * Called from Application.onCreate. Settings resolution stays on the
     * background scope. Real authenticated sessions are already pinned to LIVE
     * before this coroutine runs, preventing the former DEBUG->FAKE race.
     */
    fun start() {
        scope.launch {
            transportMode = resolveMode(settings.settings.first().useFakeGateway)
            session.start()
            runCatching { conversations.recover() }
        }
    }

    /**
     * LIVE has authority over the debug simulator whenever a persisted
     * authenticated app session exists. FAKE remains an explicit debug-only
     * development path; otherwise HTTP is the fail-safe default.
     */
    fun resolveMode(useFake: Boolean): TransportMode =
        selectTransportMode(
            debug = com.jarvis.android.BuildConfig.DEBUG,
            useFake = useFake,
            liveAuthenticated = liveSession.isAuthenticated,
        )

    val liveTransport: LiveAppGatewayTransport get() = live
}
