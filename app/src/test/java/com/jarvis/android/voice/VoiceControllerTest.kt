package com.jarvis.android.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceControllerTest {

    private class FakeRecognizer(override var onDeviceAvailable: Boolean = true) : SpeechRecognizerClient {
        var started = 0
        var cancelled = 0
        override fun start() { started++ }
        override fun stop() = Unit
        override fun cancel() { cancelled++ }
    }

    private class FakeSpeaker : LocalSpeaker {
        val spoken = mutableListOf<Pair<String, String>>()
        var stopped = 0
        override fun speak(utteranceId: String, text: String) { spoken += utteranceId to text }
        override fun stop() { stopped++ }
    }

    private fun controller(
        rec: FakeRecognizer = FakeRecognizer(),
        speaker: FakeSpeaker = FakeSpeaker(),
        read: Boolean = false,
    ) = Triple(VoiceController(rec, speaker, { read }), rec, speaker)

    @Test
    fun permissionDeniedFailsCleanlyAndDoesNotLoop() {
        val (c, rec) = controller().let { it.first to it.second }
        c.onMicTapped(permissionGranted = false)
        assertEquals(VoicePhase.ERROR, c.state.phase)
        assertTrue(c.state.permissionDenied)
        c.onMicTapped(permissionGranted = false)
        assertEquals(0, rec.started)
        assertEquals(VoicePhase.ERROR, c.state.phase)
    }

    @Test
    fun onDeviceUnavailableDoesNotStartNetworkRecognizer() {
        val rec = FakeRecognizer(onDeviceAvailable = false)
        val c = VoiceController(rec, FakeSpeaker()) { false }
        c.onMicTapped(true)
        assertEquals(VoicePhase.ERROR, c.state.phase)
        assertEquals(0, rec.started)
        assertTrue(c.state.error!!.contains("On-device"))
    }

    @Test
    fun partialThenFinalThenExplicitSendOnce() {
        val (c, rec) = controller().let { it.first to it.second }
        c.onMicTapped(true)
        assertEquals(VoicePhase.LISTENING, c.state.phase)
        assertEquals(1, rec.started)
        c.onPartial("hel")
        assertEquals("hel", c.state.partial)
        c.onFinal("hello jarvis")
        assertEquals(VoicePhase.TRANSCRIPT_READY, c.state.phase)
        assertEquals("hello jarvis", c.state.draft)
        c.editDraft("hello JARVIS")
        assertEquals("hello JARVIS", c.consumeSend())
        assertNull(c.consumeSend())
        assertEquals(VoicePhase.IDLE, c.state.phase)
    }

    @Test
    fun cancelAndRetryDoNotSend() {
        val (c) = controller()
        c.onMicTapped(true)
        c.onFinal("draft")
        c.cancelReview()
        assertNull(c.consumeSend())
        assertEquals(VoicePhase.IDLE, c.state.phase)
    }

    @Test
    fun doubleTapMicCancelsListening() {
        val (c, rec) = controller().let { it.first to it.second }
        c.onMicTapped(true)
        c.onMicTapped(true)
        assertEquals(VoicePhase.IDLE, c.state.phase)
        assertTrue(rec.cancelled >= 1)
    }

    @Test
    fun backgroundAbandonsMic() {
        val (c, rec) = controller().let { it.first to it.second }
        c.onMicTapped(true)
        c.abandon()
        assertEquals(VoicePhase.IDLE, c.state.phase)
        assertTrue(rec.cancelled >= 1)
        assertNull(c.consumeSend())
    }

    @Test
    fun ttsSpeaksCompletedAssistantOnceAndIgnoresPartials() {
        val speaker = FakeSpeaker()
        val c = VoiceController(FakeRecognizer(), speaker) { true }
        c.onAssistantCompleted("m1", "partial", isError = false, isFinal = false)
        c.onAssistantCompleted("m1", "partial still", isError = false, isFinal = false)
        c.onAssistantCompleted("m1", "Hello there", isError = false, isFinal = true)
        c.onAssistantCompleted("m1", "Hello there again", isError = false)
        assertEquals(1, speaker.spoken.size)
        assertEquals("Hello there", speaker.spoken.single().second)
    }

    @Test
    fun ttsDisabledAndErrorsAndSecretsAreNotSpoken() {
        val speaker = FakeSpeaker()
        val off = VoiceController(FakeRecognizer(), speaker) { false }
        off.onAssistantCompleted("m1", "hi", isError = false)
        val on = VoiceController(FakeRecognizer(), speaker) { true }
        on.onAssistantCompleted("e1", "boom", isError = true)
        on.onAssistantCompleted("s1", "Authorization: Bearer abc", isError = false)
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun stopTtsImmediately() {
        val speaker = FakeSpeaker()
        val c = VoiceController(FakeRecognizer(), speaker) { true }
        c.onAssistantCompleted("m1", "long reply", isError = false)
        assertTrue(c.state.speaking)
        c.stopSpeaking()
        assertFalse(c.state.speaking)
        assertEquals(1, speaker.stopped)
    }

    @Test
    fun recognizerErrorRecoversToIdleOnRetry() {
        val (c) = controller()
        c.onMicTapped(true)
        c.onRecognizerError("recognition failed")
        assertEquals(VoicePhase.ERROR, c.state.phase)
        c.retry()
        assertEquals(VoicePhase.IDLE, c.state.phase)
    }
}
