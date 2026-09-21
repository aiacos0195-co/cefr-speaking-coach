package com.cefrspeakingcoach.app

class ConcatenativeCoachVoiceSynthesizer(
    private val repository: CoachVoicePackRepository
) {

    fun canSynthesize(
        text: String,
        manifest: CoachVoicePackManifest
    ): Boolean {
        return resolveUnitsForText(text, manifest) != null
    }

    fun synthesize(
        text: String,
        definition: CoachVoicePackDefinition,
        manifest: CoachVoicePackManifest
    ): CoachVoiceAudio? {
        val units = resolveUnitsForText(text, manifest) ?: return null

        val audios = units.map { unit ->
            val path = repository.resolveUnitAssetPath(
                definition = definition,
                unit = unit
            )

            val bytes = repository.readAssetBytes(path)

            if (!path.endsWith(".wav", ignoreCase = true)) {
                return null
            }

            WavCoachAudioDecoder.decode(bytes)
        }

        if (audios.isEmpty()) return null

        val sampleRate = audios.first().sampleRate
        val channels = audios.first().channels

        val compatible = audios.all { audio ->
            audio.sampleRate == sampleRate && audio.channels == channels
        }

        if (!compatible) return null

        return concatenate(
            audios = audios,
            sampleRate = sampleRate,
            channels = channels
        )
    }

    private fun resolveUnitsForText(
        text: String,
        manifest: CoachVoicePackManifest
    ): List<CoachVoiceUnit>? {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return null

        val unitsByKey = buildUnitMap(manifest)

        val resolved = mutableListOf<CoachVoiceUnit>()

        for (token in tokens) {
            val unit = unitsByKey[token] ?: return null
            resolved.add(unit)
        }

        return resolved
    }

    private fun buildUnitMap(
        manifest: CoachVoicePackManifest
    ): Map<String, CoachVoiceUnit> {
        val map = linkedMapOf<String, CoachVoiceUnit>()

        manifest.units.forEach { unit ->
            val idKey = normalizeKey(unit.id)
            val textKey = normalizeKey(unit.text)

            if (idKey.isNotBlank()) {
                map.putIfAbsent(idKey, unit)
            }

            if (textKey.isNotBlank()) {
                map.putIfAbsent(textKey, unit)
            }
        }

        return map
    }

    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .map { normalizeKey(it) }
    }

    private fun normalizeKey(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun concatenate(
        audios: List<CoachVoiceAudio>,
        sampleRate: Int,
        channels: Int
    ): CoachVoiceAudio {
        val silenceSamples = ((sampleRate * channels) * 0.035f).toInt()

        val totalSize = audios.sumOf { it.samples.size } +
            silenceSamples * (audios.size - 1).coerceAtLeast(0)

        val output = FloatArray(totalSize)

        var cursor = 0

        audios.forEachIndexed { index, audio ->
            audio.samples.copyInto(
                destination = output,
                destinationOffset = cursor
            )

            cursor += audio.samples.size

            if (index < audios.lastIndex && silenceSamples > 0) {
                cursor += silenceSamples
            }
        }

        return CoachVoiceAudio(
            samples = output,
            sampleRate = sampleRate,
            channels = channels
        )
    }
}
