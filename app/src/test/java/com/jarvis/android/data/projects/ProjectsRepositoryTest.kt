package com.jarvis.android.data.projects

import app.cash.turbine.test
import com.jarvis.android.data.state.ConnectionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lane F / A5: every UI state of the Projects shell is reachable via the repo seam. */
class ProjectsRepositoryTest {

    @Test
    fun samplesFlowLoadsThenListsProjects() = runTest {
        val repo = FakeProjectsRepository(loadDelayMs = 0)
        repo.observeProjects().test {
            assertEquals(ProjectsResult.Loading, awaitItem())
            val loaded = awaitItem() as ProjectsResult.Loaded
            assertEquals(3, loaded.projects.size)
            assertEquals("Work", loaded.projects.first().title)
            assertTrue(loaded.projects.any { it.state == ProjectState.ARCHIVED })
            assertEquals(2, loaded.projects.first { it.id.value == "prj_home" }.conversationCount)
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

    @Test
    fun activityPlaceholderIsNotLiveBacked() = runTest {
        val repo = FakeProjectsRepository(loadDelayMs = 0)
        repo.activityFor(ProjectId("prj_home")).test {
            val activity = awaitItem()
            assertEquals(ProjectActivityState.PLACEHOLDER, activity.state)
            assertTrue(activity.caption.contains("Not live-backed"))
            awaitComplete()
        }
        repo.activityFor(ProjectId("prj_old")).test {
            assertEquals(ProjectActivityState.NONE, awaitItem().state)
            awaitComplete()
        }
    }

    @Test
    fun searchFilterSortStayLocalAndDeterministic() {
        val loaded = listOf(
            ProjectSummary(ProjectId("prj_home"), "Home", ProjectState.ACTIVE, 1000L, 2),
            ProjectSummary(ProjectId("prj_work"), "Work", ProjectState.ACTIVE, 2000L, 1),
            ProjectSummary(ProjectId("prj_old"), "Old kitchen reno", ProjectState.ARCHIVED, 500L, 0),
        )
        val byTitle = ProjectsWorkspace.apply(
            loaded,
            ProjectsWorkspace.Query(sort = ProjectsWorkspace.Sort.TITLE_ASC),
        )
        assertEquals(listOf("Home", "Old kitchen reno", "Work"), byTitle.map { it.title })
        val active = ProjectsWorkspace.apply(
            loaded,
            ProjectsWorkspace.Query(filter = ProjectsWorkspace.Filter.ACTIVE),
        )
        assertEquals(listOf("Work", "Home"), active.map { it.title })
        val searched = ProjectsWorkspace.apply(
            loaded,
            ProjectsWorkspace.Query(text = "kit"),
        )
        assertEquals(listOf("Old kitchen reno"), searched.map { it.title })
        val none = ProjectsWorkspace.apply(
            loaded,
            ProjectsWorkspace.Query(text = "no-such-fixture"),
        )
        assertTrue(none.isEmpty())
    }

    @Test
    fun releaseHidesFixtureClaimsAndConversationsGroupByRecency() {
        val loaded = ProjectsResult.Loaded(
            listOf(
                ProjectSummary(ProjectId("a"), "Alpha", ProjectState.ACTIVE, 1L, 1),
                ProjectSummary(ProjectId("b"), "Beta", ProjectState.ARCHIVED, 2L, 0),
            ),
        )
        assertTrue(ProjectsWorkspace.visibleProjects(debugBuild = true, loaded, ProjectsWorkspace.Query()).isNotEmpty())
        assertTrue(ProjectsWorkspace.visibleProjects(debugBuild = false, loaded, ProjectsWorkspace.Query()).isEmpty())
        assertEquals(
            ProjectsWorkspace.Surface.HIDDEN,
            ProjectsWorkspace.surface(false, loaded, ConnectionState.ONLINE),
        )
        assertEquals(
            ProjectsWorkspace.Surface.OFFLINE,
            ProjectsWorkspace.surface(true, ProjectsResult.Error("x"), ConnectionState.OFFLINE),
        )
        assertEquals(
            ProjectsWorkspace.Surface.ERROR,
            ProjectsWorkspace.surface(true, ProjectsResult.Error("x"), ConnectionState.ONLINE),
        )
        assertFalse(ProjectsWorkspace.visibleInBuild(false))
        val grouped = ProjectsWorkspace.groupConversations(
            listOf(
                ProjectConversation("c2", "Later", 20L),
                ProjectConversation("c1", "Earlier", 10L),
            ),
        )
        assertEquals(listOf("Later", "Earlier"), grouped.map { it.title })
        val searched = ProjectsWorkspace.searchConversations(grouped, "ear")
        assertEquals(listOf("Earlier"), searched.map { it.title })
        assertEquals("opaque", ProjectsWorkspace.sanitizedId("""{"id":"x"}"""))
        assertTrue(ProjectsWorkspace.NOT_LIVE_LABEL.contains("Not live-backed"))
        assertTrue(ProjectsWorkspace.RELEASE_HIDDEN.contains("no live projects contract"))
    }
}
