package com.jarvis.android.transport.live

import com.jarvis.android.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GatewayBootstrapWiringTest {

    private fun source(relative: String): String {
        val candidates = listOf(
            File(relative),
            File("..", relative),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("source file not found: $relative")
    }

    @Test
    fun verifiedBootstrapIsWiredIntoStartupLoginAndBiometricRefresh() {
        val bootstrap = BuildConfig.JARVIS_BOOTSTRAP_GATEWAY
        assertTrue(JarvisAppSession.isAllowedLiveHost(bootstrap))
        assertFalse(bootstrap.contains(".ts.net"))

        val container = source("app/src/main/java/com/jarvis/android/di/AppContainer.kt")
        val viewModel = source("app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt")

        assertTrue(container.contains("BuildConfig.JARVIS_BOOTSTRAP_GATEWAY"))
        assertTrue(container.contains("settings.setBaseUrl(currentGateway)"))

        val occurrences = "BuildConfig.JARVIS_BOOTSTRAP_GATEWAY".toRegex()
            .findAll(viewModel)
            .count()
        assertTrue("bootstrap must cover login and biometric refresh", occurrences >= 2)
    }
}
