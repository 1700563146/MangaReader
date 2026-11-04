package io.github.saonte.mangareader.storage

import kotlinx.coroutines.flow.Flow

data class LocalRoot(
    val id: Long,
    val uri: String,
    val label: String,
    val enabled: Boolean,
    val lastScan: Long?,
    val lastCount: Int,
)

data class LocalScanResult(
    val roots: Int,
    val comics: Int,
    val error: String? = null,
)

/**
 * 本地漫画扫描：目录（SAF tree 或 file://）→ 漫画 → 章节。
 * 目录型约定：`root/<漫画>/<章节>/图片`，或 `root/<漫画>/图片`（单章）。
 * ZIP/CBZ 顺延（docs/reports/m4.md）。
 */
interface LocalScanner {
    fun observeRoots(): Flow<List<LocalRoot>>
    suspend fun addRoot(uri: String, label: String): Long
    suspend fun removeRoot(id: Long)
    suspend fun scanAll(): LocalScanResult
    suspend fun deleteComic(comicId: Long)
}
