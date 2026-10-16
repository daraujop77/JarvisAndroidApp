package com.jarvis.android.data.prefs

import android.content.SharedPreferences

/**
 * Durable Image Studio version chains, one per account and root attachment.
 *
 * A chain is stored as comma-separated attachment IDs and always starts with its
 * root, so an untouched studio shows only the base image.
 */
class ImageStudioVersionStore(
    private val prefs: SharedPreferences,
    private val userId: String,
) {
    fun versions(root: String): List<String> {
        if (userId.isBlank()) return listOf(root)
        return prefs.getString(key(root), null)
            .orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(root) }
    }

    /** Synchronous on purpose: the caller releases the paid intent only after this returns true. */
    fun append(root: String, attachmentId: String): Boolean {
        if (userId.isBlank() || !isChainId(root) || !isChainId(attachmentId)) return false
        val current = versions(root)
        if (attachmentId in current) return true
        return prefs.edit()
            .putString(key(root), (current + attachmentId).joinToString(","))
            .commit()
    }

    private fun key(root: String) = "jarvis.imagestudio.$userId.$root"

    private fun isChainId(value: String) = value.isNotBlank() && ',' !in value

    companion object {
        const val PREFS = "jarvis_image_studio"
    }
}
