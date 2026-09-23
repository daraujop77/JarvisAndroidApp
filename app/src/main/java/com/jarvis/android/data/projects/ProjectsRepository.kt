package com.jarvis.android.data.projects

import com.jarvis.android.transport.live.JarvisAppSession
import com.jarvis.android.transport.live.writingRoomOverview
import com.jarvis.android.transport.live.writingRoomProjectCreate
import com.jarvis.android.transport.live.writingRoomProjectDelete
import com.jarvis.android.transport.live.writingRoomProjectRename
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

data class ProjectId(val value: String)

data class ProjectConversation(val conversationId: String, val title: String, val updatedAtMs: Long)

enum class ProjectKind { GENERAL, WRITING_ROOM }

enum class ProjectState { ACTIVE, ARCHIVED }

data class ProjectSummary(
    val id: ProjectId,
    val title: String,
    val state: ProjectState,
    val updatedAtMs: Long,
    val kind: ProjectKind = ProjectKind.GENERAL,
    val storyId: String = "",
    val chapterNumber: Int? = null,
    val snapshotDate: String? = null,
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
    suspend fun createProject(title: String): Result<ProjectSummary>
    suspend fun renameProject(projectId: ProjectId, title: String): Result<ProjectSummary>
    suspend fun deleteProject(projectId: ProjectId): Result<Unit>
}

/**
 * Projects come from the authenticated Writing Room session.
 * An empty project id asks the server for the published story.
 */
class LiveProjectsRepository(
    private val session: JarvisAppSession,
) : ProjectsRepository {

    override fun observeProjects(): Flow<ProjectsResult> = flow {
        emit(ProjectsResult.Loading)
        if (!session.isAuthenticated) {
            emit(ProjectsResult.Error("Sign in to load projects from JARVIS."))
            return@flow
        }
        session.writingRoomOverview("").fold(
            onSuccess = { overview ->
                val project = overview.toProjectSummary()
                if (project == null) emit(ProjectsResult.Empty)
                else emit(ProjectsResult.Loaded(listOf(project)))
            },
            onFailure = { error ->
                emit(ProjectsResult.Error(error.message ?: "Could not load projects"))
            },
        )
    }

    override fun conversationsFor(projectId: ProjectId): Flow<List<ProjectConversation>> = flowOf(emptyList())

    override suspend fun createProject(title: String): Result<ProjectSummary> {
        val clean = title.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Project title is required"))
        return session.writingRoomProjectCreate(clean).mapCatching { overview ->
            overview.toProjectSummary() ?: error("Server did not return a project")
        }
    }

    override suspend fun renameProject(projectId: ProjectId, title: String): Result<ProjectSummary> {
        val clean = title.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Project title is required"))
        return session.writingRoomProjectRename(projectId.value, clean).mapCatching { overview ->
            overview.toProjectSummary() ?: error("Server did not return a project")
        }
    }

    override suspend fun deleteProject(projectId: ProjectId): Result<Unit> =
        session.writingRoomProjectDelete(projectId.value)
}

internal fun com.jarvis.android.transport.live.WritingRoomOverview.toProjectSummary(): ProjectSummary? {
    if (project.project_id.isBlank()) return null
    return ProjectSummary(
        id = ProjectId(project.project_id),
        title = project.title.ifBlank { project.story_id.ifBlank { "Writing Room" } },
        state = ProjectState.ACTIVE,
        updatedAtMs = project.snapshot_date.toEpochMillis(),
        kind = ProjectKind.WRITING_ROOM,
        storyId = project.story_id,
        chapterNumber = latest_official_chapter?.chapter_number,
        snapshotDate = project.snapshot_date,
    )
}

private fun String?.toEpochMillis(): Long {
    if (this.isNullOrBlank()) return 0L
    return runCatching {
        LocalDate.parse(this).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrDefault(0L)
}
