package io.github.saonte.mangareader.data.repo

import io.github.saonte.mangareader.data.db.ComicDao
import io.github.saonte.mangareader.data.db.SourceDao
import io.github.saonte.mangareader.data.db.SourceEntity
import io.github.saonte.mangareader.model.SourceHealth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface SourceRepository {
    fun observeAll(): Flow<List<Source>>
    fun observeEnabled(): Flow<List<Source>>
    suspend fun get(type: Long): Source?
    suspend fun install(rule: InstalledRule): Long
    suspend fun setEnabled(type: Long, enabled: Boolean)
    suspend fun recordSuccess(type: Long)
    suspend fun recordFailure(type: Long, errorCode: String)
    suspend fun delete(type: Long)
}

@Singleton
class SourceRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao,
    private val comicDao: ComicDao,
) : SourceRepository {

    override fun observeAll(): Flow<List<Source>> =
        sourceDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeEnabled(): Flow<List<Source>> =
        sourceDao.observeEnabled().map { list -> list.map { it.toDomain() } }

    override suspend fun get(type: Long): Source? = sourceDao.find(type)?.toDomain()

    override suspend fun install(rule: InstalledRule): Long {
        val now = System.currentTimeMillis()
        val existing = sourceDao.findByRuleId(rule.ruleId)
        if (existing != null) {
            sourceDao.updateInstalled(
                type = existing.type,
                title = rule.title,
                version = rule.version,
                schemaVersion = rule.schemaVersion,
                engineMin = rule.engineMin,
                baseUrl = rule.baseUrl,
                json = rule.json,
                sha256 = rule.sha256,
                originUrl = rule.originUrl,
                trust = rule.trust,
                usedAt = existing.lastUsedAt,
                sourceKind = rule.kind,
            )
            return existing.type
        }
        return sourceDao.insert(
            SourceEntity(
                ruleId = rule.ruleId,
                title = rule.title,
                version = rule.version,
                schemaVersion = rule.schemaVersion,
                engineMin = rule.engineMin,
                baseUrl = rule.baseUrl,
                json = rule.json,
                sha256 = rule.sha256,
                originUrl = rule.originUrl,
                trust = rule.trust,
                sourceKind = rule.kind,
                installedAt = now,
            ),
        )
    }

    override suspend fun setEnabled(type: Long, enabled: Boolean) = sourceDao.setEnabled(type, enabled)

    override suspend fun recordSuccess(type: Long) {
        sourceDao.updateHealth(
            type = type,
            health = SourceEntity.HEALTH_HEALTHY,
            failStreak = 0,
            lastFailAt = null,
            lastErrorCode = null,
        )
        sourceDao.touch(type, System.currentTimeMillis())
    }

    override suspend fun recordFailure(type: Long, errorCode: String) {
        val current = sourceDao.find(type) ?: return
        val streak = current.failStreak + 1
        val health = if (streak >= DEGRADED_THRESHOLD) {
            SourceEntity.HEALTH_DEGRADED
        } else {
            current.health
        }
        sourceDao.updateHealth(
            type = type,
            health = health,
            failStreak = streak,
            lastFailAt = System.currentTimeMillis(),
            lastErrorCode = errorCode,
        )
    }

    /**
     * 删除源时显式清理其漫画（comic.source 没有外键，避免本地漫画 source=0 违约）。
     */
    override suspend fun delete(type: Long) {
        comicDao.listBySource(type).forEach { comicDao.delete(it.id) }
        sourceDao.delete(type)
    }

    private companion object {
        const val DEGRADED_THRESHOLD = 3
    }
}
