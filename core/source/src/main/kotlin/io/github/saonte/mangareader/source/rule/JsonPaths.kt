package io.github.saonte.mangareader.source.rule

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JSONPath 子集：`$`、`.name`、`..name`（递归）、`[0]`、`[-1]`、`[*]`、`['name']`。
 * 不支持过滤表达式与函数，避免规则变成任意代码。
 */
object JsonPaths {

    fun read(root: JsonElement, path: String): List<JsonElement> {
        require(path.startsWith("$")) { "JSONPath 必须以 \$ 开头: $path" }
        var nodes: List<JsonElement> = listOf(root)
        var i = 1
        while (i < path.length) {
            when {
                path.startsWith("..", i) -> {
                    i += 2
                    val (name, next) = readName(path, i)
                    i = next
                    nodes = nodes.flatMap { descendants(it) }
                        .mapNotNull { (it as? JsonObject)?.get(name) }
                }

                path[i] == '.' -> {
                    i += 1
                    val (name, next) = readName(path, i)
                    i = next
                    nodes = nodes.mapNotNull { (it as? JsonObject)?.get(name) }
                }

                path[i] == '[' -> {
                    val close = path.indexOf(']', i)
                    require(close > i) { "JSONPath 缺少 ]: $path" }
                    val inner = path.substring(i + 1, close).trim()
                    i = close + 1
                    nodes = when {
                        inner == "*" -> nodes.flatMap { (it as? JsonArray)?.toList().orEmpty() }
                        inner.startsWith("'") && inner.endsWith("'") -> {
                            val name = inner.substring(1, inner.length - 1)
                            nodes.mapNotNull { (it as? JsonObject)?.get(name) }
                        }

                        else -> {
                            val index = inner.toIntOrNull() ?: error("非法数组下标: $inner")
                            nodes.mapNotNull { node ->
                                (node as? JsonArray)?.let { array ->
                                    val real = if (index < 0) array.size + index else index
                                    array.getOrNull(real)
                                }
                            }
                        }
                    }
                }

                else -> error("JSONPath 无法解析: ${path.substring(i)}")
            }
        }
        return nodes
    }

    private fun readName(path: String, start: Int): Pair<String, Int> {
        var end = start
        while (end < path.length && path[end] != '.' && path[end] != '[') end++
        require(end > start) { "JSONPath 属性名为空" }
        return path.substring(start, end) to end
    }

    private fun descendants(node: JsonElement): List<JsonElement> {
        val out = ArrayList<JsonElement>()
        fun walk(element: JsonElement) {
            out.add(element)
            when (element) {
                is JsonObject -> element.values.forEach(::walk)
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(node)
        return out
    }
}
