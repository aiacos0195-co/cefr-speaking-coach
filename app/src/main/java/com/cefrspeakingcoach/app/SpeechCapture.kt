package com.cefrspeakingcoach.app

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Por que termino una captura.
 *
 * Siempre llega exactamente uno de estos por captura. Es lo que permite que la
 * pantalla no se quede en modo grabacion con el microfono muerto.
 */
enum class SpeechCaptureEnd {
    /** El motor cerro la frase por su cuenta y trajo un final. */
    COMPLETED,

    /** Silencio prolongado: SPEECH_TIMEOUT o NO_MATCH. Puede haber texto igual. */
    SILENCE,

    /** La paro la app: el alumno toco el boton, o se acabo el tiempo. */
    STOPPED,

    /** Fallo de verdad: permiso, motor ocupado, red. */
    FAILED
}

/**
 * Resultado de una captura. [text] trae TODO lo capturado, tambien cuando
 * [end] no es COMPLETED: un silencio largo no borra lo que el alumno ya dijo.
 */
data class SpeechCaptureResult(
    val end: SpeechCaptureEnd,
    val text: String,
    val errorCode: Int? = null
) {
    val hasText: Boolean get() = text.isNotBlank()
}

/**
 * El reconocimiento de voz de la app, en un solo sitio.
 *
 * Antes habia dos: el de AIConversationScreen, con los arreglos, y el de
 * MainActivity, sin ellos — por eso las sesiones por nivel perdian la
 * transcripcion en cuanto el alumno hacia una pausa. Esto es aquel, extraido.
 *
 * Son dos capas, y estan separadas a proposito (ver el Anexo A de GUIA_PASOS.md):
 *
 *  A. **Ventanas de silencio.** Configuracion del intent. Tolera pausas de unos
 *     cuatro segundos dentro de una sola sesion de reconocimiento. Siempre
 *     activa. No reinicia nada.
 *
 *  B. **Reinicio con acumulacion.** [restartOnFinalize]. Cuando el motor cierra
 *     la frase igual, arranca otra sesion y sigue acumulando, asi la pausa puede
 *     durar lo que sea. Solo funciona sin perder ni duplicar texto gracias al
 *     merge por prefijo y al dedup de mas abajo: el reinicio ingenuo, sin esa
 *     capa, es lo que se descarto en su dia.
 *
 * La capa A la ignoran varios fabricantes. Si en un telefono concreto no
 * alcanza, B es el plan B y se enciende con una linea.
 *
 * @param restartOnFinalize capa B. Con false, cada final del motor termina la
 *   captura y lo dice por [onEnded] — el microfono esta muerto y la pantalla
 *   tiene que enterarse.
 * @param canRestart consultado antes de cada reinicio. Sirve para que la
 *   pantalla frene la capa B mientras el coach habla o piensa. Irrelevante con
 *   [restartOnFinalize] en false.
 * @param onTextChanged texto acumulado hasta ahora, para pintar en vivo.
 * @param onListeningChanged el microfono esta abierto o no.
 * @param onEnded la captura termino. Exactamente una vez por captura.
 */
class SpeechCapture(
    context: Context,
    private val restartOnFinalize: Boolean,
    private val canRestart: () -> Boolean = { true },
    private val onTextChanged: (String) -> Unit = {},
    private val onListeningChanged: (Boolean) -> Unit = {},
    private val onEnded: (SpeechCaptureResult) -> Unit = {}
) {

    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val handler = Handler(Looper.getMainLooper())

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

        // CAPA A. Mas largas que los valores por defecto a proposito: un alumno
        // A1 o A2 se detiene a pensar a mitad de frase, y con las ventanas cortas
        // el motor cierra la frase — muchas veces con un final vacio — antes de
        // que termine de hablar.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
    }

    private val committedSegments = mutableListOf<String>()
    private var currentPartial = ""

    private var isCapturing = false
    private var stopRequested = false
    private var released = false

    // Solo para la traza de debug. No influye en el comportamiento.
    private var captureStartMs = 0L
    private var lastStartListeningMs = 0L
    private var lastEndOfSpeechMs = 0L
    private var sessionIndex = 0

    /**
     * Traza del reconocimiento, SOLO en debug.
     *
     * Existe para poder distinguir dos fallos que desde fuera se ven igual: que
     * el motor no oyera unas palabras, o que el ensamblado se las comiera. Los
     * crudos de cada parcial y cada final responden lo primero; las lineas de
     * SEGMENTO responden lo segundo.
     *
     * El tiempo va relativo al inicio de la captura, que es lo que se puede
     * comparar con lo que uno recuerda haber dicho.
     */
    private fun trace(message: String) {
        if (!BuildConfig.DEBUG) return
        val t = if (captureStartMs == 0L) 0 else SystemClock.elapsedRealtime() - captureStartMs
        Log.d(TAG, "[%5d ms] %s".format(t, message))
    }

    /** Todo lo capturado en la captura en curso, ya limpio. */
    val text: String
        get() = normalizeTranscript(
            (committedSegments + currentPartial)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        )

    // ---------------------------------------------------------------- control

    /** Empieza una captura nueva. Descarta lo de la captura anterior. */
    fun start() {
        if (released) return

        clear()
        isCapturing = true
        stopRequested = false

        captureStartMs = SystemClock.elapsedRealtime()
        sessionIndex = 0
        lastEndOfSpeechMs = 0L
        trace("START captura (capa B ${if (restartOnFinalize) "ENCENDIDA" else "apagada"})")

        beginSession()
    }

    /**
     * El alumno (o el temporizador) termina la captura.
     *
     * Compromete el parcial en vuelo antes de cerrar: si no, la ultima frase
     * dicha se pierde entre el ultimo parcial y el final que nunca llega.
     */
    fun stop() {
        if (released || !isCapturing) return

        stopRequested = true
        commitPendingPartial()

        runCatching { recognizer.cancel() }
        finish(SpeechCaptureEnd.STOPPED)
    }

    /** Corta sin avisar a nadie. Para cambios de pantalla. */
    fun cancel() {
        if (released) return
        isCapturing = false
        stopRequested = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer.cancel() }
        onListeningChanged(false)
    }

    fun release() {
        if (released) return
        released = true
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer.cancel() }
        runCatching { recognizer.destroy() }
    }

    fun clear() {
        committedSegments.clear()
        currentPartial = ""
        onTextChanged("")
    }

    // ---------------------------------------------------------------- interno

    private fun beginSession() {
        try {
            sessionIndex += 1
            lastStartListeningMs = SystemClock.elapsedRealtime()
            trace("startListening #$sessionIndex")
            recognizer.startListening(intent)
            onListeningChanged(true)
        } catch (_: Exception) {
            onListeningChanged(false)
            finish(SpeechCaptureEnd.FAILED)
        }
    }

    /**
     * Fin de sesion del motor. Con capa B reinicia; sin ella, termina la captura
     * y lo reporta.
     *
     * Este es el punto que evita que la pantalla mienta: sin reinicio, el
     * microfono esta cerrado, y [onEnded] es la unica forma de que el boton
     * salga de modo grabacion y el temporizador se detenga.
     */
    private fun sessionFinished(end: SpeechCaptureEnd, errorCode: Int? = null) {
        if (!isCapturing) return

        val puedeReiniciar =
            restartOnFinalize &&
                !stopRequested &&
                end != SpeechCaptureEnd.FAILED &&
                canRestart()

        trace("fin de sesion del motor #$sessionIndex: end=$end reinicio=${if (puedeReiniciar) "SI" else "no"}")

        if (puedeReiniciar) {
            onListeningChanged(false)
            handler.postDelayed({
                if (isCapturing && !stopRequested && canRestart()) beginSession()
            }, RESTART_DELAY_MS)
            return
        }

        finish(end, errorCode)
    }

    private fun finish(end: SpeechCaptureEnd, errorCode: Int? = null) {
        if (!isCapturing) return
        isCapturing = false
        handler.removeCallbacksAndMessages(null)
        onListeningChanged(false)
        trace("FIN captura: end=$end sesiones=$sessionIndex texto=\"$text\"")
        onEnded(SpeechCaptureResult(end = end, text = text, errorCode = errorCode))
    }

    private fun commitPendingPartial() {
        val pending = currentPartial
        if (pending.isNotBlank()) {
            appendSegment(pending)
            currentPartial = ""
        }
    }

    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            val ahora = SystemClock.elapsedRealtime()
            val arranque = ahora - lastStartListeningMs

            // La ventana sorda de verdad: desde que el motor dejo de escuchar
            // en la sesion anterior hasta que vuelve a estar listo. Lo que el
            // alumno diga en ese hueco no lo oye nadie.
            val hueco = if (lastEndOfSpeechMs > 0L) ahora - lastEndOfSpeechMs else -1L

            trace(
                "READY #$sessionIndex (motor escuchando) arranque=${arranque}ms" +
                    if (hueco >= 0) " VENTANA SORDA=${hueco}ms" else ""
            )
        }

        override fun onBeginningOfSpeech() {
            trace("habla detectada #$sessionIndex")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            lastEndOfSpeechMs = SystemClock.elapsedRealtime()
            trace("onEndOfSpeech #$sessionIndex (el motor dejo de escuchar)")
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            val best = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            trace("FINAL crudo #$sessionIndex: \"$best\"${if (best.isBlank()) "  <-- VACIO" else ""}")

            if (best.isNotBlank()) {
                appendSegment(best)
            } else {
                // Final vacio. El motor devuelve esto a menudo aunque las
                // palabras SI hayan llegado por onPartialResults. Sin este
                // rescate, cada segmento menos el ultimo se pierde en silencio:
                // es la causa real del "solo guarda el principio".
                commitPendingPartial()
            }

            currentPartial = ""
            onTextChanged(text)
            trace("acumulado: \"$text\"")
            sessionFinished(SpeechCaptureEnd.COMPLETED)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            trace("PARCIAL crudo #$sessionIndex: \"$partial\"")

            if (partial.isBlank()) return

            // Gana el ULTIMO parcial, no el primero. El reconocedor viejo de las
            // sesiones escribia solo el primero y ahi se quedaba, asi que con un
            // final vacio el alumno recibia nota por sus primeras palabras.
            val incoming = normalizeTranscript(partial)
            val anterior = currentPartial

            currentPartial = when {
                anterior.isBlank() -> incoming
                dedupKey(incoming).startsWith(dedupKey(anterior)) -> incoming
                dedupKey(anterior).startsWith(dedupKey(incoming)) -> anterior
                else -> incoming
            }

            onTextChanged(text)
        }

        override fun onError(error: Int) {
            when (error) {
                // Lo provocamos nosotros al cancelar o parar.
                SpeechRecognizer.ERROR_CLIENT ->
                    sessionFinished(SpeechCaptureEnd.STOPPED, error)

                // Silencio. NO es fatal en el sentido de que lo capturado se
                // conserva — pero el motor SI termino, asi que con la capa B
                // apagada la captura termina aqui y se reporta.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    commitPendingPartial()
                    onTextChanged(text)
                    sessionFinished(SpeechCaptureEnd.SILENCE, error)
                }

                else -> {
                    commitPendingPartial()
                    onTextChanged(text)
                    sessionFinished(SpeechCaptureEnd.FAILED, error)
                }
            }
        }
    }

    init {
        recognizer.setRecognitionListener(listener)
    }

    // ------------------------------------------------- ensamblado del texto

    /**
     * Agrega un segmento al acumulado, sin duplicar.
     *
     * Muchos motores de Android, al reiniciarse, devuelven la frase ENTERA
     * acumulada en cada sesion y no solo lo nuevo. Pegar a ciegas apila el
     * solape y la limpieza posterior lo colapsa a la ultima version — que es
     * exactamente el bug de "solo se queda el final" en discursos largos.
     */
    private fun appendSegment(segment: String) {
        val limpio = normalizeTranscript(segment)
        if (limpio.isBlank()) return

        val ultimo = committedSegments.lastOrNull()

        if (ultimo != null) {
            val anterior = dedupKey(ultimo)
            val entrante = dedupKey(limpio)

            if (anterior == entrante) {
                trace("SEGMENTO descartado (identico al anterior): \"$limpio\"")
                return
            }

            // Motor acumulativo: lo nuevo contiene a lo viejo como prefijo.
            // Reemplazar en vez de sumar deja una sola copia limpia.
            if (entrante.startsWith(anterior)) {
                trace("SEGMENTO reemplaza al anterior por prefijo: \"$limpio\"")
                committedSegments[committedSegments.lastIndex] = limpio
                return
            }

            // Parcial corto que llega tarde, despues de un final mas largo.
            if (anterior.startsWith(entrante)) {
                trace("SEGMENTO descartado (el anterior ya lo contiene): \"$limpio\"")
                return
            }
        }

        // Repetido de algun segmento anterior, no solo del inmediato.
        if (committedSegments.any { dedupKey(it) == dedupKey(limpio) }) {
            trace("SEGMENTO descartado (duplicado de uno anterior): \"$limpio\"")
            return
        }

        trace("SEGMENTO agregado: \"$limpio\"")
        committedSegments.add(limpio)
    }

    private fun dedupKey(text: String): String =
        text.trim().lowercase().replace(WHITESPACE, " ")

    companion object {
        /** Filtro de Logcat para seguir una grabacion entera. */
        private const val TAG = "SpeechCapture"
        private const val RESTART_DELAY_MS = 400L
        private val WHITESPACE = Regex("\\s+")

        private val REPEATED_TWO_WORDS = Regex("""\b(\w+\s+\w+)(\s+\1\b)+""", RegexOption.IGNORE_CASE)
        private val REPEATED_THREE_WORDS = Regex("""\b(\w+\s+\w+\s+\w+)(\s+\1\b)+""", RegexOption.IGNORE_CASE)
        private val LONE_I = Regex("""\b(i)\b""", RegexOption.IGNORE_CASE)
        private val SPACE_BEFORE_PUNCT = Regex("""\s+([,.!?])""")
        private val PUNCT_NO_SPACE = Regex("""([,.!?])([A-Za-z])""")

        /**
         * Limpieza de texto reconocido: colapsa repeticiones que el motor mete
         * al solapar sesiones, arregla el espaciado de la puntuacion y pone en
         * mayuscula el pronombre "I".
         */
        fun normalizeTranscript(text: String): String {
            if (text.isBlank()) return ""

            var limpio = text.replace(WHITESPACE, " ").trim()

            limpio = REPEATED_TWO_WORDS.replace(limpio) { it.groupValues[1] }
            limpio = REPEATED_THREE_WORDS.replace(limpio) { it.groupValues[1] }
            limpio = collapseRepeatedWords(limpio)

            return limpio
                .replace(LONE_I, "I")
                .replace(SPACE_BEFORE_PUNCT, "$1")
                .replace(PUNCT_NO_SPACE, "$1 $2")
                .replace(WHITESPACE, " ")
                .trim()
        }

        private fun collapseRepeatedWords(text: String): String {
            val palabras = text.split(WHITESPACE).filter { it.isNotBlank() }
            if (palabras.isEmpty()) return ""

            val salida = mutableListOf<String>()
            for (palabra in palabras) {
                if (!palabra.equals(salida.lastOrNull(), ignoreCase = true)) {
                    salida.add(palabra)
                }
            }
            return salida.joinToString(" ")
        }
    }
}
