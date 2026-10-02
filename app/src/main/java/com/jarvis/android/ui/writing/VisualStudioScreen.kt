package com.jarvis.android.ui.writing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvis.android.transport.live.WritingWikiEntity
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.theme.LocalJarvisAccents

private enum class VisualStudioSection {
    CHARACTERS,
    LOCATIONS,
    SCENES,
    MAP,
}

@Composable
fun VisualStudioScreen(
    vm: JarvisViewModel,
    projectId: String,
    characters: List<WritingWikiEntity>,
    locations: List<WritingWikiEntity>,
    openScenesRequest: Int = 0,
    onUseSceneInChapter: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accents = LocalJarvisAccents.current
    var section by rememberSaveable(projectId) {
        mutableStateOf(VisualStudioSection.CHARACTERS)
    }

    LaunchedEffect(projectId, openScenesRequest) {
        if (openScenesRequest > 0) {
            section = VisualStudioSection.SCENES
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(accents.backdrop),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                "VISUAL STORY STUDIO",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFF8FAFC),
            )
            Spacer(Modifier.height(7.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = section == VisualStudioSection.CHARACTERS,
                    onClick = {
                        section = VisualStudioSection.CHARACTERS
                    },
                    label = { Text("Personajes") },
                )
                FilterChip(
                    selected = section == VisualStudioSection.LOCATIONS,
                    onClick = {
                        section = VisualStudioSection.LOCATIONS
                    },
                    label = { Text("Locaciones") },
                )
                FilterChip(
                    selected = section == VisualStudioSection.SCENES,
                    onClick = {
                        section = VisualStudioSection.SCENES
                    },
                    label = { Text("Escenas") },
                )
                FilterChip(
                    selected = section == VisualStudioSection.MAP,
                    onClick = {
                        section = VisualStudioSection.MAP
                    },
                    label = { Text("Mapa") },
                )
            }
        }

        when (section) {
            VisualStudioSection.CHARACTERS -> CharacterStudioScreen(
                vm = vm,
                projectId = projectId,
                characters = characters,
                modifier = Modifier.weight(1f),
            )

            VisualStudioSection.LOCATIONS -> LocationStudioScreen(
                vm = vm,
                projectId = projectId,
                locations = locations,
                modifier = Modifier.weight(1f),
            )

            VisualStudioSection.SCENES -> SceneBuilderScreen(
                vm = vm,
                projectId = projectId,
                characters = characters,
                locations = locations,
                onUseInChapter = onUseSceneInChapter,
                modifier = Modifier.weight(1f),
            )

            VisualStudioSection.MAP -> WorldMapScreen(
                vm = vm,
                projectId = projectId,
                locations = locations,
                characters = characters,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
