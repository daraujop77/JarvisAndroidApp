package com.jarvis.android.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.File
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

    /** Save a generated image returned by the trusted JARVIS VPS into app-private storage. */
    fun stageGeneratedBase64(dataBase64: String): StagedAttachment? = runCatching {
        val raw = Base64.decode(dataBase64, Base64.DEFAULT)
        require(raw.isNotEmpty() && raw.size <= 12 * 1024 * 1024)
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: return@runCatching null
        val id = "gen_" + UUID.randomUUID().toString().take(12)
        val out = File(dir, "$id.jpg")
        out.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream))
        }
        bitmap.recycle()
        StagedAttachment(
            attachmentId = id,
            file = out,
            filename = "generated-image.jpg",
            mimeType = "image/jpeg",
            sizeBytes = out.length(),
        )
    }.getOrNull()

    fun resolve(attachmentId: String): File? =
        dir.listFiles()?.firstOrNull { it.name.startsWith(attachmentId) && it.extension == "jpg" }

    fun decodeThumbnail(attachmentId: String, maxSize: Int = 512): Bitmap? = runCatching {
        val f = resolve(attachmentId) ?: return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
        BitmapFactory.decodeFile(
            f.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    fun delete(attachmentId: String) {
        runCatching { resolve(attachmentId)?.delete() }
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
}
