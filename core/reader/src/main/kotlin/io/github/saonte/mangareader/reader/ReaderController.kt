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
    val chapterTitle: String
    suspend fun page(index: Int): ImageRef
}

/** 阅读进度出口；由 app 层接到 ComicRepository，reader 模块不依赖数据库。 */
fun interface ProgressSink {
    fun onProgress(chapterIndex: Int, chapterTitle: String, pageIndex: Int, pageCount: Int)
}

data class ReaderUiState(
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val mode: ReadMode = ReadMode.PAGED_LTR,
    val doublePage: Boolean = false,
    val scale: Float = 1f,
    val isMenuVisible: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * 阅读器状态机：不依赖 Compose，可纯 JVM 单测。
 * UI 只负责把 [state] 渲染成 Pager/LazyColumn，并把事件回传。
 */
class ReaderController(
    private val pageSource: PageSource,
    private val progressSink: ProgressSink? = null,
) {
    private val _state = MutableStateFlow(
        ReaderUiState(
            chapterTitle = pageSource.chapterTitle,
            pageCount = pageSource.pageCount.value,
        ),
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { pageSource.page(0) }
            .onSuccess {
                _state.update {
                    it.copy(loading = false, pageCount = maxOf(it.pageCount, pageSource.pageCount.value))
                }
            }
            .onFailure { throwable ->
                _state.update {
                    it.copy(loading = false, error = throwable.message ?: "页面加载失败")
                }
            }
    }

    suspend fun retry() = load()

    fun onPageSettled(pageIndex: Int) {
        val current = _state.value
        _state.value = current.copy(
            pageIndex = pageIndex,
            pageCount = maxOf(current.pageCount, pageIndex + 1),
            loading = false,
            error = null,
        )
        progressSink?.onProgress(
            chapterIndex = current.chapterIndex,
            chapterTitle = current.chapterTitle,
            pageIndex = pageIndex,
            pageCount = _state.value.pageCount,
        )
    }

    fun toggleMenu() {
        _state.update { it.copy(isMenuVisible = !it.isMenuVisible) }
    }

    fun setMode(mode: ReadMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun toggleDoublePage() {
        _state.update { it.copy(doublePage = !it.doublePage) }
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

        /** 双页模式下 pager 的逻辑页数（最后一页可能单独占一屏）。 */
        fun spreadCount(pages: Int, doublePage: Boolean): Int = when {
            pages <= 0 -> 0
            doublePage -> (pages + 1) / 2
            else -> pages
        }
    }
}
