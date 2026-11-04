package io.github.saonte.mangareader.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.saonte.mangareader.data.repo.ChapterRepository
import io.github.saonte.mangareader.data.repo.ComicRepository
import io.github.saonte.mangareader.data.repo.SourceRepository
import io.github.saonte.mangareader.data.settings.SettingsRepository
import io.github.saonte.mangareader.domain.SourceManager
import io.github.saonte.mangareader.domain.userMessage
import io.github.saonte.mangareader.model.Chapter
import io.github.saonte.mangareader.model.Comic
import io.github.saonte.mangareader.navigation.DetailRoute
import io.github.saonte.mangareader.storage.DownloadRequest
import io.github.saonte.mangareader.storage.DownloadScheduler
import io.github.saonte.mangareader.storage.DownloadState
import io.github.saonte.mangareader.storage.DownloadTask
import io.github.saonte.mangareader.source.SourceEngine
import io.github.saonte.mangareader.source.SourceFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val comic: Comic? = null,
    val chapters: List<Chapter> = emptyList(),
    val favorite: Boolean = false,
    val message: String? = null,
    val downloads: Map<Long, DownloadTask> = emptyMap(),
    val chaptersDescending: Boolean = false,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sourceManager: SourceManager,
    private val engine: SourceEngine,
    private val comics: ComicRepository,
    private val chapters: ChapterRepository,
    private val sources: SourceRepository,
    private val downloads: DownloadScheduler,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<DetailRoute>()
    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var comicId: Long = 0
    private var chaptersJob: Job? = null
    private var downloadsJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            settings.chaptersDescending.collect { descending ->
                _state.update { current ->
                    if (current.chaptersDescending == descending) {
                        current
                    } else {
                        current.copy(
                            chaptersDescending = descending,
                            chapters = current.chapters.reversed(),
                        )
                    }
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, message = null) }
            val cached = comics.findBySource(route.sourceType, route.cid)
            if (cached != null) {
                comicId = cached.id
                val cachedChapters = chapters.list(cached.id)
                _state.update {
                    it.copy(
                        loading = false,
                        comic = cached,
                        chapters = if (it.chaptersDescending) cachedChapters.reversed() else cachedChapters,
                        favorite = cached.favoriteAt != null,
                    )
                }
                observeChapters(cached.id)
                observeDownloads(cached.id)
            }
            runCatching {
                val existing = cached
                val ref = sourceManager.ref(route.sourceType)
                    ?: error("源不可用，请到规则管理重新启用")
                val detail = engine.detail(ref, route.cid)
                val base = existing ?: Comic(source = route.sourceType, cid = route.cid, title = detail.summary.title)
                val merged = base.copy(
                    title = detail.summary.title,
                    cover = detail.summary.cover,
                    author = detail.summary.author ?: base.author,
                    intro = detail.intro ?: base.intro,
                    updateText = detail.summary.update ?: base.updateText,
                    finish = detail.summary.finish ?: base.finish,
                    url = detail.summary.url ?: base.url,
                )
                val id = comics.upsert(merged)
                chapters.replaceFromSource(
                    id,
                    detail.chapters.map {
                        Chapter(
                            comicId = id,
                            title = it.title,
                            path = it.path,
                            sourceGroup = it.group,
                        )
                    },
                )
                id
            }.onSuccess { id ->
                sources.recordSuccess(route.sourceType)
                comicId = id
                val comic = comics.findById(id)
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        comic = comic,
                        favorite = comic?.favoriteAt != null,
                    )
                }
                observeChapters(id)
                observeDownloads(id)
            }.onFailure { throwable ->
                sources.recordFailure(
                    route.sourceType,
                    (throwable as? SourceFailure)?.code?.name ?: "UNKNOWN",
                )
                val hasCache = cached != null
                _state.update {
                    it.copy(
                        loading = false,
                        error = if (hasCache) {
                            null
                        } else {
                            (throwable as? SourceFailure)?.userMessage()
                                ?: throwable.message
                                ?: "加载详情失败"
                        },
                        message = if (hasCache) "离线：显示已缓存内容" else null,
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        if (comicId == 0L) return
        viewModelScope.launch {
            val favorite = !_state.value.favorite
            comics.setFavorite(comicId, favorite)
            val comic = comics.findById(comicId)
            _state.update { it.copy(favorite = favorite, comic = comic) }
        }
    }

    fun openChapter(chapter: Chapter, index: Int, onReady: (comicId: Long, chapterPath: String) -> Unit) {
        if (comicId == 0L) return
        viewModelScope.launch {
            comics.saveProgress(comicId, chapter.path, chapter.title, index, 0)
            onReady(comicId, chapter.path)
        }
    }

    fun continueReading(onReady: (comicId: Long, chapterPath: String) -> Unit) {
        val state = _state.value
        val index = state.chapters.indexOfFirst { it.path == state.comic?.lastChapterPath }
            .takeIf { it >= 0 }
            ?: state.chapters.indexOfFirst { it.readAt == null }.takeIf { it >= 0 }
            ?: 0
        state.chapters.getOrNull(index)?.let { openChapter(it, index, onReady) }
    }

    fun onDownloadAction(chapter: Chapter) {
        val comic = _state.value.comic ?: return
        val task = _state.value.downloads[chapter.id]
        viewModelScope.launch {
            when (task?.state) {
                null, DownloadState.CANCELLED -> downloads.enqueue(
                    DownloadRequest(
                        comicId = comic.id,
                        chapterId = chapter.id,
                        sourceType = comic.source,
                        cid = comic.cid,
                        chapterPath = chapter.path,
                        title = chapter.title,
                    ),
                )

                DownloadState.QUEUED, DownloadState.RUNNING -> downloads.pause(chapter.id)
                DownloadState.PAUSED, DownloadState.FAILED -> downloads.resume(chapter.id)
                DownloadState.COMPLETED -> Unit
            }
        }
    }

    fun toggleChapterOrder() {
        val next = !_state.value.chaptersDescending
        viewModelScope.launch { settings.setChaptersDescending(next) }
        _state.update {
            it.copy(chaptersDescending = next, chapters = it.chapters.reversed())
        }
    }

    fun downloadAll() {
        val comic = _state.value.comic ?: return
        val pending = _state.value.chapters.filter { chapter ->
            val task = _state.value.downloads[chapter.id]
            !chapter.downloaded && task == null
        }
        if (pending.isEmpty()) {
            _state.update { it.copy(message = "没有需要下载的章节") }
            return
        }
        viewModelScope.launch {
            pending.forEach { chapter ->
                downloads.enqueue(
                    DownloadRequest(
                        comicId = comic.id,
                        chapterId = chapter.id,
                        sourceType = comic.source,
                        cid = comic.cid,
                        chapterPath = chapter.path,
                        title = chapter.title,
                    ),
                )
            }
            _state.update { it.copy(message = "已加入下载队列：${pending.size} 章") }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun observeDownloads(id: Long) {
        downloadsJob?.cancel()
        downloadsJob = viewModelScope.launch {
            downloads.observeByComic(id).collect { list ->
                _state.update { it.copy(downloads = list.associateBy { task -> task.chapterId }) }
            }
        }
    }

    private fun observeChapters(id: Long) {
        chaptersJob?.cancel()
        chaptersJob = viewModelScope.launch {
            chapters.observe(id).collect { list ->
                _state.update {
                    it.copy(chapters = if (it.chaptersDescending) list.reversed() else list)
                }
            }
        }
    }
}
