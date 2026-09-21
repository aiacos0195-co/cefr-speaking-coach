package com.cefrspeakingcoach.app

data class CoachingPack(
    val focusSkill: String,
    val tips: List<String>,
    val exampleAnswer: String? = null
)
