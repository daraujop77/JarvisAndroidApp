package com.jarvis.android.ui.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotCharacterViewsRequestsTest {
    @Test fun directTurnaroundRequestsUseCopilotTools() {
        assertTrue(looksLikeCopilotCharacterViewsRequest("Completa las vistas de Soren"))
        assertTrue(looksLikeCopilotCharacterViewsRequest("Genera las seis perspectivas de Alexander"))
        assertTrue(looksLikeCopilotCharacterViewsRequest("Complete the turnaround views of Naruto"))
    }

    @Test fun discussionPortraitsAndScenesStayOnPreviousPath() {
        assertFalse(looksLikeCopilotCharacterViewsRequest("¿Qué vistas faltan de Soren?"))
        assertFalse(looksLikeCopilotCharacterViewsRequest("Genera retrato de Naruto"))
        assertFalse(looksLikeCopilotCharacterViewsRequest("Genera una imagen de la batalla de Soren"))
        assertFalse(looksLikeCopilotCharacterViewsRequest("Cómo puedo completar las vistas de Soren"))
        assertFalse(looksLikeCopilotCharacterViewsRequest("No generes vistas de Alexander"))
        assertFalse(looksLikeCopilotCharacterViewsRequest(""))
    }
}
