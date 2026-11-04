package io.github.saonte.mangareader.source

import io.github.saonte.mangareader.model.ErrorCode
import io.github.saonte.mangareader.source.rule.RuleSpec

/**
 * 源引擎的统一入口。v1 由 JSON 规则驱动，UI 只依赖本接口。
 */
interface SourceEngine {
    suspend fun search(ref: SourceRef, keyword: String, page: Int): SearchPage

    suspend fun detail(ref: SourceRef, cid: String): ComicDetail

    suspend fun chapters(ref: SourceRef, cid: String): List<ChapterInfo>

    suspend fun images(ref: SourceRef, cid: String, chapterPath: String): ImagePage

    suspend fun lazyImage(ref: SourceRef, cid: String, chapterPath: String, index: Int): ImageRef

    suspend fun checkUpdate(ref: SourceRef, cid: String): String?

    suspend fun category(ref: SourceRef, categoryId: String, page: Int): SearchPage
}

data class SourceRef(
    val type: Long,
    val rule: RuleSpec,
) {
    val ruleId: String get() = rule.id
    val baseUrl: String get() = rule.baseUrl

    fun headers(): Map<String, String> = buildMap {
        put("User-Agent", DEFAULT_USER_AGENT)
        putAll(rule.http.headerMap)
        putAll(rule.headers)
    }

    companion object {
        const val DEFAULT_USER_AGENT = "MangaReader/0.1 (+https://github.com/1700563146/MangaReader)"
    }
}

data class SearchPage(
    val items: List<ComicSummary>,
    val hasNext: Boolean,
)

data class ComicSummary(
    val cid: String,
    val title: String,
    val cover: String = "",
    val author: String? = null,
    val update: String? = null,
    val finish: Boolean? = null,
    val url: String? = null,
)

data class ComicDetail(
    val summary: ComicSummary,
    val intro: String? = null,
    val chapters: List<ChapterInfo> = emptyList(),
)

data class ChapterInfo(
    val title: String,
    val path: String,
    val group: String = "",
)

data class ImagePage(
    val images: List<ImageRef>,
    val lazy: Boolean = false,
    val total: Int = images.size,
)

data class ImageRef(
    val url: String,
    val urls: List<String> = emptyList(),
    val lazy: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * 引擎内部统一异常：带错误码、失败步骤与规则位置，UI 据此选择文案与恢复动作。
 */
class SourceFailure(
    val code: ErrorCode,
    message: String,
    val step: String? = null,
    val pointer: String? = null,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 域名白名单匹配：`*.example.com` 匹配子域但不匹配裸域，`example.com` 精确匹配。
 */
object HostMatcher {
    fun matches(host: String, allowed: Collection<String>): Boolean {
        val normalized = host.trim().lowercase().trimEnd('.')
        if (normalized.isEmpty()) return false
        return allowed.any { raw -> matchesOne(normalized, raw) }
    }

    private fun matchesOne(host: String, raw: String): Boolean {
        val pattern = raw.trim().lowercase().trimEnd('.')
        if (pattern.isEmpty()) return false
        return if (pattern.startsWith("*.")) {
            val suffix = pattern.removePrefix("*")
            host.endsWith(suffix) && host.length > suffix.length
        } else {
            host == pattern
        }
    }
}
