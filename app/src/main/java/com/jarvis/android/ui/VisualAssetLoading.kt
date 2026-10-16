package com.jarvis.android.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** A stalled fetch or invalid image must finish with a retryable result. */
internal suspend fun <C, T : Any> loadVisualAsset(
    timeoutMillis: Long = 35_000L,
    fetch: suspend () -> Result<C>,
    stage: suspend (C) -> T?,
): Result<T> = try {
    withTimeoutOrNull(timeoutMillis) {
        val content = fetch().getOrThrow()
        Result.success(stage(content) ?: error("No se pudo leer la imagen descargada."))
    } ?: Result.failure(IllegalStateException("La descarga tardó demasiado. Reintenta."))
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}


/**
 * The file returned by /visual-assets/fetch must be the SAME asset selected
 * from the authenticated project/character listing. A valid SHA for some
 * other image is not authorization to display it in Character Studio.
 */
internal fun matchesListedVisualAssetReceipt(
    expectedProjectId: String,
    listed: com.jarvis.android.transport.live.VisualStudioAsset,
    fetched: com.jarvis.android.transport.live.WritingVisualAssetContent,
): Boolean =
    expectedProjectId.isNotBlank() &&
        listed.project_id == expectedProjectId &&
        listed.asset_id.isNotBlank() &&
        fetched.asset_id == listed.asset_id &&
        Regex("^[a-fA-F0-9]{64}$").matches(listed.sha256) &&
        fetched.sha256.equals(listed.sha256, ignoreCase = true) &&
        listed.mime_type.startsWith("image/") &&
        fetched.mime_type.equals(listed.mime_type, ignoreCase = true)


/** Editing may incur cost: verify the *current* parent and the local original. */
internal fun verifiedCharacterEditParent(
    projectId: String,
    characterId: String,
    asset: com.jarvis.android.transport.live.VisualStudioAsset,
    bytes: ByteArray,
): Boolean {
    if (projectId.isBlank() || characterId.isBlank() ||
        asset.project_id != projectId || characterId !in asset.character_ids ||
        asset.status != "APPROVED" ||
        asset.mime_type !in setOf("image/png", "image/jpeg", "image/webp") ||
        !Regex("^[a-fA-F0-9]{64}$").matches(asset.sha256) ||
        bytes.isEmpty() || bytes.size > 12 * 1024 * 1024
    ) return false
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    return digest.equals(asset.sha256, ignoreCase = true)
}


/**
 * A paid edit's returned child must be durably attached to the exact approved
 * parent that was sent to the provider. A valid image hash on an unrelated
 * character/project is insufficient, and a child must never arrive APPROVED.
 */
internal fun verifiedCharacterEditChild(
    parent: com.jarvis.android.transport.live.VisualStudioAsset,
    child: com.jarvis.android.transport.live.VisualStudioAsset?,
    responseMimeType: String,
): Boolean {
    if (child == null) return false
    return parent.asset_id.isNotBlank() &&
        parent.project_id.isNotBlank() &&
        parent.status == "APPROVED" &&
        child.asset_id.isNotBlank() &&
        child.asset_id != parent.asset_id &&
        child.project_id == parent.project_id &&
        child.character_ids == parent.character_ids &&
        child.kind == parent.kind &&
        child.perspective == parent.perspective &&
        child.status == "CANDIDATE" &&
        child.parent_asset_id == parent.asset_id &&
        child.parent_sha256.equals(parent.sha256, ignoreCase = true) &&
        child.derivation == "EDIT" &&
        Regex("^[a-fA-F0-9]{64}$").matches(child.sha256) &&
        child.mime_type in setOf("image/png", "image/jpeg", "image/webp") &&
        child.mime_type == responseMimeType
}
