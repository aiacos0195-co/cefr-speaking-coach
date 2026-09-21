package com.cefrspeakingcoach.app

import kotlin.math.max

data class ConversationCorrection(
    val original: String,
    val corrected: String,
    val explanation: String
)

private data class UserInsight(
    val name: String? = null,
    val age: Int? = null,
    val origin: String? = null,
    val occupation: String? = null,
    val likes: String? = null
)

object ConversationCoachEngine {

    fun openingMessage(voice: ConversationVoiceModel): String {
        return when (voice.id) {
            "sophie" -> "Hello! I'm Sophie, your conversation coach. How are you today? Tell me a little about yourself."
            "emma" -> "Hi! I'm Emma. I'm here to help you practice English speaking. What would you like to talk about today?"
            "lily" -> "Hey! I'm Lily, your AI speaking partner. Let's have a relaxed conversation. What do you enjoy doing?"
            "james" -> "Hello, I'm James. It's a pleasure to meet you. Shall we have a little chat in English? Tell me a bit about yourself."
            "ethan" -> "Hi! I'm Ethan. Let's practice speaking together. What do you usually do in your free time?"
            else -> "Hello! I'm Luca. Let's chat in English. Tell me something interesting about your day."
        }
    }

    fun detectLevel(messages: List<ConversationMessage>): String {
        val userTexts = messages
            .filter { it.role == ConversationRole.USER }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }

        if (userTexts.isEmpty()) return "A1"

        val allText = userTexts.joinToString(" ").lowercase()
        val allWords = allText.split(Regex("\\s+")).filter { it.isNotBlank() }

        val avgWordsPerTurn = allWords.size.toFloat() / max(1, userTexts.size)
        val uniqueRatio = allWords.distinct().size.toFloat() / max(1, allWords.size)

        val connectors = listOf(
            "because", "but", "so", "although", "however", "for example",
            "usually", "recently", "sometimes", "if", "when", "while",
            "in my opinion", "on the other hand", "therefore", "also",
            "first", "then", "finally", "actually"
        )

        val advancedSignals = connectors.count { allText.contains(it) }
        val longWords = allWords.count { it.length >= 8 }

        return when {
            avgWordsPerTurn < 5f -> "A1"
            avgWordsPerTurn < 9f && advancedSignals <= 1 -> "A2"
            avgWordsPerTurn < 15f && advancedSignals <= 3 -> "B1"
            avgWordsPerTurn < 22f && (advancedSignals >= 2 || longWords >= 4 || uniqueRatio > 0.55f) -> "B2"
            avgWordsPerTurn < 30f -> "C1"
            else -> "C2"
        }
    }

    fun buildReply(
        voice: ConversationVoiceModel,
        userText: String,
        detectedLevel: String,
        messages: List<ConversationMessage>
    ): String {
        val clean = normalizeUserText(userText)
        if (clean.isBlank()) {
            return "I didn't catch that clearly. Please try again with a short answer."
        }

        val lowered = clean.lowercase()
        val insight = extractInsight(clean)
        val topic = detectTopic(lowered)
        val correction = detectSimpleCorrection(clean)
        val lastCoachReply = messages.lastOrNull { it.role == ConversationRole.COACH }?.text

        val repeatedWord = mostRepeatedMeaningfulWord(lowered)
        if (repeatedWord != null) {
            return when (detectedLevel) {
                "A1", "A2" -> "Nice try. I noticed you repeated the word \"$repeatedWord\" several times. Try saying it once, then add one or two simple ideas. Can you try again?"
                else -> "I noticed you repeated \"$repeatedWord\" many times. Try expanding your answer with more detail and clearer structure. Could you say it again in a fuller way?"
            }
        }

        if (isVeryShortAnswer(clean, detectedLevel)) {
            return buildShortAnswerReply(topic, insight, detectedLevel)
        }

        val acknowledgement = buildAcknowledgement(topic, insight, detectedLevel)
        val correctionLead = buildCorrectionLead(correction)
        val nextQuestion = pickNonRepeatedReply(
            options = buildFollowUps(topic, insight, detectedLevel),
            lastCoachReply = lastCoachReply
        )

        return listOf(acknowledgement, correctionLead, nextQuestion)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }

    private fun buildCorrectionLead(correction: ConversationCorrection?): String {
        if (correction == null) return ""
        return "A more natural way to say that is: \"${correction.corrected}.\" ${correction.explanation}"
    }

    private fun normalizeUserText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ""

        val normalized = trimmed
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\bi\\b"), "I")
            .trim()

        return normalized.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase() else ch.toString()
        }
    }

    private fun finalizeSentence(value: String): String {
        val normalized = value
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\bi\\b"), "I")
            .trim()
            .replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }

        return normalized.removeSuffix(".")
    }

    private fun detectSimpleCorrection(text: String): ConversationCorrection? {
        val raw = text.trim()

        fun build(corrected: String, explanation: String): ConversationCorrection {
            return ConversationCorrection(
                original = raw,
                corrected = finalizeSentence(corrected),
                explanation = explanation
            )
        }

        fun replaceWith(regex: Regex, replacement: String, explanation: String): ConversationCorrection? {
            if (!regex.containsMatchIn(raw)) return null
            return build(raw.replace(regex, replacement), explanation)
        }

        val twoClauseAgePattern = Regex(
            """^\s*my name is\s+([a-zA-ZÀ-ÿ' -]+?)\s+i have\s+(\d{1,2})\s*$""",
            RegexOption.IGNORE_CASE
        )
        twoClauseAgePattern.find(raw)?.let { match ->
            val name = match.groupValues[1].trim()
            val age = match.groupValues[2].trim()
            return build(
                corrected = "My name is $name. I am $age years old",
                explanation = "Use \"I am ... years old\" for age, and separate your ideas into two short sentences."
            )
        }

        val agePatterns = listOf(
            Regex("""\bi\s+have\s+(\d{1,2})\s+years\s+old\b""", RegexOption.IGNORE_CASE),
            Regex("""\bi\s+have\s+(\d{1,2})\s+years\b""", RegexOption.IGNORE_CASE),
            Regex("""\bi\s+have\s+(\d{1,2})\b""", RegexOption.IGNORE_CASE)
        )
        agePatterns.firstOrNull { it.containsMatchIn(raw) }?.let { pattern ->
            return build(
                corrected = pattern.replace(raw) { mr -> "I am ${mr.groupValues[1]} years old" },
                explanation = "For age, say \"I am ... years old.\""
            )
        }

        val professionPattern = Regex(
            """\b(i|he|she)\s+am\s+(teacher|student|doctor|engineer|nurse|chef|lawyer|soldier|mechanic|police officer|accountant|driver|designer)\b""",
            RegexOption.IGNORE_CASE
        )
        professionPattern.find(raw)?.let { match ->
            val subject = match.groupValues[1]
            val job = match.groupValues[2]
            val article = if (Regex("^[aeiou]", RegexOption.IGNORE_CASE).containsMatchIn(job)) "an" else "a"
            return build(
                corrected = raw.replace(professionPattern, "$subject am $article $job"),
                explanation = "With professions, use an article like \"a\" or \"an.\""
            )
        }

        replaceWith(
            Regex("""\bi\s+is\b""", RegexOption.IGNORE_CASE),
            "I am",
            "Use \"I am\", not \"I is\"."
        )?.let { return it }

        replaceWith(
            Regex("""\b(you|we|they)\s+is\b""", RegexOption.IGNORE_CASE),
            "$1 are",
            "Use \"are\" with you, we, and they."
        )?.let { return it }

        replaceWith(
            Regex("""\b(he|she|it)\s+are\b""", RegexOption.IGNORE_CASE),
            "$1 is",
            "Use \"is\" with he, she, and it."
        )?.let { return it }

        replaceWith(
            Regex("""\bi\s+am\s+agree\b""", RegexOption.IGNORE_CASE),
            "I agree",
            "Say \"I agree\", not \"I am agree.\""
        )?.let { return it }

        replaceWith(
            Regex("""\bi\s+have\s+hungry\b""", RegexOption.IGNORE_CASE),
            "I am hungry",
            "Say \"I am hungry\", not \"I have hungry.\""
        )?.let { return it }

        replaceWith(
            Regex("""\bi\s+have\s+thirsty\b""", RegexOption.IGNORE_CASE),
            "I am thirsty",
            "Say \"I am thirsty\", not \"I have thirsty.\""
        )?.let { return it }

        replaceWith(
            Regex("""\bpeople\s+is\b""", RegexOption.IGNORE_CASE),
            "people are",
            "Use \"are\" with \"people.\""
        )?.let { return it }

        val thirdPersonPatterns = listOf(
            Triple(Regex("""\b(he|she|it)\s+go\b""", RegexOption.IGNORE_CASE), "$1 goes", "With he, she, and it, the verb usually takes -s."),
            Triple(Regex("""\b(he|she|it)\s+do\b""", RegexOption.IGNORE_CASE), "$1 does", "With he, she, and it, use \"does.\""),
            Triple(Regex("""\b(he|she|it)\s+have\b""", RegexOption.IGNORE_CASE), "$1 has", "With he, she, and it, use \"has.\""),
            Triple(Regex("""\b(he|she|it)\s+like\b""", RegexOption.IGNORE_CASE), "$1 likes", "With he, she, and it, the verb usually takes -s."),
            Triple(Regex("""\b(he|she|it)\s+work\b""", RegexOption.IGNORE_CASE), "$1 works", "With he, she, and it, the verb usually takes -s."),
            Triple(Regex("""\b(he|she|it)\s+live\b""", RegexOption.IGNORE_CASE), "$1 lives", "With he, she, and it, the verb usually takes -s."),
            Triple(Regex("""\b(he|she|it)\s+play\b""", RegexOption.IGNORE_CASE), "$1 plays", "With he, she, and it, the verb usually takes -s."),
            Triple(Regex("""\b(he|she|it)\s+study\b""", RegexOption.IGNORE_CASE), "$1 studies", "With he, she, and it, the verb usually takes -s.")
        )

        for ((regex, replacement, explanation) in thirdPersonPatterns) {
            replaceWith(regex, replacement, explanation)?.let { return it }
        }

        if (!raw.contains(".") &&
            Regex("""\bmy name is\b.*\bi am\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw)
        ) {
            val corrected = raw.replace(
                Regex("""\b(i am)\b""", RegexOption.IGNORE_CASE),
                ". I am"
            )
            return build(
                corrected = corrected,
                explanation = "Try separating your ideas into short, clear sentences."
            )
        }

        return null
    }

    private fun extractInsight(text: String): UserInsight {
        val clean = text.trim()

        val name = Regex("""\bmy name is\s+([a-zA-ZÀ-ÿ' -]+)""", RegexOption.IGNORE_CASE)
            .find(clean)
            ?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.split(" ")
            ?.joinToString(" ") { word ->
                word.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                }
            }

        val age = Regex("""\bi am\s+(\d{1,2})\s+years\s+old\b""", RegexOption.IGNORE_CASE)
            .find(clean)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull()

        val origin = Regex("""\bi am from\s+([a-zA-ZÀ-ÿ' -]+)""", RegexOption.IGNORE_CASE)
            .find(clean)
            ?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val occupation = Regex(
            """\bi am\s+(?:a|an)?\s*(student|teacher|doctor|engineer|nurse|chef|lawyer|soldier|mechanic|police officer|accountant|driver|designer)\b""",
            RegexOption.IGNORE_CASE
        )
            .find(clean)
            ?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val likes = Regex("""\bi like\s+([^.!?]+)""", RegexOption.IGNORE_CASE)
            .find(clean)
            ?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return UserInsight(
            name = name,
            age = age,
            origin = origin,
            occupation = occupation,
            likes = likes
        )
    }

    private fun buildAcknowledgement(
        topic: String,
        insight: UserInsight,
        detectedLevel: String
    ): String {
        insight.name?.let { name ->
            if (insight.age != null) {
                return "Nice to meet you, $name."
            }
        }

        return when {
            insight.name != null -> "Nice to meet you, ${insight.name}."
            topic == "family" -> "Thanks for sharing that about your family."
            topic == "food" -> "That sounds tasty."
            topic == "hobby" -> "That sounds interesting."
            topic == "work_study" -> "That sounds important."
            topic == "daily_life" -> "Thanks. I can picture your routine."
            topic == "introduction" && detectedLevel in listOf("A1", "A2") -> "Good introduction."
            detectedLevel in listOf("A1", "A2") -> "Good."
            else -> "That makes sense."
        }
    }

    private fun buildShortAnswerReply(
        topic: String,
        insight: UserInsight,
        detectedLevel: String
    ): String {
        val followUp = buildFollowUps(topic, insight, detectedLevel).firstOrNull()
            ?: "Can you tell me a little more?"

        return when (detectedLevel) {
            "A1", "A2" -> "Good start. Please answer with one or two more simple sentences. $followUp"
            else -> "Good start. Try to expand your answer a little more. $followUp"
        }
    }

    private fun buildFollowUps(
        topic: String,
        insight: UserInsight,
        detectedLevel: String
    ): List<String> {
        if (topic == "introduction") {
            return introductionFollowUps(insight, detectedLevel)
        }

        return when (detectedLevel) {
            "A1" -> replyA1(topic, insight)
            "A2" -> replyA2(topic, insight)
            "B1" -> replyB1(topic, insight)
            "B2" -> replyB2(topic, insight)
            "C1" -> replyC1(topic, insight)
            else -> replyC2(topic, insight)
        }
    }

    private fun introductionFollowUps(
        insight: UserInsight,
        detectedLevel: String
    ): List<String> {
        return when {
            insight.name != null && insight.age == null -> listOf(
                "How old are you?",
                "Where are you from?",
                "What do you like to do in your free time?"
            )
            insight.name != null && insight.age != null && insight.origin == null -> listOf(
                "Where are you from?",
                "What do you like to do in your free time?",
                "Are you a student or do you work?"
            )
            insight.origin != null && insight.likes == null -> listOf(
                "What do you like most about ${"${insight.origin}"}?",
                "What do you like to do in your free time?",
                "Can you tell me a little more about yourself?"
            )
            insight.occupation != null -> listOf(
                "What do you like about being a ${insight.occupation}?",
                "What do you usually do every day?",
                if (detectedLevel in listOf("A1", "A2")) {
                    "Do you enjoy your work or studies?"
                } else {
                    "What is the most challenging part of your work or studies?"
                }
            )
            else -> listOf(
                "Where are you from?",
                "What do you like to do in your free time?",
                "Are you a student or do you work?"
            )
        }
    }

    private fun replyA1(topic: String, insight: UserInsight): List<String> {
        return when (topic) {
            "family" -> listOf(
                "Who do you live with?",
                "How many people are in your family?",
                "Can you describe one person in your family?"
            )
            "food" -> listOf(
                "What is your favorite food?",
                "When do you usually eat it?",
                "Do you like homemade food or restaurant food?"
            )
            "hobby" -> listOf(
                "When do you do that hobby?",
                "Why do you like it?",
                "Do you do it alone or with other people?"
            )
            "work_study" -> listOf(
                "What do you do every day?",
                "Do you like your work or your classes?",
                "Who do you work or study with?"
            )
            "daily_life" -> listOf(
                "What do you do in the morning?",
                "What do you do in the evening?",
                "What time do you usually start your day?"
            )
            "likes_dislikes" -> listOf(
                "Why do you like that?",
                "How often do you do that?",
                "What else do you like?"
            )
            else -> listOf(
                insight.likes?.let { "Why do you like $it?" } ?: "What do you like to do in your free time?",
                "Can you say a little more about yourself?",
                "What do you usually do every day?"
            )
        }
    }

    private fun replyA2(topic: String, insight: UserInsight): List<String> {
        return when (topic) {
            "family" -> listOf(
                "Can you describe one family member and say what that person is like?",
                "Who are you closest to in your family, and why?",
                "What do you usually do with your family?"
            )
            "food" -> listOf(
                "What is your favorite food, and why do you like it?",
                "Can you describe a meal you really enjoy?",
                "Do you like cooking, or do you prefer buying food?"
            )
            "hobby" -> listOf(
                "How often do you do that hobby, and who do you do it with?",
                "When did you start that hobby?",
                "How does that hobby make you feel?"
            )
            "work_study" -> listOf(
                "What do you like most about your work or studies?",
                "What is the hardest part of your work or studies?",
                "What would you like to improve there?"
            )
            "daily_life" -> listOf(
                "Can you describe a normal day in your life from morning to night?",
                "What part of your day do you enjoy the most?",
                "What do you usually do after work or class?"
            )
            "likes_dislikes" -> listOf(
                insight.likes?.let { "How often do you do $it?" } ?: "Can you explain that a bit more?",
                "Why is that important to you?",
                "Can you give one example?"
            )
            else -> listOf(
                "Can you give a little more detail and maybe one example?",
                "Can you explain that a bit more?",
                "Could you tell me more about that?"
            )
        }
    }

    private fun replyB1(topic: String, insight: UserInsight): List<String> {
        return when (topic) {
            "family" -> listOf(
                "How has your family influenced the way you are today?",
                "What values did you learn from your family?",
                "Do family relationships change as people grow older?"
            )
            "food" -> listOf(
                "Do you think food is an important part of culture? Why?",
                "How do your eating habits affect your daily routine?",
                "What kind of food would you recommend to a visitor in your country?"
            )
            "hobby" -> listOf(
                "How has that hobby changed your routine or your personality?",
                "Why do you think hobbies are important for mental health?",
                "Would you like to turn that hobby into something more serious?"
            )
            "work_study" -> listOf(
                "What challenges do you usually face in that area?",
                "What skills are most important in your work or studies?",
                "How do you stay motivated there?"
            )
            "daily_life" -> listOf(
                "Which part of your routine would you like to improve, and why?",
                "Do you prefer a fixed routine or a flexible one?",
                "How does your daily routine affect your mood?"
            )
            "likes_dislikes" -> listOf(
                insight.likes?.let { "Why does $it matter to you?" } ?: "Why do you feel that way?",
                "Can you support that idea with an example?",
                "Has your opinion changed over time?"
            )
            else -> listOf(
                "Can you explain your idea in a little more detail and give a reason?",
                "Could you support that idea with an example?",
                "What makes you think that?"
            )
        }
    }

    private fun replyB2(topic: String, insight: UserInsight): List<String> {
        return when (topic) {
            "family" -> listOf(
                "Do you think family relationships are changing in modern society? Why or why not?",
                "How does family background influence opportunity?",
                "Should family traditions be preserved even when society changes?"
            )
            "food" -> listOf(
                "How do eating habits reflect lifestyle and social change?",
                "Do you think convenience food is changing family life?",
                "How strongly is food connected to identity and culture?"
            )
            "hobby" -> listOf(
                "Do hobbies only help people relax, or can they also shape identity?",
                "Can a hobby become an important life skill?",
                "How do hobbies influence social relationships?"
            )
            "work_study" -> listOf(
                "What skills matter most today in work or education?",
                "Should schools and jobs value creativity more than routine performance?",
                "How is technology changing learning and work?"
            )
            "daily_life" -> listOf(
                "How do modern routines affect people's health and happiness?",
                "Do you think busy routines make people less reflective?",
                "Is balance more important than productivity?"
            )
            "likes_dislikes" -> listOf(
                insight.likes?.let { "What does your interest in $it say about your values?" }
                    ?: "What is the strongest argument for your point of view?",
                "Can you compare your idea with the opposite perspective?",
                "Has experience changed your opinion?"
            )
            else -> listOf(
                "Could you develop that idea with a clearer opinion and an example?",
                "What is the strongest argument in favor of your view?",
                "Can you compare that idea with the opposite perspective?"
            )
        }
    }

    private fun replyC1(topic: String, insight: UserInsight): List<String> {
        return when (topic) {
            "family" -> listOf(
                "To what extent do you think family background shapes opportunity and worldview?",
                "Can family support ever become a limitation rather than a strength?",
                "How do family expectations influence personal freedom?"
            )
            "food" -> listOf(
                "How would you analyze the relationship between food, identity, and globalization?",
                "Is food culture becoming more diverse or more homogeneous?",
                "Can food be used as a form of cultural resistance?"
            )
            "hobby" -> listOf(
                "Can leisure activities be considered essential to well-being rather than optional?",
                "How do hobbies reflect social class or personal values?",
                "Can a hobby become part of someone's public identity?"
            )
            "work_study" -> listOf(
                "How should institutions respond to the changing demands of professional life?",
                "Has education adapted quickly enough to modern realities?",
                "What tension exists between productivity and genuine learning?"
            )
            "daily_life" -> listOf(
                "Do routines liberate people through structure, or limit them through repetition?",
                "How much of everyday life is shaped by social pressure?",
                "Is efficiency overrated in modern life?"
            )
            else -> listOf(
                "Could you expand that idea with a more nuanced argument?",
                "Can you complicate that idea a little further?",
                "What counterargument would you take seriously?"
            )
        }
    }

    private fun replyC2(topic: String, insight: UserInsight): List<String> {
        return when (topic) {
            "family" -> listOf(
                "How might family structures evolve in response to economic and cultural pressures?",
                "Is the concept of family becoming more flexible or more fragile?",
                "To what extent does family remain the primary unit of socialization?"
            )
            "food" -> listOf(
                "Could food be read as a political and cultural text in itself? Why?",
                "How does food reveal power, class, and global influence?",
                "Can culinary practice be understood as a form of identity performance?"
            )
            "hobby" -> listOf(
                "In what way do personal interests intersect with identity, class, and social performance?",
                "Can leisure ever be fully separated from economic and cultural systems?",
                "Are hobbies expressions of freedom, or shaped by invisible structures?"
            )
            "work_study" -> listOf(
                "How do you evaluate the tension between expertise, productivity, and human fulfillment?",
                "Has modern education become too instrumental in its goals?",
                "Should work be understood primarily as necessity, identity, or social structure?"
            )
            "daily_life" -> listOf(
                "To what extent is everyday routine shaped by invisible social systems?",
                "Is personal choice in daily life less free than people assume?",
                "How do ordinary routines reproduce wider cultural values?"
            )
            else -> listOf(
                "Could you push the argument further and consider an alternative perspective?",
                "What deeper assumption is hiding behind that idea?",
                "Can you reframe your point from a broader social or philosophical angle?"
            )
        }
    }

    private fun detectTopic(text: String): String {
        return when {
            Regex("""\bmy name is\b|\bi am from\b|\bi am \d{1,2} years old\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "introduction"
            listOf("family", "mother", "father", "sister", "brother", "parents", "wife", "husband", "children").any { text.contains(it) } -> "family"
            listOf("pizza", "food", "eat", "breakfast", "lunch", "dinner", "restaurant", "cook").any { text.contains(it) } -> "food"
            listOf("hobby", "play", "games", "music", "read", "reading", "drawing", "sport", "soccer", "gym").any { text.contains(it) } -> "hobby"
            listOf("work", "job", "study", "school", "college", "class", "teacher", "university", "student").any { text.contains(it) } -> "work_study"
            listOf("morning", "evening", "routine", "day", "weekend", "usually", "sometimes").any { text.contains(it) } -> "daily_life"
            listOf("like", "love", "prefer", "enjoy", "favorite", "hate", "dislike").any { text.contains(it) } -> "likes_dislikes"
            else -> "general"
        }
    }

    private fun isVeryShortAnswer(text: String, detectedLevel: String): Boolean {
        val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
        return when (detectedLevel) {
            "A1", "A2" -> wordCount <= 3
            else -> wordCount <= 2
        }
    }

    private fun mostRepeatedMeaningfulWord(text: String): String? {
        val ignored = setOf(
            "the", "a", "an", "and", "or", "is", "are", "am", "i", "you", "he", "she",
            "it", "we", "they", "to", "of", "in", "on", "my", "your", "his", "her"
        )

        val tokens = text
            .replace(Regex("[^a-z\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in ignored }

        val grouped = tokens.groupingBy { it }.eachCount()
        val repeated = grouped.maxByOrNull { it.value }

        return if (repeated != null && repeated.value >= 5) repeated.key else null
    }

    private fun pickNonRepeatedReply(options: List<String>, lastCoachReply: String?): String {
        if (options.isEmpty()) return "Can you tell me a little more?"
        if (lastCoachReply.isNullOrBlank()) return options.first()
        return options.firstOrNull { it != lastCoachReply } ?: options.first()
    }
}
