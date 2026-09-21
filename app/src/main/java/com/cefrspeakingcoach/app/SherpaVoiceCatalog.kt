package com.cefrspeakingcoach.app

/**
 * Catálogo de modelos de voz neuronal (sherpa-onnx / Piper) y asignación de
 * cada coach a (modelo, speaker id).
 *
 * Por qué existe este archivo:
 *
 * Un solo modelo multi-hablante da muchas voces, pero todas con el MISMO
 * acento. LibriTTS-R es en_US: sirve para Emma y Ethan, no para los coaches
 * británicos. Para tener acento británico real hace falta un segundo modelo,
 * y entonces "qué voz usa cada coach" deja de ser un simple sid: es un par
 * (modelo, sid). Ese par vive aquí.
 *
 * Modelos elegidos (ambos Piper, catálogo oficial de sherpa-onnx):
 *
 *  - en_US: vits-piper-en_US-libritts_r-medium  -> 904 hablantes. Ya instalado.
 *  - en_GB: vits-piper-en_GB-vctk-medium        -> 109 hablantes, CC BY 4.0.
 *
 * VCTK es el único en_GB multi-hablante del catálogo. Con un solo archivo
 * cubre voces femeninas y masculinas británicas, igual que libritts_r cubre
 * las americanas. Las alternativas (southern_english_female / _male) son
 * mono-hablante: harían falta dos modelos más para lograr menos variedad.
 *
 * OJO con VCTK: el corpus incluye acentos escocés, irlandés y del norte de
 * Inglaterra además del inglés del sur. Por eso los sids de abajo son un punto
 * de partida y la elección final se hace de oído en la pantalla de audición.
 */
data class SherpaTtsModel(
    /** Id interno. También es el nombre de la carpeta en filesDir. */
    val id: String,

    /** Nombre para mostrar en pantallas de depuración. */
    val displayName: String,

    /** Acento que produce este modelo. */
    val accentLabel: String,

    /** Carpeta bajo assets/ con el modelo desempaquetado (Fase 1). */
    val assetDir: String,

    /** Nombre del .zip en el release de GitHub (Fase 2). */
    val downloadFileName: String,

    /** Peso aproximado de la descarga, para avisarle al alumno antes. */
    val approxDownloadMb: Int,

    /** Hablantes esperados. Se usa solo para validar antes de cargar. */
    val expectedSpeakers: Int
)

/** Qué voz usa un coach: en qué modelo y con qué speaker id dentro de él. */
data class CoachVoiceAssignment(
    val modelId: String,
    val speakerId: Int
)

object SherpaVoiceCatalog {

    const val MODEL_US_LIBRITTS = "en_us_libritts_r"
    const val MODEL_UK_VCTK = "en_gb_vctk"

    val models: List<SherpaTtsModel> = listOf(
        SherpaTtsModel(
            id = MODEL_US_LIBRITTS,
            displayName = "LibriTTS-R · American",
            accentLabel = "American",
            assetDir = "sherpa-model",
            downloadFileName = "en_us_libritts_r.zip",
            approxDownloadMb = 80,
            expectedSpeakers = 904
        ),
        SherpaTtsModel(
            id = MODEL_UK_VCTK,
            displayName = "VCTK · British",
            accentLabel = "British",
            assetDir = "sherpa-model-uk",
            downloadFileName = "en_gb_vctk.zip",
            approxDownloadMb = 70,
            expectedSpeakers = 109
        )
    )

    /**
     * Modelo de respaldo cuando el asignado a un coach no está instalado.
     * Es el americano porque es el que ya está en el dispositivo: mientras no
     * exista assets/sherpa-model-uk/, los seis coaches siguen sonando igual que
     * hoy en vez de caer al TTS del sistema.
     */
    val fallbackModelId: String = MODEL_US_LIBRITTS

    val coachOrder: List<String> = listOf("sophie", "emma", "lily", "james", "ethan", "luca")

    /**
     * De dónde se descargan los modelos (Fase 2).
     *
     * CAMBIA ESTA LÍNEA por tu usuario, tu repo y el tag del release. Es el
     * único lugar donde vive la URL: los nombres de archivo salen de
     * [SherpaTtsModel.downloadFileName]. Tiene que terminar en "/".
     *
     * Ejemplo real:
     *   https://github.com/andypaez/cefr-speaking-coach-voices/releases/download/voices-v1/
     */
    const val MODELS_BASE_URL =
        "https://github.com/aiacos0195-co/voices-v1/releases/download/models-v1/"

    /** True mientras la URL siga con los marcadores de ejemplo. */
    fun isDownloadConfigured(): Boolean = !MODELS_BASE_URL.contains("TU_USUARIO")

    fun downloadUrl(modelId: String): String? {
        val model = model(modelId) ?: return null
        return MODELS_BASE_URL + model.downloadFileName
    }

    fun model(modelId: String): SherpaTtsModel? = models.firstOrNull { it.id == modelId }

    fun displayName(modelId: String): String = model(modelId)?.displayName ?: modelId

    /**
     * Modelo por defecto de cada coach.
     *
     * Los seis están en americano por decisión del usuario (agosto 2026): al
     * probar VCTK ninguna de las voces sonó realmente británica, así que no
     * vale la pena cargar un segundo modelo para no ganar acento. La
     * infraestructura multi-modelo se queda: el modelo británico sigue en el
     * catálogo y en la pantalla de audición, y volver a repartir acentos es
     * cambiar este mapa (o asignar desde la audición, que pesa más que esto).
     */
    private val DEFAULT_MODEL_BY_COACH: Map<String, String> = mapOf(
        "sophie" to MODEL_US_LIBRITTS,
        "lily" to MODEL_US_LIBRITTS,
        "james" to MODEL_US_LIBRITTS,
        "luca" to MODEL_US_LIBRITTS,
        "emma" to MODEL_US_LIBRITTS,
        "ethan" to MODEL_US_LIBRITTS
    )

    /**
     * Speaker id por defecto de cada coach EN CADA MODELO.
     *
     * Los seis coaches tienen un sid en los dos modelos a propósito: así, si un
     * modelo falta o el usuario mueve un coach de acento, siempre hay un número
     * válido y nadie se queda mudo.
     *
     * en_US (libritts_r): son los valores que ya venías usando.
     * en_GB (vctk): elegidos por género real del corpus. p225/p228 son mujeres
     * del sur de Inglaterra, p226 hombre de Surrey. Confírmalos de oído.
     */
    private val DEFAULT_SPEAKER_IDS: Map<String, Map<String, Int>> = mapOf(
        MODEL_US_LIBRITTS to mapOf(
            "sophie" to 40,
            "emma" to 120,
            "lily" to 250,
            "james" to 92,
            "ethan" to 500,
            "luca" to 700
        ),
        MODEL_UK_VCTK to mapOf(
            "sophie" to 107, // p225 · F · sur de Inglaterra
            "emma" to 15,    // p231 · F
            "lily" to 90,    // p228 · F · sur de Inglaterra
            "james" to 95,   // p226 · M · Surrey
            "ethan" to 82,   // p227 · M
            "luca" to 60     // p232 · M
        )
    )

    fun defaultModelId(coachId: String): String =
        DEFAULT_MODEL_BY_COACH[coachId.lowercase()] ?: fallbackModelId

    fun defaultSpeakerId(modelId: String, coachId: String): Int =
        DEFAULT_SPEAKER_IDS[modelId]?.get(coachId.lowercase()) ?: 0

    fun defaultAssignment(coachId: String): CoachVoiceAssignment {
        val modelId = defaultModelId(coachId)
        return CoachVoiceAssignment(
            modelId = modelId,
            speakerId = defaultSpeakerId(modelId, coachId)
        )
    }

    /**
     * Etiqueta del hablante dentro del modelo, si se conoce. Para VCTK es el
     * id del corpus más el género ("p225 · F"), que es justo lo que hace falta
     * para no recorrer 109 voces a ciegas buscando una mujer.
     *
     * LibriTTS-R no publica género por hablante, así que devuelve null: ahí
     * toca oído, como hasta ahora.
     */
    fun speakerLabel(modelId: String, speakerId: Int): String? {
        val entry = speakerEntry(modelId, speakerId) ?: return null
        val parts = entry.split(":")
        val name = parts.getOrNull(0) ?: return null
        val gender = parts.getOrNull(1)

        return when (gender) {
            "F" -> "$name · F"
            "M" -> "$name · M"
            else -> name
        }
    }

    /** "F", "M" o null si el modelo no publica género. */
    fun speakerGender(modelId: String, speakerId: Int): String? {
        val entry = speakerEntry(modelId, speakerId) ?: return null
        val gender = entry.split(":").getOrNull(1)
        return if (gender == "F" || gender == "M") gender else null
    }

    /** True si de este modelo sí sabemos el género de cada hablante. */
    fun hasGenderData(modelId: String): Boolean = modelId == MODEL_UK_VCTK

    /**
     * Siguiente speaker id del género pedido, avanzando circularmente desde
     * [from]. Devuelve null si el modelo no tiene datos de género.
     */
    fun nextSpeakerOfGender(
        modelId: String,
        from: Int,
        gender: String,
        forward: Boolean = true
    ): Int? {
        if (!hasGenderData(modelId)) return null

        val total = VCTK_SPEAKERS.size
        if (total == 0) return null

        val step = if (forward) 1 else -1

        for (i in 1..total) {
            val index = ((from + step * i) % total + total) % total
            if (speakerGender(modelId, index) == gender) return index
        }

        return null
    }

    private fun speakerEntry(modelId: String, speakerId: Int): String? {
        if (modelId != MODEL_UK_VCTK) return null
        return VCTK_SPEAKERS.getOrNull(speakerId)
    }

    /**
     * Hablantes de VCTK en el ORDEN del modelo de Piper (speaker_id_map del
     * .onnx.json), con el género del corpus original. El índice de la lista ES
     * el speaker id que recibe sherpa.
     *
     * "s5" es un hablante especial del corpus sin género documentado.
     */
    private val VCTK_SPEAKERS: List<String> = listOf(
        "p239:F", "p236:F", "p264:F", "p250:F", "p259:M", "p247:M",
        "p261:F", "p263:M", "p283:M", "p286:M", "p274:M", "p276:F",
        "p270:M", "p281:M", "p277:F", "p231:F", "p271:M", "p238:F",
        "p257:F", "p273:M", "p284:M", "p329:F", "p361:F", "p287:M",
        "p360:M", "p374:M", "p376:M", "p310:F", "p304:M", "p334:M",
        "p340:F", "p323:F", "p347:M", "p330:F", "p308:F", "p314:F",
        "p317:F", "p339:F", "p311:M", "p294:F", "p305:F", "p266:F",
        "p335:F", "p318:F", "p351:F", "p333:F", "p313:F", "p316:M",
        "p244:F", "p307:F", "p363:M", "p336:F", "p297:F", "p312:F",
        "p267:F", "p275:M", "p295:F", "p258:M", "p288:F", "p301:F",
        "p232:M", "p292:M", "p272:M", "p280:F", "p278:M", "p341:F",
        "p268:F", "p298:M", "p299:F", "p279:M", "p285:M", "p326:M",
        "p300:F", "s5:?", "p230:F", "p345:M", "p254:M", "p269:F",
        "p293:F", "p252:M", "p262:F", "p243:M", "p227:M", "p343:F",
        "p255:M", "p229:F", "p240:F", "p248:F", "p253:F", "p233:F",
        "p228:F", "p282:F", "p251:M", "p246:M", "p234:F", "p226:M",
        "p260:M", "p245:M", "p241:M", "p303:F", "p265:F", "p306:F",
        "p237:M", "p249:F", "p256:M", "p302:M", "p364:M", "p225:F",
        "p362:F"
    )
}
