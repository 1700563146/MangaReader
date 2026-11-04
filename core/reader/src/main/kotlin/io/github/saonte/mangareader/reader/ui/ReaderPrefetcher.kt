package io.github.saonte.mangareader.reader.ui

import android.content.Context
import androidx.compose.runtime.Composable
import coil3.SingletonImageLoader
import io.github.saonte.mangareader.reader.PageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 有界预取：按窗口请求页地址并预热 Coil 缓存，窗口外未完成请求取消。
 * 只预热图片，不改变 UI 状态；失败静默（真正加载时会再次报错并展示重试）。
 */
class ReaderPrefetcher(
    private val context: Context,
    private val pageSource: PageSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loader = SingletonImageLoader.get(context)
    private val jobs = mutableMapOf<Int, Job>()

    fun prefetch(indices: IntRange) {
        val target = indices.toSet()
        jobs.filterKeys { it !in target }.values.forEach { it.cancel() }
        jobs.keys.retainAll(target)
        indices.forEach { index ->
            if (jobs[index]?.isActive != true) {
                jobs[index] = scope.launch {
                    runCatching { pageSource.page(index) }
                        .getOrNull()
                        ?.let { ref -> loader.enqueue(imageRequest(context, ref)) }
                }
            }
        }
    }

    fun close() {
        scope.cancel()
        jobs.clear()
    }
}

/**
 * Composable 生命周期内复用一个预取器。
 */
@Composable
fun rememberReaderPrefetcher(pageSource: PageSource): ReaderPrefetcher {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefetcher = androidx.compose.runtime.remember(pageSource) { ReaderPrefetcher(context, pageSource) }
    androidx.compose.runtime.DisposableEffect(prefetcher) {
        onDispose { prefetcher.close() }
    }
    return prefetcher
}
