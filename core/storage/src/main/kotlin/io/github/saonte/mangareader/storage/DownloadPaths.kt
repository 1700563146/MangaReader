package io.github.saonte.mangareader.storage

import android.content.Context
import java.io.File

object DownloadPaths {
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

    fun chapterDir(context: Context, request: DownloadRequest): File = File(
        context.filesDir,
        "Manga/downloaded/source_${request.sourceType}/comic_${request.comicId}/chapter_${request.chapterId}",
    )

    fun pageFile(dir: File, index: Int, url: String): File =
        File(dir, "%03d.%s".format(index + 1, extensionOf(url)))

    fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return if (ext in IMAGE_EXT) ext else "jpg"
    }

    fun listImages(dir: File): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.filter { it.extension.lowercase() in IMAGE_EXT }
            ?.sortedBy { it.name }
            .orEmpty()
}
