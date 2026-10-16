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
