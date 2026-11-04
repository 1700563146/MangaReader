package io.github.saonte.mangareader.source.rule

import io.github.saonte.mangareader.model.SourceRule

enum class Capability {
    SEARCH,
    DETAIL,
    CHAPTERS,
    IMAGES,
    CHECK,
    CATEGORY,
    ;

    companion object {
        fun from(value: String): Capability? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

enum class Expect { HTML, JSON, TEXT, BYTES }

enum class HttpCache { DEFAULT, NO_STORE, ONLY_IF_CACHED }

data class RateLimitSpec(val rps: Double = 2.0, val burst: Int = 4)

data class HttpSpec(
    val timeoutMs: Int = 15_000,
    val retry: Int = 2,
    val followRedirects: Boolean = true,
    val rateLimit: RateLimitSpec = RateLimitSpec(),
    val headerMap: Map<String, String> = emptyMap(),
)

data class RequestSpec(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
    val charset: String = "utf-8",
    val expect: Expect = Expect.HTML,
    val redirects: Int = 5,
    val cache: HttpCache = HttpCache.DEFAULT,
)

data class PagingSpec(
    val hasNext: SelectSpec? = null,
    val maxPage: Int = 100,
)

data class SearchSpec(
    val request: RequestSpec,
    val list: ListSpec,
    val fields: Map<String, SelectSpec>,
    val paging: PagingSpec = PagingSpec(),
    val allowEmpty: Boolean = false,
)

data class DetailSpec(
    val request: RequestSpec,
    val root: String? = null,
    val fields: Map<String, SelectSpec>,
)

data class ChaptersSpec(
    val request: RequestSpec,
    val list: ListSpec,
    val fields: Map<String, SelectSpec>,
    val reverse: Boolean = false,
    val dedupe: Boolean = true,
    val max: Int = 5000,
)

data class LazySpec(
    val enabled: Boolean = false,
    val total: SelectSpec? = null,
    val request: RequestSpec,
    val value: SelectSpec,
    val batch: Int = 1,
)

data class ImagesSpec(
    val request: RequestSpec,
    val expect: Expect? = null,
    val list: ListSpec? = null,
    val fields: Map<String, SelectSpec> = emptyMap(),
    val multi: Boolean = false,
    val order: String = "asc",
    val headers: Map<String, String> = emptyMap(),
    val lazy: LazySpec? = null,
)

data class CheckSpec(
    val request: RequestSpec? = null,
    val value: SelectSpec,
)

data class CategoryItem(
    val id: String,
    val title: String,
    val path: String? = null,
    val url: String? = null,
)

data class CategorySpec(
    val items: List<CategoryItem>,
    val request: RequestSpec,
    val list: ListSpec,
    val fields: Map<String, SelectSpec>,
    val paging: PagingSpec = PagingSpec(),
)

data class ConfigItemSpec(
    val type: String = "string",
    val label: String,
    val default: String? = null,
    val description: String? = null,
)

/**
 * 运行时规则模型。解析/校验由 [RuleCompiler] 完成；引擎只接受校验通过的 [RuleSpec]。
 */
data class RuleSpec(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val minEngine: Int,
    val baseUrl: String,
    val hosts: List<String>,
    val imageHosts: List<String>,
    val allowCleartext: Boolean,
    val adult: Boolean,
    val lang: String,
    val author: String?,
    val description: String?,
    val homepage: String?,
    val headers: Map<String, String>,
    val http: HttpSpec,
    val capabilities: Set<Capability>,
    val search: SearchSpec?,
    val detail: DetailSpec?,
    val chapters: ChaptersSpec?,
    val images: ImagesSpec?,
    val check: CheckSpec?,
    val category: CategorySpec?,
    val scripts: Map<String, String>,
    val config: Map<String, ConfigItemSpec>,
) {
    val baseHost: String? get() = runCatching { java.net.URI(baseUrl).host }.getOrNull()

    fun summary(): SourceRule = SourceRule(
        schemaVersion = schemaVersion,
        id = id,
        name = name,
        version = version,
        minEngine = minEngine,
        baseUrl = baseUrl,
        hosts = hosts,
        imageHosts = imageHosts,
        allowCleartext = allowCleartext,
        adult = adult,
        lang = lang,
        capabilities = capabilities.map { it.name.lowercase() }.toSet(),
    )

    companion object {
        const val ENGINE_VERSION = 1
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

enum class IssueSeverity { ERROR, WARNING }

data class RuleIssue(
    val severity: IssueSeverity,
    val pointer: String,
    val message: String,
    val hint: String? = null,
)

sealed interface RuleCompileResult {
    data class Success(val rule: RuleSpec, val issues: List<RuleIssue> = emptyList()) : RuleCompileResult
    data class Failure(val issues: List<RuleIssue>) : RuleCompileResult
}

fun RuleCompileResult.errorMessages(): String =
    when (this) {
        is RuleCompileResult.Success -> ""
        is RuleCompileResult.Failure -> issues.joinToString("; ") { "${it.pointer}: ${it.message}" }
    }

fun RuleSpec.requiresEncryption(): Boolean = scripts.values.any { it.contains("aesDecrypt") }
