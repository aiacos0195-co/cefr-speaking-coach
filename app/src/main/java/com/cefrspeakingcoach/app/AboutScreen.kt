package com.cefrspeakingcoach.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * CAMBIA ESTO por como quieras aparecer en la app publicada.
 *
 * Está arriba y suelto a propósito: es lo único de esta pantalla que hay que
 * tocar, y no debería obligarte a leer código para encontrarlo.
 */
private const val AUTHOR_NAME = "Andy Páez"

/** Déjalo vacío si prefieres no publicar un correo de contacto. */
private const val CONTACT_EMAIL = "aiacos0195@gmail.com"

/**
 * CAMBIA ESTO por la URL de tu repositorio cuando lo publiques.
 *
 * No es decorativo: la GPL v3 exige que quien recibe la app pueda conseguir el
 * código fuente, y este enlace es la forma de cumplirlo desde la app misma.
 */
private const val SOURCE_CODE_URL = "https://github.com/aiacos0195-co/cefr-speaking-coach"

/**
 * "Acerca de CEFR Speaking Coach".
 *
 * Además de los créditos, aquí va la explicación de privacidad. Es el lugar
 * donde alguien la busca, y en una app que manda texto a un servidor de Google
 * decirlo claro no es opcional.
 */
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val languageStore = remember { UiLanguageStore(context) }

    var language by remember { mutableStateOf(languageStore.language) }
    val strings = AboutStrings.of(language)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                strings.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )

            LanguageToggleButton(language = language) { next ->
                language = next
                languageStore.language = next
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "CEFR Speaking Coach",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    strings.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    strings.versionLabel(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            strings.whatItIsTitle,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            strings.whatItIs,
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider()

        Text(
            strings.authorTitle,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            AUTHOR_NAME,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            strings.authorRole,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (CONTACT_EMAIL.isNotBlank()) {
            Text(
                CONTACT_EMAIL,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider()

        Text(
            strings.privacyTitle,
            style = MaterialTheme.typography.titleMedium
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    strings.privacyOnDevice,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    strings.privacyCloud,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    strings.privacyNoAccount,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        HorizontalDivider()

        Text(
            strings.licenseTitle,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            strings.licenseBody,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            strings.sourceCodeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            SOURCE_CODE_URL,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )

        HorizontalDivider()

        Text(
            strings.creditsTitle,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            strings.creditsIntro,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AboutCredits.forEach { credit ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    credit.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "${credit.license} · ${credit.purpose}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            strings.licenseNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
    }
}
