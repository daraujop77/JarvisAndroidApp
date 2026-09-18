package com.jarvis.android.data.local

import com.jarvis.android.data.repo.ConversationSummary
import com.jarvis.android.ui.screens.ConversationA11y
import com.jarvis.android.ui.theme.JarvisMotion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Pin / hide / local title / recency are device chrome only. Hide never deletes
 * Room conversations, messages, pending outbound, cursor or drafts.
 */
class ConversationProductivityTest {

    private class MemoryDao : JarvisDao {
        val conversations = LinkedHashMap<String, ConversationEntity>()
        val messages = mutableListOf<MessageEntity>()
        val pending = LinkedHashMap<String, PendingOutboundEntity>()
        val drafts = LinkedHashMap<String, ConversationDraftEntity>()
        val meta = LinkedHashMap<String, ConversationLocalMetaEntity>()
        private val observedMeta = MutableStateFlow<List<ConversationLocalMetaEntity>>(emptyList())

        override fun observeLocalMeta(): Flow<List<ConversationLocalMetaEntity>> = observedMeta

        override suspend fun localMeta(id: String): ConversationLocalMetaEntity? = meta[id]

        override suspend fun upsertLocalMeta(meta: ConversationLocalMetaEntity) {
            this.meta[meta.conversationId] = meta
            observedMeta.value = this.meta.values.toList()
        }

        override fun observeStagedAttachments(id: String) = error("unused")
        override suspend fun stagedAttachments(id: String) = error("unused")
        override suspend fun stagedAttachment(id: String) = error("unused")
        override suspend fun upsertStagedAttachment(staged: StagedAttachmentEntity) = error("unused")
        override suspend fun deleteStagedAttachment(id: String) = error("unused")

        override fun observeConversations(): Flow<List<ConversationEntity>> =
            MutableStateFlow(conversations.values.toList())

        override suspend fun conversation(id: String) = conversations[id]

        override suspend fun upsertConversation(conversation: ConversationEntity) {
            conversations[conversation.conversationId] = conversation
        }

        override fun observeMessages(id: String) = error("unused")
        override suspend fun messages(id: String) = messages.filter { it.conversationId == id }
        override suspend fun insertMessage(message: MessageEntity): Long {
            messages += message
            return messages.size.toLong()
        }
        override suspend fun upsertMessage(message: MessageEntity) { insertMessage(message) }
        override suspend fun updateAssistantMessage(rid: String, text: String, status: String) = error("unused")
        override suspend fun assistantMessage(rid: String) = error("unused")
        override suspend fun deleteAssistantMessage(rid: String) = error("unused")
        override suspend fun upsertPending(pending: PendingOutboundEntity) {
            this.pending[pending.clientRequestId] = pending
        }
        override suspend fun pendingOutbound() = pending.values.toList()
        override suspend fun deletePending(rid: String) { pending.remove(rid) }
        override suspend fun pending(rid: String) = pending[rid]
        override suspend fun setCursor(id: String, cursor: String) {
            conversations[id] = conversations.getValue(id).copy(lastCursorToken = cursor)
        }
        override suspend fun draft(id: String) = drafts[id]?.text
        override fun observeDraft(id: String): Flow<String?> = MutableStateFlow(drafts[id]?.text)
        override suspend fun upsertDraft(draft: ConversationDraftEntity) {
            drafts[draft.conversationId] = draft
        }
        override suspend fun deleteDraft(id: String) { drafts.remove(id) }

        suspend fun metaMap(): Map<String, ConversationLocalMetaEntity> =
            observeLocalMeta().first().associateBy { it.conversationId }
    }

    @Test
    fun pinAndLocalTitleDoNotMutateStoredConversation() = runBlocking {
        val dao = MemoryDao()
        dao.upsertConversation(ConversationEntity("c1", "Server title", 1, 10, "tok"))
        val store = ConversationProductivityStore(dao, clock = { 50L })
        store.setPinned("c1", true)
        store.setLocalTitle("c1", "  Kitchen list  ")
        assertEquals("Server title", dao.conversation("c1")!!.title)
        assertEquals("tok", dao.conversation("c1")!!.lastCursorToken)
        val item = store.overlay(listOf(ConversationSummary("c1", "Server title", 10)), dao.metaMap()).single()
        assertEquals("Kitchen list", item.displayTitle)
        assertTrue(item.pinned)
        assertTrue(item.locallyRenamed)
        assertEquals("Server title", item.storedTitle)
    }

    @Test
    fun hideIsLocalOnlyAndDoesNotDeleteHistory() = runBlocking {
        val dao = MemoryDao()
        dao.upsertConversation(ConversationEntity("c1", "Keep me", 1, 2, "cursor-9"))
        dao.insertMessage(MessageEntity(1, "c1", "r1", "user", "hello", "Completed", 1, ""))
        dao.upsertPending(PendingOutboundEntity("r2", "c1", "queued", 2, 1, "att"))
        dao.upsertDraft(ConversationDraftEntity("c1", "draft", 3))
        val store = ConversationProductivityStore(dao)
        store.setArchived(listOf("c1"), archived = true)

        assertEquals(1, dao.conversations.size)
        assertEquals(1, dao.messages.size)
        assertEquals("queued", dao.pending["r2"]!!.text)
        assertEquals("draft", dao.drafts["c1"]!!.text)
        assertEquals("cursor-9", dao.conversation("c1")!!.lastCursorToken)
        assertTrue(dao.meta["c1"]!!.archived)

        val visible = store.overlay(
            listOf(ConversationSummary("c1", "Keep me", 2)),
            dao.metaMap(),
        )
        assertTrue(visible.isEmpty())
        val hidden = store.overlay(
            listOf(ConversationSummary("c1", "Keep me", 2)),
            dao.metaMap(),
            showHidden = true,
        )
        assertEquals(listOf("c1"), hidden.map { it.conversationId })
    }

    @Test
    fun pinsThenLocalRecencySortWithoutServerUnread() = runBlocking {
        var now = 400L
        val dao = MemoryDao()
        val store = ConversationProductivityStore(dao, clock = { now })
        val summaries = listOf(
            ConversationSummary("old", "Old", 100),
            ConversationSummary("fresh", "Fresh", 300),
            ConversationSummary("pin", "Pinned", 50),
        )
        store.setPinned("pin", true)
        store.markOpened("old")
        val overlay = store.overlay(summaries, dao.metaMap())
        assertEquals(listOf("pin", "old", "fresh"), overlay.map { it.conversationId })
        val old = overlay.first { it.conversationId == "old" }
        assertFalse(old.activitySinceOpen)
        now = 200L
        store.markOpened("fresh")
        val after = store.decorate(summaries, dao.metaMap())
            .first { it.conversationId == "fresh" }
        assertTrue(after.activitySinceOpen)
    }

    @Test
    fun searchMatchesLocalTitleAndStoredTitle() = runBlocking {
        val dao = MemoryDao()
        val store = ConversationProductivityStore(dao)
        store.setLocalTitle("c2", "Garden notes")
        val list = listOf(
            ConversationSummary("c1", "Amber Bell", 3),
            ConversationSummary("c2", "Salt Meridian", 2),
        )
        val found = store.overlay(list, dao.metaMap(), query = "garden")
        assertEquals(listOf("c2"), found.map { it.conversationId })
        assertEquals(listOf("c1"), store.overlay(list, dao.metaMap(), query = "amber").map { it.conversationId })
    }

    @Test
    fun shareSafeTextRefusesCredentialsAndPayloads() {
        assertEquals("Hello JARVIS", ShareSafeText.visibleChatText("Hello JARVIS"))
        assertNull(ShareSafeText.visibleChatText("Authorization: Bearer abc"))
        assertNull(ShareSafeText.visibleChatText("token=super-secret"))
        assertNull(ShareSafeText.visibleChatText("""{"type":"message.completed","payload":{}}"""))
        assertNull(ShareSafeText.visibleChatText("clientRequestId=r1 lastCursorToken=abc"))
        assertNull(ShareSafeText.visibleChatText("   "))
    }

    @Test
    fun reducedMotionCollapsesDurations() {
        assertEquals(0, JarvisMotion.durationMs(reducedMotion = true, durationMs = 280))
        assertEquals(280, JarvisMotion.durationMs(reducedMotion = false, durationMs = 280))
        assertNull(JarvisMotion.loopMs(reducedMotion = true, durationMs = 1500))
        assertEquals(1500, JarvisMotion.loopMs(reducedMotion = false, durationMs = 1500))
    }

    @Test
    fun a11yLabelsStayExplicitAndLocal() {
        assertTrue(ConversationA11y.HIDE_ON_DEVICE.contains("Server history stays"))
        assertTrue(ConversationA11y.HIDE_CONFIRMATION.contains("Server history"))
        assertTrue(ConversationA11y.conversationRow("Kitchen", pinned = true, activitySinceOpen = true).contains("Pinned on this device"))
        assertEquals(ConversationA11y.UNPIN_ON_DEVICE, ConversationA11y.pinAction(true))
    }

    @Test
    fun migration4to5AddsLocalMetaWithoutTouchingExistingRows() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            seedV1(conn)
            conn.createStatement().use { st ->
                st.execute("INSERT INTO conversations VALUES ('cA','Chat A',1,2,77)")
                st.execute("INSERT INTO messages VALUES (1,'cA','r1','user','hi','Completed',1,'')")
                st.execute("INSERT INTO pending_outbound VALUES ('r2','cA','queued',2,3)")
            }
            fun run(migrate: ((String) -> Unit) -> Unit) {
                conn.autoCommit = false
                migrate { sql -> conn.createStatement().use { it.execute(sql) } }
                conn.commit()
            }
            run { JarvisDatabase.applyMigration1to2(it) }
            run { JarvisDatabase.applyMigration2to3(it) }
            run { JarvisDatabase.applyMigration3to4(it) }
            conn.createStatement().use { st ->
                st.execute("INSERT INTO conversation_drafts VALUES ('cA','unsent',9)")
            }
            run { JarvisDatabase.applyMigration4to5(it) }

            conn.createStatement().use { st ->
                val cursor = st.executeQuery("SELECT lastCursorToken, title FROM conversations WHERE conversationId='cA'")
                assertTrue(cursor.next())
                assertEquals("77", cursor.getString(1))
                assertEquals("Chat A", cursor.getString(2))
                cursor.close()
                val messages = st.executeQuery("SELECT COUNT(*) FROM messages")
                assertTrue(messages.next())
                assertEquals(1, messages.getInt(1))
                messages.close()
                val pending = st.executeQuery("SELECT text, attempts FROM pending_outbound")
                assertTrue(pending.next())
                assertEquals("queued", pending.getString(1))
                assertEquals(3, pending.getInt(2))
                pending.close()
                val drafts = st.executeQuery("SELECT text FROM conversation_drafts WHERE conversationId='cA'")
                assertTrue(drafts.next())
                assertEquals("unsent", drafts.getString(1))
                drafts.close()
                val meta = st.executeQuery("SELECT COUNT(*) FROM conversation_local_meta")
                assertTrue(meta.next())
                assertEquals(0, meta.getInt(1))
                meta.close()
            }
        }
    }

    private fun seedV1(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE `conversations` (
                    `conversationId` TEXT NOT NULL, `title` TEXT NOT NULL,
                    `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL,
                    `lastCursor` INTEGER NOT NULL, PRIMARY KEY(`conversationId`)
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE `messages` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `conversationId` TEXT NOT NULL, `clientRequestId` TEXT NOT NULL,
                    `role` TEXT NOT NULL, `text` TEXT NOT NULL, `status` TEXT NOT NULL,
                    `createdAtMs` INTEGER NOT NULL, `attachmentIds` TEXT NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE `pending_outbound` (
                    `clientRequestId` TEXT NOT NULL, `conversationId` TEXT NOT NULL,
                    `text` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL,
                    `attempts` INTEGER NOT NULL, PRIMARY KEY(`clientRequestId`)
                )
                """.trimIndent(),
            )
        }
    }
}
