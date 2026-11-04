package io.github.saonte.mangareader.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.saonte.mangareader.data.db.AppMetaDao
import io.github.saonte.mangareader.data.db.ChapterDao
import io.github.saonte.mangareader.data.db.ComicDao
import io.github.saonte.mangareader.data.db.DownloadTaskDao
import io.github.saonte.mangareader.data.db.LocalRootDao
import io.github.saonte.mangareader.data.db.MIGRATION_1_2
import io.github.saonte.mangareader.data.db.MangaDatabase
import io.github.saonte.mangareader.data.db.SearchHistoryDao
import io.github.saonte.mangareader.data.db.SourceDao
import io.github.saonte.mangareader.data.db.TagDao
import io.github.saonte.mangareader.data.db.TagRefDao
import io.github.saonte.mangareader.data.settings.settingsDataStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MangaDatabase =
        Room.databaseBuilder(context, MangaDatabase::class.java, MangaDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideSourceDao(database: MangaDatabase): SourceDao = database.sourceDao()

    @Provides
    fun provideComicDao(database: MangaDatabase): ComicDao = database.comicDao()

    @Provides
    fun provideChapterDao(database: MangaDatabase): ChapterDao = database.chapterDao()

    @Provides
    fun provideTagDao(database: MangaDatabase): TagDao = database.tagDao()

    @Provides
    fun provideTagRefDao(database: MangaDatabase): TagRefDao = database.tagRefDao()

    @Provides
    fun provideDownloadTaskDao(database: MangaDatabase): DownloadTaskDao = database.downloadTaskDao()

    @Provides
    fun provideSearchHistoryDao(database: MangaDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides
    fun provideLocalRootDao(database: MangaDatabase): LocalRootDao = database.localRootDao()

    @Provides
    fun provideAppMetaDao(database: MangaDatabase): AppMetaDao = database.appMetaDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}
