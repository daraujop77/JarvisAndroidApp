package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.WritingWikiAnalysisInsight
import com.jarvis.android.transport.live.WritingWikiAppearanceItem
import com.jarvis.android.transport.live.WritingWikiChapterActivity
import com.jarvis.android.transport.live.WritingWikiEntity
import com.jarvis.android.transport.live.WritingWikiFamilyMember
import com.jarvis.android.transport.live.WritingWikiHistoryItem
import com.jarvis.android.transport.live.WritingWikiJarvisAnalysis
import com.jarvis.android.transport.live.WritingWikiProfile
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
            limitations = listOf("Venom C4 Cataclysm está prohibido."),
            appearances = listOf(
                WritingWikiAppearanceItem(
                    chapters = listOf(36, 37),
                    kind = "ON_PAGE",
                    summary = "Dirige y coordina la respuesta del nuevo Equipo 7 en el oeste.",
                ),
            ),
            central_wound = "Miedo a perder el control y destruir a su familia.",
            desire_vs_need = "Necesita aceptar que sus hermanos pueden compartir la carga.",
            internal_contradiction = "Necesita a sus hermanos, pero su instinto es aislarse.",
            history = listOf(
                WritingWikiHistoryItem(
                    period = "chapters-34-37",
                    label = "Regreso del Guardián y defensa del oeste",
                    summary = "Coordina a William, Melody y Boruto y empieza a delegar.",
                ),
            ),
            profile = WritingWikiProfile(
                rank = "Jōnin",
                affiliations = listOf("Konohagakure", "Nuevo Equipo 7"),
                age = "No establecida",
                age_note = "Las fuentes canónicas actuales no fijan una edad exacta.",
                height = "≈1.80–1.85 m",
                first_appearance = 1,
                latest_appearance = 37,
                family = listOf(
                    WritingWikiFamilyMember("character:melody", "sister", "Hermana"),
                ),
            ),
            chapter_activity = listOf(
                WritingWikiChapterActivity(
                    chapters = listOf(37),
                    title = "La contingencia",
                    presence = "ON_PAGE",
                    evidence_scope = "FULL_CHAPTER_OFFICIAL",
                    summary = "Coordina la defensa del oeste.",
                    actions = listOf("Organiza relevos con Shikamaru."),
                    decisions = listOf("Acepta repartir la defensa."),
                    consequences = listOf("Deja de sostener toda la defensa solo."),
                    source_refs = listOf("drive:c37"),
                ),
            ),
            jarvis_analysis = WritingWikiJarvisAnalysis(
                status = "DERIVED_ANALYSIS",
                summary = "Su necesidad de control responde al miedo de dañar a su familia.",
                evolution = "Pasa de absorber toda la carga a delegar.",
                insights = listOf(
                    WritingWikiAnalysisInsight(
                        title = "Delegación real",
                        analysis = "En el capítulo 37 comparte la defensa.",
                        evidence_chapters = listOf(37),
                    ),
                ),
                evidence_chapters = listOf(37),
                source_refs = listOf("drive:c37"),
                disclaimer = "Análisis derivado; no agrega hechos al canon.",
            ),
        )

        val mapped = mergeStructuredCharacter(entity)

        assertEquals("Alexander", mapped.name)
        assertTrue(mapped.statusDetail.contains("Capítulo 37"))
        assertTrue(mapped.powersOverview.contains("Rinnegan"))
        assertTrue(mapped.powersOverview.contains("Simbionte Venom"))
        assertTrue(mapped.powersOverview.contains("Venom C4 Cataclysm está prohibido"))
        assertTrue(mapped.signatureTechniques.any { it.first == "Interferencia de Flujo: Punto Cero" })
        assertTrue(mapped.storyHistory.contains("defensa del oeste"))
        assertTrue(mapped.storyHistory.contains("Capítulos 36, 37"))
        assertTrue(mapped.centralWound.orEmpty().contains("perder el control"))
        assertTrue(mapped.centralWound.orEmpty().contains("compartir la carga"))
        assertTrue(mapped.centralWound.orEmpty().contains("instinto es aislarse"))
        assertTrue(mapped.canonicalAbilities.contains("Rinnegan"))
        assertTrue(mapped.canonicalLimitations.contains("Venom C4 Cataclysm está prohibido."))
        assertTrue(mapped.historyTimeline.any { it.label.contains("defensa del oeste") })
        assertTrue(mapped.canonAppearances.any { 37 in it.chapters })
        assertEquals("Jōnin", mapped.encyclopediaProfile.rank)
        assertEquals("No establecida", mapped.encyclopediaProfile.age)
        assertEquals(37, mapped.encyclopediaProfile.latestAppearance)
        assertEquals("Hermana", mapped.encyclopediaProfile.family.single().label)
        assertTrue(mapped.chapterActivity.single().actions.any { it.contains("relevos") })
        assertEquals("DERIVED_ANALYSIS", mapped.jarvisAnalysis?.status)
        assertTrue(mapped.jarvisAnalysis?.disclaimer.orEmpty().contains("no agrega hechos al canon"))
    }

    @Test
    fun emptyStructuredListFallsBackWithoutDeletingExistingDirectory() {
        val mapped = mergeStructuredCharacters(emptyList())
        assertTrue(mapped.isNotEmpty())
        assertTrue(mapped.any { it.name == "Alexander" })
    }
}
