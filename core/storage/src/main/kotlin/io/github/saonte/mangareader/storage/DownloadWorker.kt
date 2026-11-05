package io.github.saonte.mangareader.storage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.saonte.mangareader.data.db.ChapterDao
import io.github.saonte.mangareader.data.db.ComicDao
import io.github.saonte.mangareader.data.db.DownloadTaskDao
import io.github.saonte.mangareader.data.repo.SourceRepository
import io.github.saonte.mangareader.model.ErrorCode
import io.github.saonte.mangareader.model.ImageRef
import io.github.saonte.mangareader.source.SourceEngine
import io.github.saonte.mangareader.source.SourceFailure
import io.github.saonte.mangareader.source.RuleSourceRef
import io.github.saonte.mangareader.source.http.SourceHttpClient
import io.github.saonte.mangareader.source.rule.RuleCompileResult
import io.github.saonte.mangareader.source.rule.RuleCompiler
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * 单章下载：逐页写入 `.part` 后原子改名，可断点续传（已存在且非空的页跳过）。
 * 进度写 `download_task`，完成后回写 `chapter.downloaded` 与 `comic.downloaded_at`。
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val tasks: DownloadTaskDao,
    private val chapters: ChapterDao,
    private val comics: ComicDao,
    private val sources: SourceRepository,
    private val engine: SourceEngine,
    private val compiler: RuleCompiler,
    private val http: SourceHttpClient,
) : CoroutineWorker(appContext, params) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        val task = tasks.findById(taskId) ?: return Result.failure()
        val dir = File(task.targetDir).apply { mkdirs() }
        val persistedDone = DownloadPaths.listImages(dir).size

        tasks.updateState(
            id = task.id,
            state = DownloadState.RUNNING.code,
            total = task.total,
            done = persistedDone,
            updatedAt = System.currentTimeMillis(),
            errorCode = null,
            errorMsg = null,
        )
        runCatching { setForeground(foregroundInfo(task.title, persistedDone, task.total)) }

        return try {
            val source = sources.get(task.source)
                ?: throw SourceFailure(ErrorCode.RULE_INVALID, "源不存在", step = "download")
            val rule = (compiler.compile(source.json) as? RuleCompileResult.Success)
                ?: throw SourceFailure(ErrorCode.RULE_INVALID, "规则无法解析", step = "download")
            val ref = RuleSourceRef(task.source, rule.rule)
            val imagePage = engine.images(ref, task.cid, task.chapterPath)
            val total = when {
                !imagePage.lazy -> imagePage.images.size
                imagePage.total > 0 -> imagePage.total
                else -> MAX_LAZY_PAGES
            }
            tasks.updateState(
                id = task.id,
                state = DownloadState.RUNNING.code,
                total = total,
                done = persistedDone,
                updatedAt = System.currentTimeMillis(),
                errorCode = null,
                errorMsg = null,
            )

            var completed = 0
            var index = 0
            while (index < total) {
                if (isStopped) return Result.retry()
                val image: ImageRef = try {
                    if (imagePage.lazy) {
                        engine.lazyImage(ref, task.cid, task.chapterPath, index)
                    } else {
                        imagePage.images.getOrNull(index)
                            ?: throw SourceFailure(ErrorCode.SELECTOR_EMPTY, "图片索引越界", step = "download")
                    }
                } catch (e: SourceFailure) {
                    if (imagePage.lazy && e.code == ErrorCode.SELECTOR_EMPTY) break
                    throw e
                }

                val target = DownloadPaths.pageFile(dir, index, image.url)
                if (!(target.isFile && target.length() > 0)) {
                    val data = http.fetchImageBytes(ref, image)
                    writeAtomic(target, data)
                }
                completed += 1
                index += 1
                if (completed % 3 == 0 || index == total) {
                    notifyProgress(task.title, completed, total, finished = false)
                }
                if (completed % 2 == 0) {
                    tasks.updateState(
                        id = task.id,
                        state = DownloadState.RUNNING.code,
                        total = total,
                        done = completed,
                        updatedAt = System.currentTimeMillis(),
                        errorCode = null,
                        errorMsg = null,
                    )
                    setProgress(androidx.work.workDataOf(KEY_DONE to completed, KEY_TOTAL to total))
                }
            }

            val size = DownloadPaths.listImages(dir).sumOf { it.length() }
            val finalCount = maxOf(completed, index)
            tasks.updateState(
                id = task.id,
                state = DownloadState.COMPLETED.code,
                total = finalCount,
                done = finalCount,
                updatedAt = System.currentTimeMillis(),
                errorCode = null,
                errorMsg = null,
            )
            chapters.markDownloaded(task.chapterId, size)
            comics.setDownloaded(task.comicId, System.currentTimeMillis())
            notifyProgress(task.title, finalCount, finalCount, finished = true)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? SourceFailure)?.code?.name ?: ErrorCode.UNKNOWN.name
            tasks.updateState(
                id = task.id,
                state = DownloadState.FAILED.code,
                total = task.total,
                done = persistedDone,
                updatedAt = System.currentTimeMillis(),
                errorCode = code,
                errorMsg = e.message,
            )
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun writeAtomic(target: File, data: ByteArray) {
        val part = File(target.parentFile, "${target.name}.part")
        part.writeBytes(data)
        if (!part.renameTo(target)) {
            target.writeBytes(data)
            part.delete()
        }
    }

    private fun foregroundInfo(title: String, done: Int, total: Int): ForegroundInfo {
        ensureChannel()
        val notification = buildNotification(title, done, total, finished = false)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyProgress(title: String, done: Int, total: Int, finished: Boolean) {
        ensureChannel()
        runCatching {
            notificationManager.notify(
                NOTIFICATION_ID,
                buildNotification(title, done, total, finished),
            )
        }
    }

    private fun buildNotification(
        title: String,
        done: Int,
        total: Int,
        finished: Boolean,
    ): Notification {
        val max = if (total > 0) total else 100
        val value = if (total > 0) done else 0
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (finished) "下载完成" else "正在下载")
            .setContentText(if (total > 0) "$title（$done/$total）" else title)
            .setOngoing(!finished)
            .setOnlyAlertOnce(true)
            .setProgress(max, value, total <= 0)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "漫画下载",
                NotificationManager.IMPORTANCE_LOW,
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1101
        private const val MAX_LAZY_PAGES = 200
    }
}
