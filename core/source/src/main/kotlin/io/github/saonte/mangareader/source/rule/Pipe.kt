package io.github.saonte.mangareader.source.rule

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * 模板变量与管道变换的单一实现，供 RequestBuilder 与 Selector 共用。
 */
object Template {
    private val EXPR = Regex("""\$\{([^}]+)\}""")

    fun render(template: String, vars: Map<String, String>): String =
        EXPR.replace(template) { match ->
            val expr = match.groupValues[1].trim()
            val parts = expr.split('|')
            val name = parts.first().trim()
            var value = vars[name] ?: ""
            parts.drop(1).forEach { filter -> value = applyFilter(value, filter.trim()) }
            value
        }

    private fun applyFilter(input: String, filter: String): String {
        val name = filter.substringBefore(':').trim()
        val rawArgs = filter.substringAfter(':', missingDelimiterValue = "").trim()
        return when (name) {
            "" -> input
            "urlencode" -> urlEncode(input, rawArgs.ifBlank { "utf-8" })
            "urldecode" -> runCatching { URLDecoder.decode(input, Charset.forName("UTF-8")) }.getOrDefault(input)
            "base64" -> runCatching { Base64.getEncoder().encodeToString(input.toByteArray()) }.getOrDefault(input)
            "base64url" -> runCatching {
                Base64.getUrlEncoder().withoutPadding().encodeToString(input.toByteArray())
            }.getOrDefault(input)
            "lower" -> input.lowercase()
            "upper" -> input.uppercase()
            "trim" -> input.trim()
            "htmlencode" -> input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            "md5" -> md5(input)
            "replace" -> {
                val (from, to) = splitTwo(rawArgs)
                if (from.isEmpty()) input else input.replace(from, to)
            }
            "regex" -> {
                val (pattern, group) = splitRegex(rawArgs)
                pattern?.let {
                    runCatching { Regex(it).find(input)?.groupValues?.getOrNull(group) }.getOrNull()
                } ?: input
            }
            "default" -> input.ifEmpty { rawArgs }
            else -> input
        }
    }

    private fun splitTwo(raw: String): Pair<String, String> {
        val idx = raw.indexOf(':')
        return if (idx < 0) raw to "" else raw.substring(0, idx) to raw.substring(idx + 1)
    }

    private fun splitRegex(raw: String): Pair<String?, Int> {
        val lastColon = raw.lastIndexOf(':')
        if (lastColon > 0) {
            val maybeGroup = raw.substring(lastColon + 1).toIntOrNull()
            if (maybeGroup != null) return raw.substring(0, lastColon) to maybeGroup
        }
        return raw to 0
    }

    fun urlEncode(input: String, charset: String = "utf-8"): String =
        runCatching { URLEncoder.encode(input, Charset.forName(charset)).replace("+", "%20") }
            .getOrDefault(input)

    private fun md5(input: String): String =
        runCatching {
            java.security.MessageDigest.getInstance("MD5")
                .digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.getOrDefault(input)
}

/**
 * 一个管道步骤。`args` 为标量参数，`map` 用于 map 操作。
 */
data class PipeOp(
    val name: String,
    val args: List<String> = emptyList(),
    val map: Map<String, String>? = null,
)

object Pipes {

    fun apply(input: String?, ops: List<PipeOp>, baseUrl: String? = null): String? {
        var value = input
        ops.forEach { op -> value = applyOne(value, op, baseUrl) }
        return value
    }

    private fun applyOne(input: String?, op: PipeOp, baseUrl: String?): String? {
        if (input == null) return null
        return when (op.name) {
            "trim" -> input.trim()
            "lower" -> input.lowercase()
            "upper" -> input.uppercase()
            "absUrl" -> resolveUrl(input, baseUrl)
            "prefix" -> op.args.firstOrNull().orEmpty() + input
            "suffix" -> input + op.args.firstOrNull().orEmpty()
            "replace" -> {
                val pattern = op.args.getOrNull(0) ?: return input
                val replacement = op.args.getOrNull(1).orEmpty()
                runCatching { Regex(pattern).replace(input, replacement) }.getOrDefault(input)
            }
            "regex" -> {
                val pattern = op.args.getOrNull(0) ?: return input
                val group = op.args.getOrNull(1)?.toIntOrNull() ?: 0
                runCatching { Regex(pattern).find(input)?.groupValues?.getOrNull(group) }.getOrNull()
            }
            "split" -> {
                val sep = op.args.getOrNull(0).orEmpty()
                val index = op.args.getOrNull(1)?.toIntOrNull() ?: 0
                val parts = if (sep.isEmpty()) input.split(" ") else input.split(sep)
                val real = if (index < 0) parts.size + index else index
                parts.getOrNull(real)
            }
            "join" -> input // 由列表层处理，标量场景保留原值
            "number" -> Regex("""-?\d+(?:\.\d+)?""").find(input)?.value
            "toInt" -> input.trim().toIntOrNull()?.toString()
            "toFloat" -> input.trim().toFloatOrNull()?.toString()
            "toBool" -> (input.trim().lowercase() in setOf("true", "1", "yes", "on", "是", "完结", "已完结")).toString()
            "map" -> op.map?.get(input.trim())
            "decodeHtml" -> org.jsoup.parser.Parser.unescapeEntities(input, false)
            "decodeJs" -> decodeJs(input)
            "decodeUri" -> runCatching { URLDecoder.decode(input, Charset.forName("UTF-8")) }.getOrDefault(input)
            "urlencode" -> Template.urlEncode(input)
            "base64" -> runCatching {
                String(Base64.getDecoder().decode(input.trim()))
            }.getOrDefault(input)
            "stripTags" -> org.jsoup.Jsoup.parse(input).text()
            "default" -> input.ifBlank { op.args.firstOrNull().orEmpty() }
            "trimSuffix" -> op.args.firstOrNull()?.let { input.removeSuffix(it) } ?: input
            "slice" -> {
                val start = op.args.getOrNull(0)?.toIntOrNull() ?: 0
                val end = op.args.getOrNull(1)?.toIntOrNull()
                val s = start.coerceIn(0, input.length)
                val e = (end ?: input.length).coerceIn(s, input.length)
                input.substring(s, e)
            }
            "date" -> parseDate(input, op.args.getOrNull(0))
            else -> null
        }
    }

    private fun resolveUrl(value: String, baseUrl: String?): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val base = baseUrl ?: return trimmed
        return runCatching {
            if (trimmed.startsWith("//")) {
                val scheme = URI(base).scheme ?: "https"
                "$scheme:$trimmed"
            } else {
                URI(base).resolve(trimmed).toString()
            }
        }.getOrDefault(trimmed)
    }

    private fun decodeJs(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                when (val n = input[i + 1]) {
                    'u' -> {
                        if (i + 5 < input.length) {
                            val hex = input.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                                continue
                            }
                        } else {
                            sb.append(n); i += 2; continue
                        }
                        sb.append('\\'); sb.append('u'); i += 2; continue
                    }
                    'x' -> {
                        if (i + 3 < input.length) {
                            val code = input.substring(i + 2, i + 4).toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 4
                                continue
                            }
                        }
                        sb.append(n); i += 2; continue
                    }
                    '/' -> { sb.append('/'); i += 2; continue }
                    'n' -> { sb.append('\n'); i += 2; continue }
                    't' -> { sb.append('\t'); i += 2; continue }
                    'r' -> { sb.append('\r'); i += 2; continue }
                    '\\' -> { sb.append('\\'); i += 2; continue }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun parseDate(input: String, pattern: String?): String = runCatching {
        val text = input.trim()
        val epochMillis = if (pattern.isNullOrBlank()) {
            LocalDate.parse(text).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern))
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        epochMillis.toString()
    }.getOrDefault(input)
}
