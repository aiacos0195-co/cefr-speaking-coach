package com.cefrspeakingcoach.app

enum class CoachVoicePackType {
    RECORDED_PHRASE,
    CONCATENATIVE,
    HYBRID,

    /**
     * Reserved for on-device neural synthesis (sherpa-onnx / Piper VITS).
     * CustomCoachVoiceEngine cannot play this type: it is handled by
     * SherpaCoachVoiceEngine once that engine is added to the project.
     */
    NEURAL_LOCAL
}

enum class CoachVoicePackStatus {
    NOT_INSTALLED,
    INSTALLED,
    INVALID
}

data class CoachVoicePackDefinition(
    val packId: String,
    val coachId: String,
    val displayName: String,
    val type: CoachVoicePackType,
    val assetDirectory: String,
    val defaultSampleRate: Int = 22050,
    val defaultChannels: Int = 1,
    val expectedManifestFile: String = "voicepack.json"
)

data class CoachVoicePackManifest(
    val packId: String,
    val coachId: String,
    val displayName: String,
    val version: Int,
    val type: CoachVoicePackType,
    val sampleRate: Int,
    val channels: Int,
    val units: List<CoachVoiceUnit> = emptyList()
)

data class CoachVoiceUnit(
    val id: String,
    val text: String,
    val assetPath: String,
    val tags: List<String> = emptyList()
)

data class CoachVoicePackState(
    val definition: CoachVoicePackDefinition,
    val status: CoachVoicePackStatus,
    val reason: String? = null,

    /** How many units survived asset validation. */
    val usableUnitCount: Int = 0,

    /** Units declared in the manifest whose audio file was not found. */
    val missingUnitPaths: List<String> = emptyList()
) {
    fun describe(): String {
        return "${definition.packId} [${status}] usable=$usableUnitCount " +
            "missing=${missingUnitPaths.size} reason=${reason ?: "-"}"
    }
}
