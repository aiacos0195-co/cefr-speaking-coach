package com.cefrspeakingcoach.app

data class AiFeedback(
    val overall: Int,
    val cefr_level_estimate: String,
    val scores: AiScores,
    val strengths: List<String>,
    val improvements: List<String>,
    val corrected_version: String
)
