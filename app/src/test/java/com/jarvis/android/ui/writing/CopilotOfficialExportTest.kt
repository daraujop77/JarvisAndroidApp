package com.jarvis.android.ui.writing

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotOfficialExportTest {
    private val hash = "a".repeat(64)

    private fun result(
        status: String = "READY_TO_DOWNLOAD",
        authority: String = "OFFICIAL_CANON",
        number: Int = 37,
        id: String = "chapter:37",
        format: String = "pdf",
        sha: String = hash,
    ) = buildJsonObject {
        put("status", status)
        put("authority", authority)
        put("chapter_number", number)
        put("document_id", id)
        put("format", format)
        put("source_sha256", sha)
    }

    @Test fun onlyExactOfficialFullTextBoundDownloadIsAccepted() {
        val prepared = resolveCopilotOfficialExport(result())
        assertNotNull(prepared)
        assertEquals("chapter:37", prepared!!.documentId)
        assertEquals(37, prepared.chapterNumber)
        assertEquals("pdf", prepared.format)
        assertEquals(hash, prepared.sourceSha256)
        assertNull(resolveCopilotOfficialExport(result(status = "NOT_OFFICIAL_FULL_TEXT")))
        assertNull(resolveCopilotOfficialExport(result(authority = "REFERENCE")))
        assertNull(resolveCopilotOfficialExport(result(id = "chapter:38")))
        assertNull(resolveCopilotOfficialExport(result(number = 0)))
        assertNull(resolveCopilotOfficialExport(result(format = "html")))
        assertNull(resolveCopilotOfficialExport(result(sha = "a".repeat(63))))
    }

    @Test fun naturalExportAndReadIntentStayConservative() {
        assertTrue(looksLikeCopilotOfficialExportRequest("Exporta el capítulo 37 a PDF"))
        assertTrue(looksLikeCopilotOfficialExportRequest("Descargar capítulo 12 en DOCX"))
        assertFalse(looksLikeCopilotOfficialExportRequest("¿Cómo exportar un capítulo?"))
        assertFalse(looksLikeCopilotOfficialExportRequest("No exportes el capítulo 37"))
        assertFalse(looksLikeCopilotOfficialExportRequest("Exporta este capítulo"))
        assertFalse(looksLikeCopilotOfficialExportRequest("Genera un retrato del capítulo 37"))
        assertTrue(looksLikeCopilotCanonReadRequest("Revisa la cronología del canon"))
        assertTrue(looksLikeCopilotCanonReadRequest("Quiero ver el lore establecido"))
        assertTrue(looksLikeCopilotCanonReadRequest("Muestra el estado del mapa narrativo"))
        assertFalse(looksLikeCopilotCanonReadRequest("Quiero una batalla visual épica"))
    }
}
