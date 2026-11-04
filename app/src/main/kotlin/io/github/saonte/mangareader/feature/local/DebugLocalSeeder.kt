package io.github.saonte.mangareader.feature.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 仅 Debug 入口使用：生成本地占位漫画目录，验证扫描与本地阅读链路。
 * 不包含任何真实漫画内容。
 */
object DebugLocalSeeder {

    suspend fun seed(context: Context): String = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "local-library").apply { mkdirs() }
        for (comic in 1..2) {
            for (chapter in 1..2) {
                val dir = File(root, "测试本地漫画 $comic/第${chapter}话").apply { mkdirs() }
                for (page in 1..3) {
                    File(dir, "%03d.png".format(page)).writeBytes(render(comic, chapter, page))
                }
            }
        }
        Uri.fromFile(root).toString()
    }

    private fun render(comic: Int, chapter: Int, page: Int): ByteArray {
        val width = 900
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#0B0F0E"))
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FCD34D") }
        canvas.drawRect(0f, 0f, width.toFloat(), 12f, accent)
        canvas.drawRect(0f, height - 12f, width.toFloat(), height.toFloat(), accent)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 96f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("本地漫画 $comic", width / 2f, height / 2f - 80f, title)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 56f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("第${chapter}话 · 第${page}页", width / 2f, height / 2f + 40f, sub)
        val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 38f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("DEBUG · NO CONTENT", width / 2f, height / 2f + 130f, note)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
