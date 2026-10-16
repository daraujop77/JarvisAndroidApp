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

/** Do not render internal server JSON, URLs or storage diagnostics inside a visual card. */
internal fun friendlyVisualAssetError(raw: String?): String {
    if (raw.isNullOrBlank()) return "La copia descargada no se pudo leer."
    val code = Regex("""\b(?:HTTP\s*)?(500|502|503|504|401|403|404)\b""", RegexOption.IGNORE_CASE)
        .find(raw)?.groupValues?.get(1)
    return when (code) {
        "500", "502", "503", "504" ->
            "El servidor no pudo recuperar esta imagen (HTTP $code). " +
                "El original aprobado no se ha modificado. Puedes reintentar."
        "401", "403" ->
            "Tu sesión no tiene acceso a esta imagen. Verifica la cuenta y vuelve a intentar."
        "404" -> "No se encontró el archivo de esta imagen. El registro de aprobación permanece intacto."
        else -> "No se pudo cargar la imagen. Puedes reintentar sin modificar el original."
    }
}

private data class VisualBitmapState(
    val bitmap: Bitmap? = null,
    val finished: Boolean = false,
    val attachmentId: String? = null,
)

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
    onDecodedChange: (Boolean) -> Unit = {},
) {
    val decoded by produceState(VisualBitmapState(), attachmentId) {
        value = VisualBitmapState()
        if (!attachmentId.isNullOrBlank()) {
            val bitmap = withContext(Dispatchers.IO) { store.decodeThumbnail(attachmentId, 2048) }
            value = VisualBitmapState(bitmap, finished = true, attachmentId = attachmentId)
        }
    }
    // produceState can keep the previous attachment's bitmap for a frame; it must never count as this one.
    val isDecoded = decoded.attachmentId == attachmentId && decoded.bitmap != null
    LaunchedEffect(attachmentId, isDecoded) { onDecodedChange(isDecoded) }
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
                            friendlyVisualAssetError(error),
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
