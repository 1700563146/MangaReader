package io.github.saonte.mangareader.feature.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.saonte.mangareader.model.ImageRef
import io.github.saonte.mangareader.reader.PageSource
import io.github.saonte.mangareader.reader.ProgressSink
import io.github.saonte.mangareader.reader.ReaderController
import io.github.saonte.mangareader.reader.ui.ReaderScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 开发者演示：仅生成本地占位图，验证翻页/条漫/缩放/预取与进度链路。
 * 不包含任何漫画内容，也不参与 release 的入口展示（仅 Debug 入口可达）。
 */
@Composable
fun ReaderDemoScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val pageSource = remember { DemoPageSource(context.applicationContext) }
    val controller = remember(pageSource) {
        ReaderController(
            pageSource = pageSource,
            progressSink = ProgressSink { chapter, title, page, total ->
                Log.d("ReaderDemo", "progress chapter=$chapter title=$title page=${page + 1}/$total")
            },
        )
    }
    ReaderScreen(controller = controller, pageSource = pageSource, onExit = onExit)
}

private class DemoPageSource(
    private val context: Context,
    private val pages: Int = 8,
) : PageSource {

    private val dir = File(context.cacheDir, "reader-demo")

    override val pageCount = MutableStateFlow(pages)
    override val chapterTitle: String = "阅读器演示（无内容）"

    override suspend fun page(index: Int): ImageRef {
        val file = File(dir, "page_$index.png")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeBytes(render(index))
        }
        return ImageRef(url = file.toURI().toString())
    }

    private suspend fun render(index: Int): ByteArray = withContext(Dispatchers.IO) {
        val width = 1080
        val height = 1620
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#0B0F0E"))

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5EEAD4") }
        canvas.drawRect(0f, 0f, width.toFloat(), 14f, accent)
        canvas.drawRect(0f, height - 14f, width.toFloat(), height.toFloat(), accent)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 180f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("第 ${index + 1} 页", width / 2f, height / 2f, title)

        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("阅读器演示 · 无漫画内容", width / 2f, height / 2f + 120f, subtitle)

        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("MangaReader / ${index + 1} of $pages", width / 2f, height - 80f, footer)

        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
