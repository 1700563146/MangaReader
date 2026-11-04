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
import io.github.saonte.mangareader.data.db.MangaDatabase
import io.github.saonte.mangareader.data.settings.settingsDataStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MangaDatabase =
        Room.databaseBuilder(context, MangaDatabase::class.java, MangaDatabase.NAME).build()

    @Provides
    fun provideAppMetaDao(database: MangaDatabase): AppMetaDao = database.appMetaDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}
