package com.jarvis.android.ui.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotChapterRequestsTest {
    @Test fun fullChapterDraftsUseExistingCopilotPlanner() {
        assertTrue(looksLikeCopilotChapterWriteRequest("Escribe el capítulo actual"))
        assertTrue(looksLikeCopilotChapterWriteRequest("Redacta el capítulo 38"))
        assertTrue(looksLikeCopilotChapterWriteRequest("Continúa el capítulo pendiente"))
        assertTrue(looksLikeCopilotChapterWriteRequest("Write the next chapter"))
    }

    @Test fun discussionSceneImagesAndPlanningStayInOriginalFlows() {
        assertFalse(looksLikeCopilotChapterWriteRequest("¿Qué pasó en el capítulo anterior?"))
        assertFalse(looksLikeCopilotChapterWriteRequest("Escribe una escena del capítulo"))
        assertFalse(looksLikeCopilotChapterWriteRequest("Genera una imagen de la batalla del capítulo 37"))
        assertFalse(looksLikeCopilotChapterWriteRequest("Ayúdame a escribir el capítulo"))
        assertFalse(looksLikeCopilotChapterWriteRequest("Revisa el capítulo que ya terminamos"))
        assertFalse(looksLikeCopilotChapterWriteRequest("Escribe la batalla entre Soren y Doom"))
        assertFalse(looksLikeCopilotChapterWriteRequest(""))
    }
}
