package com.cefrspeakingcoach.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Voz neuronal offline con sherpa-onnx sobre modelos VITS de Piper.
 *
 * Multi-modelo, UN modelo en RAM a la vez.
 *
 * Por qué así: cada modelo VITS "medium" ocupa del orden de 150 MB cargado.
 * Dos modelos a la vez (americano + británico) sacan de memoria a los
 * teléfonos de gama baja que usan los alumnos A1, que es justo el público. Por
 * eso el motor carga el modelo del coach que va a hablar y libera el anterior.
 * El cambio de coach cuesta uno o dos segundos la primera vez; hablar con el
 * mismo coach no cuesta nada extra.
 *
 * Otras decisiones que conviene no perder:
 *
 * - El modelo se carga desde filesDir, no desde assets. Ver SherpaModelInstaller.
 *
 * - ConversationVoiceModel.pitch se ignora. VITS no tiene parámetro de tono, y
 *   desplazar el tono después suena peor que elegir otro hablante. Los coaches
 *   se distinguen por speaker id, no por pitch.
 *
 * - La síntesis no es en tiempo real en teléfonos viejos: speak() vuelve de
 *   inmediato y el resultado llega por los callbacks.
 *
 * - Si el modelo asignado a un coach no está instalado, se usa el modelo de
 *   respaldo en vez de quedarse mudo. Así la app funciona igual antes y
 *   después de agregar el modelo británico.
 */
class SherpaCoachVoiceEngine(
    context: Context,
    private val callbacks: CoachVoiceEngineCallbacks = CoachVoiceEngineCallbacks()
) : CoachVoiceEngine, CoachAwareVoiceEngine {

    override val engineId: String = "sherpa_onnx"
    override val displayName: String = "Sherpa-ONNX Neural Voice"

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val player = PcmCoachAudioPlayer()
    private val installer = SherpaModelInstaller(appContext)
    private val speakerIdStore = SpeakerIdStore(appContext)

    /**
     * Serializa carga y síntesis. Es lo que impide liberar un modelo mientras
     * el nativo está generando audio con él (crash seguro).
     */
    private val engineMutex = Mutex()

    /**
     * Scope aparte, que nunca se cancela, solo para liberar el nativo. Si el
     * release fuera en [scope] no correría nunca: release() cancela [scope]
     * y los 150 MB del modelo se quedarían colgando.
     */
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val generation = AtomicInteger(0)

    @Volatile
    private var tts: OfflineTts? = null

    /** Id del modelo actualmente cargado en RAM, o null. */
    @Volatile
    var loadedModelId: String? = null
        private set

    @Volatile
    private var speakJob: Job? = null

    @Volatile
    private var ready: Boolean = false

    /** Modelos completos en disco. Se refresca al instalar o cargar. */
    @Volatile
    private var installedIds: List<String> = emptyList()

    /** Hablantes por modelo, conocidos solo después de cargarlo. */
    private val speakerCounts = ConcurrentHashMap<String, Int>()

    /** Última falla, para poder mostrarla en una pantalla de depuración. */
    var lastError: String? = null
        private set

    override fun initialize() {
        if (ready) return

        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    installer.migrateLegacyLayout()
                    refreshInstalled()

                    val primary = pickPrimaryModelId()
                        ?: throw IllegalStateException(
                            "No sherpa model installed and none bundled in assets"
                        )

                    engineMutex.withLock { loadModelLocked(primary) }
                    primary
                }
            }

            loaded
                .onSuccess { modelId ->
                    ready = true
                    lastError = null

                    Log.i(
                        TAG,
                        "sherpa-onnx ready: $modelId with " +
                            "${speakerCounts[modelId] ?: 0} speaker(s). " +
                            "Installed: $installedIds"
                    )

                    postReady()
                }
                .onFailure { error ->
                    ready = false
                    lastError = error.message ?: "Model load failed"
                    Log.w(TAG, "sherpa-onnx unavailable: $lastError")
                    // Silencioso a propósito: no tener modelo instalado es el
                    // estado normal antes de la primera descarga, no un error
                    // que valga la pena mostrarle al alumno.
                }
        }
    }

    /**
     * Qué modelo precargar. El del coach que hable primero puede ser otro, pero
     * precargar uno deja isReady() en true rápido y evita que la primera frase
     * se vaya al TTS del sistema.
     */
    private fun pickPrimaryModelId(): String? {
        if (installedIds.isNotEmpty()) {
            return installedIds.firstOrNull { it == SherpaVoiceCatalog.fallbackModelId }
                ?: installedIds.first()
        }

        // Nada instalado: intenta traer el de respaldo desde assets (Fase 1).
        val fromAssets = installer.ensureInstalled(SherpaVoiceCatalog.fallbackModelId)
        if (fromAssets.isSuccess) {
            refreshInstalled()
            return SherpaVoiceCatalog.fallbackModelId
        }

        // Último intento: cualquier otro modelo del catálogo que sí esté en assets.
        SherpaVoiceCatalog.models.forEach { model ->
            if (installer.ensureInstalled(model.id).isSuccess) {
                refreshInstalled()
                return model.id
            }
        }

        return null
    }

    private fun refreshInstalled() {
        installedIds = installer.installedModelIds()
    }

    /**
     * Carga [modelId] liberando el anterior. Debe llamarse con [engineMutex]
     * tomado y fuera del hilo principal.
     */
    private fun loadModelLocked(modelId: String): OfflineTts {
        val current = tts
        if (current != null && loadedModelId == modelId) return current

        if (current != null) {
            Log.i(TAG, "Releasing ${loadedModelId} to load $modelId")
            runCatching { current.release() }
            tts = null
            loadedModelId = null
        }

        val modelDir = installer.installedModelDir(modelId)
            ?: installer.ensureInstalled(modelId).getOrElse { error ->
                throw IllegalStateException(
                    "Model $modelId not available: ${error.message}"
                )
            }

        val modelFile = installer.findModelFile(modelDir)
            ?: throw IllegalStateException("No .onnx file in ${modelDir.absolutePath}")

        val tokensFile = File(modelDir, "tokens.txt")
        require(tokensFile.exists()) { "Missing tokens.txt in ${modelDir.absolutePath}" }

        val dataDir = installer.espeakDataDir(modelId)
            ?: throw IllegalStateException("No espeak-ng-data available for $modelId")

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = dataDir.absolutePath,
                    lexicon = ""
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu"
            ),
            // Parte las respuestas largas en oraciones para que el primer audio
            // llegue antes.
            maxNumSentences = 1
        )

        val engine = OfflineTts(config = config)

        tts = engine
        loadedModelId = modelId
        speakerCounts[modelId] = runCatching { engine.numSpeakers() }.getOrDefault(1)
        refreshInstalled()

        Log.i(
            TAG,
            "Loaded $modelId: ${speakerCounts[modelId]} speaker(s) at " +
                "${runCatching { engine.sampleRate() }.getOrDefault(0)} Hz"
        )

        return engine
    }

    /**
     * Qué modelo y qué hablante le tocan a este coach, ya considerando lo que
     * está instalado. null solo si no hay NINGÚN modelo utilizable.
     */
    private fun resolvePlan(coachId: String): CoachVoiceAssignment? {
        val available = installedIds
        if (available.isEmpty()) return null

        val wanted = speakerIdStore.assignmentFor(coachId)
        if (available.contains(wanted.modelId)) return wanted

        // El modelo asignado no está: respaldo. Se respeta el sid que el
        // usuario haya elegido de oído para ESE modelo de respaldo.
        val fallbackId = available.firstOrNull { it == SherpaVoiceCatalog.fallbackModelId }
            ?: available.first()

        val fallbackSid = speakerIdStore.savedSpeakerId(fallbackId, coachId)
            ?: SherpaVoiceCatalog.defaultSpeakerId(fallbackId, coachId)

        return CoachVoiceAssignment(modelId = fallbackId, speakerId = fallbackSid)
    }

    private fun clampSpeaker(modelId: String, speakerId: Int): Int {
        val total = speakerCounts[modelId] ?: SherpaVoiceCatalog.model(modelId)?.expectedSpeakers
        if (total == null || total <= 0) return 0

        return if (speakerId in 0 until total) {
            speakerId
        } else {
            Log.w(TAG, "Speaker $speakerId out of range for $modelId ($total), using 0")
            0
        }
    }

    override fun isReady(): Boolean = ready && installedIds.isNotEmpty()

    override fun canSpeakAs(voice: ConversationVoiceModel): Boolean {
        if (!isReady()) return false
        return resolvePlan(voice.id) != null
    }

    /** Hablantes de un modelo ya cargado alguna vez; 0 si aún no se sabe. */
    fun speakerCount(modelId: String): Int = speakerCounts[modelId] ?: 0

    /** Modelos del catálogo completos en disco. */
    fun installedModelIds(): List<String> = installedIds

    /**
     * Carga un modelo por adelantado y avisa cuántos hablantes tiene. La
     * pantalla de audición la usa al cambiar de acento.
     */
    fun prepareModel(modelId: String, onResult: (Int, String?) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    engineMutex.withLock { loadModelLocked(modelId) }
                    speakerCounts[modelId] ?: 0
                }
            }

            result
                .onSuccess { count ->
                    ready = true
                    lastError = null
                    mainHandler.post { onResult(count, null) }
                }
                .onFailure { error ->
                    val message = error.message ?: "Model load failed"
                    lastError = message
                    Log.w(TAG, "prepareModel($modelId) failed: $message")
                    mainHandler.post { onResult(0, message) }
                }
        }
    }

    /**
     * Borra un modelo del teléfono y libera la RAM si estaba cargado. Después
     * de esto los coaches de ese modelo caen al modelo de respaldo, y si no
     * queda ninguno, al TTS de Android.
     */
    fun deleteModel(modelId: String, onResult: (String?) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    engineMutex.withLock {
                        if (loadedModelId == modelId) {
                            runCatching { tts?.release() }
                            tts = null
                            loadedModelId = null
                        }

                        installer.uninstall(modelId)
                        speakerCounts.remove(modelId)
                        refreshInstalled()
                    }
                }
            }

            ready = installedIds.isNotEmpty() && tts != null

            result
                .onSuccess { mainHandler.post { onResult(null) } }
                .onFailure { error ->
                    val message = error.message ?: "Delete failed"
                    Log.w(TAG, "deleteModel($modelId) failed: $message")
                    mainHandler.post { onResult(message) }
                }
        }
    }

    /**
     * Recarga un modelo que cambió en disco (recién descargado, por ejemplo).
     * A diferencia de [prepareModel], no se salta la carga cuando el id ya está
     * cargado: si no se libera el anterior, el motor sigue hablando con el
     * archivo viejo que ya no existe.
     */
    fun reloadModel(modelId: String, onResult: (Int, String?) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    engineMutex.withLock {
                        if (loadedModelId == modelId) {
                            runCatching { tts?.release() }
                            tts = null
                            loadedModelId = null
                        }

                        speakerCounts.remove(modelId)
                        refreshInstalled()

                        loadModelLocked(modelId)
                    }
                    speakerCounts[modelId] ?: 0
                }
            }

            result
                .onSuccess { count ->
                    ready = true
                    lastError = null
                    mainHandler.post { onResult(count, null) }
                }
                .onFailure { error ->
                    val message = error.message ?: "Reload failed"
                    lastError = message
                    Log.w(TAG, "reloadModel($modelId) failed: $message")
                    mainHandler.post { onResult(0, message) }
                }
        }
    }

    /**
     * Borra el modelo de filesDir y lo vuelve a instalar desde assets, forzando
     * la recarga aunque ya estuviera en RAM. Es la salida cuando una instalación
     * quedó a medias o con los archivos equivocados: sin esto, el motor ve la
     * carpeta "completa" y nunca la vuelve a copiar.
     */
    fun reinstallModel(modelId: String, onResult: (Int, String?) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    engineMutex.withLock {
                        if (loadedModelId == modelId) {
                            runCatching { tts?.release() }
                            tts = null
                            loadedModelId = null
                        }

                        installer.uninstall(modelId)
                        speakerCounts.remove(modelId)
                        refreshInstalled()

                        loadModelLocked(modelId)
                    }
                    speakerCounts[modelId] ?: 0
                }
            }

            result
                .onSuccess { count ->
                    ready = true
                    lastError = null
                    Log.i(TAG, "reinstallModel($modelId) ok: $count speakers")
                    mainHandler.post { onResult(count, null) }
                }
                .onFailure { error ->
                    val message = error.message ?: "Reinstall failed"
                    lastError = message
                    Log.w(TAG, "reinstallModel($modelId) failed: $message")
                    mainHandler.post { onResult(0, message) }
                }
        }
    }

    /**
     * Estado real de un modelo, en texto, para mostrarlo EN PANTALLA. Dice qué
     * hay en assets, qué hay en filesDir, cuál .onnx se está usando y qué está
     * cargado en RAM ahora mismo. Son lecturas baratas de File; se llama desde
     * la pantalla de depuración.
     */
    fun modelDiagnostics(modelId: String): List<String> {
        val model = SherpaVoiceCatalog.model(modelId)
            ?: return listOf("Modelo desconocido: $modelId")

        val lines = mutableListOf<String>()

        val assetNames = runCatching { appContext.assets.list(model.assetDir).orEmpty() }
            .getOrDefault(emptyArray())

        lines += if (assetNames.isEmpty()) {
            "assets/${model.assetDir}/: VACÍO o no existe"
        } else {
            "assets/${model.assetDir}/: " + assetNames.joinToString(", ")
        }

        val dir = installer.modelDir(modelId)
        if (!dir.isDirectory) {
            lines += "filesDir: NO instalado"
        } else {
            val onnx = installer.findModelFile(dir)
            lines += if (onnx == null) {
                "filesDir: sin .onnx"
            } else {
                "filesDir .onnx: ${onnx.name} (${onnx.length() / 1_000_000} MB)"
            }
            lines += "filesDir contiene: " + (dir.list()?.joinToString(", ") ?: "?")
        }

        lines += "espeak: " + (installer.espeakDataDir(modelId)?.absolutePath ?: "NO ENCONTRADA")
        lines += "cargado en RAM: " + (loadedModelId ?: "ninguno")
        lines += "voces del modelo: " + (speakerCounts[modelId] ?: 0)

        return lines
    }

    /**
     * Ayuda de audición: habla [text] con un [modelId] y [speakerId] explícitos,
     * ignorando el mapa de coaches. Solo la usa SpeakerAuditionScreen.
     */
    fun auditionSpeak(
        text: String,
        modelId: String,
        speakerId: Int,
        speed: Float = 1.0f
    ): Boolean {
        if (text.isBlank()) return false

        cancelCurrentSpeech()
        val myGeneration = generation.incrementAndGet()

        speakJob = scope.launch {
            try {
                postStarted()

                val generated = withContext(Dispatchers.IO) {
                    engineMutex.withLock {
                        if (generation.get() != myGeneration) {
                            null
                        } else {
                            val engine = loadModelLocked(modelId)
                            engine.generate(
                                text = sanitize(text),
                                sid = clampSpeaker(modelId, speakerId),
                                speed = speed.coerceIn(0.5f, 2.0f)
                            )
                        }
                    }
                }

                ensureActive()
                if (generation.get() != myGeneration) return@launch
                if (generated == null) return@launch

                if (generated.samples.isEmpty()) {
                    postError("Sherpa produced no audio")
                    return@launch
                }

                player.play(
                    audio = CoachVoiceAudio(
                        samples = generated.samples,
                        sampleRate = generated.sampleRate,
                        channels = 1
                    ),
                    onCompleted = { if (generation.get() == myGeneration) postCompleted() },
                    onError = { message -> if (generation.get() == myGeneration) postError(message) }
                )
            } catch (e: Exception) {
                if (generation.get() == myGeneration) {
                    Log.e(TAG, "Audition synthesis failed", e)
                    postError(e.message ?: "Sherpa synthesis failed")
                }
            }
        }

        return true
    }

    override fun speak(
        text: String,
        voice: ConversationVoiceModel
    ): Boolean {
        if (!ready || text.isBlank()) return false

        val plan = resolvePlan(voice.id) ?: return false

        cancelCurrentSpeech()
        val myGeneration = generation.incrementAndGet()

        speakJob = scope.launch {
            try {
                postStarted()

                val generated = withContext(Dispatchers.IO) {
                    engineMutex.withLock {
                        if (generation.get() != myGeneration) {
                            null
                        } else {
                            val engine = loadModelLocked(plan.modelId)
                            engine.generate(
                                text = sanitize(text),
                                sid = clampSpeaker(plan.modelId, plan.speakerId),
                                speed = voice.speechRate.coerceIn(0.5f, 2.0f)
                            )
                        }
                    }
                }

                ensureActive()
                if (generation.get() != myGeneration) return@launch
                if (generated == null) return@launch

                if (generated.samples.isEmpty()) {
                    postError("Sherpa produced no audio")
                    return@launch
                }

                player.play(
                    audio = CoachVoiceAudio(
                        samples = generated.samples,
                        sampleRate = generated.sampleRate,
                        channels = 1
                    ),
                    onCompleted = {
                        if (generation.get() == myGeneration) postCompleted()
                    },
                    onError = { message ->
                        if (generation.get() == myGeneration) postError(message)
                    }
                )
            } catch (e: Exception) {
                if (generation.get() == myGeneration) {
                    Log.e(TAG, "Synthesis failed", e)
                    postError(e.message ?: "Sherpa synthesis failed")
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

        // El release del nativo espera a que termine cualquier síntesis en
        // curso: sin el candado se liberaría un modelo que el hilo de IO
        // todavía está usando.
        val engine = tts
        tts = null
        loadedModelId = null

        runCatching { scope.cancel() }

        releaseScope.launch {
            engineMutex.withLock {
                runCatching { engine?.release() }
            }
        }
    }

    private fun cancelCurrentSpeech() {
        generation.incrementAndGet()
        speakJob?.cancel()
        speakJob = null
    }

    /**
     * espeak-ng entiende casi toda la puntuación, pero el markdown suelto y los
     * espacios repetidos del motor de conversación lo hacen tropezar.
     */
    private fun sanitize(text: String): String {
        return text
            .replace(Regex("[*_`#>]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
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
        private const val TAG = "SherpaCoachVoiceEngine"
    }
}
