package com.jarvis.android.ui.writing

/**
 * Recognize natural requests about existing approved pixels before they reach
 * the text-only Moderator. Vision still needs an explicit owner-confirmed task;
 * image generation and pure text/model version queries stay separate.
 */
fun looksLikeCopilotApprovedVisualInspectionRequest(prompt: String): Boolean {
    val text = prompt.trim()
    if (text.length !in 8..4000) return false
    if (Regex("(?iu)\\b(genera|generar|crea|crear|generate|create)\\b").containsMatchIn(text)) {
        return false
    }
    val visual = Regex(
        "(?iu)\\b(im[aá]gen(?:es)?|ilustraci[oó]n(?:es)?|fotos?|retratos?|" +
            "masters?|referencias?|refs?|dise[nñ]os?|galer[ií]as?)\\b",
    ).containsMatchIn(text)
    val revision = Regex("(?iu)\\brev(?:isi[oó]n)?\\.?\\s*#?\\s*\\d{1,3}\\b")
        .containsMatchIn(text) ||
        (visual && Regex("(?iu)\\bv\\s*#?\\s*\\d{1,3}\\b").containsMatchIn(text))
    val action = Regex(
        "(?iu)\\b(analiza|analizar|descr[ií]be(?:me)?|describir|compara|comparar|" +
            "revisa|revisar|identifica|identificar|usa|usar|toma|tomar|" +
            "ver|ves|veo|mirar|mira|m[ií]rame|observar|observa|" +
            "consultar|consulta|abrir|abre|acceder|acceso|inspecciona|inspeccionar|" +
            "inspect|analyze|compare|describe|view|see|look|" +
            "principal|izquierda|derecha|arriba|abajo)\\b",
    ).containsMatchIn(text)
    return (revision && (visual || action)) || (visual && action)
}
