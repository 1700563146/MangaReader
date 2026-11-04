package io.github.saonte.mangareader.storage

import kotlinx.coroutines.flow.Flow

enum class DownloadState(val code: Int) {
    QUEUED(0),
    RUNNING(1),
    PAUSED(2),
    COMPLETED(3),
    FAILED(4),
    CANCELLED(5),
    ;

    companion object {
        fun from(code: Int): DownloadState = entries.firstOrNull { it.code == code } ?: FAILED
    }
}

data class DownloadRequest(
    val comicId: Long,
    val chapterId: Long,
    val sourceType: Long,
    val cid: String,
    val chapterPath: String,
    val title: String,
)

data class DownloadTask(
    val id: Long,
    val comicId: Long,
    val chapterId: Long,
    val title: String,
    val state: DownloadState,
    val done: Int,
    val total: Int,
    val targetDir: String,
    val errorCode: String? = null,
) {
    val progress: Float get() = if (total <= 0) 0f else done.toFloat() / total
}

/**
 * 下载调度入口（WorkManager 实现）。UI 只依赖本接口，离线读取通过 [completedDir] 判断。
 */
interface DownloadScheduler {
    fun observeAll(): Flow<List<DownloadTask>>
    fun observeByComic(comicId: Long): Flow<List<DownloadTask>>
    fun observeByChapter(chapterId: Long): Flow<DownloadTask?>

    suspend fun enqueue(request: DownloadRequest)
    suspend fun pause(chapterId: Long)
    suspend fun resume(chapterId: Long)
    suspend fun cancel(chapterId: Long)

    /** 章节已完成且目录存在时返回目录绝对路径。 */
    suspend fun completedDir(chapterId: Long): String?
}
