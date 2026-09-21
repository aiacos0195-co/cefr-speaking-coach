package com.cefrspeakingcoach.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dailyReminderEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0
)

class AppSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("cefr_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        AppSettings(
            themeMode = ThemeMode.valueOf(
                prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
            ),
            dailyReminderEnabled = prefs.getBoolean(KEY_DAILY_REMINDER, false),
            reminderHour = prefs.getInt(KEY_REMINDER_HOUR, 19),
            reminderMinute = prefs.getInt(KEY_REMINDER_MINUTE, 0)
        )
    )

    val settings: StateFlow<AppSettings> = _settings

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setDailyReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DAILY_REMINDER, enabled).apply()
        _settings.value = _settings.value.copy(dailyReminderEnabled = enabled)
    }

    fun setReminderTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_REMINDER_HOUR, hour)
            .putInt(KEY_REMINDER_MINUTE, minute)
            .apply()

        _settings.value = _settings.value.copy(
            reminderHour = hour.coerceIn(0, 23),
            reminderMinute = minute.coerceIn(0, 59)
        )
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DAILY_REMINDER = "daily_reminder"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
    }
}
