package io.github.saonte.mangareader.domain

import io.github.saonte.mangareader.BuildConfig
import io.github.saonte.mangareader.source.SourceKind
import io.github.saonte.mangareader.source.http.SourceHttpClient
import io.github.saonte.mangareader.source.index.LibraryEntry
import io.github.saonte.mangareader.source.index.SourceLibraryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class LibraryFetchResult(
    val entries: List<LibraryEntry> = emptyList(),
    val warnings: List<String> = emptyList(),
    val error: String? = null,
)

/**
 * 拉取并解析第三方源库索引。
 *
 * 合规边界（docs/10）：本类**不内置任何索引地址**，地址完全由用户输入；
 * 应用只做「按用户给的地址下载并展示列表」，不代为选择、不推荐具体站点。
 */
@Singleton
class SourceLibraryClient @Inject constructor(
    private val http: SourceHttpClient,
) {

    suspend fun fetch(indexUrl: String): LibraryFetchResult = withContext(Dispatchers.IO) {
        val url = indexUrl.trim()
        if (url.isEmpty()) {
            return@withContext LibraryFetchResult(error = "请填写索引地址")
        }
        if (!isAllowedScheme(url)) {
            return@withContext LibraryFetchResult(error = "索引地址必须为 https")
        }
        val raw = runCatching { http.fetchRuleText(url, MAX_INDEX_BYTES, allowInsecureLoopback = BuildConfig.DEBUG) }
            .getOrElse { error ->
                return@withContext LibraryFetchResult(
                    error = "拉取索引失败：${error.message ?: "网络错误"}",
                )
            }
        val parsed = SourceLibraryParser.parse(raw, url)
        LibraryFetchResult(entries = parsed.entries, warnings = parsed.warnings)
    }

    /**
     * 下载单个源文件并判断其真实格式。
     *
     * 判断依据是内容而不是扩展名：venera 源以 `class X extends ComicSource` 开头，
     * JSON 规则以 `{` 开头。这样即使索引写错扩展名也能正确分流。
     */
    suspend fun download(entry: LibraryEntry): DownloadResult = withContext(Dispatchers.IO) {
        val raw = runCatching { http.fetchRuleText(entry.url, MAX_SOURCE_BYTES, allowInsecureLoopback = BuildConfig.DEBUG) }
            .getOrElse { error ->
                return@withContext DownloadResult.Failure(
                    "下载失败：${error.message ?: "网络错误"}",
                )
            }
        if (entry.sha256 != null) {
            val actual = io.github.saonte.mangareader.source.index.RuleContentVerifier.sha256(
                raw.toByteArray(),
            )
            if (!actual.equals(entry.sha256, ignoreCase = true)) {
                return@withContext DownloadResult.Failure("校验失败：源文件内容与索引不一致")
            }
        }
        val detected = detectKind(raw)
        DownloadResult.Success(raw, detected)
    }

    /**
     * 索引地址协议校验。
     *
     * Release 严格只允许 https。Debug 额外放行「本机/模拟器回环地址的 http」，
     * 仅用于本地端到端验证（模拟器无法访问外网 jsDelivr、系统分区只读装不了 CA）。
     * 这不是安全放宽：私有地址在真机上不可被外部利用，且该分支在 release 中不存在。
     */
    private fun isAllowedScheme(url: String): Boolean {
        if (url.startsWith("https://")) return true
        if (!BuildConfig.DEBUG) return false
        if (!url.startsWith("http://")) return false
        val host = url.removePrefix("http://").substringBefore('/').substringBefore(':')
        return host in DEBUG_HTTP_HOSTS
    }

    companion object {
        private val DEBUG_HTTP_HOSTS = setOf("127.0.0.1", "10.0.2.2", "localhost")
        private const val MAX_INDEX_BYTES = 2L * 1024 * 1024
        private const val MAX_SOURCE_BYTES = 2L * 1024 * 1024

        /** 根据内容判断源格式；CJS 的特征是 class 声明或 module.exports。 */
        fun detectKind(raw: String): SourceKind {
            val head = raw.take(4096)
            val looksJs = head.contains("extends ComicSource") ||
                head.contains("module.exports") ||
                Regex("""\bclass\s+\w+""").containsMatchIn(head)
            return if (looksJs) SourceKind.CJS else SourceKind.RULE
        }
    }
}

sealed interface DownloadResult {
    data class Success(val raw: String, val kind: SourceKind) : DownloadResult
    data class Failure(val message: String) : DownloadResult
}
