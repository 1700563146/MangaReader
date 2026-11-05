package io.github.saonte.mangareader.source

import io.github.saonte.mangareader.source.rule.RuleSpec

/**
 * 源格式。决定用哪个引擎执行、安装时如何校验、UI 如何展示。
 *
 * v1.0 只有 [RULE]（JSON 声明式）；[CJS] 为预留格式，见 docs/adr/ADR-007-cjs-source-engine.md。
 */
enum class SourceKind(val dbValue: Int) {
    /** JSON 声明式规则，可选 Rhino 脚本钩子。 */
    RULE(0),

    /** CJS 模块源（venera 风格 `class X extends ComicSource`），需 ES2020 引擎。 */
    CJS(1),
    ;

    companion object {
        fun fromDb(value: Int): SourceKind = entries.firstOrNull { it.dbValue == value } ?: RULE
    }
}

/**
 * 已安装源的运行时引用。
 *
 * 用密封类而不是「一个 RuleSpec 字段」的原因：CJS 源没有 RuleSpec（没有 baseUrl/hosts/选择器），
 * 强行塞进 RuleSpec 会让字段语义失真。引擎按子类型分派。
 */
sealed interface SourceRef {
    val type: Long
    val kind: SourceKind

    /** 源 id（规则 id / CJS key），用于日志与撤销匹配。 */
    val sourceId: String

    /** 展示名。 */
    val name: String

    /** 版本，用于更新检测与撤销。 */
    val version: String

    /** 请求默认头（含 UA 与规则声明）。 */
    fun headers(): Map<String, String>
}

/** JSON 规则源的引用。 */
data class RuleSourceRef(
    override val type: Long,
    val rule: RuleSpec,
) : SourceRef {
    override val kind: SourceKind get() = SourceKind.RULE
    override val sourceId: String get() = rule.id
    override val name: String get() = rule.name
    override val version: String get() = rule.version
    val baseUrl: String get() = rule.baseUrl

    override fun headers(): Map<String, String> = buildMap {
        put("User-Agent", DEFAULT_USER_AGENT)
        putAll(rule.http.headerMap)
        putAll(rule.headers)
    }

    companion object {
        const val DEFAULT_USER_AGENT =
            "MangaReader/1.0 (+https://github.com/1700563146/MangaReader)"
    }
}

/** CJS 模块源的引用（引擎未就绪时仅用于展示与提示）。 */
data class CjsSourceRef(
    override val type: Long,
    override val sourceId: String,
    override val name: String,
    override val version: String,
    val script: String,
    val originUrl: String? = null,
) : SourceRef {
    override val kind: SourceKind get() = SourceKind.CJS

    override fun headers(): Map<String, String> =
        mapOf("User-Agent" to RuleSourceRef.DEFAULT_USER_AGENT)
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
    val images: List<io.github.saonte.mangareader.model.ImageRef>,
    val lazy: Boolean = false,
    val total: Int = 0,
)

/**
 * 规则引擎入口：非 RULE 类型直接拒绝。
 * 放在扩展里而不是强转，是为了让「引擎不支持该格式」变成可读错误而非 ClassCastException。
 */
fun SourceRef.asRule(): RuleSourceRef = this as? RuleSourceRef
    ?: throw SourceFailure(
        code = io.github.saonte.mangareader.model.ErrorCode.RULE_INVALID,
        message = "当前引擎不支持 ${kind.name} 格式的源，请使用 JSON 规则",
        step = "engine",
    )
