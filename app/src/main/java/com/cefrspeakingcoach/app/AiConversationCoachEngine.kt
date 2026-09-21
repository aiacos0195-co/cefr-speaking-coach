package com.cefrspeakingcoach.app

import android.util.Log

/**
 * Decides what the coach says next.
 *
 * This sits between the conversation screen and the two things that can produce
 * a reply:
 *
 *   1. [FirebaseAiGateway.generateConversationTurn] — Gemini. Reacts to what the
 *      learner actually said and can correct them.
 *   2. [ConversationCoachEngine] — the local rule engine. No network, no cost,
 *      no latency, but it follows a script and never corrects anything.
 *
 * The AI path is tried first and the rules are the fallback, not the reverse.
 * A learner on the school's wifi with a flaky connection still gets a reply;
 * it is just a scripted one.
 *
 * Set [aiEnabled] to false to go back to pure rule behaviour. That is the whole
 * switch: nothing else in the screen changes.
 */
object AiConversationCoachEngine {

    private const val TAG = "AiConversationCoach"

    /** How many previous turns to send as context. */
    private const val HISTORY_TURNS = 6

    /** Hard ceiling on a spoken reply, in characters. */
    private const val MAX_REPLY_CHARS = 600

    @Volatile
    var aiEnabled: Boolean = true

    /**
     * A short, distinct personality for each coach so the six do not all sound
     * like the same voice. Kept to two or three traits plus a speech habit; the
     * prompt turns these into how they react, not a costume they announce.
     */
    private fun personaFor(coachId: String): String {
        return when (coachId.lowercase()) {
            "sophie" -> "Warm, motherly, and patient. British. You notice how the " +
                "learner feels and gently encourage them. You like small everyday " +
                "topics: food, weekends, family. You never rush anyone."
            "emma" -> "Bright, upbeat American. You get visibly excited about what the " +
                "learner shares and cheer them on. You use light, natural exclamations " +
                "and keep energy high without being fake."
            "lily" -> "Cheerful, laid-back Australian. You are casual and funny, treat " +
                "the chat like talking to a friend, and are quick to find the fun angle " +
                "in whatever the learner brings up."
            "james" -> "Calm, thoughtful British gentleman. You speak precisely and ask " +
                "considered questions. You are the coach for someone who wants depth and " +
                "clear structure. Dry, subtle humour."
            "ethan" -> "Easygoing, curious American guy. You are genuinely interested in " +
                "people's stories and react like a real friend catching up. You keep it " +
                "casual and real, never lecture-y."
            "luca" -> "High-energy, fun Australian. You are enthusiastic and playful, love " +
                "hobbies, sports, travel, and food, and bring momentum so the learner " +
                "wants to keep talking."
            else -> "Warm, curious, and encouraging."
        }
    }

    fun openingMessage(voice: ConversationVoiceModel): String {
        return ConversationCoachEngine.openingMessage(voice)
    }

    fun detectLevel(messages: List<ConversationMessage>): String {
        return ConversationCoachEngine.detectLevel(messages)
    }

    /**
     * Produces the next coach turn. Never throws: any failure falls back to the
     * local rule engine, because a silent coach is worse than a scripted one.
     *
     * Call from a coroutine. On a slow connection this can take a couple of
     * seconds, which is why the screen shows a thinking state.
     */
    suspend fun nextTurn(
        voice: ConversationVoiceModel,
        userText: String,
        messages: List<ConversationMessage>
    ): CoachTurn {
        val localLevel = detectLevel(messages)

        if (!aiEnabled) {
            return localTurn(voice, userText, localLevel, messages)
        }

        return try {
            val turn = FirebaseAiGateway.generateConversationTurn(
                coachName = voice.name,
                coachStyle = voice.styleLabel,
                coachAccent = voice.accentLabel,
                coachPersona = personaFor(voice.id),
                targetLevel = localLevel,
                recentHistory = buildHistory(messages),
                userText = userText
            )

            val cleanReply = sanitizeForSpeech(turn.reply)

            if (cleanReply.isBlank()) {
                Log.w(TAG, "AI reply was empty after sanitising, using local rules")
                return localTurn(voice, userText, localLevel, messages)
            }

            turn.copy(
                reply = cleanReply,
                correction = turn.correction?.takeIf { it.isNotBlank() },
                levelEstimate = turn.levelEstimate ?: localLevel
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Falling back to local rules: ${t.message}")
            localTurn(voice, userText, localLevel, messages)
        }
    }

    private fun localTurn(
        voice: ConversationVoiceModel,
        userText: String,
        level: String,
        messages: List<ConversationMessage>
    ): CoachTurn {
        val reply = ConversationCoachEngine.buildReply(
            voice = voice,
            userText = userText,
            detectedLevel = level,
            messages = messages
        )

        return CoachTurn(
            reply = reply,
            correction = null,
            levelEstimate = level,
            source = CoachTurnSource.LOCAL_RULES
        )
    }

    /**
     * The last few turns as (speaker, line) pairs. The user's newest utterance
     * is deliberately excluded: it is passed separately so the prompt can point
     * at it directly.
     */
    private fun buildHistory(
        messages: List<ConversationMessage>
    ): List<Pair<String, String>> {
        return messages
            .dropLast(1)
            .takeLast(HISTORY_TURNS)
            .map { message ->
                val speaker = if (message.role == ConversationRole.COACH) "Coach" else "Learner"
                speaker to message.text.trim()
            }
            .filter { it.second.isNotBlank() }
    }

    /**
     * Strips anything a TTS engine would read out as noise.
     *
     * Even with an explicit instruction not to, models occasionally emit
     * markdown or a stage direction like "(smiling)". Both get spoken aloud,
     * which sounds broken, so they are removed here rather than hoped away.
     */
    private fun sanitizeForSpeech(text: String): String {
        var cleaned = text.trim()

        // Stage directions and parenthetical asides.
        cleaned = cleaned.replace(Regex("\\*[^*]{0,80}\\*"), " ")
        cleaned = cleaned.replace(Regex("\\([^)]{0,80}\\)"), " ")

        // Markdown emphasis, headings, list bullets, code ticks.
        cleaned = cleaned.replace(Regex("[*_`#>]"), " ")
        cleaned = cleaned.replace(Regex("(?m)^\\s*[-•]\\s+"), " ")

        // Emoji and other symbol blocks that TTS either skips or mispronounces.
        cleaned = cleaned.replace(Regex("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]"), " ")

        cleaned = cleaned
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.!?])"), "$1")
            .trim()

        if (cleaned.length > MAX_REPLY_CHARS) {
            val cut = cleaned.take(MAX_REPLY_CHARS)
            val lastStop = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
            cleaned = if (lastStop > MAX_REPLY_CHARS / 2) {
                cut.take(lastStop + 1)
            } else {
                cut.trimEnd() + "..."
            }
        }

        return cleaned
    }
}
