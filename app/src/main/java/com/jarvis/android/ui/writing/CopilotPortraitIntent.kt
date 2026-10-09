package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.WritingWikiEntity

/** Explicit portrait commands only. Names must match the active project's Wiki. */
data class CopilotPortraitIntent(
    val characterId: String,
    val canonicalName: String,
    val adjustment: String,
)

private val portraitCommand = Regex(
    """^\s*(?:genera|crea|haz|dibuja)\s+(?:(?:un|una)\s+)?(?:retrato|imagen)\s+(?:de|del)\s+(.+?)\s*$""",
    RegexOption.IGNORE_CASE,
)

fun looksLikeCopilotPortraitCommand(prompt: String): Boolean =
    portraitCommand.matches(prompt.trim())

fun resolveCopilotPortrait(
    prompt: String,
    wikiCharacters: List<WritingWikiEntity>,
): CopilotPortraitIntent? {
    val text = portraitCommand.matchEntire(prompt.trim())?.groupValues?.getOrNull(1)?.trim()
        ?: return null
    if (text.length !in 2..1000) return null
    // Prefer the longest canonical Wiki name. Never infer an identity from a substring.
    val found = wikiCharacters
        .asSequence()
        .filter { it.type.equals("character", ignoreCase = true) && it.id.startsWith("character:") }
        .flatMap { entry ->
            sequenceOf(entry.canonical_name, entry.name)
                .filter(String::isNotBlank)
                .map { name -> Pair(entry, name.trim()) }
        }
        .filter { (_, name) ->
            text.equals(name, ignoreCase = true) ||
                (text.startsWith(name, ignoreCase = true) &&
                    text.getOrNull(name.length) in listOf(' ', ',', ';', '.'))
        }
        .sortedByDescending { it.second.length }
        .toList()
    val best = found.firstOrNull() ?: return null
    val tied = found.filter { it.second.length == best.second.length && it.first.id != best.first.id }
    if (tied.isNotEmpty()) return null
    val rest = text.drop(best.second.length).trim().trimStart(',', ';', '.').trim()
    if (rest.isNotEmpty() && !Regex(
        """^(?:con|en|vistiendo|usando|desde|mirando|de\s+perfil|de\s+frente)\b""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(rest)) {
        // Reject requests potentially mixing subjects, e.g. "Naruto y Sasuke".
        return null
    }
    // A single-character portrait must never silently include a second Wiki character.
    val additionalNamedCharacter = wikiCharacters.asSequence()
        .filter { it.type.equals("character", ignoreCase = true) && it.id != best.first.id }
        .flatMap { sequenceOf(it.canonical_name, it.name).filter(String::isNotBlank) }
        .any { other ->
            val name = other.trim()
            name.isNotBlank() && Regex(
                "(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(rest)
        }
    if (additionalNamedCharacter) return null
    return CopilotPortraitIntent(
        characterId = best.first.id,
        canonicalName = best.first.canonical_name.ifBlank { best.second },
        adjustment = rest.take(850),
    )
}
