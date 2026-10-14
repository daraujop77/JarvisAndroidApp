package com.jarvis.android.data.repo

import com.jarvis.android.data.local.ConversationEntity
import com.jarvis.android.data.local.JarvisDao
import com.jarvis.android.data.local.MessageEntity
import com.jarvis.android.data.local.PendingOutboundEntity
import com.jarvis.android.data.media.AttachmentStore
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val clientRequestId: String,
    val role: String, // user | assistant
    val text: String,
    val status: RequestStatus,
    val createdAtMs: Long,
    val attachmentIds: List<String> = emptyList(),
    val sources: List<ChatCitation> = emptyList(),
    val routeProvider: String = "",
    val routeModel: String = "",
    val routeKind: String = "",
    val routeDestination: String = "",
)

data class ConversationSummary(
    val conversationId: String,
    val title: String,
    val updatedAtMs: Long,
)

/** First read of the conversation list. Ready replaces Loading and never goes back. */
sealed interface ConversationListState {
    data object Loading : ConversationListState
    data class Ready(val conversations: List<ConversationSummary>) : ConversationListState
}

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
    private val attachmentStore: AttachmentStore? = null,
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

    /**
     * The conversation list plus whether the first read has happened. Room
     * emits its current rows immediately, so [ConversationListState.Loading]
     * only shows in the gap before that first emission — exactly when a cold
     * network start would otherwise make the screen look frozen.
     */
    fun observeConversationList(): Flow<ConversationListState> =
        observeConversations()
            .map<List<ConversationSummary>, ConversationListState> { ConversationListState.Ready(it) }
            .onStart { emit(ConversationListState.Loading) }

    /** Delete server history first, then atomically remove local rows and orphaned attachment files. */
    suspend fun deleteConversation(conversationId: String): Boolean {
        val id = conversationId.trim()
        if (id.isEmpty()) return false
        val isRunningHere = _live.value.session.requests.values.any {
            it.conversationId == id && !it.status.isTerminal
        }
        if (isRunningHere || !session.deleteConversation(id)) return false
        val orphanIds = dao.deleteConversationAndGetOrphanAttachmentIds(id)
        orphanIds.forEach { attachmentStore?.delete(it) }
        return true
    }

    /** Persisted history for [conversationId] merged with the live streaming request. */
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = combine(
        dao.observeMessages(conversationId),
        _live,
    ) { stored, snap ->
        val live = liveRequestFor(snap, conversationId)
        val persisted = stored
            .filterNot { isFakeGatewayArtifact(it.role, it.text) }
            .map {
            ChatMessage(
                clientRequestId = it.clientRequestId,
                role = it.role,
                text = it.text,
                status = requestStatusFromName(it.status),
                createdAtMs = it.createdAtMs,
                attachmentIds = it.attachmentIds.split(',').filter { a -> a.isNotBlank() },
                sources = ChatSourcesCodec.decode(it.sourcesJson),
            )
        }.toMutableList()
        if (live != null) {
            val i = persisted.indexOfFirst { it.clientRequestId == live.clientRequestId && it.role == "assistant" }
            val row = ChatMessage(
                clientRequestId = live.clientRequestId,
                role = "assistant",
                text = if (live.status.isTerminal && i >= 0) persisted[i].text else live.text,
                status = if (live.status.isTerminal && i >= 0) persisted[i].status else live.status,
                createdAtMs = if (i >= 0) persisted[i].createdAtMs else live.startedAtMs,
                attachmentIds = if (i >= 0) persisted[i].attachmentIds else emptyList(),
                sources = if (i >= 0) persisted[i].sources else emptyList(),
                routeProvider = live.routeProvider,
                routeModel = live.routeModel,
                routeKind = live.routeKind,
                routeDestination = live.routeDestination,
            )
            if (!live.status.isTerminal || i >= 0) {
                if (i >= 0) persisted[i] = row else persisted += row
            }
        }
        // Render each turn as USER -> JARVIS even while the assistant row is live.
        val turnTime = persisted.groupBy { it.clientRequestId }.mapValues { (_, rows) ->
            rows.firstOrNull { it.role == "user" }?.createdAtMs
                ?: rows.minOf { it.createdAtMs }
        }
        persisted.sortedWith(
            compareBy<ChatMessage>(
                { turnTime[it.clientRequestId] ?: it.createdAtMs },
                { if (it.role == "user") 0 else 1 },
                { it.createdAtMs },
            ),
        )
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

    /**
     * Send via the session repo, persisting the optimistic outbound first.
     * `session.send` stays synchronous (request appears in the snapshot at
     * once); the conversation history is read lazily inside the transport's
     * own dispatch coroutine so this never blocks or reorders the hot path.
     */
    fun send(conversationId: String, text: String, attachmentIds: List<String> = emptyList()) {
        val cid = session.send(conversationId, text, attachmentIds) { historyContext(conversationId, text.length) }
        persistOutbound(cid, conversationId, text, attachmentIds)
        watchRequest(cid)
    }

    /**
     * Completed conversation history for multi-turn context. Android keeps a
     * defensive envelope only; the backend remains authoritative for the final
     * model context budget.
     */
    private suspend fun historyContext(
        conversationId: String,
        newMessageChars: Int,
        maxMessages: Int = 255,
        maxChars: Int = (150_000 - newMessageChars).coerceAtLeast(0),
    ): List<com.jarvis.android.contract.ChatTurn> {
        val eligible = dao.messages(conversationId)
            .filter { it.status == RequestStatus.Completed.dbName }
            .filterNot { isFakeGatewayArtifact(it.role, it.text) }
            .map { com.jarvis.android.contract.ChatTurn(role = it.role, content = it.text) }
            .filter { it.content.isNotBlank() }

        val selectedReverse = ArrayList<com.jarvis.android.contract.ChatTurn>()
        var usedChars = 0
        for (index in eligible.indices.reversed()) {
            if (selectedReverse.size >= maxMessages) break
            val turn = eligible[index]
            if (usedChars + turn.content.length > maxChars) break
            selectedReverse += turn
            usedChars += turn.content.length
        }
        selectedReverse.reverse()
        return selectedReverse
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
        val now = _live.value.session.requests[cid]?.startedAtMs ?: clock()
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

    /** Attach generated media to the SAME assistant bubble as its answer.
     * A cached response can fire this callback twice; the DAO deduplicates
     * stable attachment IDs and rejects another conversation's request.
     */
    fun recordAgentGeneratedImage(conversationId: String, requestId: String, attachmentId: String) {
        scope.launch {
            // The optimistic user row is normally stored before network send.
            // If delivery races that initial insert, only wait locally; never
            // reissue the model/image operation to recover the attachment.
            repeat(5) {
                if (dao.attachGeneratedImageToAssistant(
                        conversationId, requestId, attachmentId, clock())) return@launch
                kotlinx.coroutines.delay(80)
            }
        }
    }

    /** Source metadata is persisted on the original signed request, never
     * guessed from assistant prose or another chat.
     */
    fun recordAgentSources(conversationId: String, requestId: String, sourcesJson: String) {
        if (ChatSourcesCodec.decode(sourcesJson).isEmpty()) return
        scope.launch {
            repeat(5) {
                if (dao.attachVerifiedSources(conversationId, requestId,
                        sourcesJson, clock())) return@launch
                kotlinx.coroutines.delay(80)
            }
        }
    }

    /** Persist one generated image as a normal user/assistant pair in the current conversation. */
    fun recordGeneratedImage(conversationId: String, prompt: String, attachmentId: String) {
        val requestId = "img_" + UUID.randomUUID().toString()
        val now = clock()
        scope.launch {
            ensureConversation(conversationId, prompt, now)
            dao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    clientRequestId = requestId,
                    role = "user",
                    text = prompt,
                    status = RequestStatus.Completed.dbName,
                    createdAtMs = now,
                ),
            )
            dao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    clientRequestId = requestId,
                    role = "assistant",
                    text = "",
                    status = RequestStatus.Completed.dbName,
                    createdAtMs = now + 1,
                    attachmentIds = attachmentId,
                ),
            )
            val existing = dao.conversation(conversationId)
            if (existing != null) {
                dao.upsertConversation(existing.copy(updatedAtMs = clock()))
            }
        }
    }

    fun cancelRequest(clientRequestId: String) = session.cancel(clientRequestId)

    /**
     * Resend a failed request as a new message so Room stays the source of truth.
     * After process death the session no longer holds the request, so the
     * original text and attachments come from the stored user message.
     */
    fun retry(clientRequestId: String) {
        val inSession = _live.value.session.requests[clientRequestId]
        val retryable = session.retryableRequest(clientRequestId)
        if (inSession != null && retryable == null) return
        scope.launch {
            val stored = dao.userMessage(clientRequestId)
            val conversationId = retryable?.conversationId ?: stored?.conversationId ?: return@launch
            val text = retryable?.userText ?: stored?.text ?: return@launch
            val attachmentIds = stored?.attachmentIds.orEmpty().split(',').filter { it.isNotBlank() }
            send(conversationId, text, attachmentIds)
        }
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
            if (live != null) continue
            // Reconciliation guard: finalize may have persisted the assistant
            // text in the crash window before deleting the pending row. If the
            // terminal answer is already durable, resending would overwrite it
            // — reconcile by dropping the pending instead.
            val persisted = dao.assistantMessage(p.clientRequestId)
            if (persisted != null && persisted.status == RequestStatus.Completed.dbName) {
                dao.deletePending(p.clientRequestId)
                continue
            }
            val recovered = runCatching {
                session.recoverCompletedTurn(p.clientRequestId, p.conversationId)
            }.getOrDefault(false)
            if (recovered) {
                watchRequest(p.clientRequestId)
                continue
            }
            val cid = session.sendWithId(
                p.clientRequestId, p.conversationId, p.text,
                attachmentIds = p.attachmentIds.split(',').filter { it.isNotBlank() },
                contextProvider = { historyContext(p.conversationId, p.text.length) },
            )
            dao.upsertPending(p.copy(attempts = p.attempts + 1))
            watchRequest(cid)
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
        dao.finalizeAssistant(
            conversationId = req.conversationId,
            requestId = req.clientRequestId,
            text = assistantText(req),
            status = req.status.dbName,
            createdAtMs = req.startedAtMs + 1,
        )
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

    private fun isFakeGatewayArtifact(role: String, text: String): Boolean {
        if (role != "assistant") return false
        val normalized = text.trim()
        if (normalized.isBlank()) return false
        return FAKE_WORDS.matches(normalized)
    }

    private fun liveRequestFor(snap: SessionSnapshot, conversationId: String): RequestState? =
        snap.session.requests.values
            .filter { it.conversationId == conversationId }
            .maxByOrNull { it.startedAtMs }

    private companion object {
        val FAKE_WORDS = Regex("""^(?:word\d+\s*)+$""")
    }
}
