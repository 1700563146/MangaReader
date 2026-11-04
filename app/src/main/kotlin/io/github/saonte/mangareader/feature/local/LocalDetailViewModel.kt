package io.github.saonte.mangareader.feature.local

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.saonte.mangareader.data.repo.ChapterRepository
import io.github.saonte.mangareader.data.repo.ComicRepository
import io.github.saonte.mangareader.model.Chapter
import io.github.saonte.mangareader.model.Comic
import io.github.saonte.mangareader.navigation.LocalDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    comics: ComicRepository,
    chapters: ChapterRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<LocalDetailRoute>()

    private val _comic = MutableStateFlow<Comic?>(null)
    val comic: StateFlow<Comic?> = _comic

    val chapters: StateFlow<List<Chapter>> = chapters.observe(route.comicId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch { _comic.value = comics.findById(route.comicId) }
    }
}
