package com.cefrspeakingcoach.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recibe la alarma diaria, lanza la notificacion y programa la de manana.
 *
 * Lo segundo no es opcional: la alarma es un disparo unico
 * (setAndAllowWhileIdle), asi que sin esta reprogramacion el recordatorio
 * sonaria una vez y nunca mas.
 *
 * La notificacion se arma en DailyReminderManager para que el boton de prueba
 * de debug recorra exactamente este mismo camino.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val reminderManager = DailyReminderManager(context)
        reminderManager.showReminderNotification()

        // Lectura sincrona: AppSettingsStore vive sobre SharedPreferences y
        // carga los valores al construirse, asi que no hay que bloquear nada.
        val settings = AppSettingsStore(context).settings.value
        if (settings.dailyReminderEnabled) {
            reminderManager.scheduleDailyReminder(
                settings.reminderHour,
                settings.reminderMinute
            )
        }
    }
}
