package io.github.saonte.mangareader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.saonte.mangareader.data.settings.SettingsRepository
import io.github.saonte.mangareader.model.ReadMode
import io.github.saonte.mangareader.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM,
    )

    val readerMode: StateFlow<ReadMode> = settings.readerMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReadMode.PAGED_LTR,
    )

    val readerDoublePage: StateFlow<Boolean> = settings.readerDoublePage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setReaderMode(mode: ReadMode) {
        viewModelScope.launch { settings.setReaderMode(mode) }
    }

    fun setReaderDoublePage(enabled: Boolean) {
        viewModelScope.launch { settings.setReaderDoublePage(enabled) }
    }
}
