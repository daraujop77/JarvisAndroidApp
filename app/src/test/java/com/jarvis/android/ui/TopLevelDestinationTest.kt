package com.jarvis.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun productionNavigationAlwaysExposesProjectsWritingRoomEntry() {
        val destinations = TopLevelDestination.all

        assertEquals(
            listOf(
                TopLevelDestination.Conversations,
                TopLevelDestination.Projects,
                TopLevelDestination.Approvals,
                TopLevelDestination.Tasks,
                TopLevelDestination.Settings,
            ),
            destinations,
        )
        assertTrue(destinations.any { it.route == TopLevelDestination.Projects.route })
    }
}
