package com.jarvis.android.ui.writing

/**
 * Requests to INSPECT already-approved pixels go through an explicit
 * owner-confirmation tool plan. Merely listing images does not invoke vision.
 * Cloud image generation follows its separate confirmation policy.
 */
fun looksLikeCopilotApprovedVisualInspectionRequest(prompt: String): Boolean {
    val text = prompt.trim()
    if (text.length !in 8..400) return false
    // A follow-up about rev. 2 / rev. 3 must use project tools, not the
    // normal Moderator seat (which receives no approved image pixels).
    val revision = Regex("(?iu)\\brev(?:isi[oó]n)?\\.?\\s*#?\\s*\\d{1,3}\\b")
        .containsMatchIn(text)
    val revisionAction = Regex(
        "(?iu)\\b(analiza|analizar|compara|comparar|revisa|revisar|usa|usar|toma|tomar|principal|izquierda|derecha|arriba|abajo|describe|identifica)\\b",
    ).containsMatchIn(text)
    if (revision && revisionAction && !Regex(
            "(?iu)\\b(genera|generar|crea|crear|generate|create)\\b",
        ).containsMatchIn(text)) return true
    val request = Regex(
        "(?iu)(analiza|analizar|descr[ií]beme|describe|compara|revisa|qu[eé] ves|inspect|analyze|compare|describe).{0,150}" +
            "(im[aá]gen(?:es)?|retratos?|fotos?|referencias? visual(?:es)?|master)",
    )
    return request.containsMatchIn(text)
}
