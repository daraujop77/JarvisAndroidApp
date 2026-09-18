package com.jarvis.android.data.media

import com.jarvis.android.data.local.JarvisDao
import com.jarvis.android.data.local.StagedAttachmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One staged attachment as the composer knows it. [fileName] is the private
 * cache file name (`att_….jpg`), never a content URI and never a path that
 * leaves the device. Only [attachmentId] is allowed onto a request.
 */
data class StagedAttachmentRef(
    val attachmentId: String,
    val conversationId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val fileName: String,
    val stagedAtMs: Long,
)

/**
 * Room-backed composer attachments (A8).
 *
 * Pending chips are keyed by conversation, so opening another conversation
 * cannot show them, and a process restart restores exactly the conversation
 * they were staged in. Removing a chip deletes the row; the file delete is the
 * caller's job so this store stays free of filesystem types.
 */
class AttachmentStagingStore(
    private val dao: JarvisDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observe(conversationId: String): Flow<List<StagedAttachmentRef>> =
        dao.observeStagedAttachments(conversationId).map { rows -> rows.map { it.toRef() } }

    suspend fun staged(conversationId: String): List<StagedAttachmentRef> =
        dao.stagedAttachments(conversationId).map { it.toRef() }

    suspend fun find(attachmentId: String): StagedAttachmentRef? =
        dao.stagedAttachment(attachmentId)?.toRef()

    /** Idempotent: staging the same opaque id twice keeps the first row. */
    suspend fun remember(ref: StagedAttachmentRef) {
        if (dao.stagedAttachment(ref.attachmentId) != null) return
        dao.upsertStagedAttachment(ref.toEntity())
    }

    suspend fun rememberStaged(
        conversationId: String,
        staged: StagedAttachment,
    ): StagedAttachmentRef {
        val ref = StagedAttachmentRef(
            attachmentId = staged.attachmentId,
            conversationId = conversationId,
            displayName = staged.filename,
            mimeType = staged.mimeType,
            sizeBytes = staged.sizeBytes,
            fileName = staged.file.name,
            stagedAtMs = clock(),
        )
        remember(ref)
        return ref
    }

    /**
     * Drop the composer row. Returns the removed ref so the caller can delete
     * the private file; null when the id was already gone (idempotent remove).
     */
    suspend fun forget(attachmentId: String): StagedAttachmentRef? {
        val existing = dao.stagedAttachment(attachmentId) ?: return null
        dao.deleteStagedAttachment(attachmentId)
        return existing.toRef()
    }

    /** Send consumed these ids: they belong to the message now, not the composer. */
    suspend fun forgetAll(attachmentIds: Collection<String>) {
        attachmentIds.forEach { dao.deleteStagedAttachment(it) }
    }

    private fun StagedAttachmentEntity.toRef() = StagedAttachmentRef(
        attachmentId = attachmentId,
        conversationId = conversationId,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        fileName = fileName,
        stagedAtMs = stagedAtMs,
    )

    private fun StagedAttachmentRef.toEntity() = StagedAttachmentEntity(
        attachmentId = attachmentId,
        conversationId = conversationId,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        fileName = fileName,
        stagedAtMs = stagedAtMs,
    )
}
