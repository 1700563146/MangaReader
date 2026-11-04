package io.github.saonte.mangareader.feature.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.saonte.mangareader.data.repo.ChapterRepository
import io.github.saonte.mangareader.data.repo.ComicRepository
import io.github.saonte.mangareader.data.repo.SourceRepository
import io.github.saonte.mangareader.data.settings.SettingsRepository
import io.github.saonte.mangareader.domain.SourceManager
import io.github.saonte.mangareader.domain.userMessage
import io.github.saonte.mangareader.model.Chapter
import io.github.saonte.mangareader.model.Comic
import io.github.saonte.mangareader.model.ReadMode
import io.github.saonte.mangareader.model.TapConfig
import io.github.saonte.mangareader.navigation.ReaderRoute
import io.github.saonte.mangareader.reader.PageSource
import io.github.saonte.mangareader.storage.DownloadScheduler
import io.github.saonte.mangareader.source.SourceEngine
import io.github.saonte.mangareader.source.SourceFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReaderPreferences(
    val mode: ReadMode = ReadMode.PAGED_LTR,
    val doublePage: Boolean = false,
    val tapConfig: TapConfig = TapConfig.DEFAULT,
)

data class ReaderSession(
    val comic: Comic,
    val chapter: Chapter,
    val chapterIndex: Int,
    val pageSource: PageSource,
    val notice: String? = null,
)

sealed interface ReaderSessionState {
    data object Loading : ReaderSessionState
    data class Ready(val session: ReaderSession) : ReaderSessionState
    data class Failed(val message: String) : ReaderSessionState
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val sourceManager: SourceManager,
    private val engine: SourceEngine,
    private val comics: ComicRepository,
    private val chapters: ChapterRepository,
    private val sources: SourceRepository,
    private val downloads: DownloadScheduler,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ReaderRoute>()
    private val _state = MutableStateFlow<ReaderSessionState>(ReaderSessionState.Loading)
    val state: StateFlow<ReaderSessionState> = _state.asStateFlow()

    val preferences: StateFlow<ReaderPreferences> = combine(
        settings.readerMode,
        settings.readerDoublePage,
        settings.tapConfig,
    ) { mode, doublePage, taps -> ReaderPreferences(mode, doublePage, taps) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReaderPreferences(),
    )

    fun dismissNotice() {
        val current = _state.value
        if (current is ReaderSessionState.Ready && current.session.notice != null) {
            _state.value = current.copy(session = current.session.copy(notice = null))
        }
    }

    fun setModePreference(mode: ReadMode) {
        viewModelScope.launch { settings.setReaderMode(mode) }
    }

    fun setDoublePagePreference(enabled: Boolean) {
        viewModelScope.launch { settings.setReaderDoublePage(enabled) }
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ReaderSessionState.Loading
            var loadedSource: Long? = null
            var fallbackNotice: String? = null
            runCatching {
                val comic = comics.findById(route.comicId) ?: error("漫画不存在")
                loadedSource = comic.source.takeIf { it > 0 }
                val chapterList = chapters.list(comic.id)
                val exact = chapterList.indexOfFirst { it.path == route.chapterPath }
                // F-303：目标章节被源移除时，回退到索引最近的可用章节并提示。
                val index = if (exact >= 0) {
                    exact
                } else {
                    chapterList.lastIndex.coerceAtLeast(0).let { last ->
                        comic.lastChapterIndex.coerceIn(0, last)
                    }
                }
                val chapter = chapterList.getOrNull(index)
                    ?: error("章节不存在或已被源移除")
                if (exact < 0) {
                    fallbackNotice = "原章节已失效，已切换到「${chapter.title}」"
                }
                val offlineDir = downloads.completedDir(chapter.id)
                val pageSource = if (comic.local) {
                    LocalPageSource(
                        context = context,
                        rootUri = comic.url ?: error("本地目录缺失"),
                        relativePath = chapter.path,
                        chapterTitle = chapter.title,
                    )
                } else if (offlineDir != null) {
                    FilePageSource(File(offlineDir), chapter.title)
                } else {
                    val ref = sourceManager.ref(comic.source) ?: error("源不可用，请重新启用规则")
                    val imagePage = engine.images(ref, comic.cid, chapter.path)
                    RemotePageSource(
                        engine = engine,
                        ref = ref,
                        cid = comic.cid,
                        chapterPath = chapter.path,
                        chapterTitle = chapter.title,
                        imagePage = imagePage,
                    )
                }
                ReaderSession(
                    comic = comic,
                    chapter = chapter,
                    chapterIndex = index.coerceAtLeast(0),
                    pageSource = pageSource,
                    notice = fallbackNotice,
                )
            }.onSuccess { session ->
                sources.recordSuccess(session.comic.source)
                _state.value = ReaderSessionState.Ready(session)
                persistProgress(session, 0)
            }.onFailure { throwable ->
                loadedSource?.let { sourceType ->
                    sources.recordFailure(
                        sourceType,
                        (throwable as? SourceFailure)?.code?.name ?: "UNKNOWN",
                    )
                }
                _state.value = ReaderSessionState.Failed(
                    (throwable as? SourceFailure)?.userMessage() ?: throwable.message ?: "打开章节失败",
                )
            }
        }
    }

    fun onPageSettled(page: Int) {
        val session = (_state.value as? ReaderSessionState.Ready)?.session ?: return
        persistProgress(session, page)
    }

    private fun persistProgress(session: ReaderSession, page: Int) {
        viewModelScope.launch {
            comics.saveProgress(
                comicId = session.comic.id,
                chapterPath = session.chapter.path,
                chapterTitle = session.chapter.title,
                chapterIndex = session.chapterIndex,
                page = page,
            )
            chapters.savePage(session.chapter.id, page)
        }
    }
}
