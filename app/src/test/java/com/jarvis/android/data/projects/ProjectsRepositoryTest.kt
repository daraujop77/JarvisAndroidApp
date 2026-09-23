package com.jarvis.android.data.projects

import com.jarvis.android.transport.live.WritingLatestChapter
import com.jarvis.android.transport.live.WritingOverviewProject
import com.jarvis.android.transport.live.WritingRoomOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectsRepositoryTest {

    @Test
    fun overviewMapsToThePublishedStory() {
        val project = WritingRoomOverview(
            project = WritingOverviewProject(
                project_id = "prj_story",
                story_id = "STORY-001",
                title = "Alexander History",
                snapshot_date = "2026-09-21",
            ),
            latest_official_chapter = WritingLatestChapter(chapter_number = 37, title = "Chapter 37"),
        ).toProjectSummary()

        requireNotNull(project)
        assertEquals("prj_story", project.id.value)
        assertEquals("Alexander History", project.title)
        assertEquals("STORY-001", project.storyId)
        assertEquals(37, project.chapterNumber)
        assertEquals(ProjectKind.WRITING_ROOM, project.kind)
        assertEquals(ProjectState.ACTIVE, project.state)
    }

    @Test
    fun blankProjectIdIsNotAProject() {
        val project = WritingRoomOverview().toProjectSummary()
        assertNull(project)
    }
}
