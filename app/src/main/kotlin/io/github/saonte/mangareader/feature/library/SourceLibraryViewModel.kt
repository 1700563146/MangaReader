package io.github.saonte.mangareader.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.saonte.mangareader.domain.DownloadResult
import io.github.saonte.mangareader.domain.InstallResult
import io.github.saonte.mangareader.domain.SourceLibraryClient
import io.github.saonte.mangareader.domain.SourceManager
import io.github.saonte.mangareader.source.SourceKind
import io.github.saonte.mangareader.source.index.LibraryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val indexUrl: String = "",
    val loading: Boolean = false,
    val entries: List<LibraryEntry> = emptyList(),
    val warnings: List<String> = emptyList(),
    val message: String? = null,
    val installingId: String? = null,
    val installedIds: Set<String> = emptySet(),
)

/**
 * 源库浏览：用户填索引地址 → 拉取列表 → 选择安装。
 *
 * 合规：不预置任何索引 URL；不自动安装；不展示「推荐」排序。
 */
@HiltViewModel
class SourceLibraryViewModel @Inject constructor(
    private val client: SourceLibraryClient,
    private val sourceManager: SourceManager,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun onIndexUrlChange(value: String) {
        _state.update { it.copy(indexUrl = value) }
    }

    fun load() {
        val url = _state.value.indexUrl.trim()
        if (url.isEmpty()) {
            _state.update { it.copy(message = "请先填写索引地址") }
            return
        }
        _state.update { it.copy(loading = true, message = null, warnings = emptyList()) }
        viewModelScope.launch {
            val result = client.fetch(url)
            _state.update {
                it.copy(
                    loading = false,
                    entries = result.entries,
                    warnings = result.warnings,
                    message = result.error
                        ?: if (result.entries.isEmpty()) "索引里没有可用条目" else null,
                )
            }
        }
    }

    fun install(entry: LibraryEntry) {
        if (_state.value.installingId != null) return
        _state.update { it.copy(installingId = entry.id, message = null) }
        viewModelScope.launch {
            val download = client.download(entry)
            when (download) {
                is DownloadResult.Failure -> _state.update {
                    it.copy(installingId = null, message = download.message)
                }

                is DownloadResult.Success -> {
                    val result = when (download.kind) {
                        SourceKind.RULE -> sourceManager.install(download.raw, entry.url)
                        SourceKind.CJS -> sourceManager.installCjs(
                            sourceId = entry.id,
                            name = entry.name,
                            version = entry.version,
                            script = download.raw,
                            origin = entry.url,
                        )
                    }
                    when (result) {
                        is InstallResult.Ok -> _state.update {
                            it.copy(
                                installingId = null,
                                installedIds = it.installedIds + entry.id,
                                message = when (download.kind) {
                                    SourceKind.CJS ->
                                        "已登记「${entry.name}」，但 CJS 源需要 ES2020 引擎，当前版本无法运行（已保存，未启用）"

                                    SourceKind.RULE -> "已安装：${entry.name}"
                                },
                            )
                        }

                        is InstallResult.Invalid -> _state.update {
                            it.copy(
                                installingId = null,
                                message = "安装失败：" + result.issues.joinToString("；") { issue ->
                                    "${issue.pointer} ${issue.message}"
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /** 仅 Debug：填入本地索引并立即拉取，绕开模拟器输入法对 http 的自动补全。 */
    fun useLocalIndex() {
        _state.update { it.copy(indexUrl = "http://10.0.2.2:8766/index.json") }
        load()
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
}
