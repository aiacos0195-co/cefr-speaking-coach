package com.cefrspeakingcoach.app

data class PracticeSession(
    val id: String,
    val createdAt: Long,
    val level: String,
    val category: String,
    val prompt: String,
    val transcript: String,
    val spokenSeconds: Int,
    val wordCount: Int,
    val wpm: Int,
    val fillerCount: Int,
    val ai: AiFeedback?
)
