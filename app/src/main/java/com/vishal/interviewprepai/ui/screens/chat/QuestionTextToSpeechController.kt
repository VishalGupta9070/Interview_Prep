package com.vishal.interviewprepai.ui.screens.chat

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

private const val TTS_TAG = "MockInterviewTts"
private const val QUESTION_UTTERANCE_ID = "QUESTION_ID"

class QuestionTextToSpeechController(
    context: Context,
    private val onAvailabilityChanged: (Boolean) -> Unit,
) {

    private var isReady = false
    private var textToSpeech: TextToSpeech? = null

    init {
        textToSpeech = TextToSpeech(context) { status ->
            val engine = textToSpeech
            if (status != TextToSpeech.SUCCESS || engine == null) {
                Log.w(TTS_TAG, "TTS initialization failed: status=$status")
                isReady = false
                onAvailabilityChanged(false)
                return@TextToSpeech
            }

            val primaryLocaleStatus = engine.setLanguage(Locale.US)
            val languageConfigured = when (primaryLocaleStatus) {
                TextToSpeech.LANG_MISSING_DATA,
                TextToSpeech.LANG_NOT_SUPPORTED,
                -> {
                    val fallbackStatus = engine.setLanguage(Locale.getDefault())
                    fallbackStatus != TextToSpeech.LANG_MISSING_DATA &&
                        fallbackStatus != TextToSpeech.LANG_NOT_SUPPORTED
                }
                else -> true
            }

            if (!languageConfigured) {
                Log.w(TTS_TAG, "TTS language not supported")
                isReady = false
                onAvailabilityChanged(false)
                return@TextToSpeech
            }

            engine.setSpeechRate(0.9f)
            isReady = true
            onAvailabilityChanged(true)
            Log.d(TTS_TAG, "TTS ready")
        }
    }

    fun speakQuestion(questionText: String, utteranceId: String = QUESTION_UTTERANCE_ID) {
        val engine = textToSpeech
        if (!isReady || engine == null || questionText.isBlank()) return

        engine.speak(
            questionText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        )
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isReady = false
    }
}
