package io.github.saonte.mangareader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.saonte.mangareader.model.ReadMode
import io.github.saonte.mangareader.model.TapAction
import io.github.saonte.mangareader.model.TapConfig
import io.github.saonte.mangareader.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { raw ->
            ThemeMode.entries.firstOrNull { it.name == raw }
        } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    val readerMode: Flow<ReadMode> = dataStore.data.map { prefs ->
        prefs[KEY_READER_MODE]?.let { raw -> ReadMode.entries.firstOrNull { it.name == raw } }
            ?: ReadMode.PAGED_LTR
    }

    suspend fun setReaderMode(mode: ReadMode) {
        dataStore.edit { it[KEY_READER_MODE] = mode.name }
    }

    val readerDoublePage: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_READER_DOUBLE_PAGE] ?: false
    }

    suspend fun setReaderDoublePage(enabled: Boolean) {
        dataStore.edit { it[KEY_READER_DOUBLE_PAGE] = enabled }
    }

    val chaptersDescending: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CHAPTERS_DESC] ?: false
    }

    suspend fun setChaptersDescending(enabled: Boolean) {
        dataStore.edit { it[KEY_CHAPTERS_DESC] = enabled }
    }

    val tapConfig: Flow<TapConfig> = dataStore.data.map { prefs ->
        TapConfig(
            left = prefs[KEY_TAP_LEFT].toTapAction(TapConfig.DEFAULT.left),
            center = prefs[KEY_TAP_CENTER].toTapAction(TapConfig.DEFAULT.center),
            right = prefs[KEY_TAP_RIGHT].toTapAction(TapConfig.DEFAULT.right),
        )
    }

    suspend fun setTapAction(zone: String, action: TapAction) {
        dataStore.edit { prefs ->
            when (zone) {
                "LEFT" -> prefs[KEY_TAP_LEFT] = action.name
                "CENTER" -> prefs[KEY_TAP_CENTER] = action.name
                else -> prefs[KEY_TAP_RIGHT] = action.name
            }
        }
    }

    private fun String?.toTapAction(fallback: TapAction): TapAction =
        TapAction.entries.firstOrNull { it.name == this } ?: fallback

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme.mode")
        val KEY_READER_MODE = stringPreferencesKey("reader.mode")
        val KEY_READER_DOUBLE_PAGE = booleanPreferencesKey("reader.doublePage")
        val KEY_CHAPTERS_DESC = booleanPreferencesKey("library.chaptersDescending")
        val KEY_TAP_LEFT = stringPreferencesKey("reader.tap.left")
        val KEY_TAP_CENTER = stringPreferencesKey("reader.tap.center")
        val KEY_TAP_RIGHT = stringPreferencesKey("reader.tap.right")
    }
}
