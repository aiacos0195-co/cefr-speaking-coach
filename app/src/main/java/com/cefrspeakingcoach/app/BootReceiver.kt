package com.cefrspeakingcoach.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reprograma el recordatorio despues de reiniciar el telefono.
 *
 * Android borra todas las alarmas al apagarse. Sin esto el recordatorio queda
 * muerto hasta que el alumno vuelva a abrir la app — y el recordatorio existe
 * justamente para que la abra, asi que el circulo no se cierra solo.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val settings = AppSettingsStore(context).settings.value
        if (settings.dailyReminderEnabled) {
            DailyReminderManager(context).scheduleDailyReminder(
                settings.reminderHour,
                settings.reminderMinute
            )
        }
    }
}
