package com.jarvis.android.data.writing

import android.content.Context
import java.io.File

/**
 * Local drafts for the Writing Room. One file per project, private to the app.
 * Nothing here is a contract and nothing is uploaded: PC-A has published no
 * writing surface, so a draft never leaves the device.
 */
class WritingStore(context: Context) {

    private val dir = File(context.filesDir, "writing")

    fun read(projectId: String): String {
        val file = fileFor(projectId)
        if (!file.exists()) return ""
        return runCatching { file.readText() }.getOrDefault("")
    }

    fun write(projectId: String, text: String) {
        dir.mkdirs()
        fileFor(projectId).writeText(text)
    }

    /** Ids are sanitized so a value can never escape the drafts folder. */
    private fun fileFor(projectId: String): File {
        val safe = projectId.replace(Regex("[^A-Za-z0-9_-]"), "").ifBlank { "draft" }
        return File(dir, "$safe.md")
    }
}

/** Word count for the draft footer. Blank input is zero, not one. */
object DraftStats {
    fun words(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
}
