package com.jarvis.android.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.LinkedHashMap
import java.util.UUID

data class StagedAttachment(
    val attachmentId: String,
    val file: File,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
)

/**
 * Local attachment cache (plan AND-W6): picked content is copied into a
 * private app directory under an opaque id, EXIF/GPS metadata is stripped by
 * re-encoding (orientation preserved as a pixel rotation), and only the
 * attachmentId ever reaches a request — never a raw local path and never the
 * original URI as backend authority.
 *
 * NOTE (plan §11): the binary upload transport is not frozen; [uploadPending]
 * marks where the real Gateway upload plugs in.
 */
class AttachmentStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "attachments").apply { mkdirs() }
    private val thumbDir = File(dir, "thumbs").apply { mkdirs() }

    /** Decoded thumbnails kept in memory. Oldest entry is evicted past this. */
    private val thumbCache = object : LinkedHashMap<ThumbKey, Bitmap>(THUMB_CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ThumbKey, Bitmap>?) =
            size > THUMB_CACHE_MAX
    }

    private data class ThumbKey(val attachmentId: String, val maxSize: Int)

    /** False when the upload contract is still frozen-pending: fake mode only. */
    val uploadPending: Boolean = true

    fun stageFrom(uri: Uri, mimeGuess: String? = null): StagedAttachment? = runCatching {
        val (name, mime, size) = queryMeta(uri) ?: return@runCatching null
        val id = "att_" + UUID.randomUUID().toString().take(12)
        val raw = File(dir, "$id.raw")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            raw.outputStream().use { input.copyTo(it) }
        } ?: return@runCatching null
        val out = stripMetadata(raw)
        runCatching { raw.delete() }
        out ?: return@runCatching null
        StagedAttachment(
            attachmentId = id,
            file = out,
            filename = name,
            mimeType = "image/jpeg", // re-encode normalizes to JPEG
            sizeBytes = out.length().takeIf { it > 0 } ?: size,
        )
    }.getOrNull()

    fun resolve(attachmentId: String): File? {
        if (!isOpaqueId(attachmentId)) return null
        val direct = File(dir, "$attachmentId.jpg")
        return direct.takeIf { it.isFile }
    }

    /**
     * Decode a bounded thumbnail. The decoded bitmap is cached in memory
     * (LRU, [THUMB_CACHE_MAX] entries) and on disk under `thumbs/`, so a
     * recomposition or a process restart never re-decodes the full JPEG and
     * never holds more than the cap. [maxSize] is clamped to [MAX_THUMB_EDGE].
     */
    fun decodeThumbnail(attachmentId: String, maxSize: Int = 256): Bitmap? {
        if (!isOpaqueId(attachmentId)) return null
        val edge = maxSize.coerceIn(1, MAX_THUMB_EDGE)
        val key = ThumbKey(attachmentId, edge)
        synchronized(thumbCache) { thumbCache[key]?.let { return it } }
        val decoded = runCatching { decodeThumbnailUncached(attachmentId, edge) }.getOrNull() ?: return null
        synchronized(thumbCache) { thumbCache[key] = decoded }
        return decoded
    }

    /** How many decoded thumbnails are currently held. Test seam. */
    fun cachedThumbnailCount(): Int = synchronized(thumbCache) { thumbCache.size }

    fun delete(attachmentId: String) {
        if (!isOpaqueId(attachmentId)) return
        runCatching { resolve(attachmentId)?.delete() }
        dropThumbnail(attachmentId)
    }

    /** Forget the decoded thumbnail for one attachment, memory and disk. */
    fun dropThumbnail(attachmentId: String) {
        if (!isOpaqueId(attachmentId)) return
        synchronized(thumbCache) {
            thumbCache.keys.filter { it.attachmentId == attachmentId }.forEach { thumbCache.remove(it) }
        }
        runCatching { File(thumbDir, "$attachmentId.jpg").delete() }
    }

    private fun decodeThumbnailUncached(attachmentId: String, edge: Int): Bitmap? {
        val cached = File(thumbDir, "$attachmentId.jpg")
        if (cached.isFile && cached.length() > 0) {
            decodeSampled(cached, edge)?.let { return it }
        }
        val source = resolve(attachmentId) ?: return null
        val decoded = decodeSampled(source, edge) ?: return null
        // Persist a small copy so the next process doesn't re-decode the full image.
        runCatching {
            cached.outputStream().use { decoded.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }
        return decoded
    }

    private fun decodeSampled(file: File, edge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > edge || bounds.outHeight / sample > edge) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    // ---- owner avatar (local only; never uploaded) ----------------------------

    private val avatarFile = File(appContext.filesDir, "avatar.jpg")

    fun hasAvatar(): Boolean = avatarFile.exists() && avatarFile.length() > 0

    /**
     * Copy + EXIF-strip a picked photo into a single well-known file so the
     * chat can show a stable owner portrait without treating a URI as authority.
     */
    fun saveAvatar(uri: Uri): Boolean {
        val staged = stageFrom(uri) ?: return false
        return runCatching {
            staged.file.copyTo(avatarFile, overwrite = true)
            if (staged.file.absolutePath != avatarFile.absolutePath) staged.file.delete()
            true
        }.getOrDefault(false)
    }

    fun clearAvatar() {
        runCatching { avatarFile.delete() }
    }

    fun decodeAvatar(maxSize: Int = 256): Bitmap? = runCatching {
        if (!hasAvatar()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(avatarFile.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
        BitmapFactory.decodeFile(
            avatarFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    /**
     * Re-encode via JPEG: all EXIF/GPS/thumbnail blocks are gone; orientation
     * is baked in as a pixel transform so display stays correct.
     */
    private fun stripMetadata(raw: File): File? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(raw.absolutePath, opts)
        val longEdge = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
        val sample = when {
            longEdge > 4096 -> 4
            longEdge > 2048 -> 2
            else -> 1
        }
        var bmp = BitmapFactory.decodeFile(
            raw.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return@runCatching null
        val orientation = runCatching {
            ExifInterface(raw.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val rotated = applyOrientation(bmp, orientation)
        if (rotated !== bmp) bmp.recycle()
        bmp = rotated

        val out = File(dir, raw.nameWithoutExtension + ".jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        out
    }.getOrNull()

    private fun applyOrientation(bmp: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun queryMeta(uri: Uri): Triple<String, String, Long>? = runCatching {
        var name = "image.jpg"
        var size = 0L
        appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val si = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        val mime = appContext.contentResolver.getType(uri) ?: "image/jpeg"
        Triple(name, mime, size)
    }.getOrNull()

    companion object {
        /** Decoded thumbnails retained at once. Beyond this the oldest is dropped. */
        const val THUMB_CACHE_MAX = 16

        /** No thumbnail decode may request an edge larger than this. */
        const val MAX_THUMB_EDGE = 512

        /** Opaque ids minted by [stageFrom]: `att_` plus 12 hex-ish chars. */
        private val OPAQUE_ID = Regex("att_[0-9a-fA-F-]{8,36}")

        fun isOpaqueId(attachmentId: String): Boolean = OPAQUE_ID.matches(attachmentId)
    }
}
