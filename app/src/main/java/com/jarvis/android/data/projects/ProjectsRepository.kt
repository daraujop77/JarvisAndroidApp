package com.jarvis.android.data.projects

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * AND-W9 / A5 Projects shell.
 *
 * `PROJECTS_BACKEND = NOT_CONNECTED`: PC-A has not published a projects
 * contract, so the *only* implementation here is a deterministic fake behind
 * this interface. A future live adapter implements the same seam — no
 * endpoint, payload or enum here is a server contract.
 */
data class ProjectId(val value: String)

data class ProjectConversation(
    val conversationId: String,
    val title: String,
    val updatedAtMs: Long,
)

/** Client-side presentation state; not a claim about server enums. */
enum class ProjectState { ACTIVE, ARCHIVED }

/**
 * Local activity placeholder. Not live-backed. A future adapter may fill
 * this without changing the UI contract of [ProjectsRepository].
 */
enum class ProjectActivityState { NONE, PLACEHOLDER, UNKNOWN }

data class ProjectActivity(
    val state: ProjectActivityState,
    val caption: String,
) {
    companion object {
        val FIXTURE_PLACEHOLDER = ProjectActivity(
            state = ProjectActivityState.PLACEHOLDER,
            caption = "No live activity. Fixture placeholder. Not live-backed.",
        )
        val NONE = ProjectActivity(
            state = ProjectActivityState.NONE,
            caption = "No activity on this device.",
        )
    }
}

data class ProjectSummary(
    val id: ProjectId,
    val title: String,
    val state: ProjectState,
    val updatedAtMs: Long,
    val conversationCount: Int = 0,
)

sealed interface ProjectsResult {
    data object Loading : ProjectsResult
    data object Empty : ProjectsResult
    data class Error(val message: String) : ProjectsResult
    data class Loaded(val projects: List<ProjectSummary>) : ProjectsResult
}

/**
 * Reusable seam for the Projects workspace. Swap [FakeProjectsRepository]
 * for a live adapter only after a PC-A project contract is approved.
 */
interface ProjectsRepository {
    fun observeProjects(): Flow<ProjectsResult>
    fun conversationsFor(projectId: ProjectId): Flow<List<ProjectConversation>>
    fun activityFor(projectId: ProjectId): Flow<ProjectActivity>
}

/**
 * Fixture-backed repository for the shell + tests. `mode` lets tests
 * exercise every UI state (loading, empty, error, loaded) without a backend.
 */
class FakeProjectsRepository(
    var mode: Mode = Mode.SAMPLES,
    private val loadDelayMs: Long = 120,
) : ProjectsRepository {

    enum class Mode { SAMPLES, EMPTY, ERROR }

    private val projects = listOf(
        ProjectSummary(ProjectId("prj_home"), "Home", ProjectState.ACTIVE, 1000L, conversationCount = 2),
        ProjectSummary(ProjectId("prj_work"), "Work", ProjectState.ACTIVE, 2000L, conversationCount = 1),
        ProjectSummary(ProjectId("prj_old"), "Old kitchen reno", ProjectState.ARCHIVED, 500L, conversationCount = 0),
    )

    private val conversationsByProject = mapOf(
        ProjectId("prj_home") to listOf(
            ProjectConversation("conv_fixture_home_1", "Order replacement filter", 900L),
            ProjectConversation("conv_fixture_home_2", "Garage door sensor battery", 800L),
        ),
        ProjectId("prj_work") to listOf(
            ProjectConversation("conv_fixture_work_1", "Summarize unread email", 700L),
        ),
        ProjectId("prj_old") to emptyList(),
    )

    override fun observeProjects(): Flow<ProjectsResult> = flow {
        emit(ProjectsResult.Loading)
        delay(loadDelayMs)
        when (mode) {
            Mode.EMPTY -> emit(ProjectsResult.Empty)
            Mode.ERROR -> emit(ProjectsResult.Error("projects backend not connected"))
            Mode.SAMPLES -> emit(
                ProjectsResult.Loaded(projects.sortedByDescending { it.updatedAtMs }),
            )
        }
    }

    override fun conversationsFor(projectId: ProjectId): Flow<List<ProjectConversation>> = flow {
        delay(loadDelayMs)
        emit(conversationsByProject[projectId].orEmpty())
    }

    override fun activityFor(projectId: ProjectId): Flow<ProjectActivity> = flow {
        delay(loadDelayMs)
        emit(
            if (conversationsByProject[projectId].orEmpty().isEmpty()) {
                ProjectActivity.NONE
            } else {
                ProjectActivity.FIXTURE_PLACEHOLDER
            },
        )
    }
}
