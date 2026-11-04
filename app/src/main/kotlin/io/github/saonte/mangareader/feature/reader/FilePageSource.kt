package io.github.saonte.mangareader.feature.reader

import io.github.saonte.mangareader.model.ImageRef
import io.github.saonte.mangareader.reader.PageSource
import io.github.saonte.mangareader.storage.DownloadPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 已下载章节的离线页源：按文件名自然排序，直接以 file:// 交给 Coil。
 */
class FilePageSource(
    dir: File,
    override val chapterTitle: String,
) : PageSource {

    private val files = DownloadPaths.listImages(dir)

    override val pageCount: StateFlow<Int> = MutableStateFlow(files.size)

    override suspend fun page(index: Int): ImageRef {
        val file = files.getOrNull(index) ?: error("页不存在：$index")
        return ImageRef(url = file.toURI().toString())
    }
}
