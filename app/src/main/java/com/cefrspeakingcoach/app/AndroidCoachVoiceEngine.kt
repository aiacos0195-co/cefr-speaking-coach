package com.cefrspeakingcoach.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * System Text-to-Speech engine. This is the safety net: it must stay the last
 * entry in CoachVoiceOrchestrator so any custom engine can fall back to it.
 *
 * v2 changes:
 *  - Gender scoring no longer uses String.contains(). "female".contains("male")
 *    is true, so asking for a male coach used to award +10 to every female
 *    voice on the device and James could end up speaking with Emma's voice.
 *  - initialize() no longer depends on the TextToSpeech constructor returning
 *    before its own init callback fires. On devices where the callback runs
 *    first, the old code hit a null reference and silently never became ready.
 */
class AndroidCoachVoiceEngine(
    context: Context,
    private val callbacks: CoachVoiceEngineCallbacks = CoachVoiceEngineCallbacks()
) : CoachVoiceEngine {

    override val engineId: String = "android_tts"
    override val displayName: String = "Android Text-to-Speech"

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ttsRef = AtomicReference<TextToSpeech?>(null)

    @Volatile
    private var initStatus: Int? = null

    @Volatile
    private var ready: Boolean = false

    override fun initialize() {
        if (ttsRef.get() != null) return

        val instance = TextToSpeech(appContext) { status ->
            initStatus = status
            applyInitStatus()
        }

        ttsRef.compareAndSet(null, instance)
        applyInitStatus()
    }

    @Synchronized
    private fun applyInitStatus() {
        if (ready) return

        val status = initStatus ?: return
        val instance = ttsRef.get() ?: return

        if (status == TextToSpeech.SUCCESS) {
            ready = true
            setupProgressListener(instance)
            postReady()
            Log.i(TAG, "Android TTS ready")
        } else {
            ready = false
            ttsRef.set(null)
            runCatching { instance.shutdown() }
            postError("Android TTS could not be initialized (status $status)")
        }
    }

    override fun isReady(): Boolean {
        return ready && ttsRef.get() != null
    }

    override fun speak(
        text: String,
        voice: ConversationVoiceModel
    ): Boolean {
        val engine = ttsRef.get() ?: return false
        if (!ready || text.isBlank()) return false

        return try {
            stop()
            configureVoice(engine, voice)

            val utteranceId = "coach_${voice.id}_${UUID.randomUUID()}"

            val result = engine.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )

            if (result == TextToSpeech.ERROR) {
                postError("Android TTS could not speak")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "speak failed", e)
            postError(e.message ?: "Android TTS failed")
            false
        }
    }

    override fun stop() {
        runCatching { ttsRef.get()?.stop() }
    }

    override fun release() {
        ready = false
        initStatus = null

        val engine = ttsRef.getAndSet(null)
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
    }

    private fun setupProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                postStarted()
            }

            override fun onDone(utteranceId: String?) {
                postCompleted()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                postError("Android TTS playback failed")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                postError("Android TTS playback failed. Code: $errorCode")
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                postCompleted()
            }
        })
    }

    private fun configureVoice(
        engine: TextToSpeech,
        voiceModel: ConversationVoiceModel
    ) {
        val locale = Locale.forLanguageTag(
            voiceModel.localeTag.ifBlank { "en-US" }
        )

        val languageResult = engine.setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            engine.setLanguage(Locale.US)
        }

        selectBestSystemVoice(engine, voiceModel)?.let { selected ->
            runCatching { engine.voice = selected }
        }

        engine.setSpeechRate(voiceModel.speechRate)
        engine.setPitch(voiceModel.pitch)
    }

    private fun selectBestSystemVoice(
        engine: TextToSpeech,
        voiceModel: ConversationVoiceModel
    ): Voice? {
        val targetLocale = Locale.forLanguageTag(
            voiceModel.localeTag.ifBlank { "en-US" }
        )

        val voices = runCatching { engine.voices?.toList() }.getOrNull().orEmpty()
        if (voices.isEmpty()) return null

        val sameLanguage = voices.filter { candidate ->
            candidate.locale.language.equals(targetLocale.language, ignoreCase = true)
        }

        if (sameLanguage.isEmpty()) return null

        val offline = sameLanguage.filter { !it.isNetworkConnectionRequired }
        val candidates = offline.ifEmpty { sameLanguage }

        val wantedGender = normalizeGender(voiceModel.genderLabel)
        val accentKeywords = accentKeywordsForVoice(voiceModel)

        return candidates.maxByOrNull { candidate ->
            val descriptor = buildDescriptor(candidate)
            var score = 0

            if (candidate.locale.country.equals(targetLocale.country, ignoreCase = true)) {
                score += 10
            }

            if (accentKeywords.any { keyword -> descriptor.contains(keyword) }) {
                score += 6
            }

            when (detectGender(descriptor)) {
                null -> Unit
                wantedGender -> score += 14
                else -> score -= 10
            }

            if (candidate.quality >= Voice.QUALITY_HIGH) score += 3
            if (candidate.latency <= Voice.LATENCY_NORMAL) score += 2

            score
        }
    }

    /**
     * Lowercased tokens from the voice name plus its declared features, split on
     * every non-alphanumeric boundary so matching happens on whole words.
     */
    private fun buildDescriptor(candidate: Voice): Set<String> {
        val raw = buildString {
            append(candidate.name)
            append(' ')
            append(candidate.features?.joinToString(" ").orEmpty())
        }

        return raw
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * Order matters. "female" has to be tested before "male" or every female
     * voice reads as male under substring matching.
     */
    private fun detectGender(descriptor: Set<String>): String? {
        if (descriptor.any { it in FEMALE_TOKENS }) return "female"
        if (descriptor.any { it in MALE_TOKENS }) return "male"
        return null
    }

    private fun normalizeGender(label: String): String {
        return if (label.equals("Male", ignoreCase = true)) "male" else "female"
    }

    private fun accentKeywordsForVoice(
        voiceModel: ConversationVoiceModel
    ): List<String> {
        return when (voiceModel.localeTag.lowercase()) {
            "en-gb" -> listOf("gb", "uk", "british", "england")
            "en-au" -> listOf("au", "australia", "australian")
            "en-us" -> listOf("us", "usa", "american")
            else -> listOf("en", "us")
        }
    }

    private fun postReady() {
        mainHandler.post { callbacks.onReady(engineId) }
    }

    private fun postStarted() {
        mainHandler.post { callbacks.onStarted(engineId) }
    }

    private fun postCompleted() {
        mainHandler.post { callbacks.onCompleted(engineId) }
    }

    private fun postError(message: String) {
        mainHandler.post { callbacks.onError(engineId, message) }
    }

    companion object {
        private const val TAG = "AndroidCoachVoiceEngine"

        private val FEMALE_TOKENS = setOf("female", "woman", "girl", "feminine", "f")
        private val MALE_TOKENS = setOf("male", "man", "boy", "masculine", "m")
    }
}
