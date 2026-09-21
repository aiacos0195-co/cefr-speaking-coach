package com.cefrspeakingcoach.app

object PromptRepository {

    private val bank = listOf(
        // A1
        PromptItem("a1_1", "A1", "Family", "Describe your family members. Who do you live with?"),
        PromptItem("a1_2", "A1", "Daily Life", "What do you do in the morning?"),
        PromptItem("a1_3", "A1", "Food", "What food do you like? Why do you like it?"),
        PromptItem("a1_4", "A1", "Home", "Describe your house or apartment."),
        PromptItem("a1_5", "A1", "Friends", "Talk about your best friend."),
        PromptItem("a1_6", "A1", "Routine", "What do you usually do after class or work?"),
        PromptItem("a1_7", "A1", "Weekend", "What do you do on weekends?"),
        PromptItem("a1_8", "A1", "Hobbies", "What is your favorite hobby?"),

        // A2
        PromptItem("a2_1", "A2", "Travel", "Talk about a place you visited recently. What did you do there?"),
        PromptItem("a2_2", "A2", "Food", "What is your favorite dish? How often do you eat it?"),
        PromptItem("a2_3", "A2", "Daily Life", "Describe a normal day in your life."),
        PromptItem("a2_4", "A2", "Shopping", "Talk about the last thing you bought."),
        PromptItem("a2_5", "A2", "Work/Study", "Describe your job or your studies."),
        PromptItem("a2_6", "A2", "Free Time", "How do you spend your free time?"),
        PromptItem("a2_7", "A2", "Health", "What do you do to stay healthy?"),
        PromptItem("a2_8", "A2", "Technology", "What device do you use the most every day?"),

        // B1
        PromptItem("b1_1", "B1", "Work/Study", "Describe your work or studies. What are your main responsibilities?"),
        PromptItem("b1_2", "B1", "Future", "What are your plans for the next year? Why?"),
        PromptItem("b1_3", "B1", "Travel", "Describe a memorable trip and explain why it was special."),
        PromptItem("b1_4", "B1", "Health", "Do you think people today are healthier than before? Why or why not?"),
        PromptItem("b1_5", "B1", "Technology", "How has technology changed your daily life?"),
        PromptItem("b1_6", "B1", "Learning", "What is the best way to learn a language?"),
        PromptItem("b1_7", "B1", "Friends", "What qualities do you value in a good friend?"),
        PromptItem("b1_8", "B1", "City Life", "What do you like and dislike about living in your city?"),

        // B2
        PromptItem("b2_1", "B2", "Environment", "Discuss the importance of recycling. How can people help the environment?"),
        PromptItem("b2_2", "B2", "Technology", "How has technology changed the way we communicate?"),
        PromptItem("b2_3", "B2", "Education", "Should schools focus more on practical skills or academic knowledge?"),
        PromptItem("b2_4", "B2", "Media", "How does social media influence public opinion?"),
        PromptItem("b2_5", "B2", "Work", "What makes a workplace motivating and productive?"),
        PromptItem("b2_6", "B2", "Culture", "Why is it important to learn about other cultures?"),
        PromptItem("b2_7", "B2", "Society", "What are some challenges young people face today?"),
        PromptItem("b2_8", "B2", "Travel", "Do you think travel is more educational than classroom learning?"),

        // C1
        PromptItem("c1_1", "C1", "Society", "Analyze the impact of social media on modern political discourse."),
        PromptItem("c1_2", "C1", "Education", "To what extent should universities prioritize practical skills over theory?"),
        PromptItem("c1_3", "C1", "Work", "How is remote work changing professional culture?"),
        PromptItem("c1_4", "C1", "Ethics", "Should companies be held morally responsible for environmental damage?"),
        PromptItem("c1_5", "C1", "Media", "How do entertainment media shape social values?"),
        PromptItem("c1_6", "C1", "Culture", "Does globalization enrich cultures or weaken local identity?"),
        PromptItem("c1_7", "C1", "Technology", "Is convenience making people too dependent on technology?"),
        PromptItem("c1_8", "C1", "Leadership", "What qualities define an effective leader in times of crisis?"),

        // C2
        PromptItem("c2_1", "C2", "Philosophy", "Evaluate the ethical implications of artificial intelligence in decision-making roles."),
        PromptItem("c2_2", "C2", "Global Issues", "Critically assess the effectiveness of international cooperation on climate change."),
        PromptItem("c2_3", "C2", "Policy", "Should freedom of expression ever be limited in democratic societies?"),
        PromptItem("c2_4", "C2", "Innovation", "Does rapid innovation benefit society more than it disrupts it?"),
        PromptItem("c2_5", "C2", "Education", "Is expertise undervalued in the age of instant information?"),
        PromptItem("c2_6", "C2", "Media", "To what extent can journalism remain objective in polarized societies?"),
        PromptItem("c2_7", "C2", "Economy", "Should governments regulate wealth inequality more aggressively?"),
        PromptItem("c2_8", "C2", "Culture", "Can cultural heritage be preserved without resisting change?")
    )

    fun getCategories(level: String): List<String> {
        return listOf("All") + bank
            .filter { it.level == level }
            .map { it.category }
            .distinct()
            .sorted()
    }

    fun getPrompts(level: String, category: String? = null): List<PromptItem> {
        return bank.filter {
            it.level == level && (category == null || category == "All" || it.category == category)
        }
    }

    fun getRandomPrompt(
        level: String,
        category: String? = null,
        excludedIds: Set<String> = emptySet()
    ): PromptItem {
        val candidates = getPrompts(level, category)
        val fresh = candidates.filterNot { it.id in excludedIds }

        return when {
            fresh.isNotEmpty() -> fresh.random()
            candidates.isNotEmpty() -> candidates.random()
            else -> PromptItem(
                id = "fallback_${level.lowercase()}",
                level = level,
                category = category ?: "General",
                text = "Talk about a topic related to your daily life."
            )
        }
    }

    fun findById(id: String): PromptItem? {
        return bank.firstOrNull { it.id == id }
    }
}
