package com.cefrspeakingcoach.app

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Idioma de los textos de configuración.
 *
 * La app se usa para aprender inglés, así que la interfaz está en inglés a
 * propósito: cada pantalla es exposición al idioma. Pero las pantallas de
 * ajustes son distintas — ahí el alumno va a resolver un problema (¿por qué no
 * suena?, ¿cuánto pesa?, ¿me va a gastar los datos?), y obligarlo a descifrar
 * inglés justo ahí es un obstáculo, no una lección.
 *
 * De ahí el botón de traducción: inglés por defecto, español a un toque, y la
 * elección se recuerda. No cambia el idioma de la práctica, solo el de las
 * explicaciones.
 */
enum class UiLanguage {
    EN,
    ES;

    fun toggled(): UiLanguage = if (this == EN) ES else EN
}

class UiLanguageStore(context: Context) {

    // Respaldo: este archivo esta en la lista de INCLUSION de
    // backup_rules.xml y de las dos secciones de data_extraction_rules.xml.
    // Unas prefs NUEVAS hay que agregarlas ahi tambien, o no se respaldan
    // y nadie se entera hasta que alguien restaure.
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: UiLanguage
        get() = if (prefs.getString(KEY_LANGUAGE, EN_VALUE) == ES_VALUE) {
            UiLanguage.ES
        } else {
            UiLanguage.EN
        }
        set(value) {
            val stored = if (value == UiLanguage.ES) ES_VALUE else EN_VALUE
            prefs.edit().putString(KEY_LANGUAGE, stored).apply()
        }

    companion object {
        private const val PREFS_NAME = "ui_language_prefs"
        private const val KEY_LANGUAGE = "language"
        private const val EN_VALUE = "en"
        private const val ES_VALUE = "es"
    }
}

/**
 * Botón de traducción. El contentDescription va en el idioma AL QUE lleva, que
 * es lo que necesita oír alguien con lector de pantalla: la acción, no el
 * estado actual.
 */
@Composable
fun LanguageToggleButton(
    language: UiLanguage,
    modifier: Modifier = Modifier,
    onToggle: (UiLanguage) -> Unit
) {
    IconButton(
        onClick = { onToggle(language.toggled()) },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Outlined.Translate,
            contentDescription = if (language == UiLanguage.EN) {
                "Ver en español"
            } else {
                "View in English"
            },
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
