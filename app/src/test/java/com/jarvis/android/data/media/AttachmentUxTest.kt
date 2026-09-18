package com.jarvis.android.data.media

import com.jarvis.android.contract.ErrorEnvelope
import com.jarvis.android.data.local.ConversationDraftEntity
import com.jarvis.android.data.local.ConversationEntity
import com.jarvis.android.data.local.ConversationLocalMetaEntity
import com.jarvis.android.data.local.JarvisDao
import com.jarvis.android.data.local.JarvisDatabase
import com.jarvis.android.data.local.MessageEntity
import com.jarvis.android.data.local.PendingOutboundEntity
import com.jarvis.android.data.local.StagedAttachmentEntity
import com.jarvis.android.data.state.AttachmentUiState
import com.jarvis.android.ui.screens.attachmentChipDescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.random.Random

/**
 * A8: upload intent is never presented as uploaded, failure is retryable,
 * composer chips are conversation-scoped and survive a process restart, and
 * the migration that stores them is additive.
 */
class AttachmentUxTest {

    @Test
    fun uploadIntentIsNeverPresentedAsUploaded() {
        val intent = AttachmentUiState(
            attachmentId = "att_abc123", requestId = null, kind = "image",
            mimeType = "image/jpeg", sizeBytes = 10, ready = false, uploading = true,
        )
        val chip = AttachmentUx.chip("att_abc123", intent)
        assertEquals(AttachmentPhase.UPLOAD_INTENT, chip.phase)
        assertEquals(AttachmentUx.LABEL_UPLOAD_INTENT, chip.label)
        assertFalse(chip.phase.isUploaded)
        assertTrue(chip.showProgress)
        assertFalse(chip.canRetry)
        assertFalse(chip.label.contains("UPLOAD", ignoreCase = true) && chip.phase.isUploaded)

        val uploaded = intent.copy(uploading = false, ready = true)
        val done = AttachmentUx.chip("att_abc123", uploaded)
        assertEquals(AttachmentPhase.UPLOADED, done.phase)
        assertTrue(done.phase.isUploaded)
        assertFalse(done.canRetry)
        assertFalse("a ready attachment must not offer retry", AttachmentUx.canRetry(uploaded))
    }

    @Test
    fun failureIsDeterministicAndRetryableUntilRemoved() {
        val failed = AttachmentUiState(
            attachmentId = "att_fail", requestId = null, kind = "image",
            mimeType = "image/jpeg", sizeBytes = 10, ready = false, uploading = false,
            error = ErrorEnvelope("not_connected", "offline"),
        )
        val chip = AttachmentUx.chip("att_fail", failed)
        assertEquals(AttachmentPhase.FAILED, chip.phase)
        assertTrue(chip.canRetry)
        assertTrue(chip.canRemove)
        assertFalse(chip.showProgress)
        assertEquals("offline", chip.detail)
        assertTrue(AttachmentUx.canRetry(failed))

        // No state at all is a local preview, not a failure and not an upload.
        val preview = AttachmentUx.chip("att_new", null)
        assertEquals(AttachmentPhase.PREVIEW_READY, preview.phase)
        assertFalse(preview.phase.isUploaded)
        assertFalse(AttachmentUx.canRetry(null))
    }

    @Test
    fun chipDescriptionNeverExposesIdOrPath() {
        val chip = AttachmentUx.chip(
            "att_secret1234",
            AttachmentUiState(
                "att_secret1234", null, "image", "image/jpeg", 1, ready = false,
                error = ErrorEnvelope("upload_failed", "rejected"),
            ),
        )
        val spoken = attachmentChipDescription(chip)
        assertFalse(spoken.contains("att_"))
        assertFalse(spoken.contains("content://"))
        assertFalse(spoken.contains("/"))
        assertTrue(spoken.contains("failed"))
    }

    @Test
    fun stagingIsIsolatedPerConversationAndSurvivesRestart() = runBlocking {
        val dao = MemoryDao()
        val first = AttachmentStagingStore(dao, clock = { 1000L })
        val photo = StagedAttachmentRef(
            attachmentId = "att_photo0001", conversationId = "conv-a",
            displayName = "kitchen.jpg", mimeType = "image/jpeg", sizeBytes = 42,
            fileName = "att_photo0001.jpg", stagedAtMs = 0,
        )
        first.remember(photo)
        first.remember(photo) // idempotent
        assertEquals(1, dao.rows.size)

        // A second store is what a recreated process gets: same rows, same scope.
        val restarted = AttachmentStagingStore(dao)
        assertEquals(listOf("att_photo0001"), restarted.observe("conv-a").first().map { it.attachmentId })
        assertTrue(restarted.observe("conv-b").first().isEmpty())

        val removed = restarted.forget("att_photo0001")
        assertEquals("att_photo0001.jpg", removed!!.fileName)
        assertNull(restarted.find("att_photo0001"))
        assertTrue("removing twice is a no-op", restarted.forget("att_photo0001") == null)
    }

    @Test
    fun switchingConversationNeverLeaksChips() = runBlocking {
        val dao = MemoryDao()
        val store = AttachmentStagingStore(dao, clock = { 5L })
        repeat(3) { n ->
            store.remember(ref("att_a$n", "conv-a"))
            store.remember(ref("att_b$n", "conv-b"))
        }
        val seenByA = store.observe("conv-a").first().map { it.attachmentId }
        val seenByB = store.staged("conv-b").map { it.attachmentId }
        assertEquals(listOf("att_a0", "att_a1", "att_a2"), seenByA)
        assertTrue(seenByA.none { it in seenByB })
        assertTrue(seenByB.none { it in seenByA })
    }

    @Test
    fun randomizedStageSwitchRemoveKeepsIsolation() = runBlocking {
        val dao = MemoryDao()
        val store = AttachmentStagingStore(dao, clock = { 7L })
        val random = Random(8)
        val open = mutableMapOf<String, MutableSet<String>>()
        repeat(400) {
            val conversation = "conv-${random.nextInt(5)}"
            when (random.nextInt(3)) {
                0 -> {
                    val id = "att_${random.nextInt(10_000)}"
                    store.remember(ref(id, conversation))
                    open.getOrPut(conversation) { mutableSetOf() } += id
                }
                1 -> {
                    val id = open[conversation]?.firstOrNull()
                    if (id != null) {
                        store.forget(id)
                        open.getValue(conversation).remove(id)
                    }
                }
                else -> {
                    // Opening this conversation must show exactly its own chips.
                    val shown = store.staged(conversation).map { it.attachmentId }.toSet()
                    assertEquals(open[conversation].orEmpty(), shown)
                    open.filterKeys { it != conversation }.values.flatten().forEach { foreign ->
                        assertFalse(foreign in shown)
                    }
                }
            }
        }
    }

    @Test
    fun migration5to6AddsStagingTableWithoutTouchingExistingRows() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            seedV1(conn)
            conn.createStatement().use { st ->
                st.execute("INSERT INTO conversations VALUES ('cA','Chat A',1,2,77)")
                st.execute("INSERT INTO messages VALUES (1,'cA','r1','user','hi','Completed',1,'att_old')")
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
                st.execute("INSERT INTO conversation_local_meta VALUES ('cA',1,0,'Renamed',4)")
            }
            run { JarvisDatabase.applyMigration5to6(it) }

            conn.createStatement().use { st ->
                val cursor = st.executeQuery("SELECT lastCursorToken, title FROM conversations WHERE conversationId='cA'")
                assertTrue(cursor.next())
                assertEquals("77", cursor.getString(1))
                assertEquals("Chat A", cursor.getString(2))
                cursor.close()
                val messages = st.executeQuery("SELECT attachmentIds FROM messages")
                assertTrue(messages.next())
                assertEquals("att_old", messages.getString(1))
                messages.close()
                val pending = st.executeQuery("SELECT text, attempts FROM pending_outbound")
                assertTrue(pending.next())
                assertEquals("queued", pending.getString(1))
                assertEquals(3, pending.getInt(2))
                pending.close()
                val drafts = st.executeQuery("SELECT text FROM conversation_drafts")
                assertTrue(drafts.next())
                assertEquals("unsent", drafts.getString(1))
                drafts.close()
                val meta = st.executeQuery("SELECT localTitle FROM conversation_local_meta")
                assertTrue(meta.next())
                assertEquals("Renamed", meta.getString(1))
                meta.close()
                val staged = st.executeQuery("SELECT COUNT(*) FROM staged_attachments")
                assertTrue(staged.next())
                assertEquals("migration must not invent chips", 0, staged.getInt(1))
                staged.close()
            }
        }
    }

    @Test
    fun opaqueIdsRejectAnythingThatCouldBeAPath() {
        assertTrue(AttachmentStore.isOpaqueId("att_0123456789ab"))
        assertFalse(AttachmentStore.isOpaqueId("../etc/passwd"))
        assertFalse(AttachmentStore.isOpaqueId("content://media/1"))
        assertFalse(AttachmentStore.isOpaqueId("/data/user/0/files/att_0123456789ab.jpg"))
        assertFalse(AttachmentStore.isOpaqueId(""))
    }

    private fun ref(id: String, conversationId: String) = StagedAttachmentRef(
        attachmentId = id, conversationId = conversationId, displayName = "p.jpg",
        mimeType = "image/jpeg", sizeBytes = 1, fileName = "$id.jpg", stagedAtMs = 0,
    )

    private class MemoryDao : JarvisDao {
        val rows = LinkedHashMap<String, StagedAttachmentEntity>()
        private val observed = MutableStateFlow<List<StagedAttachmentEntity>>(emptyList())

        override fun observeStagedAttachments(id: String): Flow<List<StagedAttachmentEntity>> =
            observed.map { list -> list.filter { it.conversationId == id } }

        override suspend fun stagedAttachments(id: String) = rows.values.filter { it.conversationId == id }

        override suspend fun stagedAttachment(id: String) = rows[id]

        override suspend fun upsertStagedAttachment(staged: StagedAttachmentEntity) {
            rows[staged.attachmentId] = staged
            observed.value = rows.values.toList()
        }

        override suspend fun deleteStagedAttachment(id: String) {
            rows.remove(id)
            observed.value = rows.values.toList()
        }

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
        override suspend fun draft(id: String) = error("unused")
        override fun observeDraft(id: String) = error("unused")
        override suspend fun upsertDraft(draft: ConversationDraftEntity) = error("unused")
        override suspend fun deleteDraft(id: String) = error("unused")
        override fun observeLocalMeta() = error("unused")
        override suspend fun localMeta(id: String) = error("unused")
        override suspend fun upsertLocalMeta(meta: ConversationLocalMetaEntity) = error("unused")
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
