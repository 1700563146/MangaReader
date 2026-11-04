package io.github.saonte.mangareader.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.saonte.mangareader.source.SourceEngine
import io.github.saonte.mangareader.source.http.SourceHttpClient
import io.github.saonte.mangareader.source.rule.JsonRuleEngine
import io.github.saonte.mangareader.source.rule.RuleCompiler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideHttpClient(): SourceHttpClient =
        SourceHttpClient(SourceHttpClient.defaultClient())

    @Provides
    @Singleton
    fun provideSourceEngine(client: SourceHttpClient): SourceEngine =
        JsonRuleEngine(client)

    @Provides
    @Singleton
    fun provideRuleCompiler(): RuleCompiler = RuleCompiler()
}
