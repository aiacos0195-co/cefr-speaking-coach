package com.cefrspeakingcoach.app

data class CoachVoiceEngineCallbacks(
    val onReady: (engineId: String) -> Unit = {},
    val onStarted: (engineId: String) -> Unit = {},
    val onCompleted: (engineId: String) -> Unit = {},
    val onError: (engineId: String, message: String) -> Unit = { _, _ -> }
)

interface CoachVoiceEngine {
    val engineId: String
    val displayName: String

    fun initialize()

    fun isReady(): Boolean

    /**
     * Starts speaking and returns true if the utterance was ACCEPTED, not if it
     * finished. Engines that synthesise asynchronously (custom packs, sherpa)
     * report the real outcome through [CoachVoiceEngineCallbacks.onCompleted]
     * or [CoachVoiceEngineCallbacks.onError].
     */
    fun speak(
        text: String,
        voice: ConversationVoiceModel
    ): Boolean

    fun stop()

    fun release()
}

/**
 * Implemented by engines whose readiness is per-coach rather than global.
 *
 * CustomCoachVoiceEngine is ready as soon as ONE pack is installed, but that
 * says nothing about the coach the user actually picked. Without this, the
 * orchestrator hands an utterance to an engine that then returns false, and
 * the fallback only works because speak() happens to be synchronous.
 */
interface CoachAwareVoiceEngine {
    fun canSpeakAs(voice: ConversationVoiceModel): Boolean
}
