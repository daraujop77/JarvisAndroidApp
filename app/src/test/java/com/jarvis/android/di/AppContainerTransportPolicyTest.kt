package com.jarvis.android.di

import org.junit.Assert.assertEquals
import org.junit.Test

class AppContainerTransportPolicyTest {

    @Test
    fun authenticatedLiveWinsWhenFakeIsOff() {
        assertEquals(
            AppContainer.TransportMode.LIVE,
            AppContainer.selectTransportMode(
                debug = true,
                useFake = false,
                liveAuthenticated = true,
            ),
        )
    }

    @Test
    fun authenticatedLiveWinsEvenWhenFakeIsEnabled() {
        assertEquals(
            AppContainer.TransportMode.LIVE,
            AppContainer.selectTransportMode(
                debug = true,
                useFake = true,
                liveAuthenticated = true,
            ),
        )
    }

    @Test
    fun debugFakeIsAvailableOnlyWithoutLiveSession() {
        assertEquals(
            AppContainer.TransportMode.FAKE,
            AppContainer.selectTransportMode(
                debug = true,
                useFake = true,
                liveAuthenticated = false,
            ),
        )
    }

    @Test
    fun debugWithFakeOffFallsBackToHttp() {
        assertEquals(
            AppContainer.TransportMode.HTTP,
            AppContainer.selectTransportMode(
                debug = true,
                useFake = false,
                liveAuthenticated = false,
            ),
        )
    }

    @Test
    fun releaseBuildNeverSelectsFake() {
        assertEquals(
            AppContainer.TransportMode.HTTP,
            AppContainer.selectTransportMode(
                debug = false,
                useFake = true,
                liveAuthenticated = false,
            ),
        )
    }
}
