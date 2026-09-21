package com.cefrspeakingcoach.app

enum class PromptSource {
    LOCAL,
    AI
}

data class PromptItem(
    val id: String,
    val level: String,
    val category: String,
    val text: String,
    val source: PromptSource = PromptSource.LOCAL
)
