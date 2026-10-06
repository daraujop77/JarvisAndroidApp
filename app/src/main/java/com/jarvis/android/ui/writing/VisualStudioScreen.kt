package com.jarvis.android.ui.writing

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvis.android.transport.live.VisualStudioAsset
import com.jarvis.android.transport.live.WritingWikiEntity
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.screens.ImageStudioScreen
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
    var sceneEditorAttachmentId by rememberSaveable(projectId) {
        mutableStateOf<String?>(null)
    }
    var sceneEditorParent by remember(projectId) {
        mutableStateOf<VisualStudioAsset?>(null)
    }
    var characterEditorAttachmentId by rememberSaveable(projectId) {
        mutableStateOf<String?>(null)
    }
    var characterEditorCharacterId by rememberSaveable(projectId) {
        mutableStateOf<String?>(null)
    }
    var characterEditorParent by remember(projectId) {
        mutableStateOf<VisualStudioAsset?>(null)
    }

    LaunchedEffect(projectId, openScenesRequest) {
        if (openScenesRequest > 0) {
            section = VisualStudioSection.SCENES
        }
    }

    val characterAttachmentId = characterEditorAttachmentId
    val characterId = characterEditorCharacterId
    val characterParent = characterEditorParent
    if (
        !characterAttachmentId.isNullOrBlank() &&
        !characterId.isNullOrBlank() &&
        characterParent != null
    ) {
        ImageStudioScreen(
            vm = vm,
            initialAttachmentId = characterAttachmentId,
            onBack = {
                characterEditorAttachmentId = null
                characterEditorCharacterId = null
                characterEditorParent = null
            },
            showWikiPrimaryActions = false,
            onEditRequest = {
                    referenceAttachmentId,
                    instruction,
                    mode,
                    model,
                    preserveIdentity,
                    aspectRatio,
                ->
                vm.editCharacterVisualInStudio(
                    parentAsset = characterParent,
                    characterId = characterId,
                    referenceAttachmentId = referenceAttachmentId,
                    instruction = instruction,
                    mode = mode,
                    model = model,
                    preserveIdentity = preserveIdentity,
                    aspectRatio = aspectRatio,
                )
            },
        )
        return
    }

    val editorAttachmentId = sceneEditorAttachmentId
    val editorParent = sceneEditorParent
    if (!editorAttachmentId.isNullOrBlank() && editorParent != null) {
        ImageStudioScreen(
            vm = vm,
            initialAttachmentId = editorAttachmentId,
            onBack = {
                sceneEditorAttachmentId = null
                sceneEditorParent = null
            },
            showWikiPrimaryActions = false,
            onEditRequest = {
                    referenceAttachmentId,
                    instruction,
                    mode,
                    model,
                    preserveIdentity,
                    aspectRatio,
                ->
                vm.editSceneVisual(
                    parentAsset = editorParent,
                    referenceAttachmentId = referenceAttachmentId,
                    instruction = instruction,
                    mode = mode,
                    model = model,
                    preserveIdentity = preserveIdentity,
                    aspectRatio = aspectRatio,
                )
            },
        )
        return
    }

    val navigation: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                "VISUAL STORY STUDIO",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFF8FAFC),
            )
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = section == VisualStudioSection.CHARACTERS,
                    onClick = {
                        section = VisualStudioSection.CHARACTERS
                    },
                    label = { Text("Personajes", maxLines = 1) },
                )
                FilterChip(
                    selected = section == VisualStudioSection.LOCATIONS,
                    onClick = {
                        section = VisualStudioSection.LOCATIONS
                    },
                    label = { Text("Locaciones", maxLines = 1) },
                )
                FilterChip(
                    selected = section == VisualStudioSection.SCENES,
                    onClick = {
                        section = VisualStudioSection.SCENES
                    },
                    label = { Text("Escenas", maxLines = 1) },
                )
                FilterChip(
                    selected = section == VisualStudioSection.MAP,
                    onClick = {
                        section = VisualStudioSection.MAP
                    },
                    label = { Text("Mapa", maxLines = 1) },
                )
            }
        }
    }
    Column(modifier.fillMaxSize().background(accents.backdrop)) {
        when (section) {
            VisualStudioSection.CHARACTERS -> CharacterStudioScreen(
                vm = vm,
                projectId = projectId,
                characters = characters,
                onEditInImageStudio = { attachmentId, selectedCharacterId, asset ->
                    characterEditorAttachmentId = attachmentId
                    characterEditorCharacterId = selectedCharacterId
                    characterEditorParent = asset
                },
                headerContent = navigation,
                modifier = Modifier.weight(1f),
            )

            VisualStudioSection.LOCATIONS -> LocationStudioScreen(
                vm = vm,
                projectId = projectId,
                locations = locations,
                headerContent = navigation,
                modifier = Modifier.weight(1f),
            )

            VisualStudioSection.SCENES -> SceneBuilderScreen(
                vm = vm,
                projectId = projectId,
                characters = characters,
                locations = locations,
                onUseInChapter = onUseSceneInChapter,
                onEditScene = { attachmentId, asset ->
                    sceneEditorAttachmentId = attachmentId
                    sceneEditorParent = asset
                },
                headerContent = navigation,
                modifier = Modifier.weight(1f),
            )

            VisualStudioSection.MAP -> WorldMapScreen(
                vm = vm,
                projectId = projectId,
                locations = locations,
                characters = characters,
                headerContent = navigation,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
