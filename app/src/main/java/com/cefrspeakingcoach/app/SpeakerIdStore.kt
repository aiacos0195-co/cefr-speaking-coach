package com.cefrspeakingcoach.app

import android.content.Context

/**
 * Guarda la voz que el usuario eligió de oído para cada coach.
 *
 * Ahora una elección son DOS datos: qué modelo (acento) y qué speaker id
 * dentro de ese modelo. El sid 40 de libritts_r no tiene nada que ver con el
 * sid 40 de VCTK, así que los ids se guardan con el modelo en la llave:
 *
 *   model_<coach>          -> "en_gb_vctk"
 *   sid_<modelId>_<coach>  -> 107
 *
 * Las elecciones que ya estaban guardadas con el esquema viejo (sid_<coach>,
 * todas del modelo americano) se migran solas la primera vez. No se pierde el
 * trabajo de oído ya hecho.
 */
class SpeakerIdStore(context: Context) {

    // Respaldo: este archivo esta en la lista de INCLUSION de
    // backup_rules.xml y de las dos secciones de data_extraction_rules.xml.
    // Unas prefs NUEVAS hay que agregarlas ahi tambien, o no se respaldan
    // y nadie se entera hasta que alguien restaure.
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyKeysIfNeeded()
    }

    /** Modelo elegido para un coach, o null si nunca se cambió. */
    fun savedModelId(coachId: String): String? {
        val value = prefs.getString(modelKey(coachId), null)
        return value?.takeIf { SherpaVoiceCatalog.model(it) != null }
    }

    fun setModelId(coachId: String, modelId: String) {
        prefs.edit().putString(modelKey(coachId), modelId).apply()
    }

    /** Speaker id elegido para un coach EN ESE MODELO, o null. */
    fun savedSpeakerId(modelId: String, coachId: String): Int? {
        val key = speakerKey(modelId, coachId)
        return if (prefs.contains(key)) prefs.getInt(key, -1).takeIf { it >= 0 } else null
    }

    /**
     * Asigna una voz completa: el coach pasa a usar [modelId] con [speakerId].
     * Es lo que llama la pantalla de audición al tocar el botón de un coach.
     */
    fun assign(coachId: String, modelId: String, speakerId: Int) {
        prefs.edit()
            .putString(modelKey(coachId), modelId)
            .putInt(speakerKey(modelId, coachId), speakerId)
            .apply()
    }

    /** Elección efectiva para un coach: lo guardado, si no el default. */
    fun assignmentFor(coachId: String): CoachVoiceAssignment {
        val key = coachId.lowercase()
        val modelId = savedModelId(key) ?: SherpaVoiceCatalog.defaultModelId(key)
        val speakerId = savedSpeakerId(modelId, key)
            ?: SherpaVoiceCatalog.defaultSpeakerId(modelId, key)

        return CoachVoiceAssignment(modelId = modelId, speakerId = speakerId)
    }

    /** True si el usuario tocó algo para este coach (útil para la UI). */
    fun isCustom(coachId: String): Boolean {
        val key = coachId.lowercase()
        val modelId = savedModelId(key)
        return modelId != null || savedSpeakerId(
            SherpaVoiceCatalog.defaultModelId(key),
            key
        ) != null
    }

    /** Mapa completo coach -> asignación, con los defaults ya aplicados. */
    fun resolvedAssignments(): Map<String, CoachVoiceAssignment> =
        SherpaVoiceCatalog.coachOrder.associateWith { assignmentFor(it) }

    fun clearCoach(coachId: String) {
        val key = coachId.lowercase()
        val editor = prefs.edit()
        editor.remove(modelKey(key))
        SherpaVoiceCatalog.models.forEach { model ->
            editor.remove(speakerKey(model.id, key))
        }
        editor.apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        prefs.edit().putBoolean(MIGRATION_FLAG, true).apply()
    }

    /**
     * Esquema viejo: sid_<coach>, siempre del modelo americano. Se copia una
     * sola vez a sid_en_us_libritts_r_<coach> y se deja marcado.
     */
    private fun migrateLegacyKeysIfNeeded() {
        if (prefs.getBoolean(MIGRATION_FLAG, false)) return

        val legacyModelId = SherpaVoiceCatalog.MODEL_US_LIBRITTS
        val editor = prefs.edit()

        SherpaVoiceCatalog.coachOrder.forEach { coachId ->
            val legacyKey = "sid_$coachId"
            if (!prefs.contains(legacyKey)) return@forEach

            val legacySid = prefs.getInt(legacyKey, -1)
            if (legacySid < 0) return@forEach

            val newKey = speakerKey(legacyModelId, coachId)
            if (!prefs.contains(newKey)) {
                editor.putInt(newKey, legacySid)
            }
            // A propósito NO se fija model_<coach>. Si se fijara, los seis
            // coaches quedarían clavados en americano y el modelo británico
            // nunca entraría solo. El sid migrado se sigue usando cada vez que
            // el coach suene con el modelo americano (incluido el respaldo
            // mientras el británico no esté instalado).

            editor.remove(legacyKey)
        }

        editor.putBoolean(MIGRATION_FLAG, true)
        editor.apply()
    }

    private fun modelKey(coachId: String) = "model_${coachId.lowercase()}"

    private fun speakerKey(modelId: String, coachId: String) =
        "sid_${modelId}_${coachId.lowercase()}"

    companion object {
        private const val PREFS_NAME = "sherpa_speaker_ids"
        private const val MIGRATION_FLAG = "migrated_multimodel_v1"
    }
}
