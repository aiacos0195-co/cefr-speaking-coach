package com.cefrspeakingcoach.app

/**
 * Declares which voice pack belongs to which coach.
 *
 * The `type` here is only a FALLBACK used when voicepack.json omits or misspells
 * its own "type" field. The manifest always wins. Keeping the two in sync anyway
 * makes the registry readable as documentation of what actually ships.
 *
 * Current state:
 *  - james  -> HYBRID, has a real recorded opening.wav in assets
 *  - others -> NEURAL_LOCAL placeholders, no assets, will report NOT_INSTALLED
 *              until either a recorded pack or a sherpa-onnx model is added
 */
object CoachVoicePackRegistry {

    private val packs = listOf(
        CoachVoicePackDefinition(
            packId = "sophie_v1",
            coachId = "sophie",
            displayName = "Sophie Custom Voice Pack",
            type = CoachVoicePackType.NEURAL_LOCAL,
            assetDirectory = "coach_voice_packs/sophie"
        ),
        CoachVoicePackDefinition(
            packId = "emma_v1",
            coachId = "emma",
            displayName = "Emma Custom Voice Pack",
            type = CoachVoicePackType.NEURAL_LOCAL,
            assetDirectory = "coach_voice_packs/emma"
        ),
        CoachVoicePackDefinition(
            packId = "lily_v1",
            coachId = "lily",
            displayName = "Lily Custom Voice Pack",
            type = CoachVoicePackType.NEURAL_LOCAL,
            assetDirectory = "coach_voice_packs/lily"
        ),
        CoachVoicePackDefinition(
            packId = "james_v1",
            coachId = "james",
            displayName = "James Hybrid Voice Pack",
            type = CoachVoicePackType.HYBRID,
            assetDirectory = "coach_voice_packs/james",
            defaultSampleRate = 44100,
            defaultChannels = 2
        ),
        CoachVoicePackDefinition(
            packId = "ethan_v1",
            coachId = "ethan",
            displayName = "Ethan Custom Voice Pack",
            type = CoachVoicePackType.NEURAL_LOCAL,
            assetDirectory = "coach_voice_packs/ethan"
        ),
        CoachVoicePackDefinition(
            packId = "luca_v1",
            coachId = "luca",
            displayName = "Luca Custom Voice Pack",
            type = CoachVoicePackType.NEURAL_LOCAL,
            assetDirectory = "coach_voice_packs/luca"
        )
    )

    fun allPacks(): List<CoachVoicePackDefinition> {
        return packs
    }

    fun findByCoachId(coachId: String): CoachVoicePackDefinition? {
        return packs.firstOrNull {
            it.coachId.equals(coachId, ignoreCase = true)
        }
    }

    fun findByVoice(voice: ConversationVoiceModel): CoachVoicePackDefinition? {
        return findByCoachId(voice.id)
    }
}
