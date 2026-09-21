package com.cefrspeakingcoach.app

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDailyReminderChange: (Boolean) -> Unit,
    onReminderTimeChange: (Int, Int) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        HorizontalDivider()

        Text(
            "Appearance",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = settings.themeMode == ThemeMode.SYSTEM,
                onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                label = { Text("System") }
            )

            FilterChip(
                selected = settings.themeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                label = { Text("Light") }
            )

            FilterChip(
                selected = settings.themeMode == ThemeMode.DARK,
                onClick = { onThemeModeChange(ThemeMode.DARK) },
                label = { Text("Dark") }
            )
        }

        HorizontalDivider()

        Text(
            "Practice",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text("Daily practice reminder")
                Text(
                    "Receive a reminder to practice speaking every day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = settings.dailyReminderEnabled,
                onCheckedChange = onDailyReminderChange
            )
        }

        if (settings.dailyReminderEnabled) {
            Text(
                "Reminder time",
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
                Text("Choose time")
            }

            Text(
                "Current reminder: ${formatReminderTime(settings.reminderHour, settings.reminderMinute)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        Text(
            "Your theme and reminder settings are now saved permanently.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatReminderTime(hour: Int, minute: Int): String {
    val suffix = if (hour >= 12) "PM" else "AM"
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
