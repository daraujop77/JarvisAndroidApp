package com.jarvis.android.ui.writing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.android.data.media.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class VisualBitmapState(val bitmap: Bitmap? = null, val finished: Boolean = false)

/** Download and decode failures are visible; retry only fetches existing bytes. */
@Composable
internal fun VisualAssetPreview(
    attachmentId: String?,
    store: AttachmentStore,
    stored: Boolean,
    loading: Boolean,
    error: String?,
    description: String,
    height: Dp,
    onRetry: () -> Unit,
) {
    val decoded by produceState(VisualBitmapState(), attachmentId) {
        value = VisualBitmapState()
        if (!attachmentId.isNullOrBlank()) {
            val bitmap = withContext(Dispatchers.IO) { store.decodeThumbnail(attachmentId, 2048) }
            value = VisualBitmapState(bitmap, finished = true)
        }
    }
    Box(
        Modifier.fillMaxWidth().height(height)
            .background(Color(0xFF050B14), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = decoded.bitmap
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            val decodeFailed = !attachmentId.isNullOrBlank() && decoded.finished
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    error != null || decodeFailed -> {
                        Text("No se pudo mostrar la imagen.", color = Color(0xFFF1F5F9))
                        Text(
                            error?.take(240) ?: "La copia descargada no se pudo leer.",
                            color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = onRetry, enabled = !loading) { Text("Reintentar") }
                    }
                    !stored -> Text("Pendiente de Drive", color = Color(0xFF94A3B8))
                    else -> {
                        CircularProgressIndicator()
                        Text("Cargando imagen…", color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}
