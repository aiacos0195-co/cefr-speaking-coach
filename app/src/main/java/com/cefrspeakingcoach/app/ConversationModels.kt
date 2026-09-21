package com.cefrspeakingcoach.app

enum class ConversationRole {
    COACH,
    USER
}

data class ConversationVoiceModel(
    val id: String,
    val name: String,
    val genderLabel: String,
    val accentLabel: String,
    val styleLabel: String,
    val localeTag: String,
    val speechRate: Float = 0.98f,
    val pitch: Float = 1.0f
)

data class ConversationMessage(
    val id: String,
    val role: ConversationRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * Optional short correction shown under a coach bubble.
     *
     * It is deliberately NOT part of [text]: the coach speaks [text] only.
     * Reading a grammar note out loud in the middle of a conversation breaks
     * the flow, so corrections are seen, not heard.
     */
    val correction: String? = null
)

/** Where a coach turn came from. Useful for debugging and for the UI. */
enum class CoachTurnSource {
    /** Gemini, through FirebaseAiGateway. */
    AI,

    /** ConversationCoachEngine rules. Offline, or the AI call failed. */
    LOCAL_RULES
}

/**
 * One coach response.
 *
 * [reply] is what gets spoken. [correction] is a short written note, or null
 * when the learner's utterance did not need one.
 */
data class CoachTurn(
    val reply: String,
    val correction: String? = null,
    val levelEstimate: String? = null,
    val source: CoachTurnSource = CoachTurnSource.LOCAL_RULES
)

/**
 * Los seis coaches.
 *
 * Todos son americanos (en-US) a propósito. La app promete un acento y entrega
 * ese acento: prometer británico o australiano y sonar americano es peor que no
 * ofrecer variedad, sobre todo con alumnos que apenas están aprendiendo a
 * distinguirlos. Los coaches se diferencian por voz y personalidad, no por
 * bandera.
 *
 * [ConversationVoiceModel.localeTag] no es cosmético: AndroidCoachVoiceEngine lo
 * usa para elegir voz cuando la síntesis neuronal no está disponible. Por eso
 * también queda en en-US en los seis.
 */
val DefaultConversationVoices = listOf(
    ConversationVoiceModel(
        id = "sophie",
        name = "Sophie",
        genderLabel = "Female",
        accentLabel = "American",
        styleLabel = "Friendly & warm",
        localeTag = "en-US",
        speechRate = 0.96f,
        pitch = 1.10f
    ),
    ConversationVoiceModel(
        id = "emma",
        name = "Emma",
        genderLabel = "Female",
        accentLabel = "American",
        styleLabel = "Encouraging & clear",
        localeTag = "en-US",
        speechRate = 0.98f,
        pitch = 1.06f
    ),
    ConversationVoiceModel(
        id = "lily",
        name = "Lily",
        genderLabel = "Female",
        accentLabel = "American",
        styleLabel = "Cheerful & relaxed",
        localeTag = "en-US",
        speechRate = 1.0f,
        pitch = 1.08f
    ),
    ConversationVoiceModel(
        id = "james",
        name = "James",
        genderLabel = "Male",
        accentLabel = "American",
        styleLabel = "Professional & calm",
        localeTag = "en-US",
        speechRate = 0.92f,
        pitch = 0.78f
    ),
    ConversationVoiceModel(
        id = "ethan",
        name = "Ethan",
        genderLabel = "Male",
        accentLabel = "American",
        styleLabel = "Casual & engaging",
        localeTag = "en-US",
        speechRate = 0.95f,
        pitch = 0.80f
    ),
    ConversationVoiceModel(
        id = "luca",
        name = "Luca",
        genderLabel = "Male",
        accentLabel = "American",
        styleLabel = "Energetic & fun",
        localeTag = "en-US",
        speechRate = 0.96f,
        pitch = 0.82f
    )
)
