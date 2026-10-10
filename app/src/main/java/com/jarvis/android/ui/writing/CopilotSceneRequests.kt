package com.jarvis.android.ui.writing

/**
 * An explicit visual *scene* request can use server-owned Copilot planning
 * without requiring the author to toggle a technical "agent" mode first.
 * Ambiguous writing and single-character portrait requests remain normal chat.
 *
 * This only proposes a task; VPS owner confirmation is mandatory for spending.
 */
fun looksLikeCopilotSceneImageRequest(text: String): Boolean {
    val value = text.trim().lowercase()
    if (value.length !in 10..2000) return false
    val visual = listOf(
        "imagen", "image", "ilustra", "illustrat", "dibuj",
        "draw ", "arte visual", "visual art", "render",
    ).any(value::contains)
    val scene = listOf(
        "escena", "batalla", "pelea", "combate", "contra ",
        "enfrentamiento", "battle", "fight", "scene",
    ).any(value::contains)
    return visual && scene
}
