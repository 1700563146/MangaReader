package io.github.saonte.mangareader.source.rule

import io.github.saonte.mangareader.model.ErrorCode
import io.github.saonte.mangareader.source.ChapterInfo
import io.github.saonte.mangareader.source.ComicDetail
import io.github.saonte.mangareader.source.ComicSummary
import io.github.saonte.mangareader.model.ImageRef
import io.github.saonte.mangareader.source.ImagePage
import io.github.saonte.mangareader.source.SearchPage
import io.github.saonte.mangareader.source.SourceEngine
import io.github.saonte.mangareader.source.SourceFailure
import io.github.saonte.mangareader.source.SourceRef
import io.github.saonte.mangareader.source.http.HttpResponse
import io.github.saonte.mangareader.source.http.SourceHttpClient
import io.github.saonte.mangareader.source.script.RhinoScriptRuntime
import io.github.saonte.mangareader.source.script.ScriptCall
import io.github.saonte.mangareader.source.script.ScriptRuntime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document

/**
 * 声明式优先的规则引擎：能力块由选择器/JSONPath 解析，遇到 scripts 钩子时交给沙箱执行。
 */
class JsonRuleEngine(
    private val http: SourceHttpClient = SourceHttpClient(SourceHttpClient.defaultClient()),
    private val scripts: ScriptRuntime = RhinoScriptRuntime(),
) : SourceEngine {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(ref: SourceRef, keyword: String, page: Int): SearchPage {
        val spec = ref.rule.search ?: throw SourceFailure(
            ErrorCode.RULE_INVALID, "规则未实现 search", step = "search", pointer = "/search",
        )
        val vars = baseVars(ref) + mapOf("keyword" to keyword, "page" to page.toString())
        val response = http.execute(ref, spec.request, vars)

        ref.rule.scripts["search"]?.let { source ->
            return searchByScript(ref, source, vars, response)
        }

        val document = documentOf(response)
        val pageUrl = response.url
        val nodes = SelectorEngine.selectNodes(document, null, spec.list)
        val items = nodes.mapNotNull { node ->
            val cid = field(document, node, spec.fields["cid"], pageUrl, vars)
            val title = field(document, node, spec.fields["title"], pageUrl, vars)
            if (cid.isNullOrBlank() || title.isNullOrBlank()) return@mapNotNull null
            ComicSummary(
                cid = cid,
                title = title,
                cover = field(document, node, spec.fields["cover"], pageUrl, vars).orEmpty(),
                author = field(document, node, spec.fields["author"], pageUrl, vars),
                update = field(document, node, spec.fields["update"], pageUrl, vars),
                finish = field(document, node, spec.fields["finish"], pageUrl, vars)?.toBooleanStrictOrNull(),
                url = field(document, node, spec.fields["url"], pageUrl, vars),
            )
        }.distinctBy { it.cid }

        if (items.isEmpty() && !spec.allowEmpty) {
            throw SourceFailure(
                ErrorCode.SELECTOR_EMPTY, "搜索结果为空，页面结构可能已变化",
                step = "search", pointer = "/search/fields",
            )
        }
        val hasNext = spec.paging.hasNext?.let { field(document, null, it, pageUrl, vars) }
            ?.let { it.isNotBlank() && !it.equals("false", true) }
            ?: (nodes.size >= spec.list.limit)
        return SearchPage(items, hasNext && page < spec.paging.maxPage)
    }

    override suspend fun detail(ref: SourceRef, cid: String): ComicDetail {
        val spec = ref.rule.detail ?: throw SourceFailure(
            ErrorCode.RULE_INVALID, "规则未实现 detail", step = "detail", pointer = "/detail",
        )
        val vars = baseVars(ref) + mapOf("cid" to cid)
        val response = http.execute(ref, spec.request, vars)
        val document = documentOf(response)
        val root = spec.root?.let { runCatching { document.selectFirst(it) }.getOrNull() }
        val pageUrl = response.url

        val scriptFields = ref.rule.scripts["detail"]?.let { source ->
            scriptObject(ref, source, vars, pageUrl, response)
        }.orEmpty()

        fun value(key: String): String? =
            scriptFields[key] ?: field(document, root, spec.fields[key], pageUrl, vars)

        val title = value("title")
        if (title.isNullOrBlank()) {
            throw SourceFailure(
                ErrorCode.SELECTOR_EMPTY, "详情标题为空", step = "detail", pointer = "/detail/fields/title",
            )
        }
        val summary = ComicSummary(
            cid = cid,
            title = title,
            cover = value("cover").orEmpty(),
            author = value("author"),
            update = value("update"),
            finish = value("finish")?.toBooleanStrictOrNull(),
            url = value("url") ?: pageUrl,
        )
        val chapters = chaptersInternal(ref, cid, response, document, vars)
        return ComicDetail(summary = summary, intro = value("intro"), chapters = chapters)
    }

    override suspend fun chapters(ref: SourceRef, cid: String): List<ChapterInfo> {
        val spec = ref.rule.chapters
        val request = spec?.request ?: ref.rule.detail?.request
        val response = request?.let { http.execute(ref, it, baseVars(ref) + mapOf("cid" to cid)) }
        val document = response?.let { documentOf(it) }
        return chaptersInternal(ref, cid, response, document, baseVars(ref) + mapOf("cid" to cid))
    }

    private fun chaptersInternal(
        ref: SourceRef,
        cid: String,
        detailResponse: HttpResponse?,
        detailDocument: Document?,
        vars: Map<String, String>,
    ): List<ChapterInfo> {
        if (detailResponse != null) {
            ref.rule.scripts["chapters"]?.let { source ->
                return chaptersByScript(ref, source, vars, detailResponse)
            }
        }
        val spec = ref.rule.chapters ?: return emptyList()
        val response = detailResponse ?: return emptyList()
        val document = detailDocument ?: documentOf(response)
        val pageUrl = response.url
        val nodes = SelectorEngine.selectNodes(document, null, spec.list)

        var list = nodes.mapNotNull { node ->
            val title = field(document, node, spec.fields["title"], pageUrl, vars)
            val path = field(document, node, spec.fields["path"], pageUrl, vars)
            if (title.isNullOrBlank() || path.isNullOrBlank()) return@mapNotNull null
            ChapterInfo(
                title = title,
                path = path,
                group = field(document, node, spec.fields["group"], pageUrl, vars).orEmpty(),
            )
        }
        if (spec.reverse) list = list.reversed()
        if (spec.dedupe) list = list.distinctBy { it.path }
        return list.take(spec.max)
    }

    override suspend fun images(ref: SourceRef, cid: String, chapterPath: String): ImagePage {
        val spec = ref.rule.images ?: throw SourceFailure(
            ErrorCode.RULE_INVALID, "规则未实现 images", step = "images", pointer = "/images",
        )
        val vars = baseVars(ref) + mapOf("cid" to cid, "chapterPath" to chapterPath)
        val lazy = spec.lazy
        if (lazy?.enabled == true) {
            val total = lazy.total?.let { selector ->
                val probe = http.execute(ref, lazy.request, vars + mapOf("index" to "0", "page" to "1"))
                field(documentOf(probe), null, selector, probe.url, vars)?.toIntOrNull()
            } ?: 0
            return ImagePage(images = emptyList(), lazy = true, total = total)
        }

        val response = http.execute(ref, spec.request, vars)
        val pageUrl = response.url

        ref.rule.scripts["images"]?.let { source ->
            val array = scriptArray(ref, source, vars + ("url" to pageUrl), pageUrl, response)
            return ImagePage(images = array.mapNotNull { element -> element.toImageRef(pageUrl) })
        }

        val document = documentOf(response)
        val list = spec.list ?: throw SourceFailure(
            ErrorCode.RULE_INVALID, "images 缺少 list", step = "images", pointer = "/images/list",
        )
        val nodes = SelectorEngine.selectNodes(document, null, list)
        var refs = nodes.mapNotNull { node ->
            val url = field(document, node, spec.fields["url"], pageUrl, vars)
            if (url.isNullOrBlank()) return@mapNotNull null
            val extra = field(document, node, spec.fields["urls"], pageUrl, vars)
                ?.split(',', ' ', '\n', '\t')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
            ImageRef(
                url = url,
                urls = extra,
                headers = imageHeaders(spec.headers, vars, pageUrl),
            )
        }
        if (spec.order == "desc") refs = refs.reversed()
        return ImagePage(images = refs, lazy = false, total = refs.size)
    }

    override suspend fun lazyImage(ref: SourceRef, cid: String, chapterPath: String, index: Int): ImageRef {
        val lazy = ref.rule.images?.lazy ?: throw SourceFailure(
            ErrorCode.RULE_INVALID, "规则未启用惰性图片", step = "lazy", pointer = "/images/lazy",
        )
        val vars = baseVars(ref) + mapOf(
            "cid" to cid,
            "chapterPath" to chapterPath,
            "index" to index.toString(),
            "page" to (index + 1).toString(),
        )
        val response = http.execute(ref, lazy.request, vars)
        val url = when (lazy.request.expect) {
            Expect.JSON -> {
                val root = runCatching { json.parseToJsonElement(response.body) }.getOrElse {
                    throw SourceFailure(ErrorCode.JSON_PATH_MISSING, "惰性响应不是合法 JSON", step = "lazy")
                }
                SelectorEngine.extractJson(root, lazy.value, response.url)
            }
            else -> field(documentOf(response), null, lazy.value, response.url, vars)
        } ?: throw SourceFailure(
            ErrorCode.SELECTOR_EMPTY, "惰性图片地址为空", step = "lazy", pointer = "/images/lazy/value",
        )
        return ImageRef(url = url, lazy = true, headers = imageHeaders(ref.rule.images.headers, vars, chapterPath))
    }

    override suspend fun checkUpdate(ref: SourceRef, cid: String): String? {
        val spec = ref.rule.check ?: return null
        val request = spec.request ?: ref.rule.detail?.request ?: return null
        val vars = baseVars(ref) + mapOf("cid" to cid)
        val response = http.execute(ref, request, vars)
        val value = when (request.expect) {
            Expect.JSON -> SelectorEngine.extractJson(json.parseToJsonElement(response.body), spec.value, response.url)
            else -> field(documentOf(response), null, spec.value, response.url, vars)
        }
        return value?.trim()?.ifBlank { null }
    }

    override suspend fun category(ref: SourceRef, categoryId: String, page: Int): SearchPage {
        val spec = ref.rule.category ?: throw SourceFailure(
            ErrorCode.RULE_INVALID, "规则未实现 category", step = "category", pointer = "/category",
        )
        val item = spec.items.firstOrNull { it.id == categoryId } ?: throw SourceFailure(
            ErrorCode.RULE_INVALID, "未知分类：$categoryId", step = "category", pointer = "/category/items",
        )
        val vars = baseVars(ref) + mapOf("page" to page.toString(), "category" to categoryId)
        val path = Template.render(item.path.orEmpty(), vars)
        val url = item.url ?: path
        val request = spec.request
        val response = if (request.url.contains("\${path}") || request.url.contains("\${url}")) {
            http.execute(ref, request, vars + mapOf("path" to path, "url" to url))
        } else {
            http.execute(ref, request.copy(url = url), vars)
        }
        val document = documentOf(response)
        val nodes = SelectorEngine.selectNodes(document, null, spec.list)
        val items = nodes.mapNotNull { node ->
            val cid = field(document, node, spec.fields["cid"], response.url, vars)
            val title = field(document, node, spec.fields["title"], response.url, vars)
            if (cid.isNullOrBlank() || title.isNullOrBlank()) return@mapNotNull null
            ComicSummary(
                cid = cid,
                title = title,
                cover = field(document, node, spec.fields["cover"], response.url, vars).orEmpty(),
                author = field(document, node, spec.fields["author"], response.url, vars),
            )
        }.distinctBy { it.cid }
        val hasNext = spec.paging.hasNext?.let { field(document, null, it, response.url, vars) }
            ?.let { it.isNotBlank() && !it.equals("false", true) }
            ?: false
        return SearchPage(items, hasNext && page < spec.paging.maxPage)
    }

    // ------------------------------------------------------------------ helpers

    private fun baseVars(ref: SourceRef): MutableMap<String, String> = mutableMapOf(
        "base" to ref.baseUrl,
        "url" to ref.baseUrl,
    )

    private fun documentOf(response: HttpResponse): Document = SelectorEngine.parseDocument(response.body)

    private fun field(
        document: Document,
        node: org.jsoup.nodes.Element?,
        spec: SelectSpec?,
        pageUrl: String,
        vars: Map<String, String>,
    ): String? {
        if (spec == null) return null
        val raw = SelectorEngine.extract(document, node, spec, pageUrl)
        return if (spec.const != null) Template.render(raw.orEmpty(), vars).ifBlank { null } else raw
    }

    private fun imageHeaders(
        configured: Map<String, String>,
        vars: Map<String, String>,
        referer: String,
    ): Map<String, String> = buildMap {
        configured.forEach { (k, v) -> put(k, Template.render(v, vars)) }
        putIfAbsent("Referer", referer)
    }

    private fun scriptObject(
        ref: SourceRef,
        source: String,
        vars: Map<String, String>,
        pageUrl: String,
        response: HttpResponse,
    ): Map<String, String>? {
        val result = scriptInvoke(ref, source, vars, pageUrl, response) as? JsonObject ?: return null
        return result.mapValues { (_, value) -> value.asText().orEmpty() }
    }

    private fun scriptArray(
        ref: SourceRef,
        source: String,
        vars: Map<String, String>,
        pageUrl: String,
        response: HttpResponse,
    ): List<JsonElement> =
        (scriptInvoke(ref, source, vars, pageUrl, response) as? JsonArray)?.toList().orEmpty()

    private fun scriptInvoke(
        ref: SourceRef,
        source: String,
        vars: Map<String, String>,
        pageUrl: String,
        response: HttpResponse,
    ): JsonElement? = scripts.invoke(
        ScriptCall(
            ruleId = ref.ruleId,
            hook = "script",
            source = source,
            vars = vars,
            url = pageUrl,
            html = response.body.takeIf { true },
            json = response.body,
            ref = ref,
            httpClient = http,
        ),
    )

    private fun searchByScript(
        ref: SourceRef,
        source: String,
        vars: Map<String, String>,
        response: HttpResponse,
    ): SearchPage {
        val result = scriptInvoke(ref, source, vars, response.url, response)
        val obj = result as? JsonObject ?: return SearchPage(emptyList(), false)
        val items = (obj["items"] as? JsonArray)?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val cid = item["cid"]?.asText() ?: return@mapNotNull null
            val title = item["title"]?.asText() ?: return@mapNotNull null
            ComicSummary(
                cid = cid,
                title = title,
                cover = item["cover"]?.asText().orEmpty(),
                author = item["author"]?.asText(),
                update = item["update"]?.asText(),
                finish = item["finish"]?.asText()?.toBooleanStrictOrNull(),
            )
        }.orEmpty()
        val hasNext = obj["hasNext"]?.asText()?.toBooleanStrictOrNull() ?: false
        return SearchPage(items, hasNext)
    }

    private fun chaptersByScript(
        ref: SourceRef,
        source: String,
        vars: Map<String, String>,
        response: HttpResponse,
    ): List<ChapterInfo> {
        val result = scriptInvoke(ref, source, vars, response.url, response) as? JsonArray ?: return emptyList()
        return result.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = obj["title"]?.asText() ?: return@mapNotNull null
            val path = obj["path"]?.asText() ?: return@mapNotNull null
            ChapterInfo(title, path, obj["group"]?.asText().orEmpty())
        }
    }

    private fun JsonElement.toImageRef(pageUrl: String): ImageRef? {
        val obj = this as? JsonObject ?: return null
        val url = obj["url"]?.asText() ?: return null
        val urls = (obj["urls"] as? JsonArray)?.mapNotNull { it.asText() }.orEmpty()
        return ImageRef(url = url, urls = urls, headers = mapOf("Referer" to pageUrl))
    }

    private fun JsonElement.asText(): String? = when (this) {
        is JsonPrimitive -> contentOrNull ?: content
        else -> toString()
    }

}
