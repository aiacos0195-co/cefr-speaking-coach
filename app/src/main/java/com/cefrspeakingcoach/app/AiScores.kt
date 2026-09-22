package com.cefrspeakingcoach.app

/**
 * Notas del examinador. Sin pronunciacion a proposito: se evalua a partir de la
 * transcripcion, y la pronunciacion no se puede medir desde texto. Pedirle un
 * numero a Gemini le obligaba a inventarlo, y una cifra inventada dentro de una
 * rubrica es peor que un criterio de menos.
 *
 * Las sesiones guardadas antes de este cambio llevan el campo en su JSON; los
 * lectores lo ignoran y siguen cargando.
 */
data class AiScores(
    val fluency: Int,
    val grammar: Int,
    val vocabulary: Int,
    val coherence: Int
)
