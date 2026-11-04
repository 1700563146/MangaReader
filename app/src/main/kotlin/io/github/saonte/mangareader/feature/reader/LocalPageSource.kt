package io.github.saonte.mangareader.feature.reader

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.saonte.mangareader.model.ImageRef
import io.github.saonte.mangareader.reader.PageSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 本地漫画页源：支持 file:// 与 SAF content:// 目录，按文件名自然排序。
 * `relativePath` 形如 `漫画名/章节名`；为空时直接读根目录图片。
 */
class LocalPageSource(
    context: Context,
    rootUri: String,
    relativePath: String,
    override val chapterTitle: String,
) : PageSource {

    private val uris: List<String> = runCatching {
        val root = Uri.parse(rootUri)
        when (root.scheme) {
            "file" -> {
                val dir = if (relativePath.isBlank()) {
                    File(requireNotNull(root.path))
                } else {
                    File(requireNotNull(root.path), relativePath)
                }
                DownloadPathsCompat.listImages(dir).map { Uri.fromFile(it).toString() }
            }

            "content" -> {
                var node = DocumentFile.fromTreeUri(context, root)
                if (node != null && relativePath.isNotBlank()) {
                    relativePath.split('/').filter { it.isNotBlank() }.forEach { segment ->
                        node = node?.findFile(segment)
                    }
                }
                node?.listFiles()
                    ?.filter { it.isFile && isImage(it.name, it.type) }
                    ?.sortedBy { it.name.orEmpty() }
                    ?.map { it.uri.toString() }
                    .orEmpty()
            }

            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    override val pageCount: StateFlow<Int> = MutableStateFlow(uris.size)

    override suspend fun page(index: Int): ImageRef {
        val uri = uris.getOrNull(index) ?: error("本地页不存在：$index")
        return ImageRef(url = uri)
    }

    private fun isImage(name: String?, mime: String?): Boolean {
        val ext = name.orEmpty().substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXT || mime.orEmpty().startsWith("image/")
    }

    private companion object {
        val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
    }
}

/** 避免 app 依赖 storage 的内部实现细节，仅复用排序规则。 */
private object DownloadPathsCompat {
    fun listImages(dir: File): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp") }
            ?.sortedBy { it.name }
            .orEmpty()
}
