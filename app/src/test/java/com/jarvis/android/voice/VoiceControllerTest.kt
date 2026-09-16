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
    fun processingThenFinalReachesReview() {
        val (c) = controller()
        c.onMicTapped(true)
        c.onProcessing()
        assertEquals(VoicePhase.PROCESSING, c.state.phase)
        c.onFinal("done")
        assertEquals(VoicePhase.TRANSCRIPT_READY, c.state.phase)
    }

    @Test
    fun listenerCallbacksUpdateController() {
        val rec = FakeRecognizer()
        val speaker = FakeSpeaker()
        lateinit var controller: VoiceController
        val listener = object : SpeechListener {
            override fun onPartial(text: String) = controller.onPartial(text)
            override fun onProcessing() = controller.onProcessing()
            override fun onFinal(text: String) = controller.onFinal(text)
            override fun onError(safeMessage: String) = controller.onRecognizerError(safeMessage)
        }
        controller = VoiceController(rec, speaker) { false }
        controller.onMicTapped(true)
        listener.onPartial("hel")
        assertEquals("hel", controller.state.partial)
        listener.onFinal("hello")
        assertEquals(VoicePhase.TRANSCRIPT_READY, controller.state.phase)
        assertEquals("hello", controller.state.draft)
    }

    @Test
    fun shutdownCancelsRecognizerAndSpeaker() {
        val (c, rec, speaker) = controller()
        c.onMicTapped(true)
        c.shutdown()
        assertTrue(rec.cancelled >= 1)
        assertTrue(speaker.stopped >= 1)
    }

    @Test
    fun ttsRateIsBoundedBySettingsStoreContract() {
        assertEquals(0.5f, 0.1f.coerceIn(0.5f, 2f))
        assertEquals(2f, 3f.coerceIn(0.5f, 2f))
        assertEquals(1.2f, 1.2f.coerceIn(0.5f, 2f))
    }

    @Test
    fun api31GuardIsRequiredForOnDeviceApis() {
        assertTrue(android.os.Build.VERSION_CODES.S >= 31)
    }

    @Test
    fun permissionGrantStartsRecognizerExactlyOnce() {
        val gate = VoiceMicPermission()
        val rec = FakeRecognizer()
        val c = VoiceController(rec, FakeSpeaker()) { false }
        assertEquals(VoiceMicPermission.Action.REQUEST, gate.onMicTapped(false))
        assertEquals(VoiceMicPermission.Action.FAIL, gate.onMicTapped(false))
        assertEquals(VoiceMicPermission.Action.START, gate.onRequestResult(true))
        c.onMicTapped(true)
        assertEquals(1, rec.started)
        assertEquals(VoiceMicPermission.Action.START, gate.onMicTapped(true))
    }

    @Test
    fun permissionDeniedDoesNotStartAndDoesNotRerequest() {
        val gate = VoiceMicPermission()
        val rec = FakeRecognizer()
        val c = VoiceController(rec, FakeSpeaker()) { false }
        assertEquals(VoiceMicPermission.Action.REQUEST, gate.onMicTapped(false))
        assertEquals(VoiceMicPermission.Action.FAIL, gate.onRequestResult(false))
        c.onMicTapped(false)
        assertEquals(0, rec.started)
        assertEquals(VoiceMicPermission.Action.FAIL, gate.onMicTapped(false))
        assertEquals(0, rec.started)
    }

    @Test
    fun newFinalAssistantMessageSpeaksOnceEach() {
        val speaker = FakeSpeaker()
        val c = VoiceController(FakeRecognizer(), speaker) { true }
        c.onAssistantCompleted("m1", "one", isError = false, isFinal = true)
        c.onAssistantCompleted("m1", "one again", isError = false, isFinal = true)
        c.onAssistantCompleted("m2", "two", isError = false, isFinal = true)
        assertEquals(listOf("m1" to "one", "m2" to "two"), speaker.spoken)
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
