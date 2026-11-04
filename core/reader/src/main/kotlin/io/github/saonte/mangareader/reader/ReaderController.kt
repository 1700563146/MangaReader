package io.github.saonte.mangareader.reader

import io.github.saonte.mangareader.model.ImageRef
import io.github.saonte.mangareader.model.ReadMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 页数据源抽象：已下载章节、本地文件、在线章节共用。
 * `pageCount == 0` 表示页数未知（惰性章节）。
 */
interface PageSource {
    val pageCount: StateFlow<Int>
    suspend fun page(index: Int): ImageRef
}

data class ReaderUiState(
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val mode: ReadMode = ReadMode.PAGED_LTR,
    val scale: Float = 1f,
    val isMenuVisible: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * 阅读器状态机：不依赖 Android/Compose，可纯 JVM 单测。
 * Compose 层只负责把 [state] 渲染成 Pager/LazyColumn，并把事件回传。
 */
class ReaderController(
    private val pageSource: PageSource,
) {
    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    fun onPageSettled(pageIndex: Int) {
        _state.update {
            it.copy(
                pageIndex = pageIndex,
                pageCount = maxOf(it.pageCount, pageIndex + 1),
                loading = false,
                error = null,
            )
        }
    }

    fun toggleMenu() {
        _state.update { it.copy(isMenuVisible = !it.isMenuVisible) }
    }

    fun setMode(mode: ReadMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun onScaleChanged(scale: Float) {
        _state.update { it.copy(scale = scale.coerceIn(1f, MAX_SCALE)) }
    }

    fun onError(message: String) {
        _state.update { it.copy(loading = false, error = message) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        const val MAX_SCALE = 5f
    }
}
