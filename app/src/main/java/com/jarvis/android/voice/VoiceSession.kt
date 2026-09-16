package com.jarvis.android.voice

/**
 * POST_BASELINE_FUTURE_WORK / android/voice-v0-local.
 *
 * Device-local speech only. No voice wire contract. Audio is never persisted.
 * A transcript reaches the existing text chat only after an explicit send.
 */

enum class VoicePhase {
    IDLE,
    REQUESTING_PERMISSION,
    LISTENING,
    PROCESSING,
    TRANSCRIPT_READY,
    ERROR,
}

enum class VoiceOrbCue { IDLE, LISTENING, PROCESSING, THINKING, SPEAKING, ERROR }

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val partial: String = "",
    val draft: String = "",
    val error: String? = null,
    val permissionDenied: Boolean = false,
    val onDeviceAvailable: Boolean = false,
    val speaking: Boolean = false,
)

interface SpeechRecognizerClient {
    val onDeviceAvailable: Boolean
    fun start()
    fun stop()
    fun cancel()
}

interface SpeechListener {
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(safeMessage: String)
}

interface LocalSpeaker {
    fun speak(utteranceId: String, text: String)
    fun stop()
}

/** What may be spoken. Never errors, secrets, or debug text. */
object SpeechPrivacy {
    private val blocked = listOf(
        "bearer ", "token=", "password", "authorization:", "secret",
    )

    fun speakableAssistantText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase()
        if (blocked.any { lower.contains(it) }) return null
        return trimmed.take(2_000)
    }
}

/**
 * Pure push-to-talk + TTS dedupe. Recognizer and speaker are injected.
 * Background / navigation / process stop must call [abandon].
 */
class VoiceController(
    private val recognizer: SpeechRecognizerClient,
    private val speaker: LocalSpeaker,
    private val readAloud: () -> Boolean,
) {
    var state: VoiceUiState = VoiceUiState(onDeviceAvailable = recognizer.onDeviceAvailable)
        private set

    private var spokenIds = mutableSetOf<String>()
    private var sendConsumed = false

    val orbCue: VoiceOrbCue
        get() = when {
            state.speaking -> VoiceOrbCue.SPEAKING
            state.phase == VoicePhase.ERROR -> VoiceOrbCue.ERROR
            state.phase == VoicePhase.LISTENING -> VoiceOrbCue.LISTENING
            state.phase == VoicePhase.PROCESSING -> VoiceOrbCue.PROCESSING
            else -> VoiceOrbCue.IDLE
        }

    fun onMicTapped(permissionGranted: Boolean) {
        if (state.permissionDenied && !permissionGranted) return
        if (state.phase == VoicePhase.LISTENING || state.phase == VoicePhase.PROCESSING) {
            recognizer.cancel()
            state = state.copy(phase = VoicePhase.IDLE, partial = "", error = null)
            return
        }
        if (!permissionGranted) {
            state = state.copy(
                phase = VoicePhase.ERROR,
                permissionDenied = true,
                error = "Microphone permission denied",
            )
            return
        }
        if (!recognizer.onDeviceAvailable) {
            state = state.copy(
                phase = VoicePhase.ERROR,
                error = "On-device recognition unavailable. Network recognition stays off.",
            )
            return
        }
        sendConsumed = false
        state = state.copy(phase = VoicePhase.LISTENING, partial = "", error = null, permissionDenied = false)
        recognizer.start()
    }

    fun onPartial(text: String) {
        if (state.phase != VoicePhase.LISTENING) return
        state = state.copy(partial = text)
    }

    fun onFinal(text: String) {
        if (state.phase != VoicePhase.LISTENING && state.phase != VoicePhase.PROCESSING) return
        recognizer.stop()
        state = state.copy(
            phase = VoicePhase.TRANSCRIPT_READY,
            partial = "",
            draft = text.trim(),
            error = null,
        )
    }

    fun onRecognizerError(safeMessage: String) {
        recognizer.cancel()
        state = state.copy(phase = VoicePhase.ERROR, partial = "", error = safeMessage.take(120))
    }

    fun editDraft(text: String) {
        if (state.phase != VoicePhase.TRANSCRIPT_READY) return
        state = state.copy(draft = text)
    }

    fun retry() {
        abandon()
        state = state.copy(phase = VoicePhase.IDLE, draft = "", partial = "", error = null)
    }

    fun cancelReview() {
        abandon()
        state = VoiceUiState(onDeviceAvailable = recognizer.onDeviceAvailable)
    }

    /**
     * Explicit send. Returns the text once. A second call, rotation, or
     * recreation must not return it again.
     */
    fun consumeSend(): String? {
        if (sendConsumed) return null
        if (state.phase != VoicePhase.TRANSCRIPT_READY) return null
        val text = state.draft.trim()
        if (text.isEmpty()) return null
        sendConsumed = true
        state = VoiceUiState(onDeviceAvailable = recognizer.onDeviceAvailable)
        return text
    }

    /** Background, lock, navigation away, process stop. */
    fun abandon() {
        recognizer.cancel()
        speaker.stop()
        state = state.copy(phase = VoicePhase.IDLE, partial = "", speaking = false)
    }

    /**
     * Speak a completed assistant message at most once. Streaming partials
     * and already-spoken ids are ignored. Errors and secret-like text are not spoken.
     */
    fun onAssistantCompleted(messageId: String, text: String, isError: Boolean, isFinal: Boolean = true) {
        if (!readAloud() || isError || !isFinal) return
        if (messageId.isBlank() || messageId in spokenIds) return
        val speakable = SpeechPrivacy.speakableAssistantText(text) ?: return
        spokenIds += messageId
        state = state.copy(speaking = true)
        speaker.speak(messageId, speakable)
    }

    fun onSpeakDone(utteranceId: String) {
        if (state.speaking) state = state.copy(speaking = false)
    }

    fun stopSpeaking() {
        speaker.stop()
        state = state.copy(speaking = false)
    }
}
