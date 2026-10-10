package com.jarvis.android.ui.writing

/**
 * Explicit presentation-only updates: use the same persisted, owner-confirmed
 * Copilot plan. Questions and image requests keep their prior conversational routes.
 */
fun looksLikeCopilotVisualDirectionChangeRequest(text: String): Boolean {
    val value = text.trim().lowercase()
    if (value.length !in 15..1600) return false
    if (Regex("""^(?:¿|\s)*(?:cómo|como|qué|que|puedes explicar|explica|revisa|muestra|muéstrame)\b""")
            .containsMatchIn(value)) return false
    if (listOf("no cambies", "no modifiques", "sin cambiar", "sin modificar",
               "genera una imagen", "generar imagen", "genera retrato",
               "genera una escena", "ilustra", "batalla").any(value::contains)) return false
    val command = Regex("""\b(cambia|modifica|actualiza|ajusta|define|establece|change|update|set)\b""")
        .containsMatchIn(value)
    val style = Regex("""\b(estilo visual|estilo artístico|estilo artistico|paleta|iluminación|iluminacion|renderizado|realismo)\b""")
        .containsMatchIn(value)
    val character = Regex("""\b(apariencia|diseño físico|diseno fisico|aspecto físico|aspecto fisico|cabello|pelo|ojos|armadura|vestimenta|ropa|rostro|cara|piel|altura|complexión|complexion|aura|accesorios|armas)\b""")
        .containsMatchIn(value)
    val explicitValue = Regex("""\b(a|por|para|con|como)\b|:""").containsMatchIn(value)
    return command && (style || character) && explicitValue
}
