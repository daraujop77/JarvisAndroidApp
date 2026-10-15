package com.jarvis.android.ui.writing

/**
 * An approval button needs the preview to have decoded on screen. A server status or
 * an attachment id alone does not prove the owner can see the image.
 */
internal fun canApproveVisualPreview(
    storageState: String,
    previewDecoded: Boolean,
    requiresStored: Boolean = true,
): Boolean = previewDecoded && (!requiresStored || storageState == "stored")
