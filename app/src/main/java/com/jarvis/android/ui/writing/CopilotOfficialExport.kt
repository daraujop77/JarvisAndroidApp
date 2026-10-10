package com.jarvis.android.ui.writing

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The server must resolve an exact official source; never infer an ID on Android. */
data class CopilotOfficialExport(
    val documentId: String,
    val chapterNumber: Int,
    val format: String,
    val sourceSha256: String,
)

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.content.orEmpty()

/** Fail closed on malformed/stale/unsupported tool results. */
fun resolveCopilotOfficialExport(result: JsonObject): CopilotOfficialExport? {
    if (result.string("status") != "READY_TO_DOWNLOAD" ||
        result.string("authority") != "OFFICIAL_CANON") return null
    val number = result.string("chapter_number").toIntOrNull()
        ?.takeIf { it in 1..100000 } ?: return null
    val docId = result.string("document_id")
    if (docId != "chapter:$number") return null
    val format = result.string("format")
    if (format !in setOf("pdf", "docx", "epub", "markdown")) return null
    val sha = result.string("source_sha256")
    if (!Regex("^[a-f0-9]{64}$").matches(sha)) return null
    return CopilotOfficialExport(docId, number, format, sha)
}

/** Author requests for an official numbered chapter; explanations are left as chat. */
fun looksLikeCopilotOfficialExportRequest(text: String): Boolean {
    val value = text.trim().lowercase()
    if (value.length !in 14..600 ||
        listOf("no exportes", "no descargues", "sin exportar", "cómo exportar",
               "como exportar", "como descargo", "cómo descargo")
            .any(value::contains)) return false
    val action = Regex("""\b(exporta|exportar|descarga|descargar|imprime|imprimir)\b""")
        .containsMatchIn(value)
    val number = Regex("""\bcap[ií]tulo\s+\d{1,5}\b""").containsMatchIn(value)
    return action && number
}

/** Questions with explicit request for authoritative canon structures. */
fun looksLikeCopilotCanonReadRequest(text: String): Boolean {
    val value = text.trim().lowercase()
    if (value.length !in 13..500) return false
    return listOf("cronología del canon", "cronologia del canon",
        "línea de tiempo del canon", "linea de tiempo del canon",
        "lore establecido", "lore del proyecto").any(value::contains)
}
