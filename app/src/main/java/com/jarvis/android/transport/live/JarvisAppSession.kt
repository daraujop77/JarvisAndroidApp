package com.jarvis.android.transport.live

import android.content.Context
import com.jarvis.android.transport.TransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * PC-A authenticated app session (PCB-LIVE-1).
 *
 * Uses the *existing* PC-A session flow (`POST /api/app/login`,
 * `GET /api/app/session`, `POST /api/app/logout`) over the private front door.
 * This is NOT the final cryptographic pairing protocol — that stays owned by
 * PC-A and must not be invented here (runbook §12).
 *
 * Only the short-lived bearer token PC-A issues is persisted, never the
 * password. [isAllowedLiveHost] fail-closes the base URL to private-network
 * hosts so the token can never be sent to a public endpoint (plan §23,
 * runbook §9 "no public temporary Gateway").
 */
class JarvisAppSession(
    private val store: Store,
    private val client: OkHttpClient = defaultClient(),
) {

    /** Persistence seam (SharedPreferences in prod, in-memory in tests). */
    interface Store {
        fun get(key: String): String?
        fun put(values: Map<String, String?>) // null value => remove key
        fun clear()
    }

    class PrefsStore(context: Context) : Store {
        private val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        override fun get(key: String): String? = prefs.getString(key, null)

        override fun put(values: Map<String, String?>) {
            prefs.edit().apply {
                values.forEach { (k, v) -> if (v == null) remove(k) else putString(k, v) }
            }.apply()
        }

        override fun clear() = prefs.edit().clear().apply()
    }

    class MemoryStore : Store {
        private val map = java.util.concurrent.ConcurrentHashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(values: Map<String, String?>) {
            values.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
        }
        override fun clear() = map.clear()
        fun snapshot(): Map<String, String> = map.toMap()
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class AppUser(val id: String = "", val username: String = "", val role: String = "")

    val token: String? get() = store.get(KEY_TOKEN)
    val baseUrl: String get() = store.get(KEY_BASE) ?: ""
    val userId: String get() = store.get(KEY_USER_ID) ?: ""
    val username: String get() = store.get(KEY_USERNAME) ?: ""
    val role: String get() = store.get(KEY_ROLE) ?: ""
    val expiresUtc: String? get() = store.get(KEY_EXPIRES)

    /** Stable app-scope session id minted at login (mirrors PC-A web client). */
    val appSessionId: String
        get() = store.get(KEY_APP_SESSION)
            ?: ("and_" + UUID.randomUUID().toString()).also { store.put(mapOf(KEY_APP_SESSION to it)) }

    /**
     * Stable per-install device id sent as `device_id`/`X-Jarvis-Device-Id`.
     * Independent of the Keystore pairing key (that waits on PC-A §12); this is
     * just the existing app-surface scope token PC-A already accepts.
     */
    val deviceId: String
        get() = store.get(KEY_DEVICE)
            ?: ("android_" + UUID.randomUUID().toString()).also { store.put(mapOf(KEY_DEVICE to it)) }

    val isAuthenticated: Boolean get() = !token.isNullOrBlank() && !expired

    val expired: Boolean
        get() {
            val exp = expiresUtc ?: return false
            return runCatching { Instant.parse(exp).isBefore(Instant.now().minusSeconds(30)) }.getOrDefault(false)
        }

    suspend fun login(base: String, user: String, password: String): Result<AppUser> =
        withContext(Dispatchers.IO) {
            val root = normalizeBase(base)
                ?: return@withContext Result.failure(TransportException("invalid gateway URL"))
            if (!isAllowedLiveHost(root)) {
                return@withContext Result.failure(
                    TransportException("live front door must be a private-network host (Tailscale/LAN)"),
                )
            }
            val body = buildJsonObject {
                put("username", user)
                put("password", password)
            }.toString()
            runCatching {
                val resp = post(root, "/api/app/login", body, auth = null)
                if (resp.first == 401) throw TransportException("invalid credentials")
                requireOk(resp)
                val parsed = json.decodeFromString(LoginResponse.serializer(), resp.second)
                check(parsed.authenticated && parsed.token.isNotBlank()) { "login response missing token" }
                store.put(
                    mapOf(
                        KEY_TOKEN to parsed.token,
                        KEY_BASE to root,
                        KEY_USER_ID to parsed.user.id,
                        KEY_USERNAME to parsed.user.username,
                        KEY_ROLE to parsed.user.role,
                        KEY_EXPIRES to parsed.expires_utc,
                        KEY_APP_SESSION to null, // fresh app session per login
                    ),
                )
                parsed.user
            }
        }

    /** Validate the stored token against `/api/app/session`; clears it on 401. */
    suspend fun restore(): Result<AppUser> = withContext(Dispatchers.IO) {
        val t = token ?: return@withContext Result.failure(TransportException("no session"))
        if (expired) {
            clear()
            return@withContext Result.failure(TransportException("session expired"))
        }
        runCatching {
            val resp = get(baseUrl, "/api/app/session", auth = authHeader())
            if (resp.first == 401) {
                clear()
                throw TransportException("session expired")
            }
            requireOk(resp)
            val parsed = json.decodeFromString(SessionResponse.serializer(), resp.second)
            store.put(mapOf(KEY_ROLE to parsed.user.role))
            parsed.user
        }
    }

    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        val t = token ?: return@withContext false
        val ok = runCatching { post(baseUrl, "/api/app/logout", "{}", auth = t).first in 200..299 }
            .getOrDefault(false)
        clear()
        ok
    }

    fun clear() = store.clear()

    fun authHeader(): String? = token?.let { "Bearer $it" }

    /**
     * Conversation-local chat profile chosen by the OWNER (PCB-LIVE-4/5).
     * Values come from the server-driven catalog in `/api/app/status`
     * (`chat.models[].profile`) — the client never invents model names.
     * Selection is isolated per conversation (runbook PA-7M: route selection
     * never crosses between Web/Android conversations A and B).
     */
    var defaultChatProfile: String
        get() = store.get(KEY_PROFILE)?.takeIf { it.isNotBlank() } ?: "normal"
        set(value) = store.put(mapOf(KEY_PROFILE to value))

    fun chatProfileFor(conversationId: String): String =
        store.get(KEY_PROFILE_CONV + conversationId)?.takeIf { it.isNotBlank() } ?: defaultChatProfile

    fun setChatProfileFor(conversationId: String, profile: String) {
        store.put(mapOf(KEY_PROFILE_CONV + conversationId to profile))
    }

    /** Persist per-turn scope so process-death recovery can call GET /requests/{id}. */
    fun rememberTurn(clientRequestId: String, conversationId: String, traceId: String, deviceId: String, sessionId: String) {
        store.put(
            mapOf(
                KEY_TURN_PREFIX + clientRequestId to
                    listOf(conversationId, traceId, deviceId, sessionId).joinToString("\u001f"),
            ),
        )
        val ids = (storedTurnIds() + clientRequestId).distinct().takeLast(32)
        store.put(mapOf(KEY_TURN_IDS to ids.joinToString(",")))
    }

    fun loadTurn(clientRequestId: String): StoredTurn? {
        val raw = store.get(KEY_TURN_PREFIX + clientRequestId) ?: return null
        val parts = raw.split("\u001f")
        if (parts.size != 4) return null
        return StoredTurn(clientRequestId, parts[0], parts[1], parts[2], parts[3])
    }

    fun forgetTurn(clientRequestId: String) {
        store.put(mapOf(KEY_TURN_PREFIX + clientRequestId to null))
        store.put(mapOf(KEY_TURN_IDS to storedTurnIds().filterNot { it == clientRequestId }.joinToString(",")))
    }

    fun storedTurnIds(): List<String> =
        store.get(KEY_TURN_IDS)?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    data class StoredTurn(
        val clientRequestId: String,
        val conversationId: String,
        val traceId: String,
        val deviceId: String,
        val sessionId: String,
    )

    /**
     * PCB-LIVE-4: the conversation profile catalog is **server-driven** —
     * PC-A's `/api/app/status` returns `chat.models[]` (profile/label/model/
     * state) plus `chat.access.owner_model_selection`, the same source PC-A's
     * own web client reads. The client never invents or hardcodes models.
     */
    data class ProfileEntry(val profile: String, val label: String, val model: String, val state: String)

    data class ChatAccess(val ownerModelSelection: Boolean, val entries: List<ProfileEntry>)

    suspend fun fetchChatAccess(): ChatAccess? = withContext(Dispatchers.IO) {
        val t = token ?: return@withContext null
        runCatching {
            val resp = get(baseUrl, "/api/app/status", auth = authHeader())
            if (resp.first !in 200..299) return@runCatching null
            val chat = json.parseToJsonElement(resp.second).jsonObject["chat"]?.jsonObject
                ?: return@runCatching null
            val models = (chat["models"] as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val profile = o["profile"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ProfileEntry(
                    profile = profile,
                    label = o["label"]?.jsonPrimitive?.contentOrNull ?: profile,
                    model = o["model"]?.jsonPrimitive?.contentOrNull ?: "—",
                    state = o["state"]?.jsonPrimitive?.contentOrNull ?: "unchecked",
                )
            }
            val ownerSel = chat["access"]?.jsonObject
                ?.get("owner_model_selection")?.jsonPrimitive?.booleanOrNull ?: false
            ChatAccess(ownerModelSelection = ownerSel, entries = models)
        }.getOrNull()
    }

    @Serializable
    data class WritingRoomParticipant(
        val id: String = "",
        val label: String = "",
    )

    @Serializable
    data class WritingRoomRouting(
        val requested_mode: String = "",
        val resolved_profile: String = "",
        val destination: String = "",
        val provider: String = "",
        val model: String = "",
    )

    @Serializable
    data class WritingRoomSource(
        val chunk_id: String? = null,
        val title: String = "",
        val heading: String? = null,
        val canon_status: String = "",
        val authority: String = "",
        val drive_url: String = "",
    )

    @Serializable
    data class WritingRoomCanon(
        val connected: Boolean = false,
        val authority: String = "",
        val story_id: String? = null,
        val documents: Int = 0,
        val chunks: Int = 0,
        val sources: List<WritingRoomSource> = emptyList(),
    )

    @Serializable
    data class WritingRoomText(val text: String = "")

    @Serializable
    data class WritingRoomMetrics(
        val total_ms: Int = 0,
        val rag_source_count: Int = 0,
    )

    @Serializable
    data class WritingRoomTurn(
        val schema: String = "",
        val project_id: String = "",
        val project_title: String = "",
        val participant: WritingRoomParticipant = WritingRoomParticipant(),
        val routing: WritingRoomRouting = WritingRoomRouting(),
        val canon: WritingRoomCanon = WritingRoomCanon(),
        val response: WritingRoomText = WritingRoomText(),
        val metrics: WritingRoomMetrics = WritingRoomMetrics(),
    )

    /** One grounded Council turn over the already-authenticated private app session. */
    suspend fun writingRoomTurn(
        projectId: String,
        projectTitle: String,
        participant: String,
        prompt: String,
    ): Result<WritingRoomTurn> = withContext(Dispatchers.IO) {
        if (expired) {
            clear()
            return@withContext Result.failure(TransportException("session expired"))
        }
        val auth = authHeader()
            ?: return@withContext Result.failure(TransportException("no live session"))
        if (prompt.isBlank()) {
            return@withContext Result.failure(TransportException("Writing Room prompt is empty"))
        }
        if (prompt.length > 6000) {
            return@withContext Result.failure(TransportException("Writing Room prompt is too long"))
        }
        runCatching {
            val body = buildJsonObject {
                put("project_id", projectId)
                put("project_title", projectTitle)
                put("participant", participant)
                put("prompt", prompt)
            }.toString()
            val resp = post(baseUrl, "/api/app/writing-room/council", body, auth)
            if (resp.first == 401) {
                clear()
                throw TransportException("session expired")
            }
            requireOk(resp)
            json.decodeFromString(WritingRoomTurn.serializer(), resp.second)
        }
    }

    // ---- HTTP plumbing shared with the transport ------------------------------

    internal fun get(base: String, path: String, auth: String?): Pair<Int, String> =
        execute(Request.Builder().url(base + path).get().apply { auth?.let { header("Authorization", it) } }.build())

    internal fun post(base: String, path: String, body: String, auth: String?): Pair<Int, String> =
        execute(
            Request.Builder()
                .url(base + path)
                .post(body.toRequestBody(JSON_MEDIA))
                .apply { auth?.let { header("Authorization", it) } }
                .build(),
        )

    internal fun postSse(
        base: String,
        path: String,
        body: String,
        auth: String?,
        onEvent: (event: String, data: String) -> Unit,
    ): Int {
        val request = Request.Builder()
            .url(base + path)
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA))
            .apply { auth?.let { header("Authorization", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code !in 200..299) return response.code
            val source = response.body?.source() ?: return response.code
            var eventName = ""
            val data = StringBuilder()
            fun flush() {
                if (eventName.isNotBlank() || data.isNotEmpty()) {
                    onEvent(eventName.ifBlank { "message" }, data.toString())
                }
                eventName = ""
                data.setLength(0)
            }
            while (true) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.isEmpty() -> flush()
                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").trimStart())
                    }
                }
            }
            flush()
            return response.code
        }
    }

    private fun execute(request: Request): Pair<Int, String> =
        client.newCall(request).execute().use { resp -> resp.code to resp.body?.string().orEmpty() }

    @Serializable
    private data class LoginResponse(
        val authenticated: Boolean = false,
        val token: String = "",
        val expires_utc: String? = null,
        val user: AppUser = AppUser(),
    )

    @Serializable
    private data class SessionResponse(
        val authenticated: Boolean = false,
        val user: AppUser = AppUser(),
    )

    companion object {
        private const val PREFS = "jarvis_live_session"
        const val KEY_TOKEN = "token"
        const val KEY_BASE = "base_url"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_ROLE = "role"
        const val KEY_EXPIRES = "expires_utc"
        const val KEY_APP_SESSION = "app_session_id"
        const val KEY_DEVICE = "app_device_id"
        const val KEY_TURN_IDS = "turn_ids"
        const val KEY_TURN_PREFIX = "turn_"
        const val KEY_PROFILE = "chat_profile"
        const val KEY_PROFILE_CONV = "chat_profile_"

        internal val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun forContext(context: Context): JarvisAppSession = JarvisAppSession(PrefsStore(context))

        internal fun normalizeBase(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isBlank()) return null
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
            val host = runCatching { java.net.URI(withScheme).host }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: return null
            return withScheme
        }

        /**
         * Fail-closed URL policy: loopback, RFC1918, Tailscale CGNAT
         * (100.64.0.0/10), `*.ts.net` MagicDNS names, or a tailnet single-label
         * host. Anything else — any public hostname — is rejected so a stray
         * URL can never leak the bearer token off the approved private network.
         */
        fun isAllowedLiveHost(base: String): Boolean {
            val host = runCatching { java.net.URI(base).host }.getOrNull()?.lowercase() ?: return false
            if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
            if (host.endsWith(".ts.net")) return true
            ipv4(host)?.let { (a, b, _, _) ->
                if (a == 10 || a == 127) return true
                if (a == 100 && b in 64..127) return true // Tailscale CGNAT
                if (a == 172 && b in 16..31) return true
                if (a == 192 && b == 168) return true
            }
            // MagicDNS single-label names (e.g. desktop-l59hjk4) resolve only on the tailnet.
            return !host.contains('.')
        }

        private fun ipv4(host: String): IntArray? {
            val parts = host.split('.')
            if (parts.size != 4) return null
            val ints = parts.mapNotNull { it.toIntOrNull() }
            if (ints.size != 4 || ints.any { it !in 0..255 }) return null
            return ints.toIntArray()
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // SSE streams stay open during inference
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

internal fun requireOk(resp: Pair<Int, String>) {
    if (resp.first !in 200..299) {
        throw TransportException("HTTP ${resp.first}: ${resp.second.take(200)}")
    }
}
