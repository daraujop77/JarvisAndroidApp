package com.jarvis.android.data.projects

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * AND-W9 Projects shell (Lane F).
 *
 * `PROJECTS_BACKEND = NOT_CONNECTED`: PC-A has not published a projects
 * contract, so the *only* implementation here is a deterministic fake behind
 * this interface. When PC-A freezes `/api/v1` projects (or an equivalent), a
 * real repository slots in without touching the UI — no endpoint, payload or
 * enum here is presented as a server contract.
 */
data class ProjectId(val value: String)

data class ProjectConversation(val conversationId: String, val title: String, val updatedAtMs: Long)

/**
 * What kind of workspace a project opens. Presentation only: no server enum
 * exists yet, and [WRITING_ROOM] is a local preview until M3 publishes one.
 */
enum class ProjectKind { GENERAL, WRITING_ROOM }

/** Client-side presentation state; not a claim about server enums. */
enum class ProjectState { ACTIVE, ARCHIVED }

data class ProjectSummary(
    val id: ProjectId,
    val title: String,
    val state: ProjectState,
    val updatedAtMs: Long,
    val kind: ProjectKind = ProjectKind.GENERAL,
)

sealed interface ProjectsResult {
    data object Loading : ProjectsResult
    data object Empty : ProjectsResult
    data class Error(val message: String) : ProjectsResult
    data class Loaded(val projects: List<ProjectSummary>) : ProjectsResult
}

interface ProjectsRepository {
    fun observeProjects(): Flow<ProjectsResult>
    fun conversationsFor(projectId: ProjectId): Flow<List<ProjectConversation>>
}

/**
 * Fixture-backed repository for the shell + tests. `mode` lets tests/device
 * exercise every UI state (loading, empty, error, loaded) without a backend.
 */
class FakeProjectsRepository(
    var mode: Mode = Mode.SAMPLES,
    private val loadDelayMs: Long = 120,
) : ProjectsRepository {

    enum class Mode { SAMPLES, EMPTY, ERROR }

    private val projects = listOf(
        ProjectSummary(ProjectId("prj_story"), "Alexander History", ProjectState.ACTIVE, 3000L, ProjectKind.WRITING_ROOM),
        ProjectSummary(ProjectId("prj_home"), "Home", ProjectState.ACTIVE, 1000L),
        ProjectSummary(ProjectId("prj_work"), "Work", ProjectState.ACTIVE, 2000L),
        ProjectSummary(ProjectId("prj_old"), "Old kitchen reno", ProjectState.ARCHIVED, 500L),
    )

    private val conversationsByProject = mapOf(
        ProjectId("prj_story") to emptyList(),
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
}
