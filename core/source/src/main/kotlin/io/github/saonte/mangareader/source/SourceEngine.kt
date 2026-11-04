package io.github.saonte.mangareader.source

import io.github.saonte.mangareader.model.Chapter
import io.github.saonte.mangareader.model.Comic
import io.github.saonte.mangareader.model.ImageRef
import io.github.saonte.mangareader.model.SourceRule

/**
 * 源引擎的统一入口。v1 由 JSON 规则驱动（[io.github.saonte.mangareader.source.rule]），
 * UI 只依赖本接口，便于后续替换实现或增加内置源（不计划）。
 */
interface SourceEngine {
    suspend fun search(ref: SourceRef, keyword: String, page: Int): SearchPage

    suspend fun detail(ref: SourceRef, cid: String): Comic

    suspend fun chapters(ref: SourceRef, cid: String): List<Chapter>

    suspend fun images(ref: SourceRef, cid: String, chapterPath: String): ImagePage

    suspend fun lazyImage(ref: SourceRef, cid: String, chapterPath: String, index: Int): ImageRef

    suspend fun checkUpdate(ref: SourceRef, cid: String): String?

    suspend fun category(ref: SourceRef, categoryId: String, page: Int): SearchPage
}

data class SourceRef(
    val type: Long,
    val ruleId: String,
    val rule: SourceRule,
    val baseUrl: String = rule.baseUrl,
    val http: HttpOptions = HttpOptions(),
) {
    val headers: Map<String, String> get() = http.headers
}

data class HttpOptions(
    val timeoutMs: Int = 15_000,
    val retry: Int = 2,
    val allowCleartext: Boolean = false,
    val userAgent: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class SearchPage(
    val items: List<Comic>,
    val hasNext: Boolean,
)

data class ImagePage(
    val images: List<ImageRef>,
    val lazy: Boolean = false,
    val total: Int = images.size,
)

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
