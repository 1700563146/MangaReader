package io.github.saonte.mangareader.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.saonte.mangareader.data.repo.Source
import io.github.saonte.mangareader.domain.SearchCoordinator
import io.github.saonte.mangareader.domain.SearchOutcome
import io.github.saonte.mangareader.domain.userMessage
import io.github.saonte.mangareader.source.ComicSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SourceResult(
    val source: Source,
    val items: List<ComicSummary> = emptyList(),
    val failure: String? = null,
    val loading: Boolean = true,
    val page: Int = 1,
    val hasNext: Boolean = false,
    val loadingMore: Boolean = false,
)

data class SearchUiState(
    val keyword: String = "",
    val searching: Boolean = false,
    val results: List<SourceResult> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val coordinator: SearchCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onKeywordChange(value: String) {
        _state.update { it.copy(keyword = value) }
    }

    fun search(keyword: String = _state.value.keyword) {
        val query = keyword.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        _state.value = SearchUiState(keyword = query, searching = true)
        searchJob = viewModelScope.launch {
            coordinator.search(query).collect { outcome ->
                when (outcome) {
                    is SearchOutcome.Success -> updateResult(outcome.source) {
                        it.copy(
                            items = outcome.page.items,
                            failure = null,
                            loading = false,
                            page = 1,
                            hasNext = outcome.page.hasNext,
                        )
                    }

                    is SearchOutcome.Failure -> updateResult(outcome.source) {
                        it.copy(failure = outcome.message, loading = false)
                    }

                    SearchOutcome.NoSources -> _state.update {
                        it.copy(message = "尚未导入或启用任何源", searching = false)
                    }

                    SearchOutcome.Finished -> _state.update { it.copy(searching = false) }
                }
            }
        }
    }

    fun retry(source: Source) {
        search(_state.value.keyword)
    }

    fun loadMore(source: Source) {
        val current = _state.value.results.firstOrNull { it.source.type == source.type } ?: return
        if (current.loadingMore || !current.hasNext || current.loading) return
        val nextPage = current.page + 1
        updateResult(source) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            coordinator.searchPage(source, _state.value.keyword, nextPage)
                .onSuccess { page ->
                    updateResult(source) { result ->
                        val existing = result.items.map { it.cid }.toSet()
                        val merged = result.items + page.items.filter { it.cid !in existing }
                        result.copy(
                            items = merged,
                            page = nextPage,
                            hasNext = page.hasNext,
                            loadingMore = false,
                            failure = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    updateResult(source) { it.copy(loadingMore = false) }
                    _state.update {
                        it.copy(message = (throwable as? io.github.saonte.mangareader.source.SourceFailure)?.userMessage()
                            ?: throwable.message ?: "加载更多失败")
                    }
                }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun updateResult(source: Source, block: (SourceResult) -> SourceResult) {
        _state.update { current ->
            val existing = current.results.firstOrNull { it.source.type == source.type }
            val updated = if (existing == null) {
                listOf(block(SourceResult(source = source))) + current.results
            } else {
                current.results.map { if (it.source.type == source.type) block(it) else it }
            }
            current.copy(results = updated)
        }
    }
}
