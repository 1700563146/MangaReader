package io.github.saonte.mangareader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 数据库 v1 仅含元数据表；漫画/章节/标签/下载表在 M1 按 docs/04-data-model.md 增加，
 * 届时通过显式 Migration 升级（禁止 destructive migration）。
 */
@Database(
    entities = [AppMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MangaDatabase : RoomDatabase() {
    abstract fun appMetaDao(): AppMetaDao

    companion object {
        const val NAME = "mangareader.db"
    }
}
