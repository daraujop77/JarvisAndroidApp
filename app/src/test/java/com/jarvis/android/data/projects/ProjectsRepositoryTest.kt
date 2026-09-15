package com.jarvis.android.data.projects

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lane F gate: every UI state of the Projects shell is reachable via the repo seam. */
class ProjectsRepositoryTest {

    @Test
    fun samplesFlowLoadsThenListsProjects() = runTest {
        val repo = FakeProjectsRepository(loadDelayMs = 0)
        repo.observeProjects().test {
            assertEquals(ProjectsResult.Loading, awaitItem())
            val loaded = awaitItem() as ProjectsResult.Loaded
            assertEquals(3, loaded.projects.size)
            assertEquals("Work", loaded.projects.first().title) // most recently updated first
            assertTrue(loaded.projects.any { it.state == ProjectState.ARCHIVED })
            awaitComplete()
        }
    }

    @Test
    fun emptyAndErrorStatesFlow() = runTest {
        FakeProjectsRepository(mode = FakeProjectsRepository.Mode.EMPTY, loadDelayMs = 0)
            .observeProjects().test {
                assertEquals(ProjectsResult.Loading, awaitItem())
                assertEquals(ProjectsResult.Empty, awaitItem())
                awaitComplete()
            }
        FakeProjectsRepository(mode = FakeProjectsRepository.Mode.ERROR, loadDelayMs = 0)
            .observeProjects().test {
                assertEquals(ProjectsResult.Loading, awaitItem())
                assertTrue(awaitItem() is ProjectsResult.Error)
                awaitComplete()
            }
    }

    @Test
    fun conversationsLinkedPerProjectAndIsolated() = runTest {
        val repo = FakeProjectsRepository(loadDelayMs = 0)
        repo.conversationsFor(ProjectId("prj_home")).test {
            val home = awaitItem()
            assertEquals(2, home.size)
            assertTrue(home.all { it.title.contains("filter") || it.title.contains("Garage") })
            awaitComplete()
        }
        repo.conversationsFor(ProjectId("prj_old")).test {
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }
}
