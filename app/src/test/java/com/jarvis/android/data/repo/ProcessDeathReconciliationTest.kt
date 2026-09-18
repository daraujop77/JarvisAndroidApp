package com.jarvis.android.data.repo

import com.jarvis.android.data.local.ConversationDraftEntity
import com.jarvis.android.data.local.ConversationEntity
import com.jarvis.android.data.local.ConversationLocalMetaEntity
import com.jarvis.android.data.local.JarvisDao
import com.jarvis.android.data.local.MessageEntity
import com.jarvis.android.data.local.PendingOutboundEntity
import com.jarvis.android.data.local.StagedAttachmentEntity
import com.jarvis.android.data.state.RequestStatus
import kotlinx.coroutines.flow.first
import com.jarvis.android.transport.fake.FakeConfig
import com.jarvis.android.transport.fake.FakeGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PCB-R2 process-death: pending outbound + opaque cursor survive a new session
 * without duplicating text or leaking conversation A into B.
 */
class ProcessDeathReconciliationTest {

    private lateinit var job: Job
    private lateinit var scope: CoroutineScope
    private lateinit var dao: FakeDao

    @Before
    fun setUp() {
        job = SupervisorJob()
        scope = CoroutineScope(job + Dispatchers.Default)
        dao = FakeDao()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun <T> runBlockingShort(block: suspend kotlinx.coroutines.CoroutineScope.() -> T): T =
        kotlinx.coroutines.runBlocking(Dispatchers.Default, block = block)

    @Test
    fun processDeathReplaysFromOpaqueCursorWithoutDuplicateText() {
        val fake = FakeGateway(scope, FakeConfig(replyWords = List(4) { "w$it " }))
        val session = JarvisSessionRepository(fake, scope)
        val convos = ConversationRepository(dao, session, scope)
        session.start()
        runBlockingShort {
            withTimeout(2_000) { while (!session.snapshot.value.session.connection.isUsable) delay(10) }
        }

        convos.send("cA", "hello A")
        runBlockingShort {
            withTimeout(5_000) {
                while (session.snapshot.value.session.requests.values.none {
                        it.conversationId == "cA" && it.status == RequestStatus.Completed
                    }
                ) delay(10)
            }
        }
        val textBefore = session.snapshot.value.session.requests.values.first { it.conversationId == "cA" }.text
        val token = session.snapshot.value.session.lastCursorToken
        assertTrue(token.isNotBlank())
        runBlockingShort {
            withTimeout(3_000) {
                while (dao.conversation("cA")?.lastCursorToken != token) delay(10)
            }
        }

        session.stop()

        val session2 = JarvisSessionRepository(fake, scope)
        val convos2 = ConversationRepository(dao, session2, scope)
        session2.start()
        runBlockingShort {
            withTimeout(2_000) { while (!session2.snapshot.value.session.connection.isUsable) delay(10) }
            convos2.recover()
            delay(250)
        }
        assertTrue(session2.snapshot.value.session.lastCursorToken.isNotBlank())
        val after = session2.snapshot.value.session.requests.values
            .firstOrNull { it.conversationId == "cA" && it.status == RequestStatus.Completed }
        if (after != null) {
            assertEquals(textBefore, after.text)
        }
        val assistantRows = runBlockingShort { dao.messages("cA") }.filter { it.role == "assistant" }
        assertEquals(1, assistantRows.size)
        assertEquals(textBefore, assistantRows.single().text)
    }

    @Test
    fun conversationANeverAppearsInB() {
        val fake = FakeGateway(scope, FakeConfig(replyWords = listOf("alpha ")))
        val session = JarvisSessionRepository(fake, scope)
        val convos = ConversationRepository(dao, session, scope)
        session.start()
        runBlockingShort {
            withTimeout(2_000) { while (!session.snapshot.value.session.connection.isUsable) delay(10) }
        }
        convos.send("cA", "from A")
        convos.send("cB", "from B")
        runBlockingShort {
            withTimeout(5_000) {
                while (session.snapshot.value.session.requests.values.count { it.status == RequestStatus.Completed } < 2) {
                    delay(10)
                }
            }
            withTimeout(3_000) {
                while (dao.messages("cA").none { it.role == "user" } || dao.messages("cB").none { it.role == "user" }) {
                    delay(10)
                }
            }
        }
        val a = runBlockingShort { dao.messages("cA") }
        val b = runBlockingShort { dao.messages("cB") }
        assertTrue(a.any { it.role == "user" && it.text == "from A" })
        assertTrue(b.any { it.role == "user" && it.text == "from B" })
        assertTrue(a.none { it.text.contains("from B") })
        assertTrue(b.none { it.text.contains("from A") })
        assertTrue(a.none { it.conversationId != "cA" })
        assertTrue(b.none { it.conversationId != "cB" })
    }

    @Test
    fun concurrentStreamsKeepPerConversationMergeIsolated() {
        // Lane B checklist: two conversations streaming AT THE SAME TIME must
        // never cross through the Room+live merge: B's view only ever contains
        // rows belonging to B's requests, A's view only to A's.
        val fake = FakeGateway(scope, FakeConfig(wordDelayMs = 40, replyWords = List(15) { "w$it " }))
        val session = JarvisSessionRepository(fake, scope)
        val convos = ConversationRepository(dao, session, scope)
        session.start()
        runBlockingShort {
            withTimeout(2_000) { while (!session.snapshot.value.session.connection.isUsable) delay(10) }
        }
        convos.send("cA", "alpha")
        runBlockingShort {
            withTimeout(5_000) {
                while (session.snapshot.value.session.requests.values.none {
                        it.conversationId == "cA" && it.status == RequestStatus.Streaming
                    }
                ) delay(10)
            }
        }
        // Switch target conversation while A's stream is mid-flight.
        convos.send("cB", "beta")
        runBlockingShort {
            withTimeout(8_000) {
                while (session.snapshot.value.session.requests.values.count { it.status == RequestStatus.Completed } < 2) {
                    delay(10)
                }
            }
            withTimeout(3_000) {
                while (dao.messages("cA").none { it.role == "assistant" } ||
                    dao.messages("cB").none { it.role == "assistant" }
                ) delay(10)
            }
        }

        val idA = session.snapshot.value.session.requests.values
            .first { it.conversationId == "cA" }.clientRequestId
        val idB = session.snapshot.value.session.requests.values
            .first { it.conversationId == "cB" }.clientRequestId

        val aView = runBlockingShort { convos.observeMessages("cA").first() }
        val bView = runBlockingShort { convos.observeMessages("cB").first() }
        assertTrue(aView.all { it.clientRequestId == idA })
        assertTrue(bView.all { it.clientRequestId == idB })
        assertTrue(aView.any { it.role == "user" && it.text == "alpha" })
        assertTrue(bView.any { it.role == "user" && it.text == "beta" })
        assertTrue(aView.any { it.role == "assistant" && it.status == RequestStatus.Completed })
        assertTrue(bView.any { it.role == "assistant" && it.status == RequestStatus.Completed })
        assertEquals(
            (0 until 15).joinToString("") { "w$it " },
            aView.first { it.role == "assistant" }.text,
        )
    }

    @Test
    fun pendingOutboundResentWithSameClientRequestId() {
        runBlockingShort {
            dao.upsertPending(PendingOutboundEntity("rid_keep", "cA", "queued", 1L))
            dao.upsertConversation(ConversationEntity("cA", "Chat A", 1L, 1L, lastCursorToken = "tok_9"))
        }
        val fake = FakeGateway(scope, FakeConfig(replyWords = listOf("ok ")))
        val session = JarvisSessionRepository(fake, scope)
        val convos = ConversationRepository(dao, session, scope)
        session.start()
        runBlockingShort {
            withTimeout(2_000) { while (!session.snapshot.value.session.connection.isUsable) delay(10) }
            convos.recover()
            withTimeout(5_000) {
                while (session.snapshot.value.session.requests["rid_keep"]?.status != RequestStatus.Completed) {
                    delay(10)
                }
            }
        }
        assertEquals("rid_keep", session.snapshot.value.session.requests["rid_keep"]!!.clientRequestId)
        assertTrue(
            fake.receivedRequests.any {
                it is com.jarvis.android.contract.MobileRequest.SendMessage &&
                    it.clientRequestId == "rid_keep"
            },
        )
    }
}

private class FakeDao : JarvisDao {
    private val conversations = MutableStateFlow<Map<String, ConversationEntity>>(emptyMap())
    private val messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val pending = MutableStateFlow<Map<String, PendingOutboundEntity>>(emptyMap())
    private var nextId = 1L

    override fun observeConversations(): Flow<List<ConversationEntity>> =
        conversations.map { it.values.sortedByDescending { c -> c.updatedAtMs } }

    override suspend fun conversation(id: String): ConversationEntity? = conversations.value[id]

    override suspend fun upsertConversation(conversation: ConversationEntity) {
        conversations.update { it + (conversation.conversationId to conversation) }
    }

    override fun observeMessages(id: String): Flow<List<MessageEntity>> =
        messages.map { list -> list.filter { it.conversationId == id } }

    override suspend fun messages(id: String): List<MessageEntity> =
        messages.value.filter { it.conversationId == id }

    override suspend fun insertMessage(message: MessageEntity): Long {
        val row = if (message.id == 0L) message.copy(id = nextId++) else message
        messages.update { it + row }
        return row.id
    }

    override suspend fun upsertMessage(message: MessageEntity) {
        insertMessage(message)
    }

    override suspend fun updateAssistantMessage(rid: String, text: String, status: String) {
        messages.update { list ->
            list.map {
                if (it.clientRequestId == rid && it.role == "assistant") it.copy(text = text, status = status) else it
            }
        }
    }

    override suspend fun assistantMessage(rid: String): MessageEntity? =
        messages.value.firstOrNull { it.clientRequestId == rid && it.role == "assistant" }

    override suspend fun deleteAssistantMessage(rid: String) {
        messages.update { it.filterNot { m -> m.clientRequestId == rid && m.role == "assistant" } }
    }

    override suspend fun upsertPending(pending: PendingOutboundEntity) {
        this.pending.update { it + (pending.clientRequestId to pending) }
    }

    override suspend fun pendingOutbound(): List<PendingOutboundEntity> =
        pending.value.values.sortedBy { it.createdAtMs }

    override suspend fun deletePending(rid: String) {
        pending.update { it - rid }
    }

    override suspend fun pending(rid: String): PendingOutboundEntity? = pending.value[rid]

    override suspend fun setCursor(id: String, cursor: String) {
        conversations.update { map ->
            val cur = map[id] ?: return@update map
            map + (id to cur.copy(lastCursorToken = cursor))
        }
    }

    private val drafts = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun draft(id: String): String? = drafts.value[id]

    override fun observeDraft(id: String): Flow<String?> = drafts.map { it[id] }

    override suspend fun upsertDraft(draft: ConversationDraftEntity) {
        drafts.update { it + (draft.conversationId to draft.text) }
    }

    override suspend fun deleteDraft(id: String) {
        drafts.update { it - id }
    }

    private val localMeta = MutableStateFlow<Map<String, ConversationLocalMetaEntity>>(emptyMap())

    override fun observeLocalMeta(): Flow<List<ConversationLocalMetaEntity>> =
        localMeta.map { it.values.toList() }

    override suspend fun localMeta(id: String): ConversationLocalMetaEntity? = localMeta.value[id]

    override suspend fun upsertLocalMeta(meta: ConversationLocalMetaEntity) {
        localMeta.update { it + (meta.conversationId to meta) }
    }

    private val staged = MutableStateFlow<Map<String, StagedAttachmentEntity>>(emptyMap())

    override fun observeStagedAttachments(id: String): Flow<List<StagedAttachmentEntity>> =
        staged.map { rows -> rows.values.filter { it.conversationId == id } }

    override suspend fun stagedAttachments(id: String): List<StagedAttachmentEntity> =
        staged.value.values.filter { it.conversationId == id }

    override suspend fun stagedAttachment(id: String): StagedAttachmentEntity? = staged.value[id]

    override suspend fun upsertStagedAttachment(row: StagedAttachmentEntity) {
        staged.update { it + (row.attachmentId to row) }
    }

    override suspend fun deleteStagedAttachment(id: String) {
        staged.update { it - id }
    }
}
