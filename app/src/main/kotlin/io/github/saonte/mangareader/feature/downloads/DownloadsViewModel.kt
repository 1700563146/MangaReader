package io.github.saonte.mangareader.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.saonte.mangareader.storage.DownloadScheduler
import io.github.saonte.mangareader.storage.DownloadTask
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val scheduler: DownloadScheduler,
) : ViewModel() {

    val tasks: StateFlow<List<DownloadTask>> = scheduler.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun pause(chapterId: Long) {
        viewModelScope.launch { scheduler.pause(chapterId) }
    }

    fun resume(chapterId: Long) {
        viewModelScope.launch { scheduler.resume(chapterId) }
    }

    fun cancel(chapterId: Long) {
        viewModelScope.launch { scheduler.cancel(chapterId) }
    }
}
