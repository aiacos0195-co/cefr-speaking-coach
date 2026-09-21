package com.cefrspeakingcoach.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Encuentra e instala los modelos de TTS de sherpa-onnx.
 *
 * Layout nuevo (multi-modelo):
 *
 *   filesDir/
 *     sherpa-tts/
 *       models/
 *         en_us_libritts_r/
 *           en_US-libritts_r-medium.onnx
 *           tokens.txt
 *           espeak-ng-data/...
 *         en_gb_vctk/
 *           en_GB-vctk-medium.onnx
 *           tokens.txt
 *           (sin espeak-ng-data: usa la del otro modelo)
 *
 * Dos cosas que cambian respecto a la versión de un solo modelo:
 *
 * 1. Cada modelo vive en su propia subcarpeta. La carpeta vieja "current/" se
 *    migra automáticamente a models/en_us_libritts_r/ la primera vez, así que
 *    el .onnx de 75 MB que ya está en el teléfono NO se vuelve a copiar.
 *
 * 2. espeak-ng-data se COMPARTE. Son ~19 MB idénticos en todos los modelos
 *    Piper; duplicarlos por modelo es regalar espacio en disco y en el APK.
 *    Si un modelo no trae la suya, se usa la de cualquier otro instalado.
 *
 * Sigue habiendo dos rutas de instalación: desde assets (Fase 1, para probar)
 * y desde un .zip descargado (Fase 2, GitHub Releases).
 */
class SherpaModelInstaller(
    context: Context
) {
    private val appContext = context.applicationContext

    private val rootDir: File
        get() = File(appContext.filesDir, ROOT_DIR_NAME)

    private val modelsDir: File
        get() = File(rootDir, MODELS_DIR_NAME)

    /** Carpeta donde vive (o vivirá) un modelo. No garantiza que exista. */
    fun modelDir(modelId: String): File = File(modelsDir, modelId)

    /**
     * Mueve la instalación vieja de un solo modelo al layout nuevo. Es
     * idempotente y barata: si no hay carpeta vieja, no hace nada.
     */
    fun migrateLegacyLayout() {
        val legacy = File(rootDir, LEGACY_DIR_NAME)
        if (!legacy.isDirectory) return

        val target = modelDir(SherpaVoiceCatalog.fallbackModelId)

        if (target.isDirectory) {
            // Ya migrado en un arranque anterior (o instalado de nuevo).
            legacy.deleteRecursively()
            return
        }

        target.parentFile?.mkdirs()

        val moved = legacy.renameTo(target)
        if (!moved) {
            legacy.copyRecursively(target, overwrite = true)
            legacy.deleteRecursively()
        }

        Log.i(TAG, "Migrated legacy model dir to ${target.absolutePath} (rename=$moved)")
    }

    /** Devuelve la carpeta del modelo si la instalación está completa. */
    fun installedModelDir(modelId: String): File? {
        val dir = modelDir(modelId)
        if (!dir.isDirectory) return null

        val hasModel = findModelFile(dir) != null
        val hasTokens = File(dir, TOKENS_FILE).exists()
        val espeak = espeakDataDir(modelId)

        if (hasModel && hasTokens && espeak != null) return dir

        Log.w(
            TAG,
            "Incomplete install at ${dir.absolutePath} " +
                "(model=$hasModel tokens=$hasTokens espeak=${espeak != null})"
        )
        return null
    }

    fun isInstalled(modelId: String): Boolean = installedModelDir(modelId) != null

    /** Ids del catálogo que están completos en disco, en orden de catálogo. */
    fun installedModelIds(): List<String> =
        SherpaVoiceCatalog.models.map { it.id }.filter { isInstalled(it) }

    /**
     * Los archivos de Piper llevan el nombre de la voz, así que buscamos
     * cualquier .onnx en vez de fijar un nombre que cambia con cada modelo.
     */
    fun findModelFile(modelDir: File): File? {
        return modelDir
            .listFiles { file -> file.isFile && file.name.endsWith(".onnx", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.firstOrNull()
    }

    /**
     * espeak-ng-data que debe usar este modelo: la propia si la trae, si no una
     * compartida, si no la de cualquier otro modelo instalado.
     */
    fun espeakDataDir(modelId: String): File? {
        val own = File(modelDir(modelId), ESPEAK_DIR)
        if (own.isDirectory) return own

        val shared = File(rootDir, ESPEAK_DIR)
        if (shared.isDirectory) return shared

        return modelsDir
            .listFiles { file -> file.isDirectory }
            .orEmpty()
            .map { File(it, ESPEAK_DIR) }
            .firstOrNull { it.isDirectory }
    }

    /**
     * Desempaqueta un .zip de modelo Piper dentro de filesDir/sherpa-tts/models/<modelId>.
     * El zip puede traer los archivos en la raíz o dentro de una única carpeta;
     * en ambos casos quedan planos en la carpeta del modelo.
     *
     * Nota: los releases oficiales de sherpa-onnx son .tar.bz2, que Android no
     * abre sin librería extra. Reempaquétalos a .zip una vez y sube ese.
     */
    fun installFromZip(modelId: String, input: InputStream): Result<File> = runCatching {
        val staging = File(rootDir, STAGING_DIR_NAME)
        staging.deleteRecursively()
        staging.mkdirs()

        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry

            while (entry != null) {
                val target = File(staging, entry.name)

                // Rechaza path traversal antes de escribir nada.
                if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                    throw SecurityException("Zip entry escapes target dir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        zip.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val source = flattenSingleRoot(staging)
        val destination = modelDir(modelId)

        destination.deleteRecursively()
        destination.parentFile?.mkdirs()

        if (!source.renameTo(destination)) {
            source.copyRecursively(destination, overwrite = true)
            source.deleteRecursively()
        }

        staging.deleteRecursively()

        installedModelDir(modelId) ?: throw IllegalStateException(
            "Install finished but $modelId is incomplete at ${destination.absolutePath}"
        )
    }

    /**
     * Atajo de desarrollo: copia un modelo ya empaquetado en
     * assets/<assetDir>/ hacia filesDir. Recuerda que un .onnx en assets debe
     * quedar sin comprimir (noCompress en build.gradle.kts).
     */
    fun installFromAssets(modelId: String): Result<File> = runCatching {
        val model = SherpaVoiceCatalog.model(modelId)
            ?: throw IllegalArgumentException("Unknown model id: $modelId")

        val children = appContext.assets.list(model.assetDir).orEmpty()
        if (children.isEmpty()) {
            throw IllegalStateException(
                "assets/${model.assetDir}/ is missing or empty; nothing to install for $modelId"
            )
        }

        val destination = modelDir(modelId)
        destination.deleteRecursively()
        destination.mkdirs()

        copyAssetDir(model.assetDir, destination)

        installedModelDir(modelId) ?: throw IllegalStateException(
            "Asset copy finished but $modelId is incomplete at ${destination.absolutePath}"
        )
    }

    /**
     * Asegura que el modelo esté en disco: si ya está, lo devuelve; si no,
     * intenta instalarlo desde assets. Es lo que llama el motor al cargar.
     */
    fun ensureInstalled(modelId: String): Result<File> {
        installedModelDir(modelId)?.let { return Result.success(it) }
        return installFromAssets(modelId)
    }

    private fun copyAssetDir(assetPath: String, target: File) {
        val children = appContext.assets.list(assetPath).orEmpty()

        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
            return
        }

        target.mkdirs()

        children.forEach { child ->
            copyAssetDir("$assetPath/$child", File(target, child))
        }
    }

    /** Si el zip envolvió todo en una sola carpeta, usa esa carpeta. */
    private fun flattenSingleRoot(dir: File): File {
        val entries = dir.listFiles().orEmpty()

        return if (entries.size == 1 && entries[0].isDirectory) {
            entries[0]
        } else {
            dir
        }
    }

    fun uninstall(modelId: String) {
        modelDir(modelId).deleteRecursively()
    }

    fun uninstallAll() {
        rootDir.deleteRecursively()
    }

    fun installedSizeBytes(modelId: String): Long {
        val dir = modelDir(modelId)
        if (!dir.isDirectory) return 0L

        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    companion object {
        private const val TAG = "SherpaModelInstaller"
        private const val ROOT_DIR_NAME = "sherpa-tts"
        private const val MODELS_DIR_NAME = "models"
        private const val LEGACY_DIR_NAME = "current"
        private const val STAGING_DIR_NAME = "staging"
        private const val ESPEAK_DIR = "espeak-ng-data"
        private const val TOKENS_FILE = "tokens.txt"
    }
}
