package com.jarvis.android.ui.writing

/**
 * Only an explicit request to draft a full chapter opts into the server-owned
 * planning flow. Ordinary scene prose, chapter discussion and visual generation
 * must not accidentally launch the paid-capable W2/A4 workers.
 */
fun looksLikeCopilotChapterWriteRequest(text: String): Boolean {
    val value = text.trim().lowercase()
    if (value.length !in 12..2000) return false
    val chapter = Regex("\\b(cap[ií]tulo|chapter)\\b").containsMatchIn(value)
    if (!chapter) return false
    val writeVerb = Regex(
        "\\b(escribe|escribir|redacta|redactar|contin[uú]a|continuar|genera|generar|write|draft)\\b"
    ).containsMatchIn(value)
    if (!writeVerb) return false
    // Discussions and requests for a scene or image preserve the old paths.
    val excluded = listOf(
        "imagen", "image", "retrato", "portrait", "ilustra", "dibuj", "render",
        "escena", "scene", "ideas", "idea para", "cómo", "como puedo", "ayúdame",
        "ayudame", "planea", "planifica", "resumen", "review", "revisa",
    ).any(value::contains)
    if (excluded) return false
    return true
}
