package com.cefrspeakingcoach.app

data class CoachVoiceAudio(
    val samples: FloatArray,
    val sampleRate: Int,
    val channels: Int = 1
)
