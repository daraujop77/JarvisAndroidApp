package com.jarvis.android.ui.shared

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.android.data.media.AttachmentStore
import com.jarvis.android.ui.theme.LocalJarvisAccents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberOwnerAvatar(
    store: AttachmentStore,
    epoch: Int,
    maxSize: Int = 256,
): State<Bitmap?> = produceState<Bitmap?>(initialValue = null, epoch, maxSize) {
    value = withContext(Dispatchers.IO) { store.decodeAvatar(maxSize) }
}

@Composable
fun OwnerAvatar(
    store: AttachmentStore,
    epoch: Int,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val bmp by rememberOwnerAvatar(store, epoch)
    val accents = LocalJarvisAccents.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(accents.orbGlow.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = bmp
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Your photo",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = "Your photo",
                tint = accents.orbGlow,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}
