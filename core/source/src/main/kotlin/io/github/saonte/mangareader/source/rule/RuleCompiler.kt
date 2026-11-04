package io.github.saonte.mangareader.source.rule

import io.github.saonte.mangareader.source.HostMatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 规则编译器：JSON → [RuleSpec]，并做结构（L1）与语义（L2）校验。
 *
 * 所有错误都带 JSON Pointer（如 `/search/fields/cover/pipeline/0`），供 UI 直接定位。
 * 不执行网络请求；脚本仅做编译检查（由 ScriptRuntime 注入，M1 后续接入）。
 */
class RuleCompiler(
    private val engineVersion: Int = RuleSpec.ENGINE_VERSION,
) {
    fun compile(raw: String): RuleCompileResult {
        val root = runCatching { Json.parseToJsonElement(raw) }
            .getOrElse { return failure("/", "JSON 解析失败：${it.message}") }
        if (root !is JsonObject) return failure("/", "规则根节点必须是对象")
        return Compiler(engineVersion).compile(root)
    }

    private fun failure(pointer: String, message: String) = RuleCompileResult.Failure(
        listOf(RuleIssue(IssueSeverity.ERROR, pointer, message)),
    )

    private class Compiler(private val engineVersion: Int) {
        private val issues = mutableListOf<RuleIssue>()

        fun compile(root: JsonObject): RuleCompileResult {
            val schemaVersion = root.int("schemaVersion", "/schemaVersion", required = true) ?: 1
            val id = root.str("id", "/id", required = true).orEmpty()
            val name = root.str("name", "/name", required = true).orEmpty()
            val version = root.str("version", "/version", required = true).orEmpty()
            val minEngine = root.int("minEngine", "/minEngine") ?: RuleSpec.ENGINE_VERSION
            val baseUrl = root.str("baseUrl", "/baseUrl", required = true).orEmpty().trimEnd('/')
            val hosts = root.strList("hosts", "/hosts", required = true)
            val imageHosts = root.strList("imageHosts", "/imageHosts")
            val allowCleartext = root.bool("allowCleartext", "/allowCleartext") ?: false
            val adult = root.bool("adult", "/adult") ?: false
            val lang = root.str("lang", "/lang") ?: "zh"
            val headers = root.strMap("headers", "/headers")
            val http = root.obj("http", "/http")?.let { parseHttp(it, "/http") } ?: HttpSpec(headerMap = headers)
            val capabilities = parseCapabilities(root, "/capabilities")
            val scripts = root.strMap("scripts", "/scripts")
            val config = root.obj("config", "/config")?.let { parseConfig(it, "/config") }.orEmpty()

            val search = root.obj("search", "/search")?.let { parseSearch(it, "/search", capabilities) }
            val detail = root.obj("detail", "/detail")?.let { parseDetail(it, "/detail", capabilities) }
            val chapters = root.obj("chapters", "/chapters")?.let { parseChapters(it, "/chapters", capabilities) }
            val images = root.obj("images", "/images")?.let { parseImages(it, "/images", capabilities) }
            val check = root.obj("check", "/check")?.let { parseCheck(it, "/check", capabilities) }
            val category = root.obj("category", "/category")?.let { parseCategory(it, "/category", capabilities) }

            validateHeader(root, id, name, version, baseUrl, hosts)
            validateEngine(schemaVersion, minEngine, capabilities, search, detail, chapters, images, check, category, scripts)
            validateScripts(scripts)

            val rule = RuleSpec(
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
                author = root.str("author", "/author"),
                description = root.str("description", "/description"),
                homepage = root.str("homepage", "/homepage"),
                headers = if (headers.isEmpty()) http.headerMap else headers,
                http = http,
                capabilities = capabilities,
                search = search,
                detail = detail,
                chapters = chapters,
                images = images,
                check = check,
                category = category,
                scripts = scripts,
                config = config,
            )

            validateTemplates(rule)
            validateHosts(rule)

            val errors = issues.filter { it.severity == IssueSeverity.ERROR }
            return if (errors.isEmpty()) {
                RuleCompileResult.Success(rule, issues.toList())
            } else {
                RuleCompileResult.Failure(issues.toList())
            }
        }

        // ---------------------------------------------------------------- header

        private fun validateHeader(
            root: JsonObject,
            id: String,
            name: String,
            version: String,
            baseUrl: String,
            hosts: List<String>,
        ) {
            if (!ID_PATTERN.matches(id)) {
                err("/id", "id 不合法：$id", "建议反向域名，如 community.example")
            }
            if (name.isBlank() || name.length > 32) err("/name", "name 长度必须为 1–32")
            if (!VERSION_PATTERN.matches(version)) err("/version", "version 必须为语义化版本，如 1.0.0")
            if (!URL_PATTERN.matches(baseUrl)) err("/baseUrl", "baseUrl 必须是 http(s) 绝对地址")
            if (hosts.isEmpty()) err("/hosts", "hosts 不能为空")
            hosts.forEachIndexed { index, host ->
                if (!HOST_PATTERN.matches(host)) err("/hosts/$index", "host 不合法：$host")
            }
            root.obj("config", "/config")?.keys?.forEach { key ->
                if (!key.matches(CONFIG_KEY_PATTERN)) err("/config/$key", "配置键只能包含字母、数字、下划线")
            }
        }

        private fun validateEngine(
            schemaVersion: Int,
            minEngine: Int,
            capabilities: Set<Capability>,
            search: SearchSpec?,
            detail: DetailSpec?,
            chapters: ChaptersSpec?,
            images: ImagesSpec?,
            check: CheckSpec?,
            category: CategorySpec?,
            scripts: Map<String, String>,
        ) {
            if (schemaVersion > RuleSpec.SUPPORTED_SCHEMA_VERSION) {
                err("/schemaVersion", "规则规范版本 $schemaVersion 高于引擎支持（${RuleSpec.SUPPORTED_SCHEMA_VERSION}），请升级 App")
            }
            if (minEngine > engineVersion) {
                err("/minEngine", "规则要求引擎 ≥ $minEngine，当前引擎 $engineVersion")
            }
            if (capabilities.isEmpty()) err("/capabilities", "至少声明一个能力")

            fun required(capability: Capability, block: Any?, hook: String) {
                if (capability in capabilities && block == null && scripts[hook].isNullOrBlank()) {
                    err("/capabilities", "声明了 ${capability.name.lowercase()}，但缺少对应能力块或 scripts.$hook")
                }
            }
            required(Capability.SEARCH, search, "search")
            required(Capability.DETAIL, detail, "detail")
            required(Capability.CHAPTERS, chapters, "chapters")
            required(Capability.IMAGES, images, "images")
            required(Capability.CHECK, check, "check")
            required(Capability.CATEGORY, category, "category")

            if (Capability.IMAGES in capabilities && images != null) {
                val hasList = images.list != null && images.fields.isNotEmpty()
                val hasLazy = images.lazy?.enabled == true
                if (!hasList && !hasLazy && scripts["images"].isNullOrBlank()) {
                    err("/images", "images 需要 list+fields，或 lazy.enabled=true")
                }
            }
        }

        private fun validateHosts(rule: RuleSpec) {
            val host = rule.baseHost ?: run {
                err("/baseUrl", "无法解析 baseUrl 的 host")
                return
            }
            if (!HostMatcher.matches(host, rule.hosts)) {
                err("/hosts", "baseUrl 的 host（$host）不在 hosts 白名单中")
            }
            if (rule.baseUrl.startsWith("http://") && !rule.allowCleartext) {
                err("/allowCleartext", "baseUrl 使用 http，但未声明 allowCleartext=true")
            }
            rule.imageHosts.forEachIndexed { index, hostPattern ->
                if (!HOST_PATTERN.matches(hostPattern)) err("/imageHosts/$index", "host 不合法：$hostPattern")
            }
        }

        private fun validateTemplates(rule: RuleSpec) {
            checkRequest(rule.search?.request, "/search/request")
            checkRequest(rule.detail?.request, "/detail/request")
            checkRequest(rule.chapters?.request, "/chapters/request")
            checkRequest(rule.images?.request, "/images/request")
            checkRequest(rule.images?.lazy?.request, "/images/lazy/request")
            checkRequest(rule.check?.request, "/check/request")
            checkRequest(rule.category?.request, "/category/request")
        }

        private fun checkRequest(request: RequestSpec?, pointer: String) {
            if (request == null) return
            scanTemplate(request.url, "$pointer/url")
            request.body?.let { scanTemplate(it, "$pointer/body") }
            request.headers.values.forEach { scanTemplate(it, "$pointer/headers") }
        }

        private fun scanTemplate(text: String, pointer: String) {
            TEMPLATE_EXPR.findAll(text).forEach { match ->
                val expr = match.groupValues[1]
                val name = expr.substringBefore('|').trim()
                if (name !in ALLOWED_VARS) {
                    warn(pointer, "未知模板变量：\${$name}", "允许：${ALLOWED_VARS.joinToString(", ")}")
                }
            }
        }

        private fun validateScripts(scripts: Map<String, String>) {
            scripts.forEach { (name, code) ->
                if (name !in SCRIPT_HOOKS) err("/scripts/$name", "未知脚本钩子：$name")
                if (code.length > 65_536) err("/scripts/$name", "脚本超过 64KB")
                if (code.contains("Java.type(") || code.contains("Packages.")) {
                    err("/scripts/$name", "脚本包含被禁止的 Java 互操作")
                }
            }
        }

        // ---------------------------------------------------------------- blocks

        private fun parseHttp(obj: JsonObject, pointer: String): HttpSpec {
            val timeout = obj.int("timeoutMs", "$pointer/timeoutMs") ?: 15_000
            if (timeout !in 1_000..60_000) err("$pointer/timeoutMs", "timeoutMs 必须在 1000–60000")
            val retry = obj.int("retry", "$pointer/retry") ?: 2
            if (retry !in 0..5) err("$pointer/retry", "retry 必须在 0–5")
            val follow = obj.bool("followRedirects", "$pointer/followRedirects") ?: true
            val rate = obj.obj("rateLimit", "$pointer/rateLimit")?.let { rl ->
                val rps = rl.double("rps", "$pointer/rateLimit/rps") ?: 2.0
                if (rps !in 0.1..10.0) err("$pointer/rateLimit/rps", "rps 必须在 0.1–10")
                val burst = rl.int("burst", "$pointer/rateLimit/burst") ?: 4
                if (burst !in 1..20) err("$pointer/rateLimit/burst", "burst 必须在 1–20")
                RateLimitSpec(rps, burst)
            } ?: RateLimitSpec()
            return HttpSpec(timeout, retry, follow, rate)
        }

        private fun parseCapabilities(root: JsonObject, pointer: String): Set<Capability> {
            val array = root.array("capabilities", pointer, required = true) ?: return emptySet()
            val out = mutableSetOf<Capability>()
            array.forEachIndexed { index, element ->
                val value = (element as? JsonPrimitive)?.contentOrNull
                val capability = value?.let { Capability.from(it) }
                if (capability == null) {
                    err("$pointer/$index", "未知能力：${value ?: element.toString()}")
                } else {
                    out += capability
                }
            }
            return out
        }

        private fun parseSearch(obj: JsonObject, pointer: String, capabilities: Set<Capability>): SearchSpec? {
            if (Capability.SEARCH !in capabilities) {
                err(pointer, "未在 capabilities 中声明 search")
            }
            val request = obj.request("request", "$pointer/request") ?: return null
            val list = obj.listSpec("list", "$pointer/list", required = true) ?: return null
            val fields = obj.fields("fields", "$pointer/fields")
            if (fields.isEmpty()) return null
            val paging = obj.obj("paging", "$pointer/paging")?.let { parsePaging(it, "$pointer/paging") } ?: PagingSpec()
            return SearchSpec(
                request = request,
                list = list,
                fields = fields,
                paging = paging,
                allowEmpty = obj.bool("allowEmpty", "$pointer/allowEmpty") ?: false,
            )
        }

        private fun parsePaging(obj: JsonObject, pointer: String): PagingSpec {
            val maxPage = obj.int("maxPage", "$pointer/maxPage") ?: 100
            if (maxPage !in 1..500) err("$pointer/maxPage", "maxPage 必须在 1–500")
            return PagingSpec(
                hasNext = obj.select("hasNext", "$pointer/hasNext"),
                maxPage = maxPage,
            )
        }

        private fun parseDetail(obj: JsonObject, pointer: String, capabilities: Set<Capability>): DetailSpec? {
            if (Capability.DETAIL !in capabilities) err(pointer, "未在 capabilities 中声明 detail")
            val request = obj.request("request", "$pointer/request") ?: return null
            val fields = obj.fields("fields", "$pointer/fields")
            if (fields.isEmpty()) return null
            return DetailSpec(request, obj.str("root", "$pointer/root"), fields)
        }

        private fun parseChapters(obj: JsonObject, pointer: String, capabilities: Set<Capability>): ChaptersSpec? {
            if (Capability.CHAPTERS !in capabilities) err(pointer, "未在 capabilities 中声明 chapters")
            val request = obj.request("request", "$pointer/request") ?: return null
            val list = obj.listSpec("list", "$pointer/list", required = true) ?: return null
            val fields = obj.fields("fields", "$pointer/fields")
            if (fields.isEmpty()) return null
            val max = obj.int("max", "$pointer/max") ?: 5000
            if (max !in 1..5000) err("$pointer/max", "max 必须在 1–5000")
            return ChaptersSpec(
                request = request,
                list = list,
                fields = fields,
                reverse = obj.bool("reverse", "$pointer/reverse") ?: false,
                dedupe = obj.bool("dedupe", "$pointer/dedupe") ?: true,
                max = max,
            )
        }

        private fun parseImages(obj: JsonObject, pointer: String, capabilities: Set<Capability>): ImagesSpec? {
            if (Capability.IMAGES !in capabilities) err(pointer, "未在 capabilities 中声明 images")
            val request = obj.request("request", "$pointer/request") ?: return null
            val expect = obj.str("expect", "$pointer/expect")?.let { parseExpect(it, "$pointer/expect") }
            val list = obj.listSpec("list", "$pointer/list", required = false)
            val fields = obj.fields("fields", "$pointer/fields")
            val lazy = obj.obj("lazy", "$pointer/lazy")?.let { parseLazy(it, "$pointer/lazy") }
            return ImagesSpec(
                request = request,
                expect = expect,
                list = list,
                fields = fields,
                multi = obj.bool("multi", "$pointer/multi") ?: false,
                order = obj.str("order", "$pointer/order") ?: "asc",
                headers = obj.strMap("headers", "$pointer/headers"),
                lazy = lazy,
            )
        }

        private fun parseLazy(obj: JsonObject, pointer: String): LazySpec? {
            val enabled = obj.bool("enabled", "$pointer/enabled") ?: false
            val request = obj.request("request", "$pointer/request") ?: return null
            val value = obj.select("value", "$pointer/value") ?: run {
                err("$pointer/value", "缺少 value")
                return null
            }
            val batch = obj.int("batch", "$pointer/batch") ?: 1
            if (batch !in 1..10) err("$pointer/batch", "batch 必须在 1–10")
            return LazySpec(
                enabled = enabled,
                total = obj.select("total", "$pointer/total"),
                request = request,
                value = value,
                batch = batch,
            )
        }

        private fun parseCheck(obj: JsonObject, pointer: String, capabilities: Set<Capability>): CheckSpec? {
            if (Capability.CHECK !in capabilities) err(pointer, "未在 capabilities 中声明 check")
            val value = obj.select("value", "$pointer/value") ?: run {
                err("$pointer/value", "缺少 value")
                return null
            }
            return CheckSpec(obj.request("request", "$pointer/request"), value)
        }

        private fun parseCategory(obj: JsonObject, pointer: String, capabilities: Set<Capability>): CategorySpec? {
            if (Capability.CATEGORY !in capabilities) err(pointer, "未在 capabilities 中声明 category")
            val itemsArray = obj.array("items", "$pointer/items", required = true) ?: return null
            val items = itemsArray.mapIndexedNotNull { index, element ->
                val itemPointer = "$pointer/items/$index"
                val item = element as? JsonObject ?: run {
                    err(itemPointer, "分类项必须是对象"); return@mapIndexedNotNull null
                }
                val id = item.str("id", "$itemPointer/id", required = true) ?: return@mapIndexedNotNull null
                val title = item.str("title", "$itemPointer/title", required = true) ?: return@mapIndexedNotNull null
                val path = item.str("path", "$itemPointer/path")
                val url = item.str("url", "$itemPointer/url")
                if (path == null && url == null) err(itemPointer, "分类项需要 path 或 url")
                CategoryItem(id, title, path, url)
            }
            val request = obj.request("request", "$pointer/request") ?: return null
            val list = obj.listSpec("list", "$pointer/list", required = true) ?: return null
            val fields = obj.fields("fields", "$pointer/fields")
            if (fields.isEmpty()) return null
            val paging = obj.obj("paging", "$pointer/paging")?.let { parsePaging(it, "$pointer/paging") } ?: PagingSpec()
            return CategorySpec(items, request, list, fields, paging)
        }

        private fun parseConfig(obj: JsonObject, pointer: String): Map<String, ConfigItemSpec> {
            val out = mutableMapOf<String, ConfigItemSpec>()
            obj.forEach { (key, value) ->
                val itemPointer = "$pointer/$key"
                val item = value as? JsonObject ?: run {
                    err(itemPointer, "配置项必须是对象"); return@forEach
                }
                val type = item.str("type", "$itemPointer/type") ?: "string"
                if (type != "string") err("$itemPointer/type", "v1 仅支持 type=string")
                val label = item.str("label", "$itemPointer/label", required = true) ?: return@forEach
                out[key] = ConfigItemSpec(
                    type = type,
                    label = label,
                    default = item.str("default", "$itemPointer/default"),
                    description = item.str("description", "$itemPointer/description"),
                )
            }
            return out
        }

        // ---------------------------------------------------------------- request

        private fun JsonObject.request(key: String, pointer: String): RequestSpec? {
            val obj = obj(key, pointer) ?: run {
                err(pointer, "缺少 request")
                return null
            }
            return parseRequest(obj, pointer)
        }

        private fun parseRequest(obj: JsonObject, pointer: String): RequestSpec? {
            val url = obj.str("url", "$pointer/url", required = true) ?: return null
            val method = obj.str("method", "$pointer/method") ?: "GET"
            val upper = method.uppercase()
            if (upper !in setOf("GET", "POST")) err("$pointer/method", "method 仅支持 GET/POST")
            val redirects = obj.int("redirects", "$pointer/redirects") ?: 5
            if (redirects !in 0..5) err("$pointer/redirects", "redirects 必须在 0–5")
            val expect = obj.str("expect", "$pointer/expect")?.let { parseExpect(it, "$pointer/expect") } ?: Expect.HTML
            val cache = obj.str("cache", "$pointer/cache")?.let { raw ->
                HttpCache.entries.firstOrNull { it.name.replace('_', '-').equals(raw, true) } ?: run {
                    err("$pointer/cache", "未知 cache 策略：$raw")
                    HttpCache.DEFAULT
                }
            } ?: HttpCache.DEFAULT
            return RequestSpec(
                url = url,
                method = upper,
                headers = obj.strMap("headers", "$pointer/headers"),
                body = obj.str("body", "$pointer/body"),
                contentType = obj.str("contentType", "$pointer/contentType"),
                charset = obj.str("charset", "$pointer/charset") ?: "utf-8",
                expect = expect,
                redirects = redirects,
                cache = cache,
            )
        }

        private fun parseExpect(raw: String, pointer: String): Expect {
            return Expect.entries.firstOrNull { it.name.equals(raw, true) } ?: run {
                err(pointer, "未知 expect：$raw（html/json/text/bytes）")
                Expect.HTML
            }
        }

        private fun JsonObject.listSpec(key: String, pointer: String, required: Boolean): ListSpec? {
            val obj = obj(key, pointer) ?: run {
                if (required) err(pointer, "缺少 $key")
                return null
            }
            val select = obj.str("select", "$pointer/select", required = true) ?: return null
            checkSelector(select, "$pointer/select")
            val limit = obj.int("limit", "$pointer/limit") ?: 40
            if (limit !in 1..500) err("$pointer/limit", "limit 必须在 1–500")
            val skip = obj.int("skip", "$pointer/skip") ?: 0
            if (skip !in 0..100) err("$pointer/skip", "skip 必须在 0–100")
            return ListSpec(select, limit, skip)
        }

        private fun JsonObject.fields(key: String, pointer: String): Map<String, SelectSpec> {
            val obj = obj(key, pointer) ?: run {
                err(pointer, "缺少 $key")
                return emptyMap()
            }
            val out = linkedMapOf<String, SelectSpec>()
            obj.forEach { (name, element) ->
                val spec = selectFrom(element, "$pointer/$name", required = true) ?: return@forEach
                out[name] = spec
            }
            return out
        }

        private fun JsonObject.select(key: String, pointer: String, required: Boolean = false): SelectSpec? {
            val element = this[key] ?: run {
                if (required) err(pointer, "缺少 $key")
                return null
            }
            return selectFrom(element, pointer, required)
        }

        private fun selectFrom(element: JsonElement, pointer: String, required: Boolean): SelectSpec? {
            return when (element) {
                is JsonPrimitive -> parseSugar(element.contentOrNull ?: "", pointer, required)
                is JsonArray -> {
                    val fallbacks = element.mapIndexedNotNull { index, item ->
                        selectFrom(item, "$pointer/$index", required = false)
                    }
                    if (fallbacks.isEmpty()) {
                        err(pointer, "fallback 数组为空")
                        null
                    } else {
                        SelectSpec(fallback = fallbacks, required = required)
                    }
                }
                is JsonObject -> {
                    val css = element.str("select", "$pointer/select")
                    css?.let { checkSelector(it, "$pointer/select") }
                    val jsonPath = element.str("json", "$pointer/json")
                    val const = element["const"]?.let { primitiveToText(it) }
                    if (css == null && jsonPath == null && const == null) {
                        err(pointer, "取值对象需要 select、json 或 const 之一")
                        return null
                    }
                    if (jsonPath != null) checkJsonPath(jsonPath, "$pointer/json")
                    val ops = element["pipeline"]?.let { pipeline(it, "$pointer/pipeline") }.orEmpty()
                    val fallback = element["fallback"]?.let { array ->
                        if (array !is JsonArray) {
                            err("$pointer/fallback", "fallback 必须是数组")
                            emptyList()
                        } else {
                            array.mapIndexedNotNull { index, item ->
                                selectFrom(item, "$pointer/fallback/$index", required = false)
                            }
                        }
                    }.orEmpty()
                    val attr = element.str("attr", "$pointer/attr")
                    if (attr != null && attr.length > 64) err("$pointer/attr", "attr 过长")
                    val index = element.int("index", "$pointer/index") ?: 0
                    SelectSpec(
                        css = css,
                        attr = attr,
                        index = index,
                        jsonPath = jsonPath,
                        const = const,
                        ops = ops,
                        fallback = fallback,
                        required = element.bool("required", "$pointer/required") ?: required,
                    )
                }
            }
        }

        private fun parseSugar(raw: String, pointer: String, required: Boolean): SelectSpec? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                err(pointer, "取值表达式为空")
                return null
            }
            val parts = trimmed.split('|')
            var source = parts.first().trim()
            val ops = parts.drop(1).mapIndexedNotNull { index, op ->
                parseOpString(op.trim(), "$pointer/$index")
            }
            var attr: String? = null
            if (source.startsWith("@")) {
                attr = source.removePrefix("@").ifBlank { null }
                source = ""
            } else {
                val at = source.lastIndexOf('@')
                if (at > 0) {
                    attr = source.substring(at + 1).ifBlank { null }
                    source = source.substring(0, at).trim()
                }
            }
            if (source == "html") {
                attr = "html"
                source = ""
            }
            if (source.isEmpty() && attr == null) return null
            if (source.isNotEmpty()) checkSelector(source, pointer)
            return SelectSpec(css = source, attr = attr, ops = ops, required = required)
        }

        private fun parseOpString(raw: String, pointer: String): PipeOp? {
            if (raw.isEmpty()) return null
            val name = raw.substringBefore(':').trim()
            val arg = raw.substringAfter(':', missingDelimiterValue = "")
            if (name !in PIPE_OPS) {
                err(pointer, "未知管道操作：$name", suggestion(name))
                return null
            }
            return PipeOp(name, args = if (arg.isBlank()) emptyList() else listOf(arg))
        }

        private fun pipeline(element: JsonElement, pointer: String): List<PipeOp> {
            val array = element as? JsonArray ?: run {
                err(pointer, "pipeline 必须是数组")
                return emptyList()
            }
            return array.mapIndexedNotNull { index, item -> parsePipeItem(item, "$pointer/$index") }
        }

        private fun parsePipeItem(element: JsonElement, pointer: String): PipeOp? {
            return when (element) {
                is JsonPrimitive -> {
                    val name = element.content.trim()
                    if (name !in PIPE_OPS) {
                        err(pointer, "未知管道操作：$name", suggestion(name))
                        null
                    } else {
                        PipeOp(name)
                    }
                }
                is JsonArray -> {
                    val name = (element.firstOrNull() as? JsonPrimitive)?.content?.trim()
                    if (name == null || name !in PIPE_OPS) {
                        err(pointer, "管道数组首项必须是已知操作名")
                        return null
                    }
                    val args = element.drop(1).mapNotNull { primitiveToText(it) }
                    PipeOp(name, args)
                }
                is JsonObject -> {
                    val name = element.str("op", "$pointer/op", required = true) ?: return null
                    if (name !in PIPE_OPS) {
                        err("$pointer/op", "未知管道操作：$name", suggestion(name))
                        return null
                    }
                    val map = if (name == "map") {
                        (element["value"] as? JsonObject)?.mapValues { (_, v) -> primitiveToText(v).orEmpty() }
                    } else {
                        null
                    }
                    val args = when (name) {
                        "regex" -> listOfNotNull(
                            element.str("pattern", "$pointer/pattern"),
                            primitiveToText(element["group"]) ?: element.int("group", "$pointer/group")?.toString(),
                        )
                        "replace" -> listOfNotNull(
                            element.str("pattern", "$pointer/pattern"),
                            element.str("replacement", "$pointer/replacement"),
                        )
                        "split" -> listOfNotNull(
                            element.str("separator", "$pointer/separator"),
                            primitiveToText(element["index"]) ?: element.int("index", "$pointer/index")?.toString(),
                        )
                        "prefix", "suffix", "default", "trimSuffix", "join" ->
                            listOfNotNull(element.str("value", "$pointer/value"))
                        "slice" -> listOfNotNull(
                            primitiveToText(element["start"]) ?: element.int("start", "$pointer/start")?.toString(),
                            primitiveToText(element["end"]) ?: element.int("end", "$pointer/end")?.toString(),
                        )
                        "date" -> listOfNotNull(element.str("pattern", "$pointer/pattern"))
                        else -> emptyList()
                    }
                    PipeOp(name, args, map)
                }
            }
        }

        private fun checkSelector(css: String, pointer: String) {
            if (css.isBlank()) return
            runCatching { org.jsoup.Jsoup.parse("<div></div>").select(css) }
                .onFailure { err(pointer, "CSS 选择器无法解析：$css") }
        }

        private fun checkJsonPath(path: String, pointer: String) {
            if (!path.startsWith("$")) err(pointer, "JSONPath 必须以 \$ 开头")
        }

        // ---------------------------------------------------------------- helpers

        private fun JsonObject.obj(key: String, pointer: String): JsonObject? {
            val value = this[key] ?: return null
            return value as? JsonObject ?: run {
                err(pointer, "应为对象")
                null
            }
        }

        private fun JsonObject.str(key: String, pointer: String, required: Boolean = false): String? {
            val value = this[key] ?: run {
                if (required) err(pointer, "缺少必填字段")
                return null
            }
            val primitive = value as? JsonPrimitive ?: run {
                err(pointer, "应为字符串")
                return null
            }
            if (!primitive.isString) {
                err(pointer, "应为字符串")
                return null
            }
            return primitive.content
        }

        private fun JsonObject.int(key: String, pointer: String, required: Boolean = false): Int? {
            val value = this[key] ?: run {
                if (required) err(pointer, "缺少必填字段")
                return null
            }
            return (value as? JsonPrimitive)?.intOrNull ?: run {
                err(pointer, "应为整数")
                null
            }
        }

        private fun JsonObject.double(key: String, pointer: String): Double? {
            val value = this[key] ?: return null
            return (value as? JsonPrimitive)?.doubleOrNull ?: run {
                err(pointer, "应为数字")
                null
            }
        }

        private fun JsonObject.bool(key: String, pointer: String): Boolean? {
            val value = this[key] ?: return null
            return (value as? JsonPrimitive)?.booleanOrNull ?: run {
                err(pointer, "应为布尔值")
                null
            }
        }

        private fun JsonObject.array(key: String, pointer: String, required: Boolean = false): JsonArray? {
            val value = this[key] ?: run {
                if (required) err(pointer, "缺少必填字段")
                return null
            }
            return value as? JsonArray ?: run {
                err(pointer, "应为数组")
                null
            }
        }

        private fun JsonObject.strList(key: String, pointer: String, required: Boolean = false): List<String> {
            val array = array(key, pointer, required) ?: return emptyList()
            return array.mapIndexedNotNull { index, item ->
                primitiveToText(item) ?: run {
                    err("$pointer/$index", "应为字符串")
                    null
                }
            }
        }

        private fun JsonObject.strMap(key: String, pointer: String): Map<String, String> {
            val obj = obj(key, pointer) ?: return emptyMap()
            val out = linkedMapOf<String, String>()
            obj.forEach { (name, value) ->
                val text = primitiveToText(value)
                if (text == null) err("$pointer/$name", "应为字符串") else out[name] = text
            }
            return out
        }

        private fun primitiveToText(element: JsonElement?): String? {
            val primitive = element as? JsonPrimitive ?: return null
            return primitive.contentOrNull
        }

        private fun suggestion(name: String): String? {
            if (name.equals("absURL", true)) return "是否想用 \"absUrl\"？"
            val lower = name.lowercase()
            val candidate = PIPE_OPS.firstOrNull { it.lowercase() == lower }
            return candidate?.let { "是否想用 \"$it\"？" }
        }

        private fun err(pointer: String, message: String, hint: String? = null) {
            issues += RuleIssue(IssueSeverity.ERROR, pointer, message, hint)
        }

        private fun warn(pointer: String, message: String, hint: String? = null) {
            issues += RuleIssue(IssueSeverity.WARNING, pointer, message, hint)
        }
    }

    private companion object {
        val ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{2,64}$")
        val VERSION_PATTERN = Regex("""^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$""")
        val URL_PATTERN = Regex("""^https?://[^\s]+$""")
        val HOST_PATTERN = Regex("""^(?:\*\.)?[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$""")
        val CONFIG_KEY_PATTERN = Regex("""^[A-Za-z0-9_]+$""")
        val TEMPLATE_EXPR = Regex("""\$\{([^}]+)\}""")
        val ALLOWED_VARS = setOf("base", "keyword", "page", "cid", "chapterPath", "index", "url", "category", "path")
        val SCRIPT_HOOKS = setOf("search", "detail", "chapters", "images", "lazyUrl")
        val PIPE_OPS = setOf(
            "trim", "lower", "upper", "absUrl", "prefix", "suffix", "replace", "regex", "split",
            "number", "toInt", "toFloat", "toBool", "map", "decodeHtml", "decodeJs", "decodeUri",
            "urlencode", "base64", "stripTags", "join", "default", "trimSuffix", "slice", "date",
        )
    }
}
