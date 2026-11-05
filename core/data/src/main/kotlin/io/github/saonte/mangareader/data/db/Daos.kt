package io.github.saonte.mangareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetaDao {
    @Query("SELECT value FROM app_meta WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM app_meta WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entity: AppMetaEntity)

    @Query("DELETE FROM app_meta WHERE `key` = :key")
    suspend fun remove(key: String)
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM source ORDER BY sort ASC, type ASC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM source WHERE enabled = 1 AND health != 2 ORDER BY sort ASC, type ASC")
    fun observeEnabled(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM source ORDER BY type ASC")
    suspend fun listAll(): List<SourceEntity>

    @Query("SELECT * FROM source WHERE type = :type LIMIT 1")
    suspend fun find(type: Long): SourceEntity?

    @Query("SELECT * FROM source WHERE rule_id = :ruleId LIMIT 1")
    suspend fun findByRuleId(ruleId: String): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SourceEntity): Long

    @Query(
        """UPDATE source SET title = :title, version = :version, schema_version = :schemaVersion,
           engine_min = :engineMin, base_url = :baseUrl, json = :json, sha256 = :sha256,
           origin_url = :originUrl, trust = :trust, last_used_at = :usedAt, source_kind = :sourceKind
           WHERE type = :type""",
    )
    suspend fun updateInstalled(
        type: Long,
        title: String,
        version: String,
        schemaVersion: Int,
        engineMin: Int,
        baseUrl: String?,
        json: String,
        sha256: String,
        originUrl: String?,
        trust: Int,
        usedAt: Long?,
        sourceKind: Int,
    )

    @Query("UPDATE source SET enabled = :enabled WHERE type = :type")
    suspend fun setEnabled(type: Long, enabled: Boolean)

    @Query("UPDATE source SET sort = :sort WHERE type = :type")
    suspend fun setSort(type: Long, sort: Int)

    @Query(
        """UPDATE source SET health = :health, fail_streak = :failStreak,
           last_fail_at = :lastFailAt, last_error_code = :lastErrorCode WHERE type = :type""",
    )
    suspend fun updateHealth(
        type: Long,
        health: Int,
        failStreak: Int,
        lastFailAt: Long?,
        lastErrorCode: String?,
    )

    @Query("UPDATE source SET last_used_at = :at WHERE type = :type")
    suspend fun touch(type: Long, at: Long)

    @Query("DELETE FROM source WHERE type = :type")
    suspend fun delete(type: Long)
}

@Dao
interface ComicDao {
    @Query("SELECT * FROM comic WHERE favorite_at IS NOT NULL ORDER BY favorite_at DESC")
    fun observeShelf(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comic WHERE history_at IS NOT NULL ORDER BY history_at DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comic WHERE highlight = 1 AND favorite_at IS NOT NULL ORDER BY update_at DESC")
    fun observeUpdates(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comic WHERE source = :source AND cid = :cid LIMIT 1")
    suspend fun find(source: Long, cid: String): ComicEntity?

    @Query("SELECT * FROM comic WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ComicEntity?

    @Query("SELECT * FROM comic WHERE source = :source ORDER BY id ASC")
    suspend fun listBySource(source: Long): List<ComicEntity>

    @Query("SELECT * FROM comic WHERE local = 1 ORDER BY title ASC")
    fun observeLocal(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comic WHERE local = 1 AND cid = :cid LIMIT 1")
    suspend fun findLocal(cid: String): ComicEntity?

    @Query("SELECT * FROM comic ORDER BY id ASC")
    suspend fun listAll(): List<ComicEntity>

    @Upsert
    suspend fun upsert(entity: ComicEntity): Long

    @Query("DELETE FROM comic")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: ComicEntity): Long

    @Query(
        """UPDATE comic SET title = :title, cover = :cover, author = :author, intro = :intro,
           update_text = :updateText, update_at = :updateAt, finish = :finish, url = :url,
           chapter_count = :chapterCount WHERE id = :id""",
    )
    suspend fun updateMeta(
        id: Long,
        title: String,
        cover: String,
        author: String?,
        intro: String?,
        updateText: String?,
        updateAt: Long?,
        finish: Boolean?,
        url: String?,
        chapterCount: Int,
    )

    @Query("UPDATE comic SET favorite_at = :at, local = :local WHERE id = :id")
    suspend fun setFavorite(id: Long, at: Long?, local: Boolean)

    @Query("UPDATE comic SET highlight = 0 WHERE id = :id")
    suspend fun clearHighlight(id: Long)

    @Query(
        """UPDATE comic SET history_at = :at, last_chapter_path = :path, last_chapter_title = :title,
           last_chapter_index = :index, last_page = :page WHERE id = :id""",
    )
    suspend fun saveProgress(
        id: Long,
        at: Long,
        path: String,
        title: String,
        index: Int,
        page: Int,
    )

    @Query("UPDATE comic SET update_text = :updateText, update_at = :at, highlight = :highlight WHERE id = :id")
    suspend fun setUpdate(id: Long, updateText: String?, at: Long, highlight: Boolean)

    @Query("UPDATE comic SET history_at = :at WHERE id = :id")
    suspend fun touchHistory(id: Long, at: Long)

    @Query("UPDATE comic SET history_at = NULL, last_chapter_path = NULL, last_chapter_title = NULL, last_page = 0 WHERE id = :id")
    suspend fun clearHistory(id: Long)

    @Query("UPDATE comic SET history_at = NULL, last_chapter_path = NULL, last_chapter_title = NULL, last_page = 0")
    suspend fun clearAllHistory()

    @Query("UPDATE comic SET favorite_at = CASE WHEN :at = 0 THEN NULL ELSE :at END WHERE id IN (:ids)")
    suspend fun setFavoriteBulk(ids: List<Long>, at: Long)

    @Query("UPDATE comic SET downloaded_at = :at WHERE id = :id")
    suspend fun setDownloaded(id: Long, at: Long?)

    @Query("DELETE FROM comic WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM comic WHERE favorite_at IS NOT NULL")
    fun observeFavoriteCount(): Flow<Int>
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapter WHERE comic_id = :comicId ORDER BY id ASC")
    fun observe(comicId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapter WHERE comic_id = :comicId ORDER BY id ASC")
    suspend fun list(comicId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapter WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ChapterEntity?

    @Query("SELECT * FROM chapter WHERE comic_id = :comicId AND path = :path LIMIT 1")
    suspend fun findByPath(comicId: Long, path: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: ChapterEntity): Long

    @Query(
        """UPDATE chapter SET title = :title, source_group = :group, count = :count,
           complete = :complete WHERE id = :id""",
    )
    suspend fun updateFromSource(id: Long, title: String, group: String, count: Int, complete: Boolean)

    @Query("UPDATE chapter SET downloaded = 1, size_bytes = :sizeBytes WHERE id = :id")
    suspend fun markDownloaded(id: Long, sizeBytes: Long)

    @Query("UPDATE chapter SET downloaded = 0, size_bytes = 0 WHERE id = :id")
    suspend fun markNotDownloaded(id: Long)

    @Query("UPDATE chapter SET last_page = :page, read_at = :at WHERE id = :id")
    suspend fun savePage(id: Long, page: Int, at: Long)

    @Query("UPDATE chapter SET read_at = :at, last_page = :page WHERE id = :id")
    suspend fun markRead(id: Long, at: Long, page: Int)

    @Query("DELETE FROM chapter WHERE comic_id = :comicId")
    suspend fun deleteByComic(comicId: Long)

    @Query("SELECT COUNT(*) FROM chapter WHERE comic_id = :comicId AND downloaded = 1")
    suspend fun downloadedCount(comicId: Long): Int
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY sort ASC, name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT * FROM tag ORDER BY id ASC")
    suspend fun listAll(): List<TagEntity>

    @Query("DELETE FROM tag")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TagEntity): Long

    @Query("UPDATE tag SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """SELECT c.* FROM comic c INNER JOIN tag_ref r ON c.id = r.comic_id
           WHERE r.tag_id = :tagId AND c.favorite_at IS NOT NULL ORDER BY c.favorite_at DESC""",
    )
    fun observeComics(tagId: Long): Flow<List<ComicEntity>>
}

@Dao
interface TagRefDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(ref: TagRefEntity)

    @Query("DELETE FROM tag_ref WHERE comic_id = :comicId AND tag_id = :tagId")
    suspend fun unlink(comicId: Long, tagId: Long)

    @Query("DELETE FROM tag_ref WHERE tag_id = :tagId")
    suspend fun unlinkAll(tagId: Long)

    @Query("SELECT tag_id FROM tag_ref WHERE comic_id = :comicId")
    suspend fun tagsOf(comicId: Long): List<Long>

    @Query("SELECT * FROM tag_ref")
    suspend fun listAll(): List<TagRefEntity>

    @Query("DELETE FROM tag_ref WHERE comic_id = :comicId")
    suspend fun unlinkComic(comicId: Long)
}

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_task ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_task WHERE state = :state ORDER BY created_at ASC")
    fun observeByState(state: Int): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_task WHERE comic_id = :comicId ORDER BY created_at DESC")
    fun observeByComic(comicId: Long): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_task WHERE chapter_id = :chapterId LIMIT 1")
    suspend fun findByChapter(chapterId: Long): DownloadTaskEntity?

    @Query("SELECT * FROM download_task WHERE chapter_id = :chapterId LIMIT 1")
    fun observeByChapter(chapterId: Long): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_task WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): DownloadTaskEntity?

    @Upsert
    suspend fun upsert(entity: DownloadTaskEntity): Long

    @Query(
        """UPDATE download_task SET state = :state, total = :total, done = :done,
           updated_at = :updatedAt, error_code = :errorCode, error_msg = :errorMsg WHERE id = :id""",
    )
    suspend fun updateState(
        id: Long,
        state: Int,
        total: Int,
        done: Int,
        updatedAt: Long,
        errorCode: String?,
        errorMsg: String?,
    )

    @Query("DELETE FROM download_task WHERE chapter_id = :chapterId")
    suspend fun deleteByChapter(chapterId: Long)

    @Query("DELETE FROM download_task WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM download_task WHERE comic_id = :comicId")
    suspend fun deleteByComic(comicId: Long)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY created_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SearchHistoryEntity): Long

    @Query("UPDATE search_history SET created_at = :at WHERE keyword = :keyword AND source = :source")
    suspend fun touch(keyword: String, source: Long, at: Long)

    @Query(
        """DELETE FROM search_history WHERE id NOT IN
           (SELECT id FROM search_history ORDER BY created_at DESC LIMIT :keep)""",
    )
    suspend fun trim(keep: Int)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface LocalRootDao {
    @Query("SELECT * FROM local_root ORDER BY id ASC")
    fun observeAll(): Flow<List<LocalRootEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalRootEntity): Long

    @Query("UPDATE local_root SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE local_root SET last_scan = :at, last_count = :count WHERE id = :id")
    suspend fun updateScan(id: Long, at: Long, count: Int)

    @Query("DELETE FROM local_root WHERE id = :id")
    suspend fun delete(id: Long)
}
