package com.jarvis.android.ui.writing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var expanded by remember(attachmentId) { mutableStateOf(false) }
    LaunchedEffect(attachmentId, isDecoded) { onDecodedChange(isDecoded) }

    val fullBitmap = decoded.bitmap.takeIf { isDecoded }
    if (expanded && fullBitmap != null) {
        var zoom by remember(attachmentId) { mutableFloatStateOf(1f) }
        var pan by remember(attachmentId) { mutableStateOf(Offset.Zero) }
        val transform = rememberTransformableState { zoomChange, panChange, _ ->
            zoom = (zoom * zoomChange).coerceIn(1f, 6f)
            pan = if (zoom <= 1f) Offset.Zero else pan + panChange
        }
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF050B14)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = fullBitmap.asImageBitmap(),
                    contentDescription = description + ". Pellizca para ampliar o arrastra.",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        }
                        .transformable(transform),
                )
                TextButton(
                    onClick = { expanded = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Text("Cerrar imagen", color = Color.White)
                }
            }
        }
    }

    Box(
        Modifier.fillMaxWidth().height(height)
            .background(Color(0xFF050B14), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Never show a previous asset for a frame while the new attachment
        // is being decoded. This also prevents cross-character image flashes.
        val bitmap = fullBitmap
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                description + ". Toca para ampliar.",
                Modifier.fillMaxSize().clickable { expanded = true },
                contentScale = ContentScale.Fit,
            )
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
