package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.WritingWikiEntity
import com.jarvis.android.transport.live.WritingWikiHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredWikiCharactersTest {

    @Test
    fun structuredAlexanderOverridesBundledStoryFactsAndKeepsCanonicalName() {
        val entity = WritingWikiEntity(
            id = "character:alexander",
            type = "character",
            name = "Alexander",
            canonical_name = "Alexander",
            name_locked = true,
            authority = "OFFICIAL_CANON",
            role = "Jōnin y líder del nuevo Equipo 7",
            summary = "Estratega hiperresponsable que está aprendiendo a compartir la carga.",
            current_state = "Al cierre del Capítulo 37 coordina la defensa del oeste.",
            appearance = "≈1.82 m; Eternal Sharingan y Rinnegan; lentes negros con detalles naranjas.",
            abilities = listOf(
                "Eternal Sharingan",
                "Rinnegan",
                "Simbionte Venom",
                "Vínculo residual de tres bandas con William y Melody",
            ),
            techniques = listOf(
                "Ojo de Predicción Absoluta",
                "Interferencia de Flujo: Punto Cero",
            ),
            central_wound = "Miedo a perder el control y destruir a su familia.",
            history = listOf(
                WritingWikiHistoryItem(
                    period = "chapters-34-37",
                    label = "Regreso del Guardián y defensa del oeste",
                    summary = "Coordina a William, Melody y Boruto y empieza a delegar.",
                ),
            ),
        )

        val mapped = mergeStructuredCharacter(entity)

        assertEquals("Alexander", mapped.name)
        assertTrue(mapped.statusDetail.contains("Capítulo 37"))
        assertTrue(mapped.powersOverview.contains("Rinnegan"))
        assertTrue(mapped.powersOverview.contains("Simbionte Venom"))
        assertTrue(mapped.signatureTechniques.any { it.first == "Interferencia de Flujo: Punto Cero" })
        assertTrue(mapped.storyHistory.contains("defensa del oeste"))
        assertTrue(mapped.centralWound.orEmpty().contains("perder el control"))
    }

    @Test
    fun emptyStructuredListFallsBackWithoutDeletingExistingDirectory() {
        val mapped = mergeStructuredCharacters(emptyList())
        assertTrue(mapped.isNotEmpty())
        assertTrue(mapped.any { it.name == "Alexander" })
    }
}
