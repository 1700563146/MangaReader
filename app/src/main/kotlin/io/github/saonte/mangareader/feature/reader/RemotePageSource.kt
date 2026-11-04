package io.github.saonte.mangareader.feature.reader

import io.github.saonte.mangareader.model.ImageRef
import io.github.saonte.mangareader.reader.PageSource
import io.github.saonte.mangareader.source.ImagePage
import io.github.saonte.mangareader.source.SourceEngine
import io.github.saonte.mangareader.source.SourceRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 在线章节页源：非惰性章节直接使用已解析列表；惰性章节按页请求并缓存（不落库）。
 * `total == 0` 的惰性章节暂按 1 页处理，M4 引入「下一页探测」后再完善。
 */
class RemotePageSource(
    private val engine: SourceEngine,
    private val ref: SourceRef,
    private val cid: String,
    private val chapterPath: String,
    override val chapterTitle: String,
    private val imagePage: ImagePage,
) : PageSource {

    private val lazyCache = ConcurrentHashMap<Int, ImageRef>()

    override val pageCount: StateFlow<Int> = MutableStateFlow(
        if (imagePage.lazy) imagePage.total else imagePage.images.size,
    )

    override suspend fun page(index: Int): ImageRef {
        if (!imagePage.lazy) {
            imagePage.images.getOrNull(index)?.let { return it }
        }
        lazyCache[index]?.let { return it }
        val resolved = engine.lazyImage(ref, cid, chapterPath, index)
        lazyCache[index] = resolved
        return resolved
    }
}
