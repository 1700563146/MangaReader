package io.github.saonte.mangareader.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.saonte.mangareader.data.backup.BackupResult
import io.github.saonte.mangareader.data.backup.BackupService
import io.github.saonte.mangareader.data.backup.ImportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val busy: Boolean = false,
    val mode: ImportMode = ImportMode.MERGE,
    val message: String? = null,
    val lastSuccess: Boolean? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupService,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun setMode(mode: ImportMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun export(uri: Uri) {
        run(uri, null)
    }

    fun import(uri: Uri) {
        run(uri, _state.value.mode)
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun run(uri: Uri, mode: ImportMode?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result: BackupResult = if (mode == null) {
                backup.export(uri)
            } else {
                backup.import(uri, mode)
            }
            _state.update {
                it.copy(busy = false, message = result.message, lastSuccess = result.success)
            }
        }
    }
}
