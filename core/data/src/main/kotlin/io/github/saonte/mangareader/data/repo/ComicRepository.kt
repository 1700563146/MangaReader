package io.github.saonte.mangareader.data.repo

import io.github.saonte.mangareader.data.db.ComicDao
import io.github.saonte.mangareader.model.Comic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ComicRepository {
    fun observeShelf(): Flow<List<Comic>>
    fun observeHistory(limit: Int = 100): Flow<List<Comic>>
    fun observeUpdates(): Flow<List<Comic>>
    fun observeLocal(): Flow<List<Comic>>
    fun observeFavoriteCount(): Flow<Int>
    suspend fun findBySource(source: Long, cid: String): Comic?
    suspend fun findById(id: Long): Comic?
    suspend fun upsert(comic: Comic): Long
    suspend fun setFavorite(comicId: Long, favorite: Boolean)
    suspend fun saveProgress(comicId: Long, chapterPath: String, chapterTitle: String, chapterIndex: Int, page: Int)
    suspend fun touchHistory(comicId: Long)
    suspend fun clearHighlight(comicId: Long)
    suspend fun setUpdate(comicId: Long, updateText: String?, at: Long, highlight: Boolean)
    suspend fun delete(comicId: Long)
    suspend fun clearHistory(comicId: Long)
    suspend fun clearAllHistory()
    suspend fun setFavoriteBulk(comicIds: List<Long>, favorite: Boolean)
}

@Singleton
class ComicRepositoryImpl @Inject constructor(
    private val dao: ComicDao,
) : ComicRepository {

    override fun observeShelf(): Flow<List<Comic>> =
        dao.observeShelf().map { list -> list.map { it.toDomain() } }

    override fun observeHistory(limit: Int): Flow<List<Comic>> =
        dao.observeHistory(limit).map { list -> list.map { it.toDomain() } }

    override fun observeUpdates(): Flow<List<Comic>> =
        dao.observeUpdates().map { list -> list.map { it.toDomain() } }

    override fun observeLocal(): Flow<List<Comic>> =
        dao.observeLocal().map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteCount(): Flow<Int> = dao.observeFavoriteCount()

    override suspend fun findBySource(source: Long, cid: String): Comic? =
        dao.find(source, cid)?.toDomain()

    override suspend fun findById(id: Long): Comic? = dao.findById(id)?.toDomain()

    override suspend fun upsert(comic: Comic): Long {
        val existing = dao.find(comic.source, comic.cid)
        if (existing == null) {
            val inserted = dao.insertIgnore(comic.toEntity())
            if (inserted != -1L) return inserted
            // 并发插入：回读已有行。
            return dao.find(comic.source, comic.cid)?.id ?: -1L
        }
        dao.updateMeta(
            id = existing.id,
            title = comic.title,
            cover = comic.cover,
            author = comic.author,
            intro = comic.intro,
            updateText = comic.updateText,
            updateAt = comic.updateAt,
            finish = comic.finish,
            url = comic.url,
            chapterCount = comic.chapterCount,
        )
        return existing.id
    }

    override suspend fun setFavorite(comicId: Long, favorite: Boolean) {
        dao.setFavorite(comicId, if (favorite) System.currentTimeMillis() else null, local = false)
    }

    override suspend fun saveProgress(
        comicId: Long,
        chapterPath: String,
        chapterTitle: String,
        chapterIndex: Int,
        page: Int,
    ) {
        dao.saveProgress(
            id = comicId,
            at = System.currentTimeMillis(),
            path = chapterPath,
            title = chapterTitle,
            index = chapterIndex,
            page = page,
        )
    }

    override suspend fun touchHistory(comicId: Long) {
        dao.touchHistory(comicId, System.currentTimeMillis())
    }

    override suspend fun clearHighlight(comicId: Long) = dao.clearHighlight(comicId)

    override suspend fun setUpdate(comicId: Long, updateText: String?, at: Long, highlight: Boolean) =
        dao.setUpdate(comicId, updateText, at, highlight)

    override suspend fun delete(comicId: Long) = dao.delete(comicId)

    override suspend fun clearHistory(comicId: Long) = dao.clearHistory(comicId)

    override suspend fun clearAllHistory() = dao.clearAllHistory()

    override suspend fun setFavoriteBulk(comicIds: List<Long>, favorite: Boolean) {
        if (comicIds.isEmpty()) return
        val now = if (favorite) System.currentTimeMillis() else 0L
        dao.setFavoriteBulk(comicIds, now)
    }
}
