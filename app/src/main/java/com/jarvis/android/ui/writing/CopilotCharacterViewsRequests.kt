package com.jarvis.android.ui.writing

/** Route only explicit requests for individual character turnaround views. */
fun looksLikeCopilotCharacterViewsRequest(text: String): Boolean {
    val value = text.trim().lowercase()
    if (value.length !in 12..2000) return false
    val action = Regex("""\b(completa|completar|genera|generar|crea|crear|haz|complete|generate|make)\b""")
        .containsMatchIn(value)
    val views = Regex("""\b(vistas|perspectivas|angulos|ángulos|turnaround|views)\b""")
        .containsMatchIn(value)
    val discussion = listOf(
        "cómo", "como puedo", "ayúdame a", "ayudame a", "explica",
        "qué", "que vistas", "revisa", "sin generar", "no generes",
        "batalla", "escena", "scene", "retrato", "portrait"
    ).any(value::contains)
    return action && views && !discussion
}
