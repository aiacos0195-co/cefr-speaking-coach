package com.cefrspeakingcoach.app

/**
 * Textos de la pantalla de voz del coach, en los dos idiomas.
 *
 * Van en una data class y no en strings.xml a propósito: el idioma aquí lo
 * elige el usuario con el botón de traducción, no la configuración del
 * teléfono. Con recursos de Android habría que pelear con locales por
 * configuración, que es mucho ruido para dos idiomas y una pantalla.
 *
 * Los textos con datos adentro son lambdas para que el número quede DENTRO de
 * la frase; concatenar "Ocupa " + mb + " MB" funciona en español y se rompe en
 * cuanto un idioma pone el número en otro lado.
 */
data class CoachVoiceStrings(
    val title: String,
    val intro: String,
    val statusOn: String,
    val statusOff: String,
    val installedSize: (Int) -> String,
    val downloadSize: (Int) -> String,
    val downloadButton: String,
    val cancelButton: String,
    val deleteButton: (Int) -> String,
    val wifiOnlyTitle: String,
    val wifiOnlySubtitle: String,
    val listenTitle: String,
    val listenSubtitle: String,
    val listenButton: String,
    val playingLabel: String,
    val credits: String,
    val notConfigured: String,
    val preparing: String,
    val downloading: String,
    val noConnection: String,
    val onMobileData: String,
    val downloadCancelled: String,
    val activatingVoice: String,
    val voiceReady: String,
    val activationFailed: (String) -> String,
    val downloadFailed: (String) -> String,
    val deleted: String,
    val deleteFailed: (String) -> String,
    val playbackFailed: (String) -> String,
    val downloadToListen: String
) {
    companion object {

        fun of(language: UiLanguage): CoachVoiceStrings =
            if (language == UiLanguage.ES) spanish() else english()

        private fun english() = CoachVoiceStrings(
            title = "Coach Voice",
            intro = "Your coach can speak with a natural voice that works " +
                "without internet. You download it once and it stays on your " +
                "phone. Without it, your coach uses the system voice.",
            statusOn = "Natural voice is on",
            statusOff = "Natural voice not downloaded",
            installedSize = { mb -> "It takes up $mb MB on your phone." },
            downloadSize = { mb -> "The download is about $mb MB. Wi-Fi is best." },
            downloadButton = "Download natural voice",
            cancelButton = "Cancel download",
            deleteButton = { mb -> "Delete and free $mb MB" },
            wifiOnlyTitle = "Download over Wi-Fi only",
            wifiOnlySubtitle = "Keeps it off your mobile data plan.",
            listenTitle = "Listen to the coaches",
            listenSubtitle = "Pick who you practice with from the conversation screen.",
            listenButton = "Listen",
            playingLabel = "Playing...",
            credits = "Voice generated with LibriTTS-R (CC BY 4.0) and Piper, " +
                "through sherpa-onnx. It runs offline: nothing you say leaves " +
                "your phone to produce the voice.",
            notConfigured = "The download address is not set up yet.",
            preparing = "Getting ready...",
            downloading = "Downloading...",
            noConnection = "No internet connection.",
            onMobileData = "You are on mobile data. Connect to Wi-Fi or turn off " +
                "\"Download over Wi-Fi only\".",
            downloadCancelled = "Download cancelled.",
            activatingVoice = "Setting up the voice...",
            voiceReady = "Natural voice ready.",
            activationFailed = { error -> "Could not turn on the voice: $error" },
            downloadFailed = { error -> "Download failed: $error" },
            deleted = "Voice deleted. Your coach will use the system voice.",
            deleteFailed = { error -> "Could not delete: $error" },
            playbackFailed = { error -> "Could not play: $error" },
            downloadToListen = "Download the natural voice to hear the sample."
        )

        private fun spanish() = CoachVoiceStrings(
            title = "Voz del coach",
            intro = "Tu coach puede hablar con una voz natural que funciona sin " +
                "internet. Se descarga una sola vez y queda guardada en el " +
                "teléfono. Sin ella, el coach usa la voz del sistema.",
            statusOn = "Voz natural activada",
            statusOff = "Voz natural no descargada",
            installedSize = { mb -> "Ocupa $mb MB en el teléfono." },
            downloadSize = { mb -> "La descarga pesa unos $mb MB. Mejor con WiFi." },
            downloadButton = "Descargar voz natural",
            cancelButton = "Cancelar descarga",
            deleteButton = { mb -> "Borrar y liberar $mb MB" },
            wifiOnlyTitle = "Descargar solo con WiFi",
            wifiOnlySubtitle = "Evita gastar tu plan de datos.",
            listenTitle = "Escucha a los coaches",
            listenSubtitle = "Elige con cuál practicar desde la pantalla de conversación.",
            listenButton = "Escuchar",
            playingLabel = "Sonando...",
            credits = "Voz generada con LibriTTS-R (CC BY 4.0) y Piper, mediante " +
                "sherpa-onnx. Funciona sin conexión: nada de lo que dices sale " +
                "del teléfono para producir la voz.",
            notConfigured = "Todavía no está configurada la dirección de descarga.",
            preparing = "Preparando...",
            downloading = "Descargando...",
            noConnection = "No hay conexión a internet.",
            onMobileData = "Estás con datos móviles. Conéctate a WiFi o desactiva " +
                "\"Descargar solo con WiFi\".",
            downloadCancelled = "Descarga cancelada.",
            activatingVoice = "Preparando la voz...",
            voiceReady = "Voz natural lista.",
            activationFailed = { error -> "No se pudo activar la voz: $error" },
            downloadFailed = { error -> "Falló la descarga: $error" },
            deleted = "Voz borrada. El coach usará la voz del sistema.",
            deleteFailed = { error -> "No se pudo borrar: $error" },
            playbackFailed = { error -> "No se pudo reproducir: $error" },
            downloadToListen = "Descarga la voz natural para escuchar la muestra."
        )
    }
}
