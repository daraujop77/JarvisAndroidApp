package com.jarvis.android.ui

/**
 * Keep reference-only discovery visible when switching characters inside the
 * same writing project, including projects whose Wiki character list is empty.
 *
 * Everything character-specific intentionally starts fresh: image attachments,
 * approvals, directions, variants, errors and asynchronous media state.
 * Project switches share nothing, including the external character catalog.
 */
internal fun newCharacterStudioSelection(
    current: JarvisViewModel.CharacterStudioState,
    projectId: String,
    characterId: String,
): JarvisViewModel.CharacterStudioState =
    JarvisViewModel.CharacterStudioState(
        projectId = projectId,
        characterId = characterId,
        externalCharacters = current.externalCharacters.takeIf {
            current.projectId == projectId
        }.orEmpty(),
        projectStyle = current.projectStyle.takeIf {
            current.projectId == projectId
        },
        masterRoster = current.masterRoster.takeIf {
            current.projectId == projectId
        },
    )
