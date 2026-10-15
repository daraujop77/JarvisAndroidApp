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

    /** Copy a small document into app-private storage without changing its bytes. */
    fun stageDocumentFrom(uri: Uri): StagedAttachment? = runCatching {
        val (originalName, reportedMime, reportedSize) = queryMeta(uri) ?: return@runCatching null
        val pickedName = originalName.substringAfterLast('/').substringAfterLast('\\').take(100)
        val pickedExtension = pickedName.substringAfterLast('.', "").lowercase()
        val extension = if (pickedExtension in setOf("txt", "md", "pdf", "docx")) pickedExtension else when (reportedMime) {
            "text/plain" -> "txt"
            "text/markdown" -> "md"
            "application/pdf" -> "pdf"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            else -> return@runCatching null
        }
        val name = if (pickedExtension == extension) pickedName else "document.$extension"
        val mime = when (extension) {
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> return@runCatching null
        }
        if (reportedSize > 2_000_000) return@runCatching null
        val id = "doc_" + UUID.randomUUID().toString().take(12)
        val out = File(dir, "$id.$extension")
        var count = 0
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > 2_000_000) error("Document exceeds the chat limit")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@runCatching null
            if (count == 0) return@runCatching null
            if (extension == "pdf" && !out.inputStream().use { input ->
                    val header = ByteArray(5)
                    input.read(header) == 5 && header.contentEquals("%PDF-".toByteArray())
                }) {
                return@runCatching null
            }
            File(dir, "$id.name").writeText(name.ifBlank { "document.$extension" }, Charsets.UTF_8)
            StagedAttachment(id, out, name, mime, count.toLong())
        } finally {
            if (!File(dir, "$id.name").exists()) out.delete()
        }
    }.getOrNull()

    fun documentFilename(attachmentId: String): String? = runCatching {
        if (!attachmentId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) return@runCatching null
        File(dir, "$attachmentId.name").takeIf { it.isFile }?.readText(Charsets.UTF_8)?.take(100)
    }.getOrNull()

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

    /** Store a generated image. Scoped remote receipt keys let SSE/cache
     * replays reuse one local attachment rather than creating duplicate files.
     * The original content is still validated before a cached file is reused.
     */
    @Synchronized
    fun stageGeneratedBase64(
        dataBase64: String, stableReceiptKey: String? = null,
    ): StagedAttachment? = runCatching {
        val raw = Base64.decode(dataBase64, Base64.DEFAULT)
        require(raw.isNotEmpty() && raw.size <= 12 * 1024 * 1024)
        require(stableReceiptKey == null || stableReceiptKey.length in 1..360)
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: return@runCatching null
        val id = if (stableReceiptKey == null) {
            "gen_" + UUID.randomUUID().toString().take(12)
        } else {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(stableReceiptKey.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            "gen_" + digest.take(32)
        }
        val out = File(dir, "$id.jpg")
        try {
            if (!out.isFile || out.length() <= 0) {
                out.outputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream))
                }
            }
        } finally {
            bitmap.recycle()
        }
        StagedAttachment(
            attachmentId = id,
            file = out,
            filename = "generated-image.jpg",
            mimeType = "image/jpeg",
            sizeBytes = out.length(),
        )
    }.getOrNull()

    /** Preserve trusted visual reference bytes so edits retain the approved SHA. */
    fun stageVisualAssetBase64(dataBase64: String, expectedSha256: String): StagedAttachment? = runCatching {
        val raw = Base64.decode(dataBase64, Base64.DEFAULT)
        require(raw.isNotEmpty() && raw.size <= 12 * 1024 * 1024)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw).joinToString("") { "%02x".format(it) }
        require(expectedSha256.isBlank() || digest.equals(expectedSha256, ignoreCase = true))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val mime = bounds.outMimeType ?: error("Unknown visual image format")
        val extension = when (mime) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> error("Unsupported visual image format")
        }
        val id = "visual_" + UUID.randomUUID().toString().take(12)
        val out = File(dir, "$id.$extension")
        out.writeBytes(raw)
        StagedAttachment(id, out, "visual-reference.$extension", mime, out.length())
    }.getOrNull()

    /** Durable generated-image original: no lossy JPEG recompression.
     * The stable key is hashed, not used as a path. A repeated recovery must
     * find the exact same bytes or fail closed instead of overwriting them.
     */
    @Synchronized
    fun stageVerifiedGeneratedBase64(
        dataBase64: String,
        expectedSha256: String,
        expectedMimeType: String,
        stableReceiptKey: String,
    ): StagedAttachment? = runCatching {
        require(stableReceiptKey.length in 1..360)
        require(expectedSha256.matches(Regex("^[a-fA-F0-9]{64}$")))
        require(expectedMimeType in setOf("image/jpeg", "image/png", "image/webp"))
        val raw = Base64.decode(dataBase64, Base64.DEFAULT)
        require(raw.isNotEmpty() && raw.size <= 12 * 1024 * 1024)
        fun sha(bytes: ByteArray): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
        require(sha(raw).equals(expectedSha256, ignoreCase = true))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        require(bounds.outMimeType == expectedMimeType)
        val ext = when (expectedMimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            else -> "webp"
        }
        val id = "gen_" + sha(stableReceiptKey.toByteArray(Charsets.UTF_8)).take(32)
        val out = File(dir, "$id.$ext")
        if (out.exists()) {
            require(sha(out.readBytes()).equals(expectedSha256, ignoreCase = true))
        } else {
            val temp = File(dir, "$id.$ext.tmp")
            try {
                temp.writeBytes(raw)
                require(temp.renameTo(out) ||
                    (out.exists() &&
                     sha(out.readBytes()).equals(expectedSha256, ignoreCase = true)))
            } finally {
                temp.delete()
            }
        }
        StagedAttachment(
            attachmentId = id,
            file = out,
            filename = "generated-image.$ext",
            mimeType = expectedMimeType,
            sizeBytes = out.length(),
        )
    }.getOrNull()

    /** Keep the exact generated PNG/WebP/JPEG bytes for subsequent edits.
     * A verified SHA controls the existing receipt-backed storage path.
     */
    fun stageOriginalGeneratedBase64(
        dataBase64: String, expectedMimeType: String,
    ): StagedAttachment? = runCatching {
        val raw = Base64.decode(dataBase64, Base64.DEFAULT)
        require(raw.isNotEmpty() && raw.size <= 12 * 1024 * 1024)
        val sha = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw).joinToString("") { "%02x".format(it) }
        stageVerifiedGeneratedBase64(
            dataBase64, sha, expectedMimeType,
            "original-generated:$sha",
        ) ?: error("Generated source failed MIME/SHA validation")
    }.getOrNull()

    fun resolve(attachmentId: String): File? =
        dir.listFiles()?.firstOrNull { it.nameWithoutExtension == attachmentId && it.extension in setOf("jpg", "png", "webp", "txt", "md", "pdf", "docx") }

    fun decodeThumbnail(attachmentId: String, maxSize: Int = 512): Bitmap? = runCatching {
        val f = resolve(attachmentId) ?: return@runCatching null
        if (f.extension !in setOf("jpg", "png", "webp")) return@runCatching null
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
        if (attachmentId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) {
            runCatching { File(dir, "$attachmentId.name").delete() }
        }
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
