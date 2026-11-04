package io.github.saonte.mangareader.source.rule

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class SelectSpec(
    val css: String? = null,
    val attr: String? = null,
    val index: Int = 0,
    val jsonPath: String? = null,
    val const: String? = null,
    val ops: List<PipeOp> = emptyList(),
    val fallback: List<SelectSpec> = emptyList(),
    val required: Boolean = true,
)

data class ListSpec(
    val select: String,
    val limit: Int = 40,
    val skip: Int = 0,
)

/**
 * 取值引擎：CSS（jsoup）+ JSONPath 子集 + 管道。
 *
 * 只负责“页面 → 字符串”，不做字段语义校验；字段缺失由上层按 required 报 SELECTOR_EMPTY。
 */
object SelectorEngine {

    fun parseDocument(html: String): Document = Jsoup.parse(html)

    fun selectNodes(document: Document, node: Element?, spec: ListSpec): List<Element> {
        val root = node ?: document
        val selected = runCatching { root.select(spec.select) }
            .getOrElse { throw SelectorException("选择器无法解析: ${spec.select}", it) }
        return selected
            .drop(spec.skip.coerceAtLeast(0))
            .take(spec.limit.coerceAtLeast(0))
    }

    fun extract(
        document: Document,
        node: Element?,
        spec: SelectSpec,
        pageUrl: String,
    ): String? {
        val direct = runCatching { extractDirect(document, node, spec, pageUrl) }.getOrNull()
        if (!direct.isNullOrBlank()) return direct
        spec.fallback.forEach { fallback ->
            val value = extract(document, node, fallback, pageUrl)
            if (!value.isNullOrBlank()) return value
        }
        return direct?.ifBlank { null }
    }

    private fun extractDirect(
        document: Document,
        node: Element?,
        spec: SelectSpec,
        pageUrl: String,
    ): String? {
        if (spec.const != null) return Pipes.apply(spec.const, spec.ops, pageUrl)

        if (spec.jsonPath != null) return null

        val css = spec.css
        val element = if (css.isNullOrEmpty()) {
            node ?: document.body()
        } else {
            val root = node ?: document
            val elements = runCatching { root.select(css) }
                .getOrElse { throw SelectorException("选择器无法解析: $css", it) }
            if (elements.isEmpty()) return null
            val index = if (spec.index < 0) elements.size + spec.index else spec.index
            elements.getOrNull(index) ?: return null
        } ?: return null
        val raw = when (spec.attr) {
            null, "" -> element.text()
            "html" -> element.html()
            "outerHtml" -> element.outerHtml()
            else -> element.attr(spec.attr)
        }
        return Pipes.apply(raw, spec.ops, pageUrl)
    }

    /**
     * JSON 响应取值：支持 `$.a.b[0].c`、`$.list[*].url`、`$..name`。
     * 多个结果用换行连接，交由 `split`/`join` 管道继续处理。
     */
    fun extractJson(root: JsonElement, spec: SelectSpec, pageUrl: String): String? {
        if (spec.const != null) return Pipes.apply(spec.const, spec.ops, pageUrl)
        val path = spec.jsonPath ?: return null
        val values = runCatching { JsonPaths.read(root, path) }
            .getOrElse { throw SelectorException("JSONPath 无法解析: $path", it) }
        if (values.isEmpty()) return null
        val index = if (spec.index < 0) values.size + spec.index else spec.index
        val value = values.getOrNull(index) ?: return null
        val raw = when (value) {
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
        return Pipes.apply(raw, spec.ops, pageUrl)
    }
}

class SelectorException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
