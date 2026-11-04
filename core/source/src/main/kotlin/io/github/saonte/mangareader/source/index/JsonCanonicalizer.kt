package io.github.saonte.mangareader.source.index

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * RFC 8785 (JCS) 的受限实现：对象键按 UTF-16 排序、无空白、字符串按 JSON 规则转义。
 * 索引签名只使用字符串/整数/布尔，数值仅实现最短形式。
 */
object JsonCanonicalizer {

    fun canonicalize(element: JsonElement): String = buildString { write(element) }

    private fun StringBuilder.write(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                append('{')
                element.keys.sorted().forEachIndexed { index, key ->
                    if (index > 0) append(',')
                    writeString(key)
                    append(':')
                    write(element.getValue(key))
                }
                append('}')
            }

            is JsonArray -> {
                append('[')
                element.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    write(item)
                }
                append(']')
            }

            is JsonPrimitive -> {
                when {
                    element is JsonNull -> append("null")
                    element.isString -> writeString(element.content)
                    else -> append(canonicalNumber(element.content))
                }
            }
        }
    }

    private fun StringBuilder.writeString(value: String) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    private fun canonicalNumber(text: String): String {
        if (text == "true" || text == "false" || text == "null") return text
        val asLong = text.toLongOrNull()
        if (asLong != null) return asLong.toString()
        val asDouble = text.toDoubleOrNull() ?: return "0"
        if (asDouble == asDouble.toLong().toDouble() && kotlin.math.abs(asDouble) < 1e21) {
            return asDouble.toLong().toString()
        }
        return text
    }
}
