package com.jarvis.android.ui.writing

import com.jarvis.android.data.story.CANON_CHARACTERS
import com.jarvis.android.data.story.CharacterAppearanceRecord
import com.jarvis.android.data.story.CharacterHistoryStage
import com.jarvis.android.data.story.CharacterLifeStatus
import com.jarvis.android.data.story.StoryCharacter
import com.jarvis.android.transport.live.WritingWikiEntity
import com.jarvis.android.ui.theme.JarvisCyan

/**
 * Presentation adapter for the server-owned structured Wiki.
 *
 * Canon/story facts always come from [WritingWikiEntity]. The checked-in
 * character list is retained only for visual/presentation metadata (accent,
 * avatar, legacy faction label) and as an offline fallback.
 */
fun mergeStructuredCharacters(
    entities: List<WritingWikiEntity>,
    fallback: List<StoryCharacter> = CANON_CHARACTERS,
): List<StoryCharacter> {
    if (entities.isEmpty()) return fallback
    return entities
        .filter { it.type.equals("character", ignoreCase = true) }
        .map { entity -> mergeStructuredCharacter(entity, fallback) }
        .sortedBy { it.name.lowercase() }
}

fun mergeStructuredCharacter(
    entity: WritingWikiEntity,
    fallback: List<StoryCharacter> = CANON_CHARACTERS,
): StoryCharacter {
    val canonicalName = entity.canonical_name.ifBlank { entity.name }
    val normalizedId = entity.id.substringAfter("character:", entity.id)
    val base = fallback.firstOrNull {
        it.name.equals(canonicalName, ignoreCase = true) ||
            it.id.equals(normalizedId, ignoreCase = true)
    }

    val history = entity.history
        .filter { it.summary.isNotBlank() || it.label.isNotBlank() }
        .joinToString("\n\n") { item ->
            when {
                item.label.isNotBlank() && item.summary.isNotBlank() -> "${item.label}: ${item.summary}"
                item.summary.isNotBlank() -> item.summary
                else -> item.label
            }
        }
    val appearances = entity.appearances
        .filter { it.chapters.isNotEmpty() || it.summary.isNotBlank() }
        .joinToString("\n") { appearance ->
            val chapters = appearance.chapters.joinToString(", ")
            val prefix = if (chapters.isNotBlank()) "Capítulos $chapters" else "Aparición"
            val kind = appearance.kind.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
            if (appearance.summary.isBlank()) "$prefix$kind" else "$prefix$kind: ${appearance.summary}"
        }
    val narrativeHistory = buildString {
        if (history.isNotBlank()) append(history)
        if (appearances.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append("Apariciones canónicas:\n").append(appearances)
        }
        if (entity.mentions.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append("Menciones adicionales: capítulos ").append(entity.mentions.joinToString(", "))
        }
    }
    val psychology = listOfNotNull(
        entity.central_wound.takeIf { it.isNotBlank() }?.let { "Herida central: $it" },
        entity.desire_vs_need.takeIf { it.isNotBlank() }?.let { "Deseo vs. necesidad: $it" },
        entity.internal_contradiction.takeIf { it.isNotBlank() }?.let { "Contradicción interna: $it" },
    ).joinToString("\n\n")
    val powers = buildList {
        addAll(entity.abilities)
        if (entity.limitations.isNotEmpty()) {
            add("Límites y costos: " + entity.limitations.joinToString(" · "))
        }
    }.joinToString(" · ")

    val relationshipText = entity.relationships
        .filter { it.target.isNotBlank() }
        .joinToString(" · ") { relation ->
            val targetId = relation.target.substringAfter("character:", relation.target)
            val targetName = fallback.firstOrNull { it.id.equals(targetId, ignoreCase = true) }?.name
                ?: targetId.replace('_', ' ').replaceFirstChar { it.uppercase() }
            if (relation.kind.isBlank()) targetName else "$targetName (${relation.kind})"
        }

    return StoryCharacter(
        id = base?.id ?: normalizedId,
        name = canonicalName.ifBlank { base?.name.orEmpty() }.ifBlank { normalizedId },
        epithet = base?.epithet ?: entity.role.ifBlank { "Expediente canónico" },
        faction = base?.faction ?: "Canon JARVIS",
        role = entity.role.ifBlank { base?.role.orEmpty() },
        status = base?.status ?: inferStatus(entity.current_state),
        statusDetail = entity.current_state.ifBlank { base?.statusDetail.orEmpty() },
        appearance = entity.appearance.ifBlank { base?.appearance.orEmpty() },
        essence = entity.summary.ifBlank { base?.essence.orEmpty() },
        powersOverview = powers.ifBlank { base?.powersOverview.orEmpty() },
        signatureTechniques = if (entity.techniques.isNotEmpty()) {
            entity.techniques.map { technique ->
                technique to "Técnica canónica registrada en la Wiki estructurada."
            }
        } else {
            base?.signatureTechniques.orEmpty()
        },
        storyHistory = narrativeHistory.ifBlank { base?.storyHistory.orEmpty() },
        centralWound = psychology.ifBlank { base?.centralWound.orEmpty() }.ifBlank { null },
        relationships = relationshipText.ifBlank { base?.relationships.orEmpty() }.ifBlank { null },
        avatarInitial = base?.avatarInitial
            ?: canonicalName.firstOrNull()?.uppercaseChar()?.toString()
            ?: "?",
        themeColor = base?.themeColor ?: JarvisCyan,
        relatedCharacterIds = if (entity.relationships.isNotEmpty()) {
            entity.relationships.map { it.target.substringAfter("character:", it.target) }
        } else {
            base?.relatedCharacterIds.orEmpty()
        },
        canonicalAbilities = entity.abilities,
        canonicalLimitations = entity.limitations,
        historyTimeline = entity.history
            .filter { it.label.isNotBlank() || it.summary.isNotBlank() }
            .map { item ->
                CharacterHistoryStage(
                    period = item.period,
                    label = item.label,
                    summary = item.summary,
                )
            },
        canonAppearances = entity.appearances
            .filter { it.chapters.isNotEmpty() || it.summary.isNotBlank() }
            .map { item ->
                CharacterAppearanceRecord(
                    chapters = item.chapters,
                    kind = item.kind,
                    summary = item.summary,
                )
            },
        mentionedChapters = entity.mentions,
    )
}

fun searchStructuredCharacters(
    characters: List<StoryCharacter>,
    query: String,
): List<StoryCharacter> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return characters
    return characters.filter { character ->
        val haystack = buildString {
            append(character.name).append(' ')
            append(character.epithet).append(' ')
            append(character.faction).append(' ')
            append(character.role).append(' ')
            append(character.statusDetail).append(' ')
            append(character.appearance).append(' ')
            append(character.essence).append(' ')
            append(character.powersOverview).append(' ')
            append(character.storyHistory).append(' ')
            append(character.relationships.orEmpty()).append(' ')
            append(character.signatureTechniques.joinToString(" ") { it.first + " " + it.second })
        }.lowercase()
        terms.all { it in haystack }
    }
}

private fun inferStatus(currentState: String): CharacterLifeStatus {
    val value = currentState.lowercase()
    return when {
        "coma" in value -> CharacterLifeStatus.IN_COMA
        "deceased" in value || "fallecid" in value || "muert" in value -> CharacterLifeStatus.DECEASED
        "controlad" in value || "controlled" in value -> CharacterLifeStatus.CONTROLLED
        "crític" in value || "critical" in value || "herida grave" in value -> CharacterLifeStatus.CRITICAL_INJURY
        "marcad" in value || "marked" in value -> CharacterLifeStatus.ALIVE_MARKED
        else -> CharacterLifeStatus.ALIVE
    }
}
