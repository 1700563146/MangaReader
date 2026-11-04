package io.github.saonte.mangareader.feature.local

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.saonte.mangareader.data.repo.ComicRepository
import io.github.saonte.mangareader.model.Comic
import io.github.saonte.mangareader.storage.LocalRoot
import io.github.saonte.mangareader.storage.LocalScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalViewModel @Inject constructor(
    private val scanner: LocalScanner,
    comics: ComicRepository,
) : ViewModel() {

    val roots: StateFlow<List<LocalRoot>> = scanner.observeRoots().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val localComics: StateFlow<List<Comic>> = comics.observeLocal().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    fun addRoot(uri: String) {
        viewModelScope.launch {
            _scanning.value = true
            scanner.addRoot(uri, "")
            scanInternal()
        }
    }

    fun scan() {
        viewModelScope.launch {
            _scanning.value = true
            scanInternal()
        }
    }

    fun removeRoot(id: Long) {
        viewModelScope.launch {
            scanner.removeRoot(id)
            _message.value = "已移除目录"
        }
    }

    fun deleteComic(comicId: Long) {
        viewModelScope.launch {
            scanner.deleteComic(comicId)
            _message.value = "已从书架移除"
        }
    }

    fun seedDemo(context: Context) {
        viewModelScope.launch {
            _scanning.value = true
            val uri = DebugLocalSeeder.seed(context)
            scanner.addRoot(uri, "演示本地目录（无内容）")
            scanInternal()
        }
    }

    fun dismissMessage() {
        _message.update { null }
    }

    private suspend fun scanInternal() {
        val result = scanner.scanAll()
        _message.value = result.error ?: "扫描完成：${result.comics} 部漫画"
        _scanning.value = false
    }
}
