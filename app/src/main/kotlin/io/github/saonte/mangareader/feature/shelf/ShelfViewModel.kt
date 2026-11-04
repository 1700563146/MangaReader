package io.github.saonte.mangareader.feature.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.saonte.mangareader.data.repo.ComicRepository
import io.github.saonte.mangareader.data.repo.SourceRepository
import io.github.saonte.mangareader.domain.SourceManager
import io.github.saonte.mangareader.model.Comic
import io.github.saonte.mangareader.source.SourceEngine
import io.github.saonte.mangareader.source.SourceFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class ShelfViewModel @Inject constructor(
    private val comics: ComicRepository,
    private val sources: SourceRepository,
    private val sourceManager: SourceManager,
    private val engine: SourceEngine,
) : ViewModel() {

    val shelf: StateFlow<List<Comic>> = comics.observeShelf().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _updatesOnly = MutableStateFlow(false)
    val updatesOnly: StateFlow<Boolean> = _updatesOnly

    fun toggleUpdatesOnly() {
        _updatesOnly.value = !_updatesOnly.value
    }

    fun checkUpdates() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            _message.value = null
            val targets = shelf.value
            var updated = 0
            var failed = 0
            val semaphore = Semaphore(4)
            coroutineScope {
                targets.map { comic ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val ref = sourceManager.ref(comic.source) ?: run {
                                failed += 1
                                return@withPermit
                            }
                            runCatching { engine.checkUpdate(ref, comic.cid) }
                                .onSuccess { text ->
                                    sources.recordSuccess(comic.source)
                                    if (!text.isNullOrBlank() && text != comic.updateText) {
                                        updated += 1
                                        comics.setUpdate(comic.id, text, System.currentTimeMillis(), highlight = true)
                                    } else {
                                        comics.setUpdate(comic.id, text ?: comic.updateText, System.currentTimeMillis(), highlight = false)
                                    }
                                }
                                .onFailure { error ->
                                    failed += 1
                                    sources.recordFailure(
                                        comic.source,
                                        (error as? SourceFailure)?.code?.name ?: "UNKNOWN",
                                    )
                                }
                        }
                    }
                }.awaitAll()
            }
            _message.value = "检查完成：${targets.size} 部，更新 $updated 部" +
                if (failed > 0) "，失败 $failed 部" else ""
            _checking.value = false
        }
    }

    fun dismissMessage() {
        _message.update { null }
    }
}
