package io.github.saonte.mangareader.model

import kotlinx.serialization.Serializable

/**
 * 一部漫画（某源的某条记录）。
 *
 * `id` 是本地自增主键，`source + cid` 才是源内唯一标识；
 * 本地漫画使用 `source = 0`，`cid` 为相对路径的稳定哈希。
 */
@Serializable
data class Comic(
    val id: Long = 0,
    val source: Long = 0,
    val cid: String,
    val title: String,
    val cover: String = "",
    val author: String? = null,
    val intro: String? = null,
    val updateText: String? = null,
    val updateAt: Long? = null,
    val finish: Boolean? = null,
    val highlight: Boolean = false,
    val local: Boolean = false,
    val favoriteAt: Long? = null,
    val historyAt: Long? = null,
    val downloadedAt: Long? = null,
    val lastChapterPath: String? = null,
    val lastChapterTitle: String? = null,
    val lastChapterIndex: Int = 0,
    val lastPage: Int = 0,
    val chapterCount: Int = 0,
    val url: String? = null,
)

@Serializable
data class Chapter(
    val id: Long = 0,
    val comicId: Long = 0,
    val title: String,
    val path: String,
    val sourceGroup: String = "",
    val count: Int = 0,
    val complete: Boolean = false,
    val downloaded: Boolean = false,
    val lastPage: Int = 0,
    val readAt: Long? = null,
    val sizeBytes: Long = 0,
)

/**
 * 图片引用。`urls` 为同页备用地址，`lazy` 表示需要二次解析。
 */
@Serializable
data class ImageRef(
    val url: String,
    val urls: List<String> = emptyList(),
    val lazy: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)

enum class ReadMode {
    PAGED_LTR,
    PAGED_RTL,
    PAGED_TTB,
    WEBTOON,
}

enum class SourceHealth {
    HEALTHY,
    DEGRADED,
    DISABLED,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 源规则的强类型最小模型。v1 完整规范见 docs/05-source-rule-spec.md，
 * 解析/校验在 :core:source 中实现，本类只承载引擎需要的字段。
 */
@Serializable
data class SourceRule(
    val schemaVersion: Int = SCHEMA_VERSION,
    val id: String,
    val name: String,
    val version: String,
    val minEngine: Int = ENGINE_VERSION,
    val baseUrl: String,
    val hosts: List<String>,
    val imageHosts: List<String> = emptyList(),
    val allowCleartext: Boolean = false,
    val adult: Boolean = false,
    val lang: String = "zh",
    val capabilities: Set<String> = emptySet(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val ENGINE_VERSION = 1
    }
}

/**
 * 统一错误模型：UI 通过 [AppError] 选择文案与恢复动作，禁止把原始异常直接抛到界面。
 */
sealed interface AppError {
    val code: ErrorCode
    val retryable: Boolean

    data class Network(
        override val code: ErrorCode,
        val host: String? = null,
        val detail: String? = null,
        override val retryable: Boolean = true,
    ) : AppError

    data class Rule(
        val pointer: String?,
        val reason: String,
        override val code: ErrorCode = ErrorCode.RULE_INVALID,
        override val retryable: Boolean = false,
    ) : AppError

    data class Script(
        val line: Int? = null,
        val reason: String,
        override val code: ErrorCode = ErrorCode.SCRIPT_ERROR,
        override val retryable: Boolean = false,
    ) : AppError

    data class Image(
        val host: String? = null,
        val reason: String,
        override val code: ErrorCode = ErrorCode.IMAGE_FETCH_FAILED,
        override val retryable: Boolean = true,
    ) : AppError

    data class Storage(
        val reason: String,
        override val code: ErrorCode = ErrorCode.STORAGE_FULL,
        override val retryable: Boolean = false,
    ) : AppError

    data class Unknown(
        val reason: String,
        override val code: ErrorCode = ErrorCode.UNKNOWN,
        override val retryable: Boolean = false,
    ) : AppError
}

enum class ErrorCode {
    NET_TIMEOUT,
    NET_DNS,
    NET_TLS,
    HTTP_401,
    HTTP_403,
    HTTP_404,
    HTTP_429,
    HTTP_5XX,
    RESPONSE_TOO_LARGE,
    RULE_INVALID,
    RULE_UNSUPPORTED,
    RULE_REVOKED,
    SELECTOR_EMPTY,
    JSON_PATH_MISSING,
    SCRIPT_TIMEOUT,
    SCRIPT_ERROR,
    IMAGE_FETCH_FAILED,
    IMAGE_DECODE,
    STORAGE_FULL,
    PERMISSION_DENIED,
    CANCELLED,
    UNKNOWN,
}

/** 点击区动作（F-405）：左/中/右三区可独立配置。 */
enum class TapAction {
    PREV,
    NEXT,
    MENU,
}

data class TapConfig(
    val left: TapAction = TapAction.PREV,
    val center: TapAction = TapAction.MENU,
    val right: TapAction = TapAction.NEXT,
) {
    fun actionFor(zone: String): TapAction = when (zone) {
        "LEFT" -> left
        "RIGHT" -> right
        else -> center
    }

    companion object {
        val DEFAULT = TapConfig()
    }
}
