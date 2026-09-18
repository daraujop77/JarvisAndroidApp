package com.jarvis.android.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "conversations",
    indices = [Index("updatedAtMs", unique = false)],
)
data class ConversationEntity(
    @PrimaryKey val conversationId: String,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    /** Opaque Web V1 replay token persisted so replay can resume after process death. */
    val lastCursorToken: String = "",
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["conversationId"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId"), Index("clientRequestId", unique = false)],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    /** Client idempotency key; assistant messages reuse the originating request's key. */
    val clientRequestId: String,
    val role: String, // "user" | "assistant"
    val text: String,
    val status: String, // RequestStatus name — drives pending/streaming/failed bubble
    val createdAtMs: Long,
    val attachmentIds: String = "", // comma-joined
)

/**
 * Outbound messages not yet confirmed ACCEPTED by the server. Persisting these
 * is what lets a kill/recreate restore the conversation *without duplicating*
 * the outgoing message (plan AND-W2 gate).
 */
/**
 * One unsent composer text per conversation. Local only: it is never an outbound
 * request and it is never sent by anything other than the explicit Send action.
 * Keyed by conversation so opening another conversation cannot read it.
 */
@Entity(tableName = "conversation_drafts")
data class ConversationDraftEntity(
    @PrimaryKey val conversationId: String,
    val text: String,
    val updatedAtMs: Long,
)

@Entity(tableName = "pending_outbound")
data class PendingOutboundEntity(
    @PrimaryKey val clientRequestId: String,
    val conversationId: String,
    val text: String,
    val createdAtMs: Long,
    val attempts: Int = 0,
    val attachmentIds: String = "",
)

/**
 * Device-only conversation chrome. Pin, hide, local display title and
 * last-opened time never leave this table, so archive/hide cannot delete
 * server or Room history in `conversations` / `messages`.
 */
@Entity(
    tableName = "conversation_local_meta",
    indices = [Index("archived"), Index("pinned")],
)
data class ConversationLocalMetaEntity(
    @PrimaryKey val conversationId: String,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val localTitle: String = "",
    val lastOpenedAtMs: Long = 0,
)

@Dao
interface JarvisDao {

    @Query("SELECT * FROM conversations ORDER BY updatedAtMs DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE conversationId = :id")
    suspend fun conversation(id: String): ConversationEntity?

    @Upsert
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAtMs ASC, id ASC")
    fun observeMessages(id: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAtMs ASC, id ASC")
    suspend fun messages(id: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    @Query("UPDATE messages SET text = :text, status = :status WHERE clientRequestId = :rid AND role = 'assistant'")
    suspend fun updateAssistantMessage(rid: String, text: String, status: String)

    @Query("SELECT * FROM messages WHERE clientRequestId = :rid AND role = 'assistant' LIMIT 1")
    suspend fun assistantMessage(rid: String): MessageEntity?

    @Query("DELETE FROM messages WHERE clientRequestId = :rid AND role = 'assistant'")
    suspend fun deleteAssistantMessage(rid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPending(pending: PendingOutboundEntity)

    @Query("SELECT * FROM pending_outbound ORDER BY createdAtMs ASC")
    suspend fun pendingOutbound(): List<PendingOutboundEntity>

    @Query("DELETE FROM pending_outbound WHERE clientRequestId = :rid")
    suspend fun deletePending(rid: String)

    @Query("SELECT * FROM pending_outbound WHERE clientRequestId = :rid")
    suspend fun pending(rid: String): PendingOutboundEntity?

    @Query("UPDATE conversations SET lastCursorToken = :cursor WHERE conversationId = :id")
    suspend fun setCursor(id: String, cursor: String)

    @Query("SELECT text FROM conversation_drafts WHERE conversationId = :id")
    suspend fun draft(id: String): String?

    @Query("SELECT text FROM conversation_drafts WHERE conversationId = :id")
    fun observeDraft(id: String): Flow<String?>

    @Upsert
    suspend fun upsertDraft(draft: ConversationDraftEntity)

    @Query("DELETE FROM conversation_drafts WHERE conversationId = :id")
    suspend fun deleteDraft(id: String)

    @Query("SELECT * FROM conversation_local_meta")
    fun observeLocalMeta(): Flow<List<ConversationLocalMetaEntity>>

    @Query("SELECT * FROM conversation_local_meta WHERE conversationId = :id")
    suspend fun localMeta(id: String): ConversationLocalMetaEntity?

    @Upsert
    suspend fun upsertLocalMeta(meta: ConversationLocalMetaEntity)
}
