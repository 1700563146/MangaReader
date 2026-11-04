package io.github.saonte.mangareader.data.repo

import io.github.saonte.mangareader.data.db.ChapterDao
import io.github.saonte.mangareader.model.Chapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ChapterRepository {
    fun observe(comicId: Long): Flow<List<Chapter>>
    suspend fun list(comicId: Long): List<Chapter>
    suspend fun replaceFromSource(comicId: Long, chapters: List<Chapter>)
    suspend fun markDownloaded(chapterId: Long, sizeBytes: Long)
    suspend fun savePage(chapterId: Long, page: Int)
    suspend fun markRead(chapterId: Long, page: Int)
    suspend fun downloadedCount(comicId: Long): Int
}

@Singleton
class ChapterRepositoryImpl @Inject constructor(
    private val dao: ChapterDao,
) : ChapterRepository {

    override fun observe(comicId: Long): Flow<List<Chapter>> =
        dao.observe(comicId).map { list -> list.map { it.toDomain() } }

    override suspend fun list(comicId: Long): List<Chapter> = dao.list(comicId).map { it.toDomain() }

    /**
     * 以 path 为稳定键合并：已有章节只更新元数据，保留阅读进度与下载状态。
     * 源中已消失的章节暂不删除（M3 再引入“缺失”标记与 UI 提示）。
     */
    override suspend fun replaceFromSource(comicId: Long, chapters: List<Chapter>) {
        chapters.forEach { chapter ->
            val existing = dao.findByPath(comicId, chapter.path)
            if (existing == null) {
                dao.insertIgnore(chapter.copy(comicId = comicId).toEntity())
            } else {
                dao.updateFromSource(
                    id = existing.id,
                    title = chapter.title,
                    group = chapter.sourceGroup,
                    count = chapter.count,
                    complete = chapter.complete,
                )
            }
        }
    }

    override suspend fun markDownloaded(chapterId: Long, sizeBytes: Long) =
        dao.markDownloaded(chapterId, sizeBytes)

    override suspend fun savePage(chapterId: Long, page: Int) =
        dao.savePage(chapterId, page, System.currentTimeMillis())

    override suspend fun markRead(chapterId: Long, page: Int) =
        dao.markRead(chapterId, System.currentTimeMillis(), page)

    override suspend fun downloadedCount(comicId: Long): Int = dao.downloadedCount(comicId)
}
