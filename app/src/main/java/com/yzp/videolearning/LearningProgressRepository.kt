package com.yzp.videolearning

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import org.json.JSONObject

private val Context.learningDataStore by preferencesDataStore(name = "learning_progress")

class LearningProgressRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(context.applicationContext.learningDataStore)

    private val progressKey = stringPreferencesKey("progress_json")

    val progress: Flow<Map<String, LearningProgress>> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        decode(preferences[progressKey].orEmpty())
    }

    suspend fun markOpened(videoId: String) {
        update(videoId) { old -> old.copy(lastOpenedAt = nowMs()) }
    }

    suspend fun savePosition(videoId: String, positionMs: Long?) {
        // Missing player telemetry must not erase an earlier known position.
        if (positionMs != null && positionMs >= 0) {
            update(videoId) { old -> old.copy(lastPositionMs = positionMs) }
        }
    }

    suspend fun setCompleted(videoId: String, completed: Boolean) {
        update(videoId) { old -> old.copy(completed = completed) }
    }

    private suspend fun update(videoId: String, transform: (LearningProgress) -> LearningProgress) {
        if (videoId.isBlank()) return
        dataStore.edit { preferences ->
            val values = decode(preferences[progressKey].orEmpty()).toMutableMap()
            values[videoId] = transform(values[videoId] ?: LearningProgress())
            preferences[progressKey] = encode(values)
        }
    }

    private fun decode(raw: String): Map<String, LearningProgress> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { videoId ->
                    val item = root.getJSONObject(videoId)
                    put(
                        videoId,
                        LearningProgress(
                            lastPositionMs = item.optLong("lastPositionMs", 0L),
                            completed = item.optBoolean("completed", false),
                            lastOpenedAt = item.optLong("lastOpenedAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encode(values: Map<String, LearningProgress>): String {
        val root = JSONObject()
        values.forEach { (videoId, state) ->
            root.put(videoId, JSONObject().apply {
                put("lastPositionMs", state.lastPositionMs)
                put("completed", state.completed)
                put("lastOpenedAt", state.lastOpenedAt)
            })
        }
        return root.toString()
    }
}
