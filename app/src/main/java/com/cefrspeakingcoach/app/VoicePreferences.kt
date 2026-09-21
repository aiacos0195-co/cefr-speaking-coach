package com.cefrspeakingcoach.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Ajustes de la descarga de voz y estado de la conexión.
 *
 * El modelo pesa ~80 MB. Para un alumno con plan de datos prepago eso es
 * dinero, así que la app pregunta antes y por defecto exige WiFi. Es una
 * decisión de respeto al bolsillo del usuario, no una limitación técnica: el
 * que quiera bajarlo con datos puede apagar el interruptor.
 */
class VoicePreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) {
            prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "voice_download_prefs"
        private const val KEY_WIFI_ONLY = "wifi_only"

        /**
         * True si hay WiFi (o Ethernet, en emulador y tablets con dock).
         *
         * Si el sistema no sabe decirnos el tipo de red, devuelve true: es
         * preferible dejar descargar que bloquear a alguien que sí está en
         * WiFi por una lectura que falló.
         */
        fun isOnUnmeteredNetwork(context: Context): Boolean {
            val manager = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true

            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return true

            val wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val ethernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

            return wifi || ethernet
        }

        fun hasConnection(context: Context): Boolean {
            val manager = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true

            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return true

            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
