package com.jarvis.android.ui.writing

/**
 * Compute disposable, private local copies after a read-only Copilot gallery
 * recovery. Never remove an attachment still referenced by the visible gallery,
 * an originating chat turn, approved context or a different visual panel.
 *
 * Inputs are local attachment IDs, not provider asset IDs or shared Drive IDs.
 */
internal fun replacedCopilotPortraitCopies(
    previousIds: Set<String>,
    newlyStagedIds: Set<String>,
    visibleIds: Set<String>,
    otherReferencedIds: Set<String>,
): Set<String> =
    ((previousIds + newlyStagedIds) - visibleIds - otherReferencedIds)
        .filter { it.isNotBlank() }
        .toSet()
