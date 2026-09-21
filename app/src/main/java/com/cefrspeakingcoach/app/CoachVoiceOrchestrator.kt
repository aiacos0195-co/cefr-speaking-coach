package com.cefrspeakingcoach.app

import android.util.Log

/**
 * Routes an utterance to the first engine that can actually speak it.
 *
 * Engine order is priority order. Keep AndroidCoachVoiceEngine last: it is the
 * only engine guaranteed to work on a stock device, so it is the safety net.
 *
 * v2 changes:
 *  - Per-coach routing. Engines implementing [CoachAwareVoiceEngine] are asked
 *    whether they can speak as THIS coach, not just whether they are ready.
 *  - Async failure recovery via [retryWithNextEngine]. speak() returning true
 *    only means the utterance was accepted; a neural engine can still fail
 *    while synthesising, and without this the coach just goes silent.
 *
 * The constructor signature is unchanged, so existing call sites keep working.
 */
class CoachVoiceOrchestrator(
    private val engines: List<CoachVoiceEngine>
) {
    var activeEngineId: String? = null
        private set

    private var activeEngineIndex: Int = -1
    private var lastText: String? = null
    private var lastVoice: ConversationVoiceModel? = null

    fun initialize() {
        engines.forEach { engine ->
            runCatching { engine.initialize() }
                .onFailure { error ->
                    Log.e(TAG, "initialize failed for ${engine.engineId}", error)
                }
        }
    }

    fun isReady(): Boolean {
        return engines.any { it.isReady() }
    }

    /** True when at least one engine can voice this specific coach. */
    fun isReadyFor(voice: ConversationVoiceModel): Boolean {
        return engines.any { engine -> canHandle(engine, voice) }
    }

    fun speak(
        text: String,
        voice: ConversationVoiceModel
    ): Boolean {
        if (text.isBlank()) return false

        stop()

        lastText = text
        lastVoice = voice

        return speakFrom(startIndex = 0, text = text, voice = voice)
    }

    /**
     * Call this from the screen's onError callback when an engine fails after
     * accepting an utterance. Returns true if another engine picked it up.
     *
     * Wiring, in AIConversationScreen:
     *
     *     onError = { engineId, message ->
     *         if (!voiceOrchestrator.retryWithNextEngine(engineId)) {
     *             isCoachSpeaking = false
     *             statusText = message
     *         }
     *     }
     */
    fun retryWithNextEngine(failedEngineId: String): Boolean {
        val text = lastText ?: return false
        val voice = lastVoice ?: return false

        val failedIndex = engines.indexOfFirst { it.engineId == failedEngineId }
        if (failedIndex < 0) return false
        if (failedIndex != activeEngineIndex) return false

        Log.w(TAG, "Engine $failedEngineId failed, trying the next one")

        return speakFrom(startIndex = failedIndex + 1, text = text, voice = voice)
    }

    private fun speakFrom(
        startIndex: Int,
        text: String,
        voice: ConversationVoiceModel
    ): Boolean {
        for (index in startIndex until engines.size) {
            val engine = engines[index]

            if (!canHandle(engine, voice)) continue

            val started = runCatching {
                engine.speak(text = text, voice = voice)
            }.getOrElse { error ->
                Log.e(TAG, "speak threw in ${engine.engineId}", error)
                false
            }

            if (started) {
                activeEngineId = engine.engineId
                activeEngineIndex = index
                Log.i(TAG, "Speaking with ${engine.engineId} as ${voice.id}")
                return true
            }
        }

        activeEngineId = null
        activeEngineIndex = -1
        Log.w(TAG, "No engine could speak as ${voice.id}")
        return false
    }

    private fun canHandle(
        engine: CoachVoiceEngine,
        voice: ConversationVoiceModel
    ): Boolean {
        if (!engine.isReady()) return false

        if (engine is CoachAwareVoiceEngine) {
            return engine.canSpeakAs(voice)
        }

        return true
    }

    fun stop() {
        activeEngineId = null
        activeEngineIndex = -1

        engines.forEach { engine ->
            runCatching { engine.stop() }
        }
    }

    fun release() {
        activeEngineId = null
        activeEngineIndex = -1
        lastText = null
        lastVoice = null

        engines.forEach { engine ->
            runCatching { engine.release() }
        }
    }

    /** One line per engine. Handy for a debug screen or a logcat dump. */
    fun describeEngines(): List<String> {
        return engines.map { engine ->
            "${engine.engineId} (${engine.displayName}) ready=${engine.isReady()}"
        }
    }

    companion object {
        private const val TAG = "CoachVoiceOrchestrator"
    }
}
