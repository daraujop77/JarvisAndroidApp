package com.jarvis.android.ui.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotVisualDirectionRequestsTest {
    @Test fun explicitProjectStyleUpdatesRouteThroughConfirmedTools() {
        assertTrue(looksLikeCopilotVisualDirectionChangeRequest("Cambia el estilo visual a anime cinematográfico"))
        assertTrue(looksLikeCopilotVisualDirectionChangeRequest("Ajusta la paleta del proyecto a colores fríos"))
        assertTrue(looksLikeCopilotVisualDirectionChangeRequest("Actualiza la iluminación del proyecto: suave y fría"))
    }
    @Test fun complexCharacterDirectionEditsRouteToCopilot() {
        assertTrue(looksLikeCopilotVisualDirectionChangeRequest("Actualiza la apariencia de Soren: cabello más largo"))
        assertTrue(looksLikeCopilotVisualDirectionChangeRequest("Modifica el diseño físico de Alexander para tener armadura plateada"))
        assertTrue(looksLikeCopilotVisualDirectionChangeRequest("Cambia el cabello y los ojos de Soren a plateado y violeta"))
    }
    @Test fun discussionAndImagesNeverBecomeProfileWrites() {
        assertFalse(looksLikeCopilotVisualDirectionChangeRequest("¿Cómo cambiar el estilo visual?"))
        assertFalse(looksLikeCopilotVisualDirectionChangeRequest("Muestra el estilo visual"))
        assertFalse(looksLikeCopilotVisualDirectionChangeRequest("No cambies el estilo visual"))
        assertFalse(looksLikeCopilotVisualDirectionChangeRequest("Genera una imagen con estilo visual anime"))
        assertFalse(looksLikeCopilotVisualDirectionChangeRequest("Cambia la apariencia de Soren en la batalla"))
        assertFalse(looksLikeCopilotVisualDirectionChangeRequest(""))
    }
}
