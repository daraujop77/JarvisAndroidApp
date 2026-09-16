package com.jarvis.android.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * On-device recognizer only. If the platform cannot do on-device recognition,
 * this client reports unavailable and never starts a network recognizer.
 * Audio is not written to disk.
 */
class AndroidSpeechRecognizerClient(
    context: Context,
    private val listener: SpeechListener,
) : SpeechRecognizerClient {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    override val onDeviceAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isRecognitionAvailable(appContext) &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    override fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !onDeviceAvailable) {
            listener.onError("On-device recognition unavailable")
            return
        }
        release()
        val sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                listener.onProcessing()
            }
            override fun onError(error: Int) {
                listener.onError("recognition failed")
                release()
            }
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                listener.onFinal(text)
                release()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) listener.onPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        sr.startListening(intent)
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.cancel()
        release()
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }
}

class AndroidLocalSpeaker(
    context: Context,
    private val rate: () -> Float,
    private val onDone: (String) -> Unit,
) : LocalSpeaker, TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.getDefault()
    }

    override fun speak(utteranceId: String, text: String) {
        if (!ready) return
        tts.setSpeechRate(rate().coerceIn(0.5f, 2f))
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId != null) onDone(utteranceId)
            }
            @Deprecated("legacy")
            override fun onError(utteranceId: String?) {
                if (utteranceId != null) onDone(utteranceId)
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        tts.stop()
    }

    override fun shutdown() {
        tts.shutdown()
    }
}
