package io.github.saonte.mangareader.feature.rules

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.saonte.mangareader.source.http.SourceHttpClient
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
    @ApplicationContext private val context: Context,
    private val sourceManager: SourceManager,
    private val http: SourceHttpClient,
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

    /** F-206：从 SAF 文件导入（`content://`，限制 1MB）。 */
    fun installFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    require(bytes.size <= MAX_RULE_BYTES) { "规则文件超过 1MB" }
                    bytes.toString(Charsets.UTF_8)
                } ?: error("无法读取文件")
            }.getOrElse { error ->
                _state.update { it.copy(message = "读取失败：${error.message}") }
                return@launch
            }
            install(raw, origin = uri.toString())
        }
    }

    /** F-206：从 HTTPS 地址导入（大小限制、仅 https）。 */
    fun installFromUrl(url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://")) {
            _state.update { it.copy(message = "仅支持 https 规则地址") }
            return
        }
        _state.update { it.copy(importing = true, message = null, issues = emptyList()) }
        viewModelScope.launch {
            val raw = runCatching { http.fetchRuleText(trimmed, MAX_RULE_BYTES.toLong()) }
                .getOrElse { error ->
                    _state.update {
                        it.copy(importing = false, message = "下载失败：${error.message}")
                    }
                    return@launch
                }
            install(raw, origin = trimmed)
        }
    }

    fun install(raw: String, origin: String? = null) {
        if (raw.isBlank()) {
            _state.update { it.copy(issues = emptyList(), message = "规则内容不能为空") }
            return
        }
        _state.update { it.copy(importing = true, message = null, issues = emptyList()) }
        viewModelScope.launch {
            when (val result = sourceManager.install(raw, origin)) {
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

    private companion object {
        const val MAX_RULE_BYTES = 1024 * 1024
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
}