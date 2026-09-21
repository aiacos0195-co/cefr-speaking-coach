package com.cefrspeakingcoach.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plays coach speech from bundled voice packs: whole recorded phrases first,
 * concatenated word units as a fallback.
 *
 * v2 changes:
 *  - The synthesis coroutine is now tracked in [speakJob] and cancelled by
 *    stop()/release(). Previously the Job was untracked, so a cancelled
 *    utterance still fired onCompleted after its estimated duration and raced
 *    with whatever was playing by then.
 *  - Completion comes from the player instead of delay(estimatedMs).
 *  - findBestRecordedPhraseUnit() matches on token boundaries with a coverage
 *    floor. The old version used String.contains(), so a unit whose text was
 *    "hi" matched the coach line "This is important" and played the wrong clip.
 *  - The scope is cancelled in release(), which previously leaked it.
 */
class CustomCoachVoiceEngine(
    context: Context,
    private val callbacks: CoachVoiceEngineCallbacks = CoachVoiceEngineCallbacks()
) : CoachVoiceEngine, CoachAwareVoiceEngine {

    override val engineId: String = "custom_voice_engine"
    override val displayName: String = "CEFR Custom Voice Engine"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val player = PcmCoachAudioPlayer()
    private val voicePackRepository = CoachVoicePackRepository(context)
    private val concatenativeSynthesizer = ConcatenativeCoachVoiceSynthesizer(
        repository = voicePackRepository
    )

    private val generation = AtomicInteger(0)

    @Volatile
    private var speakJob: Job? = null

    @Volatile
    private var ready: Boolean = false

    private var installedPackDefinitions: Map<String, CoachVoicePackDefinition> = emptyMap()
    private var installedPackManifests: Map<String, CoachVoicePackManifest> = emptyMap()

    /** Human readable pack diagnostics, useful from a debug screen or logcat. */
    var lastInspectionReport: List<String> = emptyList()
        private set

    override fun initialize() {
        val states = voicePackRepository.inspectAllPacks()

        lastInspectionReport = states.map { it.describe() }
        lastInspectionReport.forEach { line -> Log.i(TAG, line) }

        val definitions = mutableMapOf<String, CoachVoicePackDefinition>()
        val manifests = mutableMapOf<String, CoachVoicePackManifest>()

        states
            .filter { it.status == CoachVoicePackStatus.INSTALLED }
            .forEach { state ->
                val definition = state.definition

                val manifest = runCatching {
                    voicePackRepository.loadUsableManifest(definition)
                }.getOrElse { error ->
                    Log.w(TAG, "Could not load manifest for ${definition.packId}", error)
                    null
                }

                if (manifest != null && manifest.units.isNotEmpty()) {
                    definitions[definition.coachId.lowercase()] = definition
                    manifests[definition.coachId.lowercase()] = manifest
                }
            }

        installedPackDefinitions = definitions
        installedPackManifests = manifests

        ready = installedPackManifests.values.any { manifest ->
            when (manifest.type) {
                CoachVoicePackType.RECORDED_PHRASE,
                CoachVoicePackType.CONCATENATIVE,
                CoachVoicePackType.HYBRID -> manifest.units.isNotEmpty()

                CoachVoicePackType.NEURAL_LOCAL -> false
            }
        }

        Log.i(TAG, "initialize() ready=$ready packs=${installedPackManifests.keys}")

        if (ready) postReady()
    }

    override fun isReady(): Boolean {
        return ready
    }

    /** True when this specific coach has a usable pack, not just any coach. */
    override fun canSpeakAs(voice: ConversationVoiceModel): Boolean {
        val coachId = voice.id.lowercase()
        val manifest = installedPackManifests[coachId] ?: return false
        return manifest.units.isNotEmpty()
    }

    override fun speak(
        text: String,
        voice: ConversationVoiceModel
    ): Boolean {
        if (!ready) return false
        if (text.isBlank()) return false

        val coachId = voice.id.lowercase()
        val definition = installedPackDefinitions[coachId] ?: return false
        val manifest = installedPackManifests[coachId] ?: return false

        val playbackPlan = createPlaybackPlan(
            text = text,
            definition = definition,
            manifest = manifest
        ) ?: return false

        // Cancel anything still in flight before claiming this generation.
        cancelCurrentSpeech()

        val myGeneration = generation.incrementAndGet()

        speakJob = scope.launch {
            try {
                postStarted()

                val audio = withContext(Dispatchers.IO) {
                    playbackPlan.loadAudio()
                }

                ensureActive()
                if (generation.get() != myGeneration) return@launch

                player.play(
                    audio = audio,
                    onCompleted = {
                        if (generation.get() == myGeneration) postCompleted()
                    },
                    onError = { message ->
                        if (generation.get() == myGeneration) postError(message)
                    }
                )
            } catch (e: Exception) {
                if (generation.get() == myGeneration) {
                    Log.e(TAG, "Custom voice playback failed", e)
                    postError(e.message ?: "Custom voice failed")
                }
            }
        }

        return true
    }

    override fun stop() {
        cancelCurrentSpeech()
        player.stop()
    }

    override fun release() {
        ready = false
        cancelCurrentSpeech()
        player.release()

        installedPackDefinitions = emptyMap()
        installedPackManifests = emptyMap()

        runCatching { scope.cancel() }
    }

    private fun cancelCurrentSpeech() {
        generation.incrementAndGet()
        speakJob?.cancel()
        speakJob = null
    }

    private fun createPlaybackPlan(
        text: String,
        definition: CoachVoicePackDefinition,
        manifest: CoachVoicePackManifest
    ): CustomVoicePlaybackPlan? {
        return when (manifest.type) {
            CoachVoicePackType.RECORDED_PHRASE -> {
                createRecordedPhrasePlan(text, definition, manifest)
            }

            CoachVoicePackType.CONCATENATIVE -> {
                createConcatenativePlan(text, definition, manifest)
            }

            CoachVoicePackType.HYBRID -> {
                createRecordedPhrasePlan(text, definition, manifest)
                    ?: createConcatenativePlan(text, definition, manifest)
            }

            CoachVoicePackType.NEURAL_LOCAL -> null
        }
    }

    private fun createRecordedPhrasePlan(
        text: String,
        definition: CoachVoicePackDefinition,
        manifest: CoachVoicePackManifest
    ): CustomVoicePlaybackPlan? {
        val unit = findBestRecordedPhraseUnit(text, manifest) ?: return null

        return CustomVoicePlaybackPlan {
            loadRecordedPhraseAudio(definition, unit)
        }
    }

    private fun createConcatenativePlan(
        text: String,
        definition: CoachVoicePackDefinition,
        manifest: CoachVoicePackManifest
    ): CustomVoicePlaybackPlan? {
        if (!concatenativeSynthesizer.canSynthesize(text, manifest)) return null

        return CustomVoicePlaybackPlan {
            concatenativeSynthesizer.synthesize(
                text = text,
                definition = definition,
                manifest = manifest
            ) ?: throw IllegalStateException("Concatenative synthesis failed")
        }
    }

    /**
     * A recorded phrase may only stand in for the coach line if it *is*
     * substantially that line. Exact match wins; otherwise the unit's tokens
     * must appear contiguously in the text AND cover at least
     * [MIN_PHRASE_COVERAGE] of it. Longest match wins.
     */
    private fun findBestRecordedPhraseUnit(
        text: String,
        manifest: CoachVoicePackManifest
    ): CoachVoiceUnit? {
        val textTokens = tokenize(text)
        if (textTokens.isEmpty()) return null

        val exact = manifest.units.firstOrNull { unit ->
            tokenize(unit.text) == textTokens
        }

        if (exact != null) return exact

        return manifest.units
            .mapNotNull { unit ->
                val unitTokens = tokenize(unit.text)
                if (unitTokens.isEmpty()) return@mapNotNull null
                if (!containsTokenSequence(textTokens, unitTokens)) return@mapNotNull null

                val coverage = unitTokens.size.toFloat() / textTokens.size.toFloat()
                if (coverage < MIN_PHRASE_COVERAGE) return@mapNotNull null

                unit to unitTokens.size
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun containsTokenSequence(
        haystack: List<String>,
        needle: List<String>
    ): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false

        for (start in 0..(haystack.size - needle.size)) {
            var matched = true

            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) {
                    matched = false
                    break
                }
            }

            if (matched) return true
        }

        return false
    }

    private fun loadRecordedPhraseAudio(
        definition: CoachVoicePackDefinition,
        unit: CoachVoiceUnit
    ): CoachVoiceAudio {
        val path = voicePackRepository.resolveUnitAssetPath(definition, unit)

        require(path.endsWith(".wav", ignoreCase = true)) {
            "Unsupported recorded phrase format for '${unit.id}'. Use .wav."
        }

        val bytes = voicePackRepository.readAssetBytes(path)
        return WavCoachAudioDecoder.decode(bytes)
    }

    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
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

    private fun interface CustomVoicePlaybackPlan {
        fun loadAudio(): CoachVoiceAudio
    }

    companion object {
        private const val TAG = "CustomCoachVoiceEngine"

        /**
         * A recorded clip must cover at least this share of the coach line.
         * Below it, falling through to TTS is more honest than playing a
         * fragment that does not match the text on screen.
         */
        private const val MIN_PHRASE_COVERAGE = 0.7f
    }
}
