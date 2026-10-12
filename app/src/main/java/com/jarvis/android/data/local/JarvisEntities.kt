package com.jarvis.android.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
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
@Entity(tableName = "pending_outbound")
data class PendingOutboundEntity(
    @PrimaryKey val clientRequestId: String,
    val conversationId: String,
    val text: String,
    val createdAtMs: Long,
    val attempts: Int = 0,
    val attachmentIds: String = "",
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

    @Query("SELECT * FROM messages WHERE clientRequestId = :rid AND role = 'user' LIMIT 1")
    suspend fun userMessage(rid: String): MessageEntity?

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

    @Query("SELECT attachmentIds FROM messages WHERE conversationId = :id")
    suspend fun conversationMessageAttachmentIds(id: String): List<String>

    @Query("SELECT attachmentIds FROM pending_outbound WHERE conversationId = :id")
    suspend fun conversationPendingAttachmentIds(id: String): List<String>

    @Query("DELETE FROM pending_outbound WHERE conversationId = :id")
    suspend fun deleteConversationPending(id: String)

    @Query("DELETE FROM conversations WHERE conversationId = :id")
    suspend fun deleteConversationRow(id: String)

    @Query("SELECT attachmentIds FROM messages")
    suspend fun allMessageAttachmentIds(): List<String>

    @Query("SELECT attachmentIds FROM pending_outbound")
    suspend fun allPendingAttachmentIds(): List<String>

    /** Remove local conversation rows atomically and return files with no remaining references. */
    @Transaction
    suspend fun deleteConversationAndGetOrphanAttachmentIds(id: String): List<String> {
        fun parseIds(values: List<String>): Set<String> = values
            .flatMap { value -> value.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val removedIds = parseIds(conversationMessageAttachmentIds(id) + conversationPendingAttachmentIds(id))
        deleteConversationPending(id)
        deleteConversationRow(id) // MessageEntity rows cascade with their parent conversation.
        val stillReferenced = parseIds(allMessageAttachmentIds() + allPendingAttachmentIds())
        return (removedIds - stillReferenced).toList()
    }

    @Query("UPDATE conversations SET lastCursorToken = :cursor WHERE conversationId = :id")
    suspend fun setCursor(id: String, cursor: String)
}
