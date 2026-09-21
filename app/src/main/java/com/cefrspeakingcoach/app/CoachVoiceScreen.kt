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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Pantalla de voz del coach, la versión para el alumno.
 *
 * La de audición (SpeakerAuditionScreen) sigue existiendo para afinar voces;
 * esta es la que ve alguien que solo quiere que su coach suene natural. Por eso
 * habla de "voz natural" y megas, no de speaker ids ni de modelos VITS.
 *
 * Está en inglés por defecto, como el resto de la app, con un botón de
 * traducción que la pasa a español y recuerda la elección. Ver UiLanguage.kt
 * para el porqué.
 *
 * Los mensajes de estado se guardan como lambdas sobre [CoachVoiceStrings] y no
 * como texto ya armado: si se guardara el texto, al tocar el botón de
 * traducción la pantalla quedaría medio en inglés y medio en español, con el
 * último mensaje congelado en el idioma anterior.
 */
@Composable
fun CoachVoiceScreen(
    language: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit
) {
    val context = LocalContext.current

    val installer = remember { SherpaModelInstaller(context) }
    val downloader = remember { SherpaModelDownloader(context) }
    val preferences = remember { VoicePreferences(context) }
    val uiScope = rememberCoroutineScope()

    val modelId = SherpaVoiceCatalog.fallbackModelId
    val model = SherpaVoiceCatalog.model(modelId)

    var wifiOnly by remember { mutableStateOf(preferences.wifiOnly) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var percent by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<((CoachVoiceStrings) -> String)?>(null) }
    var stateTick by remember { mutableStateOf(0) }
    var speakingCoach by remember { mutableStateOf<String?>(null) }

    val strings = CoachVoiceStrings.of(language)

    val installedMb = if (stateTick >= 0) {
        (installer.installedSizeBytes(modelId) / 1_000_000).toInt()
    } else {
        0
    }

    val engine = remember {
        SherpaCoachVoiceEngine(
            context = context,
            callbacks = CoachVoiceEngineCallbacks(
                onCompleted = { speakingCoach = null },
                onError = { _, error ->
                    speakingCoach = null
                    message = { text -> text.playbackFailed(error) }
                }
            )
        )
    }

    DisposableEffect(engine) {
        engine.initialize()
        onDispose { engine.release() }
    }

    fun startDownload() {
        if (!VoicePreferences.hasConnection(context)) {
            message = { text -> text.noConnection }
            return
        }

        if (wifiOnly && !VoicePreferences.isOnUnmeteredNetwork(context)) {
            message = { text -> text.onMobileData }
            return
        }

        percent = 0
        message = { text -> text.preparing }

        downloadJob = uiScope.launch {
            val result = downloader.downloadAndInstall(modelId) { progress, _ ->
                // El texto del descargador se ignora a propósito: viene en
                // español fijo. Aquí solo interesa el porcentaje, y la frase
                // sale del idioma elegido.
                percent = progress
                message = { text -> text.downloading }
            }

            downloadJob = null

            result
                .onSuccess {
                    message = { text -> text.activatingVoice }
                    engine.reloadModel(modelId) { _, error ->
                        stateTick++
                        message = if (error != null) {
                            { text -> text.activationFailed(error) }
                        } else {
                            { text -> text.voiceReady }
                        }
                    }
                }
                .onFailure { error ->
                    stateTick++
                    val reason = error.message ?: "unknown error"
                    message = { text -> text.downloadFailed(reason) }
                }
        }
    }

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

            LanguageToggleButton(language = language, onToggle = onLanguageChange)
        }

        Text(
            strings.intro,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (installedMb > 0) strings.statusOn else strings.statusOff,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    if (installedMb > 0) {
                        strings.installedSize(installedMb)
                    } else {
                        strings.downloadSize(model?.approxDownloadMb ?: 80)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                if (downloadJob != null && percent in 0..100) {
                    Text(
                        "$percent %",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                message?.let { render ->
                    Text(
                        render(strings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (downloadJob != null) {
                    OutlinedButton(
                        onClick = {
                            downloadJob?.cancel()
                            downloadJob = null
                            percent = 0
                            message = { text -> text.downloadCancelled }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.cancelButton) }
                } else if (installedMb > 0) {
                    OutlinedButton(
                        onClick = {
                            engine.deleteModel(modelId) { error ->
                                stateTick++
                                message = if (error != null) {
                                    { text -> text.deleteFailed(error) }
                                } else {
                                    { text -> text.deleted }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.deleteButton(installedMb)) }
                } else {
                    Button(
                        onClick = { startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = SherpaVoiceCatalog.isDownloadConfigured()
                    ) { Text(strings.downloadButton) }

                    if (!SherpaVoiceCatalog.isDownloadConfigured()) {
                        Text(
                            strings.notConfigured,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            strings.wifiOnlyTitle,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            strings.wifiOnlySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = { checked ->
                            wifiOnly = checked
                            preferences.wifiOnly = checked
                        }
                    )
                }
            }
        }

        Text(
            strings.listenTitle,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            strings.listenSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DefaultConversationVoices.forEach { voice ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            voice.name,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "${voice.genderLabel} · ${voice.styleLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(
                        onClick = {
                            speakingCoach = voice.id
                            message = null
                            val started = engine.speak(
                                text = SAMPLE_LINE,
                                voice = voice
                            )
                            if (!started) {
                                speakingCoach = null
                                message = { text -> text.downloadToListen }
                            }
                        }
                    ) {
                        Text(
                            if (speakingCoach == voice.id) {
                                strings.playingLabel
                            } else {
                                strings.listenButton
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            strings.credits,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * La muestra es la misma para los seis: lo que cambia entre coaches es la voz,
 * no el texto, y con frases distintas es más difícil compararlos de oído.
 */
private const val SAMPLE_LINE =
    "Hi! I'm ready when you are. Tell me about your day."
