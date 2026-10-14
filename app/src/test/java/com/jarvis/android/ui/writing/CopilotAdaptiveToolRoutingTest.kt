package com.jarvis.android.ui.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotAdaptiveToolRoutingTest {
    @Test fun portraitCommandChecksAgentBeforeLegacyPortraitCard() {
        assertTrue(shouldProbeAdaptiveCopilotTool("Genera un retrato de Alexander"))
        assertTrue(shouldProbeAdaptiveCopilotTool("Crea una imagen de Naruto"))
    }

    @Test fun appearanceReadAndEditCheckAgentBeforeStaticCard() {
        assertTrue(shouldProbeAdaptiveCopilotTool("Muéstrame la apariencia de Alexander"))
        assertTrue(shouldProbeAdaptiveCopilotTool("Cambia el cabello de Naruto a rojo"))
    }

    @Test fun normalConversationDoesNotNeedASecondCapabilityProbe() {
        assertFalse(shouldProbeAdaptiveCopilotTool("¿Qué imágenes aprobadas tiene Alexander?"))
        assertFalse(shouldProbeAdaptiveCopilotTool("Hola"))
    }

    @Test fun deliberateToolModeNeverPerformsASecondProbe() {
        assertFalse(shouldProbeAdaptiveCopilotTool(
            "Genera un retrato de Alexander", toolMode = true,
        ))
        assertFalse(shouldProbeAdaptiveCopilotTool(
            "Genera un retrato de Alexander", skipAdaptiveProbe = true,
        ))
        assertFalse(shouldProbeAdaptiveCopilotTool(
            "Muéstrame la apariencia de Alexander", toolMode = true,
        ))
    }
}
