package com.cefrspeakingcoach.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Herramienta TEMPORAL de afinación de voces.
 *
 * Ahora audita VARIOS modelos: se elige el acento arriba (británico VCTK /
 * americano LibriTTS-R), se recorren sus hablantes, se escucha, y se le asigna
 * la voz a un coach. La asignación guarda modelo + speaker id, así que cambiar
 * a un coach de acento se hace desde aquí, sin recompilar.
 *
 * De VCTK se conoce el género de cada hablante (viene del corpus), así que los
 * botones "Sig. F" y "Sig. M" saltan directo a la siguiente voz de ese género:
 * son 109 voces y no tiene sentido recorrerlas de a una buscando una mujer.
 * De LibriTTS-R no hay ese dato, ahí toca de oído como antes.
 */
@Composable
fun SpeakerAuditionScreen() {
    val context = LocalContext.current

    val store = remember { SpeakerIdStore(context) }

    var status by remember { mutableStateOf("Loading model...") }
    var speakerCount by remember { mutableStateOf(0) }
    var currentId by remember { mutableStateOf(0) }
    var assignTick by remember { mutableStateOf(0) }
    var diagTick by remember { mutableStateOf(0) }
    var downloadPercent by remember { mutableStateOf(-1) }
    var downloadMessage by remember { mutableStateOf("") }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    var sizeTick by remember { mutableStateOf(0) }

    val uiScope = rememberCoroutineScope()
    val downloader = remember { SherpaModelDownloader(context) }
    val installer = remember { SherpaModelInstaller(context) }

    var selectedModelId by remember { mutableStateOf(SherpaVoiceCatalog.models.first().id) }
    var sampleText by remember {
        mutableStateOf("Hello, I'm your English conversation coach. How are you today?")
    }

    // Va DESPUÉS de selectedModelId a propósito: lo lee. Se recalcula en cada
    // recomposición y sizeTick fuerza una tras descargar o borrar. Son unas
    // pocas lecturas de File, barato para una pantalla de depuración.
    val installedMb = if (sizeTick >= 0) {
        (installer.installedSizeBytes(selectedModelId) / 1_000_000).toInt()
    } else {
        0
    }

    val engine = remember {
        SherpaCoachVoiceEngine(
            context = context,
            callbacks = CoachVoiceEngineCallbacks(
                onError = { _, message -> status = "Error: $message" }
            )
        )
    }

    DisposableEffect(engine) {
        engine.initialize()
        onDispose { engine.release() }
    }

    // Cargar el modelo elegido y averiguar cuántos hablantes tiene. Al cambiar
    // de acento se libera el anterior: nunca hay dos modelos en RAM.
    LaunchedEffect(selectedModelId) {
        status = "Loading ${SherpaVoiceCatalog.displayName(selectedModelId)}..."
        speakerCount = 0
        currentId = 0
        // Se limpia al cambiar de modelo: si no, queda pegado el "Listo: 904
        // voces" de la descarga anterior debajo del modelo equivocado.
        downloadMessage = ""
        downloadPercent = -1

        engine.prepareModel(selectedModelId) { count, error ->
            if (error != null) {
                status = "No se pudo cargar ${SherpaVoiceCatalog.displayName(selectedModelId)}: $error"
                speakerCount = 0
            } else {
                speakerCount = count
                status = "${SherpaVoiceCatalog.displayName(selectedModelId)}: $count voces"
            }
            diagTick++
        }
    }

    fun maxId(): Int = if (speakerCount > 0) speakerCount - 1 else 0

    fun play() {
        if (speakerCount <= 0) {
            status = "El modelo todavía no está listo"
            return
        }
        val ok = engine.auditionSpeak(
            text = sampleText,
            modelId = selectedModelId,
            speakerId = currentId
        )
        status = if (ok) "Sonando voz $currentId" else "No se pudo reproducir $currentId"
    }

    fun jumpToGender(gender: String, forward: Boolean) {
        val next = SherpaVoiceCatalog.nextSpeakerOfGender(
            modelId = selectedModelId,
            from = currentId,
            gender = gender,
            forward = forward
        )
        if (next == null) {
            status = "Este modelo no trae género por hablante"
        } else {
            currentId = next.coerceIn(0, maxId())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Speaker audition",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Elige el acento, recorre las voces, escucha, y asigna la que te " +
                "guste a un coach. Se guarda modelo + voz, y aplica de inmediato " +
                "en la conversación.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Selector de acento / modelo.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botón lleno = modelo activo. Se evita FilterChip a propósito:
            // en varias versiones de Material3 sigue siendo experimental y
            // pediría @OptIn, un motivo tonto para romper el build.
            SherpaVoiceCatalog.models.forEach { model ->
                if (selectedModelId == model.id) {
                    Button(
                        onClick = { },
                        modifier = Modifier.weight(1f)
                    ) { Text(model.accentLabel) }
                } else {
                    OutlinedButton(
                        onClick = { selectedModelId = model.id },
                        modifier = Modifier.weight(1f)
                    ) { Text(model.accentLabel) }
                }
            }
        }

        Text(status, style = MaterialTheme.typography.labelLarge)

        // Descarga del modelo (Fase 2). Los modelos ya no viajan en el APK: se
        // bajan una vez desde GitHub Releases y quedan en filesDir.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val model = SherpaVoiceCatalog.model(selectedModelId)

                Text(
                    "Modelo en el teléfono",
                    style = MaterialTheme.typography.labelLarge
                )

                Text(
                    if (installedMb > 0) {
                        "Instalado · $installedMb MB"
                    } else {
                        "No instalado · descarga de ~${model?.approxDownloadMb ?: 0} MB"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                // Progreso en texto y no con LinearProgressIndicator: la firma
                // del composable cambió entre versiones de Material3 (Float vs
                // lambda) y no vale la pena romper el build por una barrita.
                if (downloadJob != null && downloadPercent in 0..100) {
                    Text(
                        "$downloadPercent %",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (downloadMessage.isNotBlank()) {
                    Text(
                        downloadMessage,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (downloadJob != null) {
                        OutlinedButton(
                            onClick = {
                                downloadJob?.cancel()
                                downloadJob = null
                                downloadMessage = "Descarga cancelada"
                                downloadPercent = -1
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancelar") }
                    } else {
                        Button(
                            onClick = {
                                downloadPercent = 0
                                downloadMessage = "Preparando..."
                                downloadJob = uiScope.launch {
                                    val result = downloader.downloadAndInstall(
                                        modelId = selectedModelId
                                    ) { percent, message ->
                                        downloadPercent = percent
                                        downloadMessage = message
                                    }

                                    downloadJob = null
                                    downloadPercent = -1

                                    result
                                        .onSuccess {
                                            downloadMessage = "Descargado. Cargando modelo..."
                                            engine.reloadModel(selectedModelId) { count, error ->
                                                diagTick++
                                                sizeTick++
                                                if (error != null) {
                                                    downloadMessage = "Cargó mal: $error"
                                                    speakerCount = 0
                                                } else {
                                                    speakerCount = count
                                                    downloadMessage = "Listo: $count voces"
                                                    status = "$count voces disponibles"
                                                }
                                            }
                                        }
                                        .onFailure { error ->
                                            downloadMessage =
                                                "Falló: ${error.message ?: "error desconocido"}"
                                        }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = SherpaVoiceCatalog.isDownloadConfigured()
                        ) {
                            Text(if (installedMb > 0) "Volver a descargar" else "Descargar")
                        }

                        if (installedMb > 0) {
                            OutlinedButton(
                                onClick = {
                                    engine.deleteModel(selectedModelId) { error ->
                                        diagTick++
                                        sizeTick++
                                        speakerCount = 0
                                        downloadMessage = error?.let { "No se pudo borrar: $it" }
                                            ?: "Modelo borrado del teléfono"
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Borrar") }
                        }
                    }
                }

                if (!SherpaVoiceCatalog.isDownloadConfigured()) {
                    Text(
                        "Falta poner tu URL en MODELS_BASE_URL (SherpaVoiceCatalog.kt).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Diagnóstico SIEMPRE visible. La línea de status se pisa con cada
        // acción; esto no. Dice qué modelo está realmente cargado y qué archivo
        // se está usando, que es lo único que distingue "suena americano" de
        // "cargó el modelo equivocado".
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Diagnóstico del modelo",
                    style = MaterialTheme.typography.labelLarge
                )

                if (diagTick >= 0) {
                    engine.modelDiagnostics(selectedModelId).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { diagTick++ },
                        modifier = Modifier.weight(1f)
                    ) { Text("Refrescar") }

                    OutlinedButton(
                        onClick = {
                            status = "Reinstalando ${SherpaVoiceCatalog.displayName(selectedModelId)}..."
                            speakerCount = 0
                            engine.reinstallModel(selectedModelId) { count, error ->
                                diagTick++
                                if (error != null) {
                                    status = "Reinstalar falló: $error"
                                    speakerCount = 0
                                } else {
                                    speakerCount = count
                                    status = "Reinstalado: $count voces"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Reinstalar") }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Speaker id",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    currentId.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                )

                val label = SherpaVoiceCatalog.speakerLabel(selectedModelId, currentId)
                if (label != null) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentId = (currentId - 10).coerceAtLeast(0) },
                        modifier = Modifier.weight(1f)
                    ) { Text("-10") }
                    OutlinedButton(
                        onClick = { currentId = (currentId - 1).coerceAtLeast(0) },
                        modifier = Modifier.weight(1f)
                    ) { Text("-1") }
                    OutlinedButton(
                        onClick = { currentId = (currentId + 1).coerceAtMost(maxId()) },
                        modifier = Modifier.weight(1f)
                    ) { Text("+1") }
                    OutlinedButton(
                        onClick = { currentId = (currentId + 10).coerceAtMost(maxId()) },
                        modifier = Modifier.weight(1f)
                    ) { Text("+10") }
                }

                if (SherpaVoiceCatalog.hasGenderData(selectedModelId)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { jumpToGender("F", forward = true) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Sig. F") }
                        OutlinedButton(
                            onClick = { jumpToGender("M", forward = true) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Sig. M") }
                    }
                }

                Button(
                    onClick = { play() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = speakerCount > 0
                ) {
                    Text(if (speakerCount > 0) "Play speaker $currentId" else "Cargando...")
                }
            }
        }

        OutlinedTextField(
            value = currentId.toString(),
            onValueChange = { text ->
                val n = text.filter { it.isDigit() }.toIntOrNull()
                if (n != null) {
                    currentId = if (speakerCount > 0) n.coerceIn(0, maxId()) else n
                }
            },
            label = { Text("Jump to id") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = sampleText,
            onValueChange = { sampleText = it },
            label = { Text("Sample sentence") },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Asignar esta voz a un coach:",
            style = MaterialTheme.typography.titleSmall
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                listOf("sophie", "emma", "lily"),
                listOf("james", "ethan", "luca")
            ).forEach { rowCoaches ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowCoaches.forEach { coachId ->
                        Button(
                            onClick = {
                                store.assign(coachId, selectedModelId, currentId)
                                assignTick++
                                status = "$coachId = ${SherpaVoiceCatalog.displayName(selectedModelId)} " +
                                    "voz $currentId (guardado)"
                            },
                            modifier = Modifier.weight(1f),
                            enabled = speakerCount > 0
                        ) {
                            Text(coachId.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }
        }

        // Asignaciones actuales, leídas en vivo (assignTick fuerza el redibujo).
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Voces actuales",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(4.dp))
                if (assignTick >= 0) {
                    val assignments = store.resolvedAssignments()
                    SherpaVoiceCatalog.coachOrder.forEach { coachId ->
                        val assignment = assignments[coachId]
                        val model = assignment?.modelId.orEmpty()
                        val sid = assignment?.speakerId ?: 0
                        val speaker = SherpaVoiceCatalog.speakerLabel(model, sid)
                        val suffix = if (store.isCustom(coachId)) "tuyo" else "default"

                        Text(
                            "${coachId.replaceFirstChar { it.uppercase() }}: " +
                                "${SherpaVoiceCatalog.model(model)?.accentLabel ?: "?"} " +
                                "· $sid" + (speaker?.let { " ($it)" } ?: "") + "  [$suffix]",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                store.clearAll()
                assignTick++
                status = "Todas las voces vuelven a los valores por defecto"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset all to defaults")
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Nota: VCTK es británico pero el corpus mezcla acentos (sur de " +
                "Inglaterra, escocés, irlandés, norte). Los defaults apuntan a " +
                "voces del sur, confírmalo de oído antes de dejarlas fijas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
    }
}
