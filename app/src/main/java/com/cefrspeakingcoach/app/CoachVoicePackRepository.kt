package com.cefrspeakingcoach.app

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads coach voice packs from assets.
 *
 * Degradation policy (v2):
 * a unit whose audio file is missing is DROPPED, it no longer invalidates the
 * whole pack. Previously a single missing .wav made inspectPack() return
 * INVALID, which silently disabled CustomCoachVoiceEngine for every coach.
 */
class CoachVoicePackRepository(
    context: Context
) {
    private val appContext = context.applicationContext

    fun inspectAllPacks(): List<CoachVoicePackState> {
        return CoachVoicePackRegistry.allPacks().map { definition ->
            inspectPack(definition)
        }
    }

    fun inspectPack(definition: CoachVoicePackDefinition): CoachVoicePackState {
        val manifestPath = "${definition.assetDirectory}/${definition.expectedManifestFile}"

        if (!assetExists(manifestPath)) {
            return CoachVoicePackState(
                definition = definition,
                status = CoachVoicePackStatus.NOT_INSTALLED,
                reason = "Missing ${definition.expectedManifestFile}"
            )
        }

        return try {
            val manifest = loadManifest(definition)

            if (!manifest.packId.equals(definition.packId, ignoreCase = true)) {
                return CoachVoicePackState(
                    definition = definition,
                    status = CoachVoicePackStatus.INVALID,
                    reason = "Pack id mismatch: manifest=${manifest.packId} registry=${definition.packId}"
                )
            }

            if (!manifest.coachId.equals(definition.coachId, ignoreCase = true)) {
                return CoachVoicePackState(
                    definition = definition,
                    status = CoachVoicePackStatus.INVALID,
                    reason = "Coach id mismatch: manifest=${manifest.coachId} registry=${definition.coachId}"
                )
            }

            if (!requiresAudioAssets(manifest.type)) {
                return CoachVoicePackState(
                    definition = definition,
                    status = CoachVoicePackStatus.INSTALLED,
                    reason = "Installed (type ${manifest.type} needs no bundled audio)",
                    usableUnitCount = 0,
                    missingUnitPaths = emptyList()
                )
            }

            val missing = manifest.units.filter { unit ->
                !assetExists(resolveUnitAssetPath(definition, unit))
            }

            val usable = manifest.units.size - missing.size

            if (missing.isNotEmpty()) {
                Log.w(
                    TAG,
                    "Pack ${definition.packId}: dropping ${missing.size} unit(s), missing assets: " +
                        missing.joinToString { it.assetPath }
                )
            }

            if (usable <= 0) {
                return CoachVoicePackState(
                    definition = definition,
                    status = CoachVoicePackStatus.INVALID,
                    reason = "No usable audio units. Missing: " + missing.joinToString { it.assetPath },
                    usableUnitCount = 0,
                    missingUnitPaths = missing.map { it.assetPath }
                )
            }

            CoachVoicePackState(
                definition = definition,
                status = CoachVoicePackStatus.INSTALLED,
                reason = if (missing.isEmpty()) {
                    "Installed with $usable unit(s)"
                } else {
                    "Installed with $usable unit(s), ${missing.size} dropped"
                },
                usableUnitCount = usable,
                missingUnitPaths = missing.map { it.assetPath }
            )
        } catch (e: Exception) {
            Log.e(TAG, "inspectPack failed for ${definition.packId}", e)
            CoachVoicePackState(
                definition = definition,
                status = CoachVoicePackStatus.INVALID,
                reason = e.message ?: "Invalid manifest"
            )
        }
    }

    /**
     * Loads the manifest and removes units whose audio file is not present.
     * Use this, not loadManifest, anywhere units are about to be played.
     */
    fun loadUsableManifest(
        definition: CoachVoicePackDefinition
    ): CoachVoicePackManifest {
        val manifest = loadManifest(definition)

        if (!requiresAudioAssets(manifest.type)) return manifest

        val usableUnits = manifest.units.filter { unit ->
            assetExists(resolveUnitAssetPath(definition, unit))
        }

        return manifest.copy(units = usableUnits)
    }

    fun loadManifestForVoice(
        voice: ConversationVoiceModel
    ): CoachVoicePackManifest? {
        val definition = CoachVoicePackRegistry.findByVoice(voice) ?: return null
        val state = inspectPack(definition)

        if (state.status != CoachVoicePackStatus.INSTALLED) {
            return null
        }

        return runCatching {
            loadUsableManifest(definition)
        }.getOrNull()
    }

    fun loadDefinitionForVoice(
        voice: ConversationVoiceModel
    ): CoachVoicePackDefinition? {
        return CoachVoicePackRegistry.findByVoice(voice)
    }

    fun loadManifest(
        definition: CoachVoicePackDefinition
    ): CoachVoicePackManifest {
        val manifestPath = "${definition.assetDirectory}/${definition.expectedManifestFile}"
        val jsonText = readAssetText(manifestPath)
        val json = JSONObject(jsonText)

        val typeText = json.optString("type", definition.type.name)
        val type = runCatching {
            CoachVoicePackType.valueOf(typeText.uppercase())
        }.getOrElse {
            Log.w(TAG, "Unknown pack type '$typeText', falling back to ${definition.type}")
            definition.type
        }

        val unitsJson = json.optJSONArray("units") ?: JSONArray()
        val units = mutableListOf<CoachVoiceUnit>()

        for (index in 0 until unitsJson.length()) {
            val item = unitsJson.optJSONObject(index) ?: continue

            val id = item.optString("id", "").trim()
            val assetPath = item.optString("assetPath", "").trim()

            if (id.isBlank() || assetPath.isBlank()) {
                Log.w(TAG, "Skipping unit at index $index: missing id or assetPath")
                continue
            }

            units.add(
                CoachVoiceUnit(
                    id = id,
                    text = item.optString("text", ""),
                    assetPath = assetPath,
                    tags = parseStringArray(item.optJSONArray("tags"))
                )
            )
        }

        return CoachVoicePackManifest(
            packId = json.optString("packId", definition.packId),
            coachId = json.optString("coachId", definition.coachId),
            displayName = json.optString("displayName", definition.displayName),
            version = json.optInt("version", 1),
            type = type,
            sampleRate = json.optInt("sampleRate", definition.defaultSampleRate),
            channels = json.optInt("channels", definition.defaultChannels),
            units = units
        )
    }

    fun resolveUnitAssetPath(
        definition: CoachVoicePackDefinition,
        unit: CoachVoiceUnit
    ): String {
        val path = unit.assetPath.trim().trim('/')

        return if (path.startsWith("coach_voice_packs/")) {
            path
        } else {
            "${definition.assetDirectory}/$path"
        }
    }

    fun readAssetBytes(path: String): ByteArray {
        return appContext.assets.open(path).use { input ->
            input.readBytes()
        }
    }

    private fun requiresAudioAssets(type: CoachVoicePackType): Boolean {
        return when (type) {
            CoachVoicePackType.RECORDED_PHRASE,
            CoachVoicePackType.CONCATENATIVE,
            CoachVoicePackType.HYBRID -> true

            CoachVoicePackType.NEURAL_LOCAL -> false
        }
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()

        val result = mutableListOf<String>()

        for (index in 0 until array.length()) {
            result.add(array.optString(index))
        }

        return result.filter { it.isNotBlank() }
    }

    private fun assetExists(path: String): Boolean {
        val cleanPath = path.trim().trim('/')

        val parent = cleanPath.substringBeforeLast(
            delimiter = "/",
            missingDelimiterValue = ""
        )

        val fileName = cleanPath.substringAfterLast("/")

        return try {
            val items = appContext.assets.list(parent).orEmpty()
            items.any { it == fileName }
        } catch (_: Exception) {
            false
        }
    }

    private fun readAssetText(path: String): String {
        return appContext.assets.open(path).bufferedReader().use { reader ->
            reader.readText()
        }
    }

    companion object {
        private const val TAG = "CoachVoicePackRepo"
    }
}
