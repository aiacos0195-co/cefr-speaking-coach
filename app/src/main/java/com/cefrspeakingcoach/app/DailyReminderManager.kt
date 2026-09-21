package com.cefrspeakingcoach.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class DailyReminderManager(private val context: Context) {

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Practice Reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminder to practice speaking"
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Programa UN disparo: el proximo.
     *
     * Aqui no hay alarma repetitiva a proposito. setRepeating es inexacta desde
     * API 19 y en Doze se va a la siguiente ventana de mantenimiento, que tras
     * varias horas quieto puede tardar horas en llegar. setAndAllowWhileIdle
     * tampoco es exacta, pero Android garantiza que dispara con el telefono
     * dormido, con un tope de una vez cada ~9 minutos por app. Para "recuerdame
     * practicar a las 7" ese margen sobra, y evita pedir SCHEDULE_EXACT_ALARM,
     * que en Android 14+ el alumno tendria que conceder a mano y que Play
     * obliga a justificar.
     *
     * Al ser disparo unico hay que reprogramar cada vez que suena: de eso se
     * encarga ReminderReceiver. Y tras reiniciar el telefono, BootReceiver.
     */
    fun scheduleDailyReminder(hour: Int, minute: Int) {
        ensureNotificationChannel()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTriggerAtMillis(hour, minute),
            buildPendingIntent()
        )
    }

    /**
     * Cuando cae el proximo recordatorio: hoy si esa hora todavia no ha pasado,
     * manana si ya paso.
     *
     * Los segundos se ponen a cero, asi que elegir el minuto en curso cuenta
     * como pasado y se va a manana. No es un error — es lo unico sensato — pero
     * es invisible, y por eso la pantalla dice "hoy" o "manana" en vez de
     * dejar al alumno adivinando.
     */
    fun nextTriggerAtMillis(hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

    /** True si el proximo recordatorio cae hoy. */
    fun isNextReminderToday(hour: Int, minute: Int): Boolean {
        val ahora = Calendar.getInstance()
        val proximo = Calendar.getInstance().apply {
            timeInMillis = nextTriggerAtMillis(hour, minute)
        }

        return proximo.get(Calendar.YEAR) == ahora.get(Calendar.YEAR) &&
            proximo.get(Calendar.DAY_OF_YEAR) == ahora.get(Calendar.DAY_OF_YEAR)
    }

    fun cancelDailyReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent())
    }

    /**
     * Arma y lanza la notificacion del recordatorio, ya.
     *
     * La usan el receptor de la alarma y el boton de prueba de debug, a
     * proposito: si el boton suena y la alarma no llega, el problema esta en la
     * alarma y no en la notificacion. Desde fuera los dos fallos se ven igual y
     * hay que poder separarlos.
     *
     * ensureNotificationChannel() se llama aqui y no solo al programar: en
     * API 26+ una notificacion sin canal se descarta en silencio.
     */
    fun showReminderNotification() {
        ensureNotificationChannel()

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time to practice")
            .setContentText(
                "Open CEFR Speaking Coach and complete a speaking session today."
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "daily_practice_reminder_channel"
        private const val REQUEST_CODE = 2001
        private const val OPEN_APP_REQUEST_CODE = 3001
        private const val NOTIFICATION_ID = 3002
    }
}
