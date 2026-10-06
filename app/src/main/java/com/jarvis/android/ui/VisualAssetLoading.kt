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
