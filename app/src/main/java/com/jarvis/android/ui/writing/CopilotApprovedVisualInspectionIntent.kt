package com.jarvis.android.ui.writing

/**
 * Requests to INSPECT already-approved pixels go through an explicit
 * owner-confirmation tool plan. Merely listing images does not invoke vision.
 * Cloud image generation follows its separate confirmation policy.
 */
fun looksLikeCopilotApprovedVisualInspectionRequest(prompt: String): Boolean {
    val text = prompt.trim()
    if (text.length !in 8..400) return false
    val request = Regex(
        "(?iu)(analiza|analizar|descr[ií]beme|describe|compara|revisa|qu[eé] ves|inspect|analyze|compare|describe).{0,150}" +
            "(im[aá]gen(?:es)?|retratos?|fotos?|referencias? visual(?:es)?|master)",
    )
    return request.containsMatchIn(text)
}
