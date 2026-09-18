package com.jarvis.android.data.local

import com.jarvis.android.data.repo.ConversationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.random.Random

/**
 * Drafts are local, per conversation, and never become requests. Search matches
 * titles only. The migration is additive and has no destructive fallback.
 */
class DraftSearchTest {

    private class MemoryDao : JarvisDao {
        val drafts = LinkedHashMap<String, ConversationDraftEntity>()
        private val observed = MutableStateFlow<Map<String, String>>(emptyMap())

        override suspend fun draft(id: String): String? = drafts[id]?.text

        override fun observeDraft(id: String): Flow<String?> =
            observed.map { it[id] }

        override suspend fun upsertDraft(draft: ConversationDraftEntity) {
            drafts[draft.conversationId] = draft
            observed.value = drafts.mapValues { it.value.text }
        }

        override suspend fun deleteDraft(id: String) {
            drafts.remove(id)
            observed.value = drafts.mapValues { it.value.text }
        }

        override fun observeLocalMeta() = error("unused")
        override suspend fun localMeta(id: String) = error("unused")
        override suspend fun upsertLocalMeta(meta: ConversationLocalMetaEntity) = error("unused")

        override fun observeConversations() = error("unused")
        override suspend fun conversation(id: String) = error("unused")
        override suspend fun upsertConversation(conversation: ConversationEntity) = error("unused")
        override fun observeMessages(id: String) = error("unused")
        override suspend fun messages(id: String) = error("unused")
        override suspend fun insertMessage(message: MessageEntity) = error("unused")
        override suspend fun upsertMessage(message: MessageEntity) = error("unused")
        override suspend fun updateAssistantMessage(rid: String, text: String, status: String) = error("unused")
        override suspend fun assistantMessage(rid: String) = error("unused")
        override suspend fun deleteAssistantMessage(rid: String) = error("unused")
        override suspend fun upsertPending(pending: PendingOutboundEntity) = error("unused")
        override suspend fun pendingOutbound() = error("unused")
        override suspend fun deletePending(rid: String) = error("unused")
        override suspend fun pending(rid: String) = error("unused")
        override suspend fun setCursor(id: String, cursor: String) = error("unused")
    }

    @Test
    fun draftIsIsolatedPerConversationAndClearsWhenBlank() = runBlocking {
        val dao = MemoryDao()
        val store = DraftSearchStore(dao, clock = { 1000L })
        store.saveDraft("conv-a", "hola 🌞")
        store.saveDraft("conv-b", "otro texto")

        assertEquals("hola 🌞", dao.draft("conv-a"))
        assertEquals("otro texto", dao.draft("conv-b"))
        // Opening conv-b cannot surface conv-a's draft.
        assertEquals("otro texto", store.observeDraft("conv-b").first())

        store.saveDraft("conv-a", "   ")
        assertEquals(null, dao.draft("conv-a"))
        assertEquals("otro texto", dao.draft("conv-b"))
    }

    @Test
    fun draftIsBoundedAndNeverStoredAsARequest() = runBlocking {
        val dao = MemoryDao()
        val store = DraftSearchStore(dao)
        val long = "x".repeat(store.maxDraftChars + 500)
        store.saveDraft("conv-a", long)
        assertEquals(store.maxDraftChars, dao.draft("conv-a")!!.length)
        // The store has no send path: the only rows it writes are drafts.
        assertEquals(1, dao.drafts.size)
    }

    @Test
    fun unicodeEmojiAndLongTextRoundTrip() = runBlocking {
        val dao = MemoryDao()
        val store = DraftSearchStore(dao)
        val text = "línea uno\nemoji 🚀🔥\n" + "á".repeat(3000)
        store.saveDraft("conv-a", text)
        assertEquals(text.take(store.maxDraftChars), dao.draft("conv-a"))
    }

    @Test
    fun searchMatchesTitleOnlyAndKeepsOrderWhenEmpty() {
        val store = DraftSearchStore(MemoryDao())
        val list = listOf(
            ConversationSummary("c1", "Amber Bell", 3),
            ConversationSummary("c2", "Salt Meridian", 2),
            ConversationSummary("c3", "amber notes", 1),
        )
        assertEquals(list, store.filter(list, ""))
        assertEquals(list, store.filter(list, "   "))
        val found = store.filter(list, "amber")
        assertEquals(listOf("c1", "c3"), found.map { it.conversationId })
        assertTrue(store.filter(list, "no-such").isEmpty())
    }

    @Test
    fun randomizedSaveSwitchSearchCyclesStayIsolated() = runBlocking {
        val dao = MemoryDao()
        val store = DraftSearchStore(dao)
        val conversations = (1..6).map { "conv-$it" }
        val random = Random(42)
        val expected = HashMap<String, String>()
        val titles = conversations.map {
            ConversationSummary(it, "Title $it amber", it.hashCode().toLong())
        }
        repeat(200) {
            val id = conversations[random.nextInt(conversations.size)]
            val text = when (random.nextInt(4)) {
                0 -> ""
                1 -> "nota ${random.nextInt(1000)} 🌟"
                2 -> "x".repeat(random.nextInt(store.maxDraftChars + 200))
                else -> "amber ${random.nextInt(50)}"
            }
            store.saveDraft(id, text)
            expected[id] = text.take(store.maxDraftChars).let { saved ->
                if (saved.isBlank()) "" else saved
            }
            // Switching to a different conversation must not move the draft.
            val other = conversations.first { it != id }
            assertEquals(expected[other].orEmpty(), dao.draft(other).orEmpty())
        }
        conversations.forEach { id ->
            assertEquals(expected[id].orEmpty(), dao.draft(id).orEmpty())
        }
        assertFalse(store.filter(titles, "amber").isEmpty())
        assertEquals(titles, store.filter(titles, ""))
    }

    @Test
    fun migrationChainToV4AddsDraftsWithoutTouchingExistingRows() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
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
                val cursor = st.executeQuery("SELECT lastCursorToken FROM conversations WHERE conversationId='cA'")
                assertTrue(cursor.next())
                assertEquals("77", cursor.getString(1))
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
                // The new table exists and starts empty: nothing was invented.
                val drafts = st.executeQuery("SELECT COUNT(*) FROM conversation_drafts")
                assertTrue(drafts.next())
                assertEquals(0, drafts.getInt(1))
                drafts.close()
            }
        }
    }
}
