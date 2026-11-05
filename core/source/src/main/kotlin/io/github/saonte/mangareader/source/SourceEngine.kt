package io.github.saonte.mangareader.source

import io.github.saonte.mangareader.model.ErrorCode
import io.github.saonte.mangareader.model.ImageRef

/**
 * 源引擎的统一入口。UI 只依赖本接口，具体格式由 [SourceRef] 子类型决定。
 *
 * 实现方按 `ref.kind` 分派；遇到不支持的格式应抛 [SourceFailure]（code = [ErrorCode.RULE_INVALID]），
 * 而不是静默返回空结果，否则 UI 无法区分「没结果」与「引擎不支持」。
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
