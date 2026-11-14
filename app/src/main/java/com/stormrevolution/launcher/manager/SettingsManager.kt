package com.stormrevolution.launcher.manager

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stormrevolution.launcher.model.ControlLayoutConfig
import com.stormrevolution.launcher.model.GameConfiguration
import com.stormrevolution.launcher.model.GameLibraryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.stormDataStore by preferencesDataStore(name = "storm_launcher_prefs")

class SettingsManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val GAME_CONFIG = stringPreferencesKey("game_config_json")
        val CONTROL_LAYOUT = stringPreferencesKey("control_layout_json")
        val GAME_LIBRARY = stringPreferencesKey("game_library_json")
    }

    suspend fun loadConfiguration(): GameConfiguration {
        val raw = context.stormDataStore.data.map { it[Keys.GAME_CONFIG] }.first() ?: return GameConfiguration()
        return runCatching { json.decodeFromString<GameConfiguration>(raw) }.getOrDefault(GameConfiguration())
    }

    suspend fun saveConfiguration(config: GameConfiguration) {
        context.stormDataStore.edit { prefs ->
            prefs[Keys.GAME_CONFIG] = json.encodeToString(GameConfiguration.serializer(), config)
        }
    }

    suspend fun loadControlLayout(): ControlLayoutConfig {
        val raw = context.stormDataStore.data.map { it[Keys.CONTROL_LAYOUT] }.first() ?: return ControlLayoutConfig()
        return runCatching { json.decodeFromString<ControlLayoutConfig>(raw) }.getOrDefault(ControlLayoutConfig())
    }

    suspend fun saveControlLayout(layout: ControlLayoutConfig) {
        context.stormDataStore.edit { prefs ->
            prefs[Keys.CONTROL_LAYOUT] = json.encodeToString(ControlLayoutConfig.serializer(), layout)
        }
    }

    suspend fun loadGameLibraryState(): GameLibraryState {
        val raw = context.stormDataStore.data.map { it[Keys.GAME_LIBRARY] }.first() ?: return GameLibraryState()
        return runCatching { json.decodeFromString<GameLibraryState>(raw) }.getOrDefault(GameLibraryState())
    }

    suspend fun saveGameLibraryState(state: GameLibraryState) {
        context.stormDataStore.edit { prefs ->
            prefs[Keys.GAME_LIBRARY] = json.encodeToString(GameLibraryState.serializer(), state)
        }
    }
}
