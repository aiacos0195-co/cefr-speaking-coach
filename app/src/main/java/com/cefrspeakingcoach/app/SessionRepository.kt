package com.cefrspeakingcoach.app

import androidx.compose.runtime.mutableStateListOf

object SessionRepository {

    private val sessions = mutableStateListOf<PracticeSession>()

    fun addSession(session: PracticeSession) {
        sessions.add(0, session)
    }

    fun getSessions(): List<PracticeSession> {
        return sessions
    }

}
