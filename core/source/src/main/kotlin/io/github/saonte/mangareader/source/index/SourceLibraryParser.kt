package io.github.saonte.mangareader.source.index

import io.github.saonte.mangareader.source.SourceKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 第三方源库索引条目。
 *
 * 本应用**不内置任何索引 URL**，也不内置任何站点规则；用户自行填入索引地址。
 * 支持两种索引格式：
 *
 * 1. **venera 兼容格式**（`[{name, fileName, key, version}]`）——社区源库常用，源文件是 CJS。
 * 2. **MangaReader 签名格式**（`RuleIndex`，带 Ed25519 签名与 sha256）——可校验完整性。
 *
 * 解析是宽松的：字段缺失只跳过该条目并记录原因，不让整个索引失败。
 */
@Serializable
data class LibraryEntry(
    /** 索引内的稳定 id（venera 的 `key`，或签名索引的 `id`）。 */
    val id: String,
    val name: String,
    val version: String,
    /** 源文件绝对地址（已按索引所在位置解析为绝对 URL）。 */
    val url: String,
    val kind: SourceKind,
    val adult: Boolean = false,
    /** 签名索引提供的 sha256；venera 格式没有，为 null。 */
    val sha256: String? = null,
    /** 源文件声明的 minAppVersion（venera 有）。 */
    val minAppVersion: String? = null,
)

data class LibraryParseResult(
    val entries: List<LibraryEntry>,
    val warnings: List<String> = emptyList(),
)

/**
 * 索引解析：只看结构，不发网络请求，便于单测。
 */
object SourceLibraryParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param indexUrl 索引自身的地址，用于把相对 `fileName` 解析成绝对 URL。
     */
    fun parse(raw: String, indexUrl: String): LibraryParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return LibraryParseResult(emptyList(), listOf("索引内容为空"))

        val root = runCatching { json.parseToJsonElement(trimmed) }.getOrElse {
            return LibraryParseResult(emptyList(), listOf("索引不是合法 JSON：${it.message}"))
        }

        return when (root) {
            is JsonArray -> parseVeneraArray(root, indexUrl)
            is JsonObject -> {
                val rules = root["rules"] as? JsonArray
                if (rules != null) parseSignedIndex(root, indexUrl) else parseSingleObject(root, indexUrl)
            }

            else -> LibraryParseResult(emptyList(), listOf("无法识别的索引结构"))
        }
    }

    /** venera 格式：顶层数组，元素为 {name, fileName, key, version}。 */
    private fun parseVeneraArray(array: JsonArray, indexUrl: String): LibraryParseResult {
        val warnings = mutableListOf<String>()
        val entries = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: run {
                warnings += "跳过非对象条目"
                return@mapNotNull null
            }
            val key = obj.str("key") ?: obj.str("id")
            val fileName = obj.str("fileName") ?: obj.str("file")
            val name = obj.str("name")
            if (key.isNullOrBlank() || fileName.isNullOrBlank()) {
                warnings += "跳过缺少 key/fileName 的条目：${name ?: "?"}"
                return@mapNotNull null
            }
            LibraryEntry(
                id = key,
                name = name ?: key,
                version = obj.str("version") ?: "0.0.0",
                url = resolve(indexUrl, fileName),
                // venera 源库都是 CJS 模块
                kind = SourceKind.CJS,
                minAppVersion = obj.str("minAppVersion"),
            )
        }
        return LibraryParseResult(entries, warnings)
    }

    /** MangaReader 签名索引：{formatVersion, rules:[...], signature}。 */
    private fun parseSignedIndex(root: JsonObject, indexUrl: String): LibraryParseResult {
        val warnings = mutableListOf<String>()
        val rules = root["rules"] as? JsonArray ?: return LibraryParseResult(emptyList())
        val entries = rules.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.str("id")
            val url = obj.str("url")
            if (id.isNullOrBlank() || url.isNullOrBlank()) {
                warnings += "跳过缺少 id/url 的规则条目"
                return@mapNotNull null
            }
            if (obj.bool("revoked") == true) {
                warnings += "$id 已被索引标记为撤销"
                return@mapNotNull null
            }
            LibraryEntry(
                id = id,
                name = obj.str("name") ?: id,
                version = obj.str("version") ?: "0.0.0",
                url = resolve(indexUrl, url),
                kind = SourceKind.RULE,
                adult = obj.bool("adult") ?: false,
                sha256 = obj.str("sha256"),
            )
        }
        return LibraryParseResult(entries, warnings)
    }

    /** 单对象：直接是一个源描述（少见，但允许用户贴单个源地址）。 */
    private fun parseSingleObject(obj: JsonObject, indexUrl: String): LibraryParseResult {
        val id = obj.str("id") ?: obj.str("key")
        val url = obj.str("url") ?: obj.str("fileName")
        if (id.isNullOrBlank() || url.isNullOrBlank()) {
            return LibraryParseResult(emptyList(), listOf("索引缺少 id/url"))
        }
        val kind = if (url.endsWith(".js", true)) SourceKind.CJS else SourceKind.RULE
        return LibraryParseResult(
            listOf(
                LibraryEntry(
                    id = id,
                    name = obj.str("name") ?: id,
                    version = obj.str("version") ?: "0.0.0",
                    url = resolve(indexUrl, url),
                    kind = kind,
                ),
            ),
        )
    }

    /** 把 `fileName` / 相对路径解析为绝对 URL。 */
    private fun resolve(indexUrl: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = indexUrl.substringBeforeLast('/', missingDelimiterValue = indexUrl)
        return "$base/${path.removePrefix("./").removePrefix("/")}"
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.jsonPrimitive?.contentOrNull
            ?.toBooleanStrictOrNull()

    private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
        get() = if (this is kotlinx.serialization.json.JsonNull) null else content
}
