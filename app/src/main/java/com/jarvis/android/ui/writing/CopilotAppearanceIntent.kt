package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.VisualCharacterDirectionProfile
import com.jarvis.android.transport.live.WritingWikiEntity

/** Only an unambiguous named character from the active project's Wiki can be edited. */
data class CopilotAppearanceIntent(
    val characterId: String,
    val canonicalName: String,
    val field: String? = null,
    val value: String? = null,
) {
    // `field` unqualified inside a Kotlin getter means this property's backing field.
    val isChange: Boolean get() = this.field != null && this.value != null
}

private val appearanceRead = Regex(
    """^\s*¿?\s*(?:(?:muéstrame|muestrame|muestra|enséñame|ensename|consulta|revisa)\s+(?:(?:la|el)\s+)?(?:apariencia|aspecto|ficha\s+visual|diseño\s+físico|diseno\s+fisico)\s+de|(?:cómo|como)\s+se\s+ve)\s+(.+?)\s*\??\s*$""",
    RegexOption.IGNORE_CASE,
)
private val appearanceWrite = Regex(
    """^\s*¿?\s*(?:cambia|modifica|actualiza|edita|ajusta|pon)\s+(?:(?:el|la|los|las|su)\s+)?(cabello|pelo|ojos|armadura|ropa|vestimenta|atuendo|piel|rostro|cara|altura|complexión|complexion|edad\s+aparente|accesorios|armas|colores|aura)\s+de\s+(.+?)\s+(?:a|por|para|con)\s+(.+?)\s*[.!?]?\s*$""",
    RegexOption.IGNORE_CASE,
)

private val fields = mapOf(
    "cabello" to "hair", "pelo" to "hair",
    "ojos" to "eyes",
    "armadura" to "armor",
    "ropa" to "base_outfit", "vestimenta" to "base_outfit", "atuendo" to "base_outfit",
    "piel" to "skin",
    "rostro" to "face", "cara" to "face",
    "altura" to "height",
    "complexión" to "build", "complexion" to "build",
    "edad aparente" to "apparent_age",
    "accesorios" to "accessories",
    "armas" to "weapons",
    "colores" to "dominant_colors",
    "aura" to "aura",
)

/** Supported imperative/read-only commands. Anything else goes to the normal Copilot. */
fun looksLikeCopilotAppearanceCommand(text: String): Boolean =
    appearanceRead.matches(text) || appearanceWrite.matches(text)

fun resolveCopilotAppearance(
    text: String,
    wikiCharacters: List<WritingWikiEntity>,
): CopilotAppearanceIntent? {
    val write = appearanceWrite.matchEntire(text)
    val read = if (write == null) appearanceRead.matchEntire(text) else null
    val name = (write?.groupValues?.getOrNull(2) ?: read?.groupValues?.getOrNull(1))
        ?.trim()?.trimEnd('.', '?', '!', ' ') ?: return null
    if (name.length !in 2..100) return null
    val entries = wikiCharacters.filter {
        it.type.equals("character", ignoreCase = true) && it.id.startsWith("character:") &&
            sequenceOf(it.canonical_name, it.name).any { alias ->
                alias.isNotBlank() && alias.trim().equals(name, ignoreCase = true)
            }
    }.distinctBy { it.id }
    if (entries.size != 1) return null
    val found = entries.single()
    val field = write?.groupValues?.getOrNull(1)?.trim()?.lowercase()
        ?.replace(Regex("""\s+"""), " ")?.let(fields::get)
    if (write != null && field == null) return null
    val value = write?.groupValues?.getOrNull(3)
        ?.trim()?.trimEnd('.', '?', '!', ' ')?.trim()
    if (write != null && (value == null || value.length !in 2..180 ||
            value.contains('\n') || value.contains('\r') ||
            Regex("""\b(?:y\s+)?(?:cambia|modifica|actualiza|edita|guarda|borra)\b""",
                RegexOption.IGNORE_CASE).containsMatchIn(value)
        )
    ) return null
    // Do not silently merge two Wiki character identities into one visual direction.
    if (value != null && wikiCharacters.asSequence()
            .filter { it.type.equals("character", ignoreCase = true) && it.id != found.id }
            .flatMap { sequenceOf(it.canonical_name, it.name) }
            .filter(String::isNotBlank)
            .any { alias ->
                Regex(
                    "(?<![\\p{L}\\p{N}])" + Regex.escape(alias.trim()) +
                        "(?![\\p{L}\\p{N}])",
                    RegexOption.IGNORE_CASE,
                ).containsMatchIn(value)
            }
    ) return null
    return CopilotAppearanceIntent(
        characterId = found.id,
        canonicalName = found.canonical_name.ifBlank { found.name },
        field = field,
        value = value,
    )
}

fun copilotAppearanceFieldLabel(field: String): String = when (field) {
    "hair" -> "Cabello"
    "eyes" -> "Ojos"
    "armor" -> "Armadura"
    "base_outfit" -> "Vestimenta"
    "skin" -> "Piel"
    "face" -> "Rostro"
    "height" -> "Altura"
    "build" -> "Complexión"
    "apparent_age" -> "Edad aparente"
    "accessories" -> "Accesorios"
    "weapons" -> "Armas"
    "dominant_colors" -> "Colores"
    "aura" -> "Aura"
    else -> field
}

fun copilotAppearanceFieldValue(profile: VisualCharacterDirectionProfile, field: String): String =
    when (field) {
        "hair" -> profile.hair
        "eyes" -> profile.eyes
        "armor" -> profile.armor
        "base_outfit" -> profile.base_outfit
        "skin" -> profile.skin
        "face" -> profile.face
        "height" -> profile.height
        "build" -> profile.build
        "apparent_age" -> profile.apparent_age
        "accessories" -> profile.accessories
        "weapons" -> profile.weapons
        "dominant_colors" -> profile.dominant_colors
        "aura" -> profile.aura
        else -> ""
    }

fun applyCopilotAppearanceChange(
    original: VisualCharacterDirectionProfile,
    intent: CopilotAppearanceIntent,
): VisualCharacterDirectionProfile? {
    val value = intent.value?.takeIf { intent.isChange && it.length in 2..180 } ?: return null
    return when (intent.field) {
        "hair" -> original.copy(hair = value)
        "eyes" -> original.copy(eyes = value)
        "armor" -> original.copy(armor = value)
        "base_outfit" -> original.copy(base_outfit = value)
        "skin" -> original.copy(skin = value)
        "face" -> original.copy(face = value)
        "height" -> original.copy(height = value)
        "build" -> original.copy(build = value)
        "apparent_age" -> original.copy(apparent_age = value)
        "accessories" -> original.copy(accessories = value)
        "weapons" -> original.copy(weapons = value)
        "dominant_colors" -> original.copy(dominant_colors = value)
        "aura" -> original.copy(aura = value)
        else -> null
    }
}
