package io.github.saonte.mangareader.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.saonte.mangareader.data.repo.ComicRepository
import io.github.saonte.mangareader.model.Comic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val comics: ComicRepository,
) : ViewModel() {
    val history: StateFlow<List<Comic>> = comics.observeHistory(limit = 100).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun delete(comicId: Long) {
        viewModelScope.launch {
            comics.clearHistory(comicId)
            _message.value = "已删除该条历史"
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            comics.clearAllHistory()
            _message.value = "已清空阅读历史"
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
