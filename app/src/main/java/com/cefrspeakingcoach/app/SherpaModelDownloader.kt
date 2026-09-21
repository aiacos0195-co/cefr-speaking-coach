package com.cefrspeakingcoach.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Descarga un modelo de voz desde GitHub Releases y lo instala.
 *
 * Fase 2: los modelos salen del APK. Un .onnx de 75 MB dentro del APK es
 * absurdo cuando la mayoría de los alumnos ni va a cambiar de voz, y Play
 * Store cobra ese peso a todo el mundo. Se descarga una vez, queda en filesDir
 * y sobrevive a las actualizaciones de la app.
 *
 * Va en dos pasos a propósito:
 *
 *  1. Descargar el .zip completo a cacheDir, con progreso real.
 *  2. Descomprimirlo con [SherpaModelInstaller.installFromZip].
 *
 * Descomprimir directo del stream HTTP sería más elegante y bastante peor: si
 * se corta el internet a mitad (que es lo normal en un celular), quedaría una
 * carpeta de modelo a medias que el motor podría creer válida. Con archivo
 * temporal, una descarga incompleta simplemente no se instala.
 *
 * No usa OkHttp ni WorkManager: HttpURLConnection alcanza y no suma
 * dependencias por una pantalla que se usa una vez.
 */
class SherpaModelDownloader(
    context: Context
) {
    private val appContext = context.applicationContext
    private val installer = SherpaModelInstaller(appContext)

    /**
     * Descarga e instala [modelId].
     *
     * [onProgress] recibe el porcentaje (0..100, o -1 si el servidor no manda
     * Content-Length) y un texto de estado listo para mostrar. Se llama desde
     * el hilo de IO: la pantalla debe saltar al principal si va a tocar estado
     * de Compose (los `mutableStateOf` de Compose se pueden escribir desde
     * cualquier hilo, pero mejor no acostumbrarse).
     *
     * Cancelable: si se cancela la corrutina, se borra el temporal y no se
     * toca nada de lo ya instalado.
     */
    suspend fun downloadAndInstall(
        modelId: String,
        onProgress: (percent: Int, message: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val url = SherpaVoiceCatalog.downloadUrl(modelId)
                ?: throw IllegalArgumentException("Sin URL de descarga para $modelId")

            if (!SherpaVoiceCatalog.isDownloadConfigured()) {
                throw IllegalStateException(
                    "Falta configurar MODELS_BASE_URL en SherpaVoiceCatalog.kt"
                )
            }

            val temp = File(appContext.cacheDir, "$modelId-download.zip")
            temp.delete()

            try {
                onProgress(0, "Conectando...")
                downloadTo(url, temp, onProgress)

                currentCoroutineContext().ensureActive()

                onProgress(100, "Instalando...")
                val installed = temp.inputStream().use { input ->
                    installer.installFromZip(modelId, input).getOrThrow()
                }

                val mb = installer.installedSizeBytes(modelId) / 1_000_000
                onProgress(100, "Listo: $mb MB instalados")

                Log.i(TAG, "Installed $modelId from $url ($mb MB)")
                installed
            } finally {
                temp.delete()
            }
        }
    }

    private suspend fun downloadTo(
        url: String,
        target: File,
        onProgress: (Int, String) -> Unit
    ) {
        var connection: HttpURLConnection? = null

        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                // GitHub redirige la descarga a objects.githubusercontent.com.
                instanceFollowRedirects = true
            }

            connection.connect()

            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code al descargar el modelo")
            }

            val total = connection.contentLength.toLong()
            var downloaded = 0L
            var lastReported = -1

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                    while (true) {
                        currentCoroutineContext().ensureActive()

                        val read = input.read(buffer)
                        if (read <= 0) break

                        output.write(buffer, 0, read)
                        downloaded += read

                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(
                                    percent,
                                    "Descargando ${downloaded / 1_000_000} de " +
                                        "${total / 1_000_000} MB"
                                )
                            }
                        } else {
                            onProgress(-1, "Descargando ${downloaded / 1_000_000} MB")
                        }
                    }

                    output.flush()
                }
            }

            if (total > 0 && downloaded < total) {
                throw IllegalStateException(
                    "Descarga incompleta: $downloaded de $total bytes"
                )
            }
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    companion object {
        private const val TAG = "SherpaModelDownloader"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
