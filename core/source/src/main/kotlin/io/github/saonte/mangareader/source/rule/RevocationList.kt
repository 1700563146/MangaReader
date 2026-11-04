package io.github.saonte.mangareader.source.rule

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RevokedRule(
    val id: String,
    val version: String? = null,
)

@Serializable
data class RevocationDocument(
    val formatVersion: Int = 1,
    val rules: List<RevokedRule> = emptyList(),
)

/**
 * 撤销列表：`version = null` 表示撤销该规则的所有版本；否则撤销 <= 指定版本。
 * 由 App 资产与规则索引双通道下发（M5）。
 */
class RevocationList(
    private val entries: List<RevokedRule> = emptyList(),
) {
    fun isRevoked(id: String, version: String): Boolean =
        entries.any { entry ->
            entry.id == id && (entry.version == null || compareVersions(entry.version, version) >= 0)
        }

    fun size(): Int = entries.size

    companion object {
        val EMPTY = RevocationList()

        fun parse(raw: String): RevocationList {
            val document = runCatching {
                Json { ignoreUnknownKeys = false }.decodeFromString(RevocationDocument.serializer(), raw)
            }.getOrElse { return EMPTY }
            return RevocationList(document.rules)
        }
    }
}

private fun compareVersions(left: String, right: String): Int {
    val a = left.split('.', '-')
    val b = right.split('.', '-')
    val max = maxOf(a.size, b.size)
    for (i in 0 until max) {
        val x = a.getOrNull(i)?.toIntOrNull()
        val y = b.getOrNull(i)?.toIntOrNull()
        if (x != null && y != null) {
            if (x != y) return x.compareTo(y)
        } else {
            val cmp = (a.getOrNull(i) ?: "").compareTo(b.getOrNull(i) ?: "")
            if (cmp != 0) return cmp
        }
    }
    return 0
}
