package com.jarvis.android.transport.live

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VisualDirectionEditorWiringTest {

    private fun source(relative: String): String {
        val candidates = listOf(
            File(relative),
            File("..", relative),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("source file not found: $relative")
    }

    @Test
    fun projectStyleAndCharacterAppearanceAreWiredEndToEnd() {
        val api = source("app/src/main/java/com/jarvis/android/transport/live/VisualStudioApi.kt")
        val viewModel = source("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        val screen = source("app/src/main/java/com/jarvis/android/ui/writing/CharacterStudioScreen.kt")

        assertTrue(api.contains("/api/app/writing-room/v2/visual/style/profile"))
        assertTrue(api.contains("/api/app/writing-room/v2/visual/style/update"))
        assertTrue(api.contains("/api/app/writing-room/v2/visual/characters/design-profile"))
        assertTrue(api.contains("/api/app/writing-room/v2/visual/characters/design-profile/update"))
        assertTrue(api.contains("/api/app/writing-room/v2/visual/style/restore"))
        assertTrue(api.contains("/api/app/writing-room/v2/visual/characters/design-profile/restore"))
        assertTrue(api.contains("put(\"expected_revision\", profile.revision)"))

        assertTrue(viewModel.contains("saveProjectVisualStyle"))
        assertTrue(viewModel.contains("saveCharacterVisualDirection"))
        assertTrue(viewModel.contains("restoreProjectVisualStyle"))
        assertTrue(viewModel.contains("restoreCharacterVisualDirection"))
        assertTrue(viewModel.contains("visualProjectStyle"))
        assertTrue(viewModel.contains("visualCharacterDirection"))

        assertTrue(screen.contains("ESTILO VISUAL DEL PROYECTO"))
        assertTrue(screen.contains("APARIENCIA DEL PERSONAJE"))
        assertTrue(screen.contains("Guardar estilo del proyecto"))
        assertTrue(screen.contains("Restaurar estilo Alexander"))
        assertTrue(screen.contains("Guardar apariencia"))
        assertTrue(screen.contains("Restaurar apariencia desde canon"))
        assertTrue(screen.contains("Requiere diseño"))
        assertTrue(screen.contains("canon escrito conserva prioridad"))
    }
}
