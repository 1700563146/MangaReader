package io.github.saonte.mangareader.storage

import kotlinx.coroutines.flow.Flow

enum class DownloadState {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class DownloadRequest(
    val comicId: Long,
    val chapterId: Long,
    val sourceType: Long,
    val cid: String,
    val chapterPath: String,
    val title: String,
)

/**
 * 下载调度入口。M4 由 WorkManager 实现；M0 仅保留契约。
 */
interface DownloadScheduler {
    suspend fun enqueue(request: DownloadRequest)

    suspend fun cancel(chapterId: Long)

    fun observe(chapterId: Long): Flow<DownloadState>
}
