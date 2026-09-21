package com.cefrspeakingcoach.app

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.util.Locale

/**
 * Ajustes de la app.
 *
 * Bilingüe con el mismo mecanismo que "Coach Voice" y "About", pero el idioma
 * ya no vive aquí: llega desde MainActivity, que se lo pasa también a la barra
 * superior del drawer. Así el título de la barra y el de la pantalla dicen
 * siempre lo mismo. La elección se recuerda entre sesiones.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDailyReminderChange: (Boolean) -> Unit,
    onReminderTimeChange: (Int, Int) -> Unit,
    onNotificationsGranted: () -> Unit,
    language: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit
) {
    val context = LocalContext.current
    val strings = SettingsStrings.of(language)

    val reminderManager = remember { DailyReminderManager(context) }
    var notificationsAllowed by remember {
        mutableStateOf(reminderManager.hasNotificationPermission())
    }

    // rememberUpdatedState porque el efecto de abajo se crea una sola vez y se
    // quedaria con la primera version de este callback.
    val notifyGranted by rememberUpdatedState(onNotificationsGranted)

    // El alumno puede salir a los ajustes de Android, conceder el permiso y
    // volver. Dos cosas tienen que pasar al volver:
    //
    //  1. La advertencia se va. Sin esto seguiria en pantalla diciendo lo
    //     contrario de lo que pasa, que es justo el problema que resuelve.
    //  2. La alarma se programa. MainActivity solo la programa cuando cambia
    //     el interruptor o la hora, y al conceder el permiso no cambia
    //     ninguna de las dos. Sin este aviso la advertencia desapareceria y
    //     el recordatorio seguiria sin llegar: el mismo engano, mas callado.
    LifecycleResumeEffect(Unit) {
        val allowed = reminderManager.hasNotificationPermission()
        val estabaBloqueado = !notificationsAllowed
        notificationsAllowed = allowed

        if (allowed && estabaBloqueado) {
            notifyGranted()
        }

        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                strings.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )

            LanguageToggleButton(language = language, onToggle = onLanguageChange)
        }

        HorizontalDivider()

        Text(
            strings.appearanceTitle,
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = settings.themeMode == ThemeMode.SYSTEM,
                onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                label = { Text(strings.themeSystem) }
            )

            FilterChip(
                selected = settings.themeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                label = { Text(strings.themeLight) }
            )

            FilterChip(
                selected = settings.themeMode == ThemeMode.DARK,
                onClick = { onThemeModeChange(ThemeMode.DARK) },
                label = { Text(strings.themeDark) }
            )
        }

        HorizontalDivider()

        Text(
            strings.practiceTitle,
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.dailyReminderTitle)
                Text(
                    strings.dailyReminderSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = settings.dailyReminderEnabled,
                onCheckedChange = onDailyReminderChange
            )
        }

        if (settings.dailyReminderEnabled && !notificationsAllowed) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsOff,
                            contentDescription = null
                        )
                        Text(
                            strings.notificationsBlockedTitle,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    Text(
                        strings.notificationsBlockedBody,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Button(
                        onClick = { openAppNotificationSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.openNotificationSettings)
                    }
                }
            }
        }

        if (settings.dailyReminderEnabled) {
            Text(
                strings.reminderTimeTitle,
                style = MaterialTheme.typography.titleSmall
            )

            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            onReminderTimeChange(hourOfDay, minute)
                        },
                        settings.reminderHour,
                        settings.reminderMinute,
                        false
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.chooseTimeButton)
            }

            // Elegir el minuto en curso manda el recordatorio a manana, porque
            // los segundos cuentan como ya pasados. Decir "hoy" o "manana"
            // evita que alguien se quede esperando un aviso de hace un minuto.
            val hora = formatReminderTime(
                settings.reminderHour,
                settings.reminderMinute,
                strings.timeAm,
                strings.timePm
            )

            Text(
                if (reminderManager.isNextReminderToday(
                        settings.reminderHour,
                        settings.reminderMinute
                    )
                ) {
                    strings.nextReminderToday(hora)
                } else {
                    strings.nextReminderTomorrow(hora)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Solo en debug. Separa las dos cosas que desde fuera se ven igual:
        // "la app puede notificar" y "la alarma se programa bien". Si este
        // boton suena y el recordatorio no llega, el problema es la alarma.
        // No depende del interruptor a proposito: se prueba la notificacion,
        // no el recordatorio.
        if (BuildConfig.DEBUG) {
            OutlinedButton(
                onClick = { reminderManager.showReminderNotification() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.testReminderButton)
            }
        }
    }
}

/**
 * Abre los ajustes de notificaciones DE ESTA APP, no los del sistema entero.
 *
 * Algunos fabricantes no exponen esa pantalla; si la llamada falla, se cae a la
 * ficha de la aplicacion, que siempre existe y desde ahi tambien se llega.
 */
private fun openAppNotificationSettings(context: Context) {
    val appSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(appSettings) }.onFailure {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Hora en formato de 12 horas. El sufijo llega traducido desde los textos de la
 * pantalla: en español la forma correcta lleva puntos y espacio.
 */
private fun formatReminderTime(
    hour: Int,
    minute: Int,
    amLabel: String,
    pmLabel: String
): String {
    val suffix = if (hour >= 12) pmLabel else amLabel
    val normalizedHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }

    return String.format(
        Locale.getDefault(),
        "%d:%02d %s",
        normalizedHour,
        minute,
        suffix
    )
}
