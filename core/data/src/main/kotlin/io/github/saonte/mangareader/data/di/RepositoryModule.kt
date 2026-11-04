package io.github.saonte.mangareader.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.saonte.mangareader.data.repo.ChapterRepository
import io.github.saonte.mangareader.data.repo.ChapterRepositoryImpl
import io.github.saonte.mangareader.data.repo.ComicRepository
import io.github.saonte.mangareader.data.repo.ComicRepositoryImpl
import io.github.saonte.mangareader.data.repo.SourceRepository
import io.github.saonte.mangareader.data.repo.SourceRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindComicRepository(impl: ComicRepositoryImpl): ComicRepository

    @Binds
    @Singleton
    abstract fun bindChapterRepository(impl: ChapterRepositoryImpl): ChapterRepository

    @Binds
    @Singleton
    abstract fun bindSourceRepository(impl: SourceRepositoryImpl): SourceRepository
}
