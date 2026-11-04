package io.github.saonte.mangareader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SourceEntity::class,
        ComicEntity::class,
        ChapterEntity::class,
        TagEntity::class,
        TagRefEntity::class,
        DownloadTaskEntity::class,
        SearchHistoryEntity::class,
        LocalRootEntity::class,
        AppMetaEntity::class,
    ],
    version = MangaDatabase.VERSION,
    exportSchema = true,
)
abstract class MangaDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun comicDao(): ComicDao
    abstract fun chapterDao(): ChapterDao
    abstract fun tagDao(): TagDao
    abstract fun tagRefDao(): TagRefDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun localRootDao(): LocalRootDao
    abstract fun appMetaDao(): AppMetaDao

    companion object {
        const val NAME = "mangareader.db"
        const val VERSION = 2
    }
}
