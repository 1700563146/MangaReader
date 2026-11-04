package io.github.saonte.mangareader.feature.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.saonte.mangareader.data.repo.Source
import io.github.saonte.mangareader.domain.InstallResult
import io.github.saonte.mangareader.domain.SourceManager
import io.github.saonte.mangareader.source.rule.RuleIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RulesUiState(
    val sources: List<Source> = emptyList(),
    val issues: List<RuleIssue> = emptyList(),
    val message: String? = null,
    val importing: Boolean = false,
    val showImport: Boolean = false,
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val sourceManager: SourceManager,
) : ViewModel() {

    private val _state = MutableStateFlow(RulesUiState())
    val state: StateFlow<RulesUiState> = _state.asStateFlow()

    private val _revoked = MutableStateFlow<Set<Long>>(emptySet())
    val revoked: StateFlow<Set<Long>> = _revoked

    init {
        viewModelScope.launch {
            sourceManager.observeSources().collect { sources ->
                val revoked = sources.filter { sourceManager.isRevoked(it.type) }.map { it.type }.toSet()
                _revoked.value = revoked
                sources.filter { it.enabled && it.type in revoked }.forEach {
                    sourceManager.setEnabled(it.type, false)
                }
                _state.update { it.copy(sources = sources) }
            }
        }
    }

    fun openImport() {
        _state.update { it.copy(showImport = true, issues = emptyList(), message = null) }
    }

    fun dismissImport() {
        _state.update { it.copy(showImport = false, issues = emptyList()) }
    }

    fun install(raw: String) {
        if (raw.isBlank()) {
            _state.update { it.copy(issues = emptyList(), message = "规则内容不能为空") }
            return
        }
        _state.update { it.copy(importing = true, message = null, issues = emptyList()) }
        viewModelScope.launch {
            when (val result = sourceManager.install(raw)) {
                is InstallResult.Ok -> _state.update {
                    it.copy(
                        importing = false,
                        showImport = false,
                        message = "已安装：${result.name}",
                    )
                }

                is InstallResult.Invalid -> _state.update {
                    it.copy(importing = false, issues = result.issues)
                }
            }
        }
    }

    fun setEnabled(source: Source, enabled: Boolean) {
        viewModelScope.launch { sourceManager.setEnabled(source.type, enabled) }
    }

    fun delete(source: Source) {
        viewModelScope.launch {
            sourceManager.delete(source.type)
            _state.update { it.copy(message = "已删除：${source.title}") }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
}
