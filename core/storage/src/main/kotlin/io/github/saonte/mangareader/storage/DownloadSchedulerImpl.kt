package io.github.saonte.mangareader.storage

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.saonte.mangareader.data.db.ChapterDao
import io.github.saonte.mangareader.data.db.DownloadTaskDao
import io.github.saonte.mangareader.data.db.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: DownloadTaskDao,
    private val chapters: ChapterDao,
    private val workManager: WorkManager,
) : DownloadScheduler {

    override fun observeAll(): Flow<List<DownloadTask>> =
        tasks.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByComic(comicId: Long): Flow<List<DownloadTask>> =
        tasks.observeByComic(comicId).map { list -> list.map { it.toDomain() } }

    override fun observeByChapter(chapterId: Long): Flow<DownloadTask?> =
        tasks.observeByChapter(chapterId).map { it?.toDomain() }

    override suspend fun enqueue(request: DownloadRequest) {
        val existing = tasks.findByChapter(request.chapterId)
        if (existing?.state == DownloadState.COMPLETED.code) return
        val dir = DownloadPaths.chapterDir(context, request)
        val now = System.currentTimeMillis()
        val entity = existing?.copy(
            state = DownloadState.QUEUED.code,
            targetDir = dir.absolutePath,
            errorCode = null,
            errorMsg = null,
            updatedAt = now,
        ) ?: DownloadTaskEntity(
            comicId = request.comicId,
            chapterId = request.chapterId,
            source = request.sourceType,
            cid = request.cid,
            chapterPath = request.chapterPath,
            title = request.title,
            state = DownloadState.QUEUED.code,
            targetDir = dir.absolutePath,
            createdAt = now,
            updatedAt = now,
        )
        val id = tasks.upsert(entity)
        enqueueWork(id, request.chapterId)
    }

    override suspend fun pause(chapterId: Long) {
        workManager.cancelUniqueWork(workName(chapterId))
        tasks.findByChapter(chapterId)?.let { task ->
            tasks.updateState(
                id = task.id,
                state = DownloadState.PAUSED.code,
                total = task.total,
                done = task.done,
                updatedAt = System.currentTimeMillis(),
                errorCode = null,
                errorMsg = null,
            )
        }
    }

    override suspend fun resume(chapterId: Long) {
        val task = tasks.findByChapter(chapterId) ?: return
        tasks.updateState(
            id = task.id,
            state = DownloadState.QUEUED.code,
            total = task.total,
            done = task.done,
            updatedAt = System.currentTimeMillis(),
            errorCode = null,
            errorMsg = null,
        )
        enqueueWork(task.id, chapterId)
    }

    override suspend fun cancel(chapterId: Long) {
        workManager.cancelUniqueWork(workName(chapterId))
        val task = tasks.findByChapter(chapterId)
        task?.let {
            File(it.targetDir).deleteRecursively()
            tasks.deleteById(it.id)
            chapters.markNotDownloaded(chapterId)
        }
    }

    override suspend fun completedDir(chapterId: Long): String? {
        val task = tasks.findByChapter(chapterId) ?: return null
        if (task.state != DownloadState.COMPLETED.code) return null
        val dir = File(task.targetDir)
        return if (dir.isDirectory && DownloadPaths.listImages(dir).isNotEmpty()) dir.absolutePath else null
    }

    private fun enqueueWork(taskId: Long, chapterId: Long) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_TASK_ID to taskId))
            .setConstraints(
                Constraints.Builder()
                    // 不强制 VALIDATED 网络：部分网络（模拟器/内网/强制门户）无法通过系统校验，
                    // 但目标站点仍可达；失败由 Worker 重试与错误提示兜底。
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            workName(chapterId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun workName(chapterId: Long): String = "dl:$chapterId"

    companion object {
        fun workNameFor(chapterId: Long): String = "dl:$chapterId"
    }
}

private fun DownloadTaskEntity.toDomain(): DownloadTask = DownloadTask(
    id = id,
    comicId = comicId,
    chapterId = chapterId,
    title = title,
    state = DownloadState.from(state),
    done = done,
    total = total,
    targetDir = targetDir,
    errorCode = errorCode,
)
