package com.jarvis.android.data.media

import com.jarvis.android.data.state.AttachmentUiState

/**
 * Local presentation of one staged attachment (A8). This is UI state derived
 * from the existing session attachment record — it never adds a server field
 * and never claims the binary bytes were uploaded.
 *
 * [UPLOAD_INTENT] is the honest state while the binary upload contract is
 * frozen: the client has declared the attachment by its opaque id, but no
 * server has stored the bytes. [UPLOADED] is reserved for an authoritative
 * ready event and is the only phase allowed to say "uploaded".
 */
enum class AttachmentPhase {
    STAGING,
    PREVIEW_READY,
    UPLOAD_INTENT,
    UPLOADED,
    FAILED,
    REMOVED,
    ;

    /** Only an authoritative ready event may present as uploaded. */
    val isUploaded: Boolean get() = this == UPLOADED

    val isFailure: Boolean get() = this == FAILED

    /** A chip in this phase must not survive a conversation switch. */
    val isComposerBound: Boolean get() = this != UPLOADED && this != REMOVED
}

/**
 * What the chip row is allowed to render. Pure so unit tests can pin the
 * upload-intent vs uploaded distinction without a composable.
 */
data class AttachmentChipModel(
    val attachmentId: String,
    val phase: AttachmentPhase,
    val label: String,
    val detail: String,
    val canRemove: Boolean,
    val canRetry: Boolean,
    val showProgress: Boolean,
)

object AttachmentUx {

    const val LABEL_STAGING = "STAGING"
    const val LABEL_PREVIEW = "PREVIEW"
    const val LABEL_UPLOAD_INTENT = "UPLOAD INTENT"
    const val LABEL_UPLOADED = "UPLOADED"
    const val LABEL_FAILED = "FAILED"
    const val LABEL_REMOVED = "REMOVED"

    const val DETAIL_STAGING = "Copying into private storage"
    const val DETAIL_PREVIEW = "Local preview only"
    const val DETAIL_UPLOAD_INTENT =
        "Declared by id. Binary upload waits on the frozen contract."
    const val DETAIL_UPLOADED = "Server accepted this attachment"
    const val DETAIL_FAILED = "Not uploaded. Retry keeps the same local copy."
    const val DETAIL_REMOVED = "Removed from this conversation"

    fun phaseOf(state: AttachmentUiState?, staging: Boolean = false): AttachmentPhase = when {
        staging -> AttachmentPhase.STAGING
        state == null -> AttachmentPhase.PREVIEW_READY
        state.uploading -> AttachmentPhase.UPLOAD_INTENT
        state.ready && state.error == null -> AttachmentPhase.UPLOADED
        state.error != null -> AttachmentPhase.FAILED
        else -> AttachmentPhase.PREVIEW_READY
    }

    fun chip(attachmentId: String, state: AttachmentUiState?, staging: Boolean = false): AttachmentChipModel {
        val phase = phaseOf(state, staging)
        val (label, detail) = when (phase) {
            AttachmentPhase.STAGING -> LABEL_STAGING to DETAIL_STAGING
            AttachmentPhase.PREVIEW_READY -> LABEL_PREVIEW to DETAIL_PREVIEW
            AttachmentPhase.UPLOAD_INTENT -> LABEL_UPLOAD_INTENT to DETAIL_UPLOAD_INTENT
            AttachmentPhase.UPLOADED -> LABEL_UPLOADED to DETAIL_UPLOADED
            AttachmentPhase.FAILED -> {
                val reason = state?.error?.message?.takeIf { it.isNotBlank() } ?: DETAIL_FAILED
                LABEL_FAILED to reason
            }
            AttachmentPhase.REMOVED -> LABEL_REMOVED to DETAIL_REMOVED
        }
        return AttachmentChipModel(
            attachmentId = attachmentId,
            phase = phase,
            label = label,
            detail = detail,
            canRemove = phase != AttachmentPhase.REMOVED && phase != AttachmentPhase.STAGING,
            canRetry = phase == AttachmentPhase.FAILED,
            showProgress = phase == AttachmentPhase.STAGING || phase == AttachmentPhase.UPLOAD_INTENT,
        )
    }

    /**
     * A failed upload may be retried only while it is still the same staged
     * attachment and has not been removed. Ready attachments are never retried:
     * that would be a second upload of an already accepted id.
     */
    fun canRetry(state: AttachmentUiState?): Boolean =
        state != null && !state.uploading && !state.ready && state.error != null
}
