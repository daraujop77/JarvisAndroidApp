package com.jarvis.android.transport.live

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalVariantIntegrationWiringTest {
    private fun source(path: String): String =
        listOf(File(path), File("..", path)).firstOrNull { it.isFile }?.readText()
            ?: error("source file missing: $path")

    @Test
    fun externalErasPreserveBackendOwnerAndRevisionContracts() {
        val api = source("app/src/main/java/com/jarvis/android/transport/live/VisualStudioApi.kt")
        val vm = source("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")
        val screen = source("app/src/main/java/com/jarvis/android/ui/writing/CharacterStudioScreen.kt")

        assertTrue(api.contains("/visual/characters/external-catalog"))
        assertTrue(api.contains("/visual/characters/design-profile/variants"))
        assertTrue(api.contains("/visual/characters/design-profile/apply-variant"))
        assertTrue(api.contains("put(\"expected_revision\", expectedRevision)"))
        assertTrue(api.contains("put(\"variant_id\", variantId)"))
        assertTrue(api.contains("val seed_variant: String"))
        assertTrue(vm.contains("val externalCharacters: List<VisualExternalCharacter>"))
        assertTrue(vm.contains("val characterVariants: List<VisualExternalCharacterVariant>"))
        assertTrue(vm.contains("visualExternalCharacterCatalog(projectId)"))
        assertTrue(vm.contains("visualCharacterVariants(projectId, characterId)"))
        assertTrue(vm.contains("fun applyExternalCharacterVariant("))
        assertTrue(vm.contains("!settings.value.isOwner"))
        assertTrue(vm.contains("characterStudioScopeVersion != scopeVersion"))
        assertTrue(screen.contains("state.externalCharacters"))
        assertTrue(screen.contains("state.characterVariants.forEach"))
        assertTrue(screen.contains("settings.isOwner"))
        assertTrue(screen.contains("vm.applyExternalCharacterVariant("))
        assertTrue(screen.contains("no añaden ni cambian hechos de la historia"))
    }
}
