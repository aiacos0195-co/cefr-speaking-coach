package com.cefrspeakingcoach.app

/**
 * Textos de "Acerca de", en los dos idiomas.
 *
 * Mismo criterio que CoachVoiceStrings: el idioma lo elige el usuario con el
 * botón de traducción, no la configuración del teléfono.
 *
 * La sección de privacidad merece una nota. Está escrita para ser exacta, no
 * tranquilizadora: la voz se genera en el teléfono, pero el texto de la
 * conversación SÍ viaja a Google para que Gemini responda, y el reconocimiento
 * de voz lo hace Android, que en muchos teléfonos también usa la nube. Decir
 * "todo es privado" sería más bonito y sería mentira, y con alumnos militares
 * hablando de su día, esa diferencia importa.
 */
data class AboutStrings(
    val title: String,
    val tagline: String,
    val versionLabel: (String, Int) -> String,
    val whatItIsTitle: String,
    val whatItIs: String,
    val authorTitle: String,
    val authorRole: String,
    val privacyTitle: String,
    val privacyOnDevice: String,
    val privacyCloud: String,
    val privacyNoAccount: String,
    val licenseTitle: String,
    val licenseBody: String,
    val sourceCodeLabel: String,
    val creditsTitle: String,
    val creditsIntro: String,
    val licenseNote: String
) {
    companion object {

        fun of(language: UiLanguage): AboutStrings =
            if (language == UiLanguage.ES) spanish() else english()

        private fun english() = AboutStrings(
            title = "About",
            tagline = "Speaking practice for the CEFR levels, with a coach that " +
                "listens and answers.",
            versionLabel = { name, code -> "Version $name (build $code)" },
            whatItIsTitle = "What this app is",
            whatItIs = "A place to practice speaking English out loud without " +
                "an audience. You choose a coach, you talk, and the coach " +
                "answers as a conversation partner would, adjusting to your " +
                "level. Corrections appear in writing under the reply so they " +
                "do not interrupt the conversation.",
            authorTitle = "Made by",
            authorRole = "English instructor, Escuela de Idiomas y Dialectos " +
                "del Ejército (ESIDE)",
            privacyTitle = "Your privacy",
            privacyOnDevice = "The coach's voice is generated on your phone. " +
                "Nothing is sent anywhere to produce it, and it works with no " +
                "internet at all.",
            privacyCloud = "What you say is turned into text by your phone's " +
                "speech recognition, and that text is sent to Google's Gemini " +
                "to write the coach's reply. So your conversations do leave the " +
                "device. Keep that in mind before discussing anything sensitive.",
            privacyNoAccount = "Your practice history is stored on your phone. " +
                "If Android backup is turned on, it is also included in your " +
                "Google account's backup. The developer has no access to " +
                "either.",
            licenseTitle = "License",
            licenseBody = "This app is free software, released under the GNU " +
                "General Public License v3.0. You may use it, study it, change " +
                "it and share it. Its source code is public.",
            sourceCodeLabel = "Source code:",
            creditsTitle = "Credits and licenses",
            creditsIntro = "This app stands on open source work:",
            licenseNote = "Full license texts are available from each project. " +
                "LibriTTS-R is used under CC BY 4.0, which requires this credit."
        )

        private fun spanish() = AboutStrings(
            title = "Acerca de",
            tagline = "Práctica de expresión oral para los niveles del MCER, con " +
                "un coach que escucha y responde.",
            versionLabel = { name, code -> "Versión $name (compilación $code)" },
            whatItIsTitle = "Qué es esta app",
            whatItIs = "Un lugar para practicar inglés hablado en voz alta sin " +
                "público. Eliges un coach, hablas, y el coach responde como lo " +
                "haría un interlocutor real, ajustándose a tu nivel. Las " +
                "correcciones aparecen escritas debajo de la respuesta para no " +
                "cortar la conversación.",
            authorTitle = "Hecha por",
            authorRole = "Instructor de inglés, Escuela de Idiomas y Dialectos " +
                "del Ejército (ESIDE)",
            privacyTitle = "Tu privacidad",
            privacyOnDevice = "La voz del coach se genera en tu teléfono. No se " +
                "envía nada a ningún lado para producirla, y funciona sin " +
                "internet.",
            privacyCloud = "Lo que dices lo convierte en texto el reconocimiento " +
                "de voz de tu teléfono, y ese texto se envía a Gemini, de " +
                "Google, para escribir la respuesta del coach. O sea que tus " +
                "conversaciones sí salen del dispositivo. Tenlo presente antes " +
                "de hablar de algo sensible.",
            privacyNoAccount = "Tu historial de práctica se guarda en tu " +
                "teléfono. Si tienes activada la copia de seguridad de Android, " +
                "también queda en la copia de seguridad de tu cuenta de Google. " +
                "El desarrollador no tiene acceso a ninguna de las dos.",
            licenseTitle = "Licencia",
            licenseBody = "Esta app es software libre, publicada bajo la " +
                "Licencia Pública General de GNU v3.0. Puedes usarla, " +
                "estudiarla, modificarla y compartirla. Su código fuente es " +
                "público.",
            sourceCodeLabel = "Código fuente:",
            creditsTitle = "Créditos y licencias",
            creditsIntro = "Esta app se apoya en trabajo de código abierto:",
            licenseNote = "Los textos completos de las licencias están " +
                "disponibles en cada proyecto. LibriTTS-R se usa bajo CC BY 4.0, " +
                "que exige este crédito."
        )
    }
}

/** Una dependencia con su licencia, para la lista de créditos. */
data class AboutCredit(
    val name: String,
    val license: String,
    val purpose: String
)

/**
 * Lo que va en la lista de créditos. No lleva traducción porque son nombres
 * propios y licencias; solo el "para qué" es prosa, y se deja corto en inglés
 * para que no dependa del idioma elegido.
 */
val AboutCredits: List<AboutCredit> = listOf(
    AboutCredit("sherpa-onnx", "Apache 2.0", "on-device speech synthesis"),
    AboutCredit("Piper", "MIT", "voice models"),
    AboutCredit("LibriTTS-R", "CC BY 4.0", "voice dataset"),
    AboutCredit("espeak-ng", "GPL 3.0", "text to phonemes"),
    AboutCredit("Firebase AI Logic · Gemini", "Google Terms", "conversation"),
    AboutCredit("Jetpack Compose", "Apache 2.0", "interface")
)
