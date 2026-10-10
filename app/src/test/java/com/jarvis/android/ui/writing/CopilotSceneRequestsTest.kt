package com.jarvis.android.ui.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotSceneRequestsTest {
    @Test fun routesExplicitVisualBattlesThroughCopilotPlanner() {
        assertTrue(looksLikeCopilotSceneImageRequest("Genera una imagen de la batalla de Doom contra el Guardián"))
        assertTrue(looksLikeCopilotSceneImageRequest("Ilustra la escena del enfrentamiento entre Soren y Doom"))
        assertTrue(looksLikeCopilotSceneImageRequest("Draw a battle scene between the Guardian and Doom"))
    }

    @Test fun preservesOrdinaryChatStoryWorkAndPortraitFlows() {
        assertFalse(looksLikeCopilotSceneImageRequest("Escribe la batalla entre Soren y Doom"))
        assertFalse(looksLikeCopilotSceneImageRequest("Haz un retrato de Naruto"))
        assertFalse(looksLikeCopilotSceneImageRequest("Genera una imagen de Sasuke"))
        assertFalse(looksLikeCopilotSceneImageRequest("¿Qué ocurrió durante la pelea del Guardián?"))
        assertFalse(looksLikeCopilotSceneImageRequest(""))
    }
}
