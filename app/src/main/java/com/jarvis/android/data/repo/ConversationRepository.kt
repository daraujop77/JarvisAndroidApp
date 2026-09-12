package com.jarvis.android.data.repo

import com.jarvis.android.data.local.ConversationEntity
import com.jarvis.android.data.local.JarvisDao
import com.jarvis.android.data.local.MessageEntity
import com.jarvis.android.data.local.PendingOutboundEntity
import com.jarvis.android.data.state.RequestState
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.data.state.dbName
import com.jarvis.android.data.state.requestStatusFromName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val clientRequestId: String,
    val role: String, // user | assistant
    val text: String,
    val status: RequestStatus,
    val createdAtMs: Long,
    val attachmentIds: List<String> = emptyList(),
)

data class ConversationSummary(
    val conversationId: String,
    val title: String,
    val updatedAtMs: Long,
)

/**
 * Room-backed conversation store + reconciliation (plan AND-W2):
 *
 *  - optimistic user bubble persisted immediately, plus a `pending_outbound` row
 *    written BEFORE the request goes on the wire (crash-safe)
 *  - terminal states remove the pending row and store the final assistant text
 *  - [recover] re-sends pending outbound only when the server never accepted them,
 *    reusing the SAME clientRequestId so server-side idempotency prevents a
 *    duplicate message (plan AND-W2 gate: kill/recreate -> no duplicate outgoing)
 *
 * Rendering merges persisted Room rows with the live in-flight request from the
 * session snapshot so streaming text updates without a Room write per delta.
 */
class ConversationRepository(
    private val dao: JarvisDao,
    private val session: JarvisSessionRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _live = session.snapshot

    init {
        scope.launch {
            var lastToken = ""
            _live.collect { snap ->
                val token = snap.session.lastCursorToken
                if (token.isBlank() || token == lastToken) return@collect
                lastToken = token
                val cid = snap.session.requests.values.maxByOrNull { it.startedAtMs }?.conversationId
                    ?: return@collect
                runCatching { dao.setCursor(cid, token) }
            }
        }
    }

    fun observeConversations(): Flow<List<ConversationSummary>> =
        dao.observeConversations().map { list ->
            list.map { ConversationSummary(it.conversationId, it.title, it.updatedAtMs) }
        }

    /** Persisted history for [conversationId] merged with the live streaming request. */
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = combine(
        dao.observeMessages(conversationId),
        _live,
    ) { stored, snap ->
        val live = liveRequestFor(snap, conversationId)
        val persisted = stored.map {
            ChatMessage(
                clientRequestId = it.clientRequestId,
                role = it.role,
                text = it.text,
                status = requestStatusFromName(it.status),
                createdAtMs = it.createdAtMs,
                attachmentIds = it.attachmentIds.split(',').filter { a -> a.isNotBlank() },
            )
        }.toMutableList()
        if (live != null && !live.status.isTerminal) {
            val i = persisted.indexOfFirst { it.clientRequestId == live.clientRequestId && it.role == "assistant" }
            val row = ChatMessage(live.clientRequestId, "assistant", live.text, live.status, live.startedAtMs)
            if (i >= 0) persisted[i] = row else persisted += row
        }
        persisted.sortedWith(compareBy({ it.createdAtMs }, { if (it.role == "user") 0 else 1 }))
    }

    fun newConversation(onCreated: (String) -> Unit = {}) {
        val id = newConversationId()
        onCreated(id)
    }

    /** Generates the id synchronously; persists the conversation row asynchronously. */
    fun newConversationId(): String {
        val id = "conv_" + UUID.randomUUID().toString().take(8)
        scope.launch {
            dao.upsertConversation(ConversationEntity(id, "New chat", clock(), clock()))
        }
        return id
    }

    /** Send via the session repo, persisting the optimistic outbound first. */
    fun send(conversationId: String, text: String, attachmentIds: List<String> = emptyList()) {
        val cid = session.send(conversationId, text, attachmentIds)
        persistOutbound(cid, conversationId, text, attachmentIds)
        watchRequest(cid)
    }

    /**
     * The conversation row must exist before the message row: `messages` has a
     * foreign key on `conversationId`, so these writes are strictly ordered in a
     * single coroutine rather than racing in separate launches.
     */
    private fun persistOutbound(
        cid: String,
        conversationId: String,
        text: String,
        attachmentIds: List<String>,
    ) {
        val now = clock()
        val joined = attachmentIds.joinToString(",")
        scope.launch {
            ensureConversation(conversationId, text, now)
            dao.upsertPending(PendingOutboundEntity(cid, conversationId, text, now, attachmentIds = joined))
            dao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    clientRequestId = cid,
                    role = "user",
                    text = text,
                    status = RequestStatus.Pending.dbName,
                    createdAtMs = now,
                    attachmentIds = joined,
                )
            )
        }
    }

    fun cancelRequest(clientRequestId: String) = session.cancel(clientRequestId)

    /** Resend a failed request as a new message so Room stays the source of truth. */
    fun retry(clientRequestId: String) {
        val req = session.retryableRequest(clientRequestId) ?: return
        send(req.conversationId, req.userText)
    }

    /**
     * After process death: for each pending outbound, if the freshly reconnected
     * session has no live/terminal state for it, re-issue with the SAME key.
     */
    suspend fun recover() {
        val storedCursor = dao.observeConversations().first()
            .maxByOrNull { it.updatedAtMs }
            ?.lastCursorToken
            .orEmpty()
        if (storedCursor.isNotBlank()) session.restoreCursor(storedCursor)
        val pendings = dao.pendingOutbound()
        kotlinx.coroutines.withTimeoutOrNull(5_000) {
            _live.first { it.session.connection.isUsable }
        }
        if (storedCursor.isNotBlank()) session.replayNow()
        if (pendings.isEmpty()) return
        for (p in pendings) {
            val live = _live.value.session.requests[p.clientRequestId]
            if (live == null) {
                val cid = session.sendWithId(
                    p.clientRequestId, p.conversationId, p.text,
                    attachmentIds = p.attachmentIds.split(',').filter { it.isNotBlank() },
                )
                dao.upsertPending(p.copy(attempts = p.attempts + 1))
                watchRequest(cid)
            }
        }
    }

    // ---- session -> room projection ------------------------------------------

    /** One-shot watcher: mirrors a request into Room once it reaches a terminal state. */
    private fun watchRequest(clientRequestId: String) {
        synchronized(requestWatchers) {
            if (requestWatchers[clientRequestId]?.isActive == true) return
        }
        val job = scope.launch {
            try {
                _live.collect { snap ->
                    val req = snap.session.requests[clientRequestId] ?: return@collect
                    if (req.status.isTerminal) {
                        finalize(req, snap)
                        cancel() // settled: stop collecting
                    }
                }
            } finally {
                synchronized(requestWatchers) { requestWatchers.remove(clientRequestId) }
            }
        }
        synchronized(requestWatchers) { requestWatchers[clientRequestId] = job }
    }

    private val requestWatchers = mutableMapOf<String, kotlinx.coroutines.Job>()

    private suspend fun finalize(req: RequestState, snap: SessionSnapshot) {
        dao.deletePending(req.clientRequestId)
        val existing = dao.assistantMessage(req.clientRequestId)
        val status = req.status.dbName
        val text = assistantText(req)
        if (existing == null) {
            dao.insertMessage(
                MessageEntity(
                    conversationId = req.conversationId,
                    clientRequestId = req.clientRequestId,
                    role = "assistant",
                    text = text,
                    status = status,
                    createdAtMs = req.startedAtMs + 1,
                )
            )
        } else {
            dao.updateAssistantMessage(req.clientRequestId, text, status)
        }
        // Keep the original title/creation time; only bump activity + replay cursor.
        val existingConversation = dao.conversation(req.conversationId)
        dao.upsertConversation(
            ConversationEntity(
                conversationId = req.conversationId,
                title = existingConversation?.title ?: req.userText.take(40).ifBlank { "Chat" },
                createdAtMs = existingConversation?.createdAtMs ?: req.startedAtMs,
                updatedAtMs = clock(),
                lastCursorToken = snap.session.lastCursorToken,
            )
        )
    }

    private fun assistantText(req: RequestState): String = when (val s = req.status) {
        is RequestStatus.Failed -> s.error.message
        RequestStatus.Cancelled -> if (req.text.isBlank()) "(cancelled)" else req.text
        else -> req.text
    }

    private suspend fun ensureConversation(conversationId: String, firstText: String, now: Long) {
        if (dao.conversation(conversationId) == null) {
            dao.upsertConversation(
                ConversationEntity(conversationId, firstText.take(40).ifBlank { "Chat" }, now, now)
            )
        }
    }

    private fun liveRequestFor(snap: SessionSnapshot, conversationId: String): RequestState? =
        snap.session.requests.values
            .filter { it.conversationId == conversationId }
            .maxByOrNull { it.startedAtMs }
}
