package com.jarvis.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageSourceBadgeTest {
    @Test
    fun freellmapiRouterObservedIsLabeledRouter() {
        assertEquals("ROUTER", usageSourceBadge("freellmapi_router_observed"))
        assertEquals("ROUTER", usageSourceBadge("freellmapi_router_observed+provider_quota"))
    }

    @Test
    fun normalProviderUsageRemainsJarvis() {
        assertEquals("JARVIS", usageSourceBadge("jarvis_observed"))
        assertEquals("JARVIS", usageSourceBadge("jarvis_observed+provider_quota"))
    }
}
