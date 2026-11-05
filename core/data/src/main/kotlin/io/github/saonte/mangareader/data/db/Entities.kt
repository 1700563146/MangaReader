package io.github.saonte.mangareader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 已安装的规则包 = 一个源实例。`type` 自增并作为 comic.source 的引用值。
 */
@Entity(
    tableName = "source",
    indices = [
        Index(value = ["rule_id"], unique = true),
        Index(value = ["enabled", "sort"]),
    ],
)
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val type: Long = 0,
    @ColumnInfo(name = "rule_id") val ruleId: String,
    val title: String,
    val version: String,
    @ColumnInfo(name = "source_kind") val sourceKind: Int = KIND_RULE,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "engine_min") val engineMin: Int,
    @ColumnInfo(name = "base_url") val baseUrl: String?,
    val json: String,
    val sha256: String,
    val enabled: Boolean = true,
    val trust: Int = TRUST_COMMUNITY,
    @ColumnInfo(name = "origin_url") val originUrl: String? = null,
    @ColumnInfo(name = "installed_at") val installedAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long? = null,
    val health: Int = HEALTH_HEALTHY,
    @ColumnInfo(name = "fail_streak") val failStreak: Int = 0,
    @ColumnInfo(name = "last_fail_at") val lastFailAt: Long? = null,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
    val sort: Int = 0,
) {
    companion object {
        /** JSON 声明式规则（v1 主力格式，Rhino 脚本钩子可选）。 */
        const val KIND_RULE = 0

        /** CJS 模块源（venera 风格 `class X extends ComicSource`），需 ES2020 引擎。 */
        const val KIND_CJS = 1

        const val TRUST_OFFICIAL = 0
        const val TRUST_COMMUNITY = 1
        const val TRUST_LOCAL = 2

        const val HEALTH_HEALTHY = 0
        const val HEALTH_DEGRADED = 1
        const val HEALTH_DISABLED = 2
    }
}

@Entity(
    tableName = "comic",
    indices = [
        Index(value = ["source", "cid"], unique = true),
        Index(value = ["favorite_at"]),
        Index(value = ["history_at"]),
        Index(value = ["source"]),
        Index(value = ["highlight"]),
    ],
)
data class ComicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: Long = 0,
    val cid: String,
    val title: String,
    val cover: String = "",
    val author: String? = null,
    val intro: String? = null,
    @ColumnInfo(name = "update_text") val updateText: String? = null,
    @ColumnInfo(name = "update_at") val updateAt: Long? = null,
    val finish: Boolean? = null,
    val highlight: Boolean = false,
    val local: Boolean = false,
    @ColumnInfo(name = "favorite_at") val favoriteAt: Long? = null,
    @ColumnInfo(name = "history_at") val historyAt: Long? = null,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long? = null,
    @ColumnInfo(name = "last_chapter_path") val lastChapterPath: String? = null,
    @ColumnInfo(name = "last_chapter_title") val lastChapterTitle: String? = null,
    @ColumnInfo(name = "last_chapter_index") val lastChapterIndex: Int = 0,
    @ColumnInfo(name = "last_page") val lastPage: Int = 0,
    @ColumnInfo(name = "chapter_count") val chapterCount: Int = 0,
    val url: String? = null,
)

@Entity(
    tableName = "chapter",
    foreignKeys = [
        ForeignKey(
            entity = ComicEntity::class,
            parentColumns = ["id"],
            childColumns = ["comic_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["comic_id", "path"], unique = true),
        Index(value = ["comic_id", "id"]),
        Index(value = ["comic_id", "downloaded"]),
    ],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "comic_id") val comicId: Long,
    val title: String,
    val path: String,
    @ColumnInfo(name = "source_group") val sourceGroup: String = "",
    val count: Int = 0,
    val complete: Boolean = false,
    val downloaded: Boolean = false,
    @ColumnInfo(name = "last_page") val lastPage: Int = 0,
    @ColumnInfo(name = "read_at") val readAt: Long? = null,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long = 0,
)

@Entity(
    tableName = "tag",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sort: Int = 0,
)

@Entity(
    tableName = "tag_ref",
    primaryKeys = ["comic_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = ComicEntity::class,
            parentColumns = ["id"],
            childColumns = ["comic_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class TagRefEntity(
    @ColumnInfo(name = "comic_id") val comicId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(
    tableName = "download_task",
    foreignKeys = [
        ForeignKey(
            entity = ComicEntity::class,
            parentColumns = ["id"],
            childColumns = ["comic_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chapter_id"], unique = true),
        Index(value = ["state", "created_at"]),
        Index(value = ["comic_id"]),
    ],
)
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "comic_id") val comicId: Long,
    @ColumnInfo(name = "chapter_id") val chapterId: Long,
    val source: Long,
    val cid: String,
    @ColumnInfo(name = "chapter_path") val chapterPath: String,
    val title: String,
    val state: Int,
    val total: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    @ColumnInfo(name = "target_dir") val targetDir: String,
    @ColumnInfo(name = "error_code") val errorCode: String? = null,
    @ColumnInfo(name = "error_msg") val errorMsg: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["keyword", "source"], unique = true),
        Index(value = ["created_at"]),
    ],
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val source: Long = -1,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "local_root",
    indices = [Index(value = ["uri"], unique = true)],
)
data class LocalRootEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val label: String,
    val enabled: Boolean = true,
    @ColumnInfo(name = "last_scan") val lastScan: Long? = null,
    @ColumnInfo(name = "last_count") val lastCount: Int = 0,
)

/**
 * 小型键值元数据（last backup、install id 等），不放业务实体。
 */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String?,
)
