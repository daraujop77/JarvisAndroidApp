package com.jarvis.android.ui.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotApprovedVisualInspectionIntentTest {
    @Test fun detectsExplicitVisualInspectionWithoutImageGeneration() {
        assertTrue(looksLikeCopilotApprovedVisualInspectionRequest(
            "Analiza la imagen aprobada de Alexander"))
        assertTrue(looksLikeCopilotApprovedVisualInspectionRequest(
            "Compara el retrato de Naruto con el canon"))
        assertFalse(looksLikeCopilotApprovedVisualInspectionRequest(
            "Muéstrame las imágenes aprobadas de Alexander"))
        assertFalse(looksLikeCopilotApprovedVisualInspectionRequest(
            "Genera una imagen del Guardián"))
        assertFalse(looksLikeCopilotApprovedVisualInspectionRequest(""))
        assertTrue(looksLikeCopilotApprovedVisualInspectionRequest(
            "La rev 3 es la principal; en rev 2 están Boruto abajo, Melody izquierda y William derecha"))
        assertTrue(looksLikeCopilotApprovedVisualInspectionRequest(
            "Revisa la revisión 2 de Alexander"))
        assertFalse(looksLikeCopilotApprovedVisualInspectionRequest(
            "Genera una imagen de la rev 2"))
        assertFalse(looksLikeCopilotApprovedVisualInspectionRequest(
            "Buenas noches, ¿qué tal el capítulo?"))
    }
}
