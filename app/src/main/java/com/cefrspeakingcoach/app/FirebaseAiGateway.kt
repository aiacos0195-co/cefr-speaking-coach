package com.cefrspeakingcoach.app

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object FirebaseAiGateway {

    private const val TAG = "FirebaseAiGateway"
    private const val MODEL_NAME = "gemini-2.5-flash"

    private val feedbackSchema: Schema = Schema.obj(
        mapOf(
            "overall" to Schema.integer(),
            "cefr_level_estimate" to Schema.enumeration(listOf("A1", "A2", "B1", "B2", "C1", "C2")),
            "scores" to Schema.obj(
                mapOf(
                    "fluency" to Schema.integer(),
                    "pronunciation" to Schema.integer(),
                    "grammar" to Schema.integer(),
                    "vocabulary" to Schema.integer(),
                    "coherence" to Schema.integer(),
                )
            ),
            "strengths" to Schema.array(Schema.string()),
            "improvements" to Schema.array(Schema.string()),
            "corrected_version" to Schema.string()
        )
    )

    private val promptBankSchema: Schema = Schema.obj(
        mapOf(
            "prompts" to Schema.array(
                Schema.obj(
                    mapOf(
                        "category" to Schema.string(),
                        "text" to Schema.string()
                    )
                )
            )
        )
    )

    private val coachingSchema: Schema = Schema.obj(
        mapOf(
            "focusSkill" to Schema.string(),
            "tips" to Schema.array(Schema.string()),
            "exampleAnswer" to Schema.string()
        )
    )

    private val conversationSchema: Schema = Schema.obj(
        mapOf(
            "reply" to Schema.string(),
            "correction" to Schema.string(),
            "cefr_level_estimate" to Schema.enumeration(listOf("A1", "A2", "B1", "B2", "C1", "C2"))
        )
    )

    private val feedbackModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = feedbackSchema
                temperature = 0.2f

                // Razonamiento apagado. En Gemini 2.5 viene ENCENDIDO por
                // defecto y sus tokens salen de maxOutputTokens, invisibles.
                // Con 1200 y una transcripcion de 75 palabras el razonamiento
                // se comia el presupuesto y el JSON se cortaba a medias:
                // "Content generation stopped. Reason: MAX_TOKENS".
                //
                // Para un examinador con esquema fijo y temperature 0.2 la
                // tarea es puntuar contra una rubrica, no resolver nada
                // abierto, asi que el razonamiento largo aporta poco y a cambio
                // hace que el costo dependa de cuanto hable el alumno.
                //
                // Si alguna transcripcion sale mal calificada, subir esto a 512
                // antes que tocar cualquier otra cosa.
                thinkingConfig = thinkingConfig { thinkingBudget = 0 }

                // Con el razonamiento en cero, esto es todo para el JSON. Da de
                // sobra para las 5 puntuaciones, 2 fortalezas, 3 mejoras y una
                // version corregida de 80 palabras.
                maxOutputTokens = 2048
            }
        )
    }

    private val promptBankModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = promptBankSchema
                temperature = 0.7f

                // Mismo motivo que el examinador: no habia fallado todavia,
                // pero corria con 900 compartidos con el razonamiento. Genera
                // una lista de prompts; no necesita deliberar.
                thinkingConfig = thinkingConfig { thinkingBudget = 0 }
                maxOutputTokens = 1536
            }
        )
    }

    private val coachingModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = coachingSchema
                temperature = 0.6f

                // Igual: 1000 compartidos con el razonamiento, y el esquema
                // pide una lista de consejos mas una respuesta de ejemplo.
                thinkingConfig = thinkingConfig { thinkingBudget = 0 }
                maxOutputTokens = 1536
            }
        )
    }

    private val conversationModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = conversationSchema
                // Higher than the examiner model on purpose: a conversation
                // partner that answers identically every time stops feeling
                // like one. Low enough that it stays on task.
                temperature = 0.8f
                // Sin thinkingConfig a proposito: esta pantalla funciona y no
                // se toca en el mismo cambio que las otras tres. Queda pendiente
                // probar aqui un presupuesto bajo (no cero), a ver si acorta la
                // demora de las respuestas del coach. Ver GUIA_PASOS.md.
                //
                // Was 500, which caused MAX_TOKENS: the budget must cover the
                // model's internal reasoning tokens (Gemini 2.5) PLUS the full
                // JSON (reply + correction + level). When it ran out mid-object
                // the JSON was truncated, failed to parse, and the turn fell
                // back to the robotic rule engine. The spoken reply is still
                // short; this is headroom, not longer answers.
                maxOutputTokens = 1500
            }
        )
    }

    private fun extractJsonObject(text: String): JSONObject {
        val t = text.trim()
        if (t.isBlank()) throw IllegalStateException("AI returned empty response.")
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        val json = if (start >= 0 && end > start) t.substring(start, end + 1) else t
        return JSONObject(json)
    }

    private fun jsonArrayToList(arr: JSONArray): List<String> =
        (0 until arr.length()).map { arr.getString(it) }

    suspend fun evaluateSpeaking(
        targetLevel: String,
        prompt: String,
        transcript: String,
        wpm: Int,
        fillerCount: Int,
        spokenSeconds: Int
    ): AiFeedback = withContext(Dispatchers.IO) {
        try {
            val examinerPrompt = """
You are a CEFR English speaking examiner.

Return JSON ONLY.
Be concise.
Use short output.
Do not add any explanation outside the JSON.

Target level: $targetLevel

Return:
- overall: integer 1 to 5
- cefr_level_estimate: one of A1, A2, B1, B2, C1, C2
- scores: fluency, pronunciation, grammar, vocabulary, coherence (each 1 to 5)
- strengths: exactly 2 short items
- improvements: exactly 3 short items
- corrected_version: short corrected version, max 80 words

Prompt:
$prompt

Student transcript:
$transcript

Stats:
- spokenSeconds: $spokenSeconds
- wpm: $wpm
- fillerCount: $fillerCount
""".trimIndent()

            val resp = feedbackModel.generateContent(examinerPrompt).text.orEmpty()
            val j = extractJsonObject(resp)

            val s = j.getJSONObject("scores")
            AiFeedback(
                overall = j.optInt("overall", 0),
                cefr_level_estimate = j.optString("cefr_level_estimate", ""),
                scores = AiScores(
                    fluency = s.optInt("fluency", 0),
                    pronunciation = s.optInt("pronunciation", 0),
                    grammar = s.optInt("grammar", 0),
                    vocabulary = s.optInt("vocabulary", 0),
                    coherence = s.optInt("coherence", 0)
                ),
                strengths = jsonArrayToList(j.optJSONArray("strengths") ?: JSONArray()),
                improvements = jsonArrayToList(j.optJSONArray("improvements") ?: JSONArray()),
                corrected_version = j.optString("corrected_version", "")
            )
        } catch (t: Throwable) {
            Log.e(TAG, "evaluateSpeaking failed", t)
            throw RuntimeException("AI evaluation failed: ${t.message ?: t.javaClass.simpleName}", t)
        }
    }

    suspend fun generatePromptBank(
        targetLevel: String,
        count: Int
    ): List<PromptItem> = withContext(Dispatchers.IO) {
        try {
            val p = """
You create CEFR speaking prompts for English practice.

Return JSON ONLY.

Target level: $targetLevel
Count: $count

Rules:
- prompts in English
- clear and natural
- short
- varied categories
- no duplicates
""".trimIndent()

            val resp = promptBankModel.generateContent(p).text.orEmpty()
            val obj = extractJsonObject(resp)
            val arr = obj.optJSONArray("prompts") ?: JSONArray()

            val out = ArrayList<PromptItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val category = o.optString("category", "General").trim().ifBlank { "General" }
                val text = o.optString("text", "").trim()
                if (text.isBlank()) continue

                out.add(
                    PromptItem(
                        id = "${targetLevel}_ai_${System.currentTimeMillis()}_$i",
                        level = targetLevel,
                        category = category,
                        text = text
                    )
                )
            }
            out
        } catch (t: Throwable) {
            Log.e(TAG, "generatePromptBank failed", t)
            throw RuntimeException("Prompt refresh failed: ${t.message ?: t.javaClass.simpleName}", t)
        }
    }

    suspend fun generateCoaching(
        targetLevel: String,
        focusSkill: String,
        prompt: String,
        transcript: String,
        lastOverall: Int?
    ): CoachingPack = withContext(Dispatchers.IO) {
        try {
            val coachPrompt = """
You are a CEFR English speaking coach.

Return JSON ONLY.
Be concise.

Target level: $targetLevel
Focus skill: $focusSkill

Return:
- focusSkill
- tips: exactly 5 short actionable tips
- exampleAnswer: 4 to 5 short sentences

Prompt:
$prompt

Transcript:
$transcript

Last overall score:
${lastOverall ?: "unknown"}
""".trimIndent()

            val resp = coachingModel.generateContent(coachPrompt).text.orEmpty()
            val j = extractJsonObject(resp)

            val tipsArr = j.optJSONArray("tips") ?: JSONArray()
            val tips = (0 until tipsArr.length()).map { tipsArr.getString(it) }

            CoachingPack(
                focusSkill = j.optString("focusSkill", focusSkill).ifBlank { focusSkill },
                tips = tips,
                exampleAnswer = j.optString("exampleAnswer", "").trim().ifBlank { null }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "generateCoaching failed", t)
            throw RuntimeException("Coaching failed: ${t.message ?: t.javaClass.simpleName}", t)
        }
    }
    /**
     * One turn of spoken conversation practice.
     *
     * Constraints that matter here and not in the examiner prompt:
     *  - the reply is going to be SPOKEN, so no markdown, no bullet lists and
     *    no emoji: a TTS engine reads those out as noise
     *  - length is capped by level, because a wall of B2 English aimed at an
     *    A1 learner ends the conversation
     *  - at most one correction, and only when it is worth interrupting for
     */
    suspend fun generateConversationTurn(
        coachName: String,
        coachStyle: String,
        coachAccent: String,
        coachPersona: String,
        targetLevel: String,
        recentHistory: List<Pair<String, String>>,
        userText: String
    ): CoachTurn = withContext(Dispatchers.IO) {
        try {
            val historyBlock = if (recentHistory.isEmpty()) {
                "(this is the first exchange)"
            } else {
                recentHistory.joinToString("\n") { (speaker, line) -> "$speaker: $line" }
            }

            val lengthRule = when (targetLevel.uppercase()) {
                "A1" -> "1 to 2 very short sentences. Present simple. High frequency words only."
                "A2" -> "2 short sentences. Simple past and future are fine."
                "B1" -> "2 to 3 sentences. Everyday vocabulary, some linking words."
                "B2" -> "3 sentences. Natural phrasing, some idiom is fine."
                else -> "3 to 4 sentences. Natural, fluent, nuanced."
            }

            // How hard to push the learner. Higher levels get richer vocabulary,
            // more probing questions, and finer corrections; lower levels get
            // simplicity and lots of encouragement.
            val demandRule = when (targetLevel.uppercase()) {
                "A1" -> "Keep it extremely simple and slow. Celebrate any attempt. " +
                    "Ask only concrete, yes/no or one-word-answer questions."
                "A2" -> "Stay simple but stretch them slightly. Ask short open " +
                    "questions about familiar topics."
                "B1" -> "Use everyday vocabulary and ask them to explain reasons " +
                    "and give simple opinions."
                "B2" -> "Use natural adult phrasing and idiom. Push for detail, " +
                    "nuance, and their real opinion. Correct errors that a fluent " +
                    "speaker would notice."
                else -> "Speak as you would to a near-native adult. Challenge their " +
                    "reasoning, introduce sophisticated vocabulary, and correct only " +
                    "subtle, high-level slips."
            }

            val conversationPrompt = """
You are $coachName, a real English conversation partner with a $coachAccent accent.

WHO YOU ARE:
$coachPersona
Overall manner: $coachStyle.
Let this personality shape HOW you react and what you ask about. Do not announce
your personality or describe yourself; just be this person.

You are having a real spoken conversation with an English learner. Think of a
friendly language exchange, not an exam.

Return JSON ONLY.

Learner level: $targetLevel
How to pitch it: $demandRule

reply:
- $lengthRule
- Plain spoken English. No markdown, no bullet points, no emoji, no stage directions.
- FIRST, react to the SPECIFIC thing the learner just said. Name a concrete
  detail from it. If they told a story about their father's military career,
  respond to THAT story; if they greeted you, greet back naturally. Show you
  actually listened, in your own personality's voice.
- THEN ask ONE follow-up question that flows from what they said and invites
  them to keep talking about THAT topic.
- Sound like a real, specific person who finds them interesting.
- Match a greeting with a greeting. Do not answer "how are you" by asking for
  detail or examples.

- BANNED. These empty coaching phrases make you sound like a form, not a person.
  Never use them or anything close:
  "Can you give (a little) more detail", "add an example", "give an example",
  "develop that idea", "with a clearer opinion", "Good start", "That makes
  sense. Could you develop...", "Can you support that idea". If you are about to
  write one, replace it with a real, specific reaction to their actual words.
- Never mention that you are an AI, a model, or that you are assessing anything.

correction:
- Empty string if the learner's English was clear and appropriate for $targetLevel.
- Otherwise ONE short note, under 20 words, in this shape:
  "You said X. Better: Y."
- Correct only errors that block meaning or are clearly below $targetLevel. At
  higher levels you may correct finer slips; at A1/A2 correct only what breaks
  meaning. Ignore accent, filler words, and transcription artifacts. When in
  doubt, leave it empty.

cefr_level_estimate:
- Your best estimate of the learner's level from what they just said.

Recent conversation:
$historyBlock

Learner just said:
$userText
""".trimIndent()

            val resp = generateWithOneRetry(conversationPrompt)
            val j = extractJsonObject(resp)

            val reply = j.optString("reply", "").trim()
            if (reply.isBlank()) {
                throw IllegalStateException("AI returned an empty reply")
            }

            val correction = j.optString("correction", "").trim()

            CoachTurn(
                reply = reply,
                correction = correction.ifBlank { null },
                levelEstimate = j.optString("cefr_level_estimate", "").trim().ifBlank { null },
                source = CoachTurnSource.AI
            )
        } catch (t: Throwable) {
            Log.w(TAG, "generateConversationTurn failed: ${t.message}")
            throw RuntimeException("Conversation turn failed: ${t.message ?: t.javaClass.simpleName}", t)
        }
    }

    /**
     * One AI call, retried once on failure. Transient failures (a dropped
     * network packet, an App Check token still refreshing, a brief rate-limit)
     * were sending whole turns to the local rule fallback, which is what
     * produced the robotic "Could you develop that idea" replies. A single
     * retry after a short backoff recovers most of those without a visible
     * stall. If BOTH attempts fail, the caller falls back as before.
     */
    private suspend fun generateWithOneRetry(prompt: String): String {
        return try {
            conversationModel.generateContent(prompt).text.orEmpty()
        } catch (first: Throwable) {
            Log.w(TAG, "AI attempt 1 failed (${first.message}); retrying once")
            delay(600)
            conversationModel.generateContent(prompt).text.orEmpty()
        }
    }
}
