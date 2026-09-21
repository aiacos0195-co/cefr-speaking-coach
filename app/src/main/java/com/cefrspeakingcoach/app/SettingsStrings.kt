package com.cefrspeakingcoach.app

/**
 * Textos de la pantalla de ajustes, en los dos idiomas.
 *
 * Mismo criterio que AboutStrings y CoachVoiceStrings: el idioma lo elige el
 * usuario con el botón de traducción, no la configuración del teléfono.
 *
 * La interfaz de práctica se queda en inglés a propósito, porque cada pantalla
 * es exposición al idioma. Los ajustes no: aquí el alumno viene a resolver algo
 * concreto — apagar un aviso, cambiar el tema, saber si se guarda — y el inglés
 * en ese momento estorba en vez de enseñar.
 *
 * La hora del recordatorio va como lambda y no concatenada, por lo mismo que en
 * CoachVoiceStrings: así el dato queda DENTRO de la frase y cada idioma lo pone
 * donde le corresponde. El sufijo de 12 horas también es texto traducible: en
 * español no se escribe "PM" sino "p. m.", con puntos y espacio.
 */
data class SettingsStrings(
    val title: String,
    val appearanceTitle: String,
    val themeSystem: String,
    val themeLight: String,
    val themeDark: String,
    val practiceTitle: String,
    val dailyReminderTitle: String,
    val dailyReminderSubtitle: String,
    val reminderTimeTitle: String,
    val chooseTimeButton: String,
    val currentReminder: (String) -> String,
    val timeAm: String,
    val timePm: String,
    val notificationsBlockedTitle: String,
    val notificationsBlockedBody: String,
    val openNotificationSettings: String
) {
    companion object {

        fun of(language: UiLanguage): SettingsStrings =
            if (language == UiLanguage.ES) spanish() else english()

        private fun english() = SettingsStrings(
            title = "Settings",
            appearanceTitle = "Appearance",
            themeSystem = "System",
            themeLight = "Light",
            themeDark = "Dark",
            practiceTitle = "Practice",
            dailyReminderTitle = "Daily practice reminder",
            dailyReminderSubtitle = "Receive a reminder to practice speaking " +
                "every day.",
            reminderTimeTitle = "Reminder time",
            chooseTimeButton = "Choose time",
            currentReminder = { time -> "Current reminder: $time" },
            timeAm = "AM",
            timePm = "PM",
            notificationsBlockedTitle = "The reminder will not arrive",
            notificationsBlockedBody = "Notifications for this app are turned " +
                "off in Android. The switch above is on, but nothing will " +
                "reach you until you allow them.",
            openNotificationSettings = "Allow notifications"
        )

        private fun spanish() = SettingsStrings(
            title = "Ajustes",
            appearanceTitle = "Apariencia",
            themeSystem = "Del sistema",
            themeLight = "Claro",
            themeDark = "Oscuro",
            practiceTitle = "Práctica",
            dailyReminderTitle = "Recordatorio diario de práctica",
            dailyReminderSubtitle = "Recibe todos los días un aviso para " +
                "practicar inglés hablado.",
            reminderTimeTitle = "Hora del recordatorio",
            chooseTimeButton = "Elegir hora",
            currentReminder = { time -> "Recordatorio actual: $time" },
            timeAm = "a. m.",
            timePm = "p. m.",
            notificationsBlockedTitle = "El recordatorio no te va a llegar",
            notificationsBlockedBody = "Las notificaciones de esta app están " +
                "desactivadas en Android. El interruptor de arriba quedó " +
                "encendido, pero no vas a recibir nada hasta que las permitas.",
            openNotificationSettings = "Permitir notificaciones"
        )
    }
}
