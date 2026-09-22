package com.cefrspeakingcoach.app

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreSessionRepository {

    private val db = FirebaseFirestore.getInstance()

    private companion object {
        const val TAG = "FirestoreSessions"
    }

    suspend fun saveUserProfile(user: SignedInUser) {
        val data = mapOf(
            "uid" to user.uid,
            "displayName" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
            "photoUrl" to (user.photoUrl ?: ""),
            "updatedAt" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(user.uid)
            .set(data, SetOptions.merge())
            .await()
    }

    /**
     * Sube la sesion a la nube SIN esperar la confirmacion del servidor.
     *
     * El Task de set() no se completa cuando el dato se guarda: se completa
     * cuando el servidor lo confirma. Con la persistencia offline de Firestore
     * la escritura local entra al instante y el Task queda pendiente — y si el
     * cliente no alcanza el backend, no se completa NUNCA. Un await() aqui
     * dejaba la evaluacion colgada dentro del try, sin llegar al finally, con
     * isEvaluating clavado en true y el boton en "Evaluating..." para siempre.
     *
     * Nada de withTimeout: no cancelaria la escritura, solo esconderia que no
     * se confirmo. La escritura queda encolada y Firestore la reintenta cuando
     * pueda; lo que se deja de hacer es ESPERARLA.
     *
     * Los dos listeners son el unico rastro de si llego o no. El de exito solo
     * dispara con la confirmacion del servidor, asi que verlo en Logcat es la
     * prueba de que la sesion esta de verdad en la nube.
     */
    fun saveSessionInBackground(
        uid: String,
        session: PracticeSession
    ) {
        db.collection("users")
            .document(uid)
            .collection("sessions")
            .document(session.id)
            .set(session.toMap())
            .addOnSuccessListener {
                Log.d(TAG, "sesion ${'$'}{session.id} confirmada por el servidor")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "sesion ${'$'}{session.id} NO llego a la nube", e)
            }
    }

    suspend fun loadSessions(uid: String): List<PracticeSession> {
        val snapshot = db.collection("users")
            .document(uid)
            .collection("sessions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            try {
                doc.toPracticeSession()
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun clearSessions(uid: String) {
        val snapshot = db.collection("users")
            .document(uid)
            .collection("sessions")
            .get()
            .await()

        snapshot.documents.forEach { it.reference.delete().await() }
    }
}

private fun PracticeSession.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "createdAt" to createdAt,
        "level" to level,
        "category" to category,
        "prompt" to prompt,
        "transcript" to transcript,
        "spokenSeconds" to spokenSeconds,
        "wordCount" to wordCount,
        "wpm" to wpm,
        "fillerCount" to fillerCount,
        "ai" to ai?.let {
            mapOf(
                "overall" to it.overall,
                "cefr_level_estimate" to it.cefr_level_estimate,
                "scores" to mapOf(
                    "fluency" to it.scores.fluency,
                    "grammar" to it.scores.grammar,
                    "vocabulary" to it.scores.vocabulary,
                    "coherence" to it.scores.coherence
                ),
                "strengths" to it.strengths,
                "improvements" to it.improvements,
                "corrected_version" to it.corrected_version
            )
        }
    )
}

@Suppress("UNCHECKED_CAST")
private fun com.google.firebase.firestore.DocumentSnapshot.toPracticeSession(): PracticeSession {
    val aiMap = get("ai") as? Map<String, Any?>
    val scoresMap = aiMap?.get("scores") as? Map<String, Any?>

    val aiFeedback = if (aiMap != null && scoresMap != null) {
        AiFeedback(
            overall = (aiMap["overall"] as? Number)?.toInt() ?: 0,
            cefr_level_estimate = aiMap["cefr_level_estimate"] as? String ?: "",
            scores = AiScores(
                fluency = (scoresMap["fluency"] as? Number)?.toInt() ?: 0,
                grammar = (scoresMap["grammar"] as? Number)?.toInt() ?: 0,
                vocabulary = (scoresMap["vocabulary"] as? Number)?.toInt() ?: 0,
                coherence = (scoresMap["coherence"] as? Number)?.toInt() ?: 0
            ),
            strengths = (aiMap["strengths"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            improvements = (aiMap["improvements"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            corrected_version = aiMap["corrected_version"] as? String ?: ""
        )
    } else {
        null
    }

    return PracticeSession(
        id = getString("id").orEmpty(),
        createdAt = getLong("createdAt") ?: 0L,
        level = getString("level").orEmpty(),
        category = getString("category").orEmpty(),
        prompt = getString("prompt").orEmpty(),
        transcript = getString("transcript").orEmpty(),
        spokenSeconds = getLong("spokenSeconds")?.toInt() ?: 0,
        wordCount = getLong("wordCount")?.toInt() ?: 0,
        wpm = getLong("wpm")?.toInt() ?: 0,
        fillerCount = getLong("fillerCount")?.toInt() ?: 0,
        ai = aiFeedback
    )
}
