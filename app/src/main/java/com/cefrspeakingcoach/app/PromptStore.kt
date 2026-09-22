package com.cefrspeakingcoach.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PromptStore(context: Context) {

    // Respaldo: este archivo esta en la lista de INCLUSION de
    // backup_rules.xml y de las dos secciones de data_extraction_rules.xml.
    // Unas prefs NUEVAS hay que agregarlas ahi tambien, o no se respaldan
    // y nadie se entera hasta que alguien restaure.
    private val prefs = context.getSharedPreferences("cefr_prompt_store", Context.MODE_PRIVATE)

    fun getRecentPromptIds(level: String): List<String> {
        val raw = prefs.getString("recent_$level", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { index -> arr.getString(index) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun pushRecentPrompt(level: String, promptId: String, maxSize: Int = 8) {
        val updated = getRecentPromptIds(level)
            .filterNot { it == promptId }
            .toMutableList()
            .apply { add(0, promptId) }
            .take(maxSize)

        val arr = JSONArray()
        updated.forEach { arr.put(it) }
        prefs.edit().putString("recent_$level", arr.toString()).apply()
    }

    fun getSelectedCategory(level: String): String {
        return prefs.getString("category_$level", "All") ?: "All"
    }

    fun setSelectedCategory(level: String, category: String) {
        prefs.edit().putString("category_$level", category).apply()
    }

    fun isFavorite(promptId: String): Boolean {
        return getFavoritePrompts().any { it.id == promptId }
    }

    fun isFavorite(prompt: PromptItem): Boolean {
        return isFavorite(prompt.id)
    }

    fun toggleFavorite(prompt: PromptItem): Boolean {
        val current = getFavoritePrompts().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == prompt.id }

        return if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            saveFavoritePrompts(current)
            false
        } else {
            current.add(0, prompt)
            saveFavoritePrompts(current.distinctBy { it.id }.take(100))
            true
        }
    }

    fun removeFavorite(promptId: String) {
        val updated = getFavoritePrompts().filterNot { it.id == promptId }
        saveFavoritePrompts(updated)
    }

    fun getFavoritePrompts(): List<PromptItem> {
        migrateLegacyFavoritesIfNeeded()

        val raw = prefs.getString(KEY_FAVORITE_PROMPTS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        PromptItem(
                            id = obj.getString("id"),
                            level = obj.getString("level"),
                            category = obj.getString("category"),
                            text = obj.getString("text"),
                            source = runCatching {
                                PromptSource.valueOf(obj.optString("source", PromptSource.LOCAL.name))
                            }.getOrElse { PromptSource.LOCAL }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveFavoritePrompts(prompts: List<PromptItem>) {
        val arr = JSONArray()
        prompts.forEach { prompt ->
            arr.put(
                JSONObject().apply {
                    put("id", prompt.id)
                    put("level", prompt.level)
                    put("category", prompt.category)
                    put("text", prompt.text)
                    put("source", prompt.source.name)
                }
            )
        }
        prefs.edit().putString(KEY_FAVORITE_PROMPTS_JSON, arr.toString()).apply()
    }

    private fun migrateLegacyFavoritesIfNeeded() {
        val currentJson = prefs.getString(KEY_FAVORITE_PROMPTS_JSON, null)
        if (!currentJson.isNullOrBlank()) return

        val legacyIds = prefs.getStringSet(KEY_LEGACY_FAVORITES, emptySet()).orEmpty()
        if (legacyIds.isEmpty()) return

        val migrated = legacyIds.mapNotNull { PromptRepository.findById(it) }
        if (migrated.isNotEmpty()) {
            saveFavoritePrompts(migrated)
        }

        prefs.edit().remove(KEY_LEGACY_FAVORITES).apply()
    }

    companion object {
        private const val KEY_LEGACY_FAVORITES = "favorite_prompt_ids"
        private const val KEY_FAVORITE_PROMPTS_JSON = "favorite_prompts_json"
    }
}
