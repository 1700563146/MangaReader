package io.github.saonte.mangareader.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.saonte.mangareader.data.db.ChapterDao
import io.github.saonte.mangareader.data.db.ChapterEntity
import io.github.saonte.mangareader.data.db.ComicDao
import io.github.saonte.mangareader.data.db.ComicEntity
import io.github.saonte.mangareader.data.db.LocalRootDao
import io.github.saonte.mangareader.data.db.LocalRootEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentLocalScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roots: LocalRootDao,
    private val comics: ComicDao,
    private val chapters: ChapterDao,
) : LocalScanner {

    override fun observeRoots(): Flow<List<LocalRoot>> =
        roots.observeAll().map { list ->
            list.map {
                LocalRoot(
                    id = it.id,
                    uri = it.uri,
                    label = it.label,
                    enabled = it.enabled,
                    lastScan = it.lastScan,
                    lastCount = it.lastCount,
                )
            }
        }

    override suspend fun addRoot(uri: String, label: String): Long {
        val name = label.ifBlank {
            Uri.decode(uri).substringAfterLast('/').substringAfterLast(':').ifBlank { "本地目录" }
        }
        return roots.upsert(
            LocalRootEntity(uri = uri, label = name, lastScan = System.currentTimeMillis()),
        )
    }

    override suspend fun removeRoot(id: Long) = roots.delete(id)

    override suspend fun deleteComic(comicId: Long) = comics.delete(comicId)

    override suspend fun scanAll(): LocalScanResult = withContext(Dispatchers.IO) {
        val enabled = roots.observeAll().first().filter { it.enabled }
        var comicCount = 0
        var failed = 0
        enabled.forEach { root ->
            runCatching {
                comicCount += scanRoot(root)
                roots.updateScan(root.id, System.currentTimeMillis(), comicCount)
            }.onFailure { failed += 1 }
        }
        LocalScanResult(
            roots = enabled.size,
            comics = comicCount,
            error = if (failed > 0) "$failed 个目录扫描失败" else null,
        )
    }

    private suspend fun scanRoot(root: LocalRootEntity): Int {
        val rootNode = resolveRoot(root.uri) ?: return 0
        val rootChildren = rootNode.children()
        val directImages = rootChildren.filter { it.isImage() }
        if (directImages.isNotEmpty()) {
            return saveComic(
                root = root,
                comicPath = "",
                title = root.label,
                chapterList = listOf(ChapterData("第1话", "", directImages)),
            )
        }

        var count = 0
        rootChildren.filter { it.isDirectory() }.forEach { comicDir ->
            val children = comicDir.children()
            val direct = children.filter { it.isImage() }
            val chapterDirs = children.filter { it.isDirectory() }
                .map { dir -> dir to dir.children().filter { it.isImage() } }
                .filter { it.second.isNotEmpty() }

            val chapterData: List<ChapterData> = when {
                chapterDirs.isNotEmpty() -> chapterDirs.map { (dir, images) ->
                    ChapterData(
                        title = dir.name.ifBlank { "未命名" },
                        path = "${comicDir.name}/${dir.name}",
                        images = images,
                    )
                }

                direct.isNotEmpty() -> listOf(
                    ChapterData("第1话", comicDir.name, direct),
                )

                else -> emptyList()
            }
            if (chapterData.isNotEmpty()) {
                count += saveComic(
                    root = root,
                    comicPath = comicDir.name,
                    title = comicDir.name.ifBlank { root.label },
                    chapterList = chapterData,
                )
            }
        }
        return count
    }

    private suspend fun saveComic(
        root: LocalRootEntity,
        comicPath: String,
        title: String,
        chapterList: List<ChapterData>,
    ): Int {
        val cover = chapterList.firstOrNull()?.images?.firstOrNull()?.uriString().orEmpty()
        val cid = sha1("${root.uri}#$comicPath")
        val existing = comics.findLocal(cid)
        val comicId: Long
        if (existing == null) {
            val inserted = comics.insertIgnore(
                ComicEntity(
                    source = 0,
                    cid = cid,
                    title = title,
                    cover = cover,
                    local = true,
                    url = root.uri,
                    chapterCount = chapterList.size,
                ),
            )
            comicId = if (inserted != -1L) inserted else comics.findLocal(cid)?.id ?: return 0
        } else {
            comics.updateMeta(
                id = existing.id,
                title = title,
                cover = cover,
                author = existing.author,
                intro = existing.intro,
                updateText = existing.updateText,
                updateAt = existing.updateAt,
                finish = existing.finish,
                url = root.uri,
                chapterCount = chapterList.size,
            )
            comicId = existing.id
        }

        chapterList.forEach { chapter ->
            val found = chapters.findByPath(comicId, chapter.path)
            if (found == null) {
                chapters.insertIgnore(
                    ChapterEntity(
                        comicId = comicId,
                        title = chapter.title,
                        path = chapter.path,
                        count = chapter.images.size,
                        complete = true,
                        downloaded = true,
                    ),
                )
            } else {
                chapters.updateFromSource(
                    id = found.id,
                    title = chapter.title,
                    group = "",
                    count = chapter.images.size,
                    complete = true,
                )
            }
        }
        return 1
    }

    private fun resolveRoot(uri: String): Node? = runCatching {
        val parsed = Uri.parse(uri)
        when (parsed.scheme) {
            "file" -> parsed.path?.let { path ->
                File(path).takeIf { it.isDirectory }?.let { FileNode(it, it.name) }
            }

            "content" -> DocumentFile.fromTreeUri(context, parsed)?.takeIf { it.isDirectory }?.let {
                DocumentNode(it, it.name.orEmpty())
            }

            else -> null
        }
    }.getOrNull()

    private fun Node.uriString(): String = when (this) {
        is FileNode -> Uri.fromFile(file).toString()
        is DocumentNode -> document.uri.toString()
    }

    private fun Node.children(): List<Node> = when (this) {
        is FileNode -> file.listFiles().orEmpty()
            .filter { !it.name.startsWith('.') }
            .map { if (it.isDirectory) FileNode(it, it.name) else FileNode(it, it.name) }

        is DocumentNode -> document.listFiles()
            .filter { !it.name.orEmpty().startsWith('.') }
            .map { if (it.isDirectory) DocumentNode(it, it.name.orEmpty()) else DocumentNode(it, it.name.orEmpty()) }
    }

    private fun Node.isDirectory(): Boolean = when (this) {
        is FileNode -> file.isDirectory
        is DocumentNode -> document.isDirectory
    }

    private fun Node.isImage(): Boolean = when (this) {
        is FileNode -> file.isFile && file.extension.lowercase() in IMAGE_EXT
        is DocumentNode -> {
            val mime = document.type.orEmpty()
            val ext = document.name.orEmpty().substringAfterLast('.', "").lowercase()
            !document.isDirectory && (mime.startsWith("image/") || ext in IMAGE_EXT)
        }
    }

    private sealed interface Node {
        val name: String
    }

    private data class FileNode(val file: File, override val name: String) : Node
    private data class DocumentNode(val document: DocumentFile, override val name: String) : Node

    private data class ChapterData(
        val title: String,
        val path: String,
        val images: List<Node>,
    )

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
    }
}
