package com.jarvis.android.ui.shared

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.jarvis.android.data.media.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads an attachment thumbnail off the main thread and keys it to the
 * attachment id, so recomposition (which happens per streaming delta) never
 * re-reads the file or re-decodes the JPEG.
 */
@Composable
fun rememberAttachmentThumb(
    attachmentId: String,
    store: AttachmentStore,
    maxSize: Int = 256,
): State<Bitmap?> = produceState<Bitmap?>(initialValue = null, attachmentId, maxSize) {
    value = withContext(Dispatchers.IO) { store.decodeThumbnail(attachmentId, maxSize) }
}
