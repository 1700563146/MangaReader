package io.github.saonte.mangareader.domain

import io.github.saonte.mangareader.data.repo.InstalledRule
import io.github.saonte.mangareader.data.repo.Source
import io.github.saonte.mangareader.data.repo.SourceRepository
import io.github.saonte.mangareader.source.SourceRef
import io.github.saonte.mangareader.source.rule.RuleCompiler
import io.github.saonte.mangareader.source.rule.RuleCompileResult
import io.github.saonte.mangareader.source.rule.RuleIssue
import io.github.saonte.mangareader.source.rule.RuleSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface InstallResult {
    data class Ok(val type: Long, val name: String) : InstallResult
    data class Invalid(val issues: List<RuleIssue>) : InstallResult
}

/**
 * 规则的安装/编译/缓存：UI 只拿到 [Source] 与 [SourceRef]，不直接碰 JSON。
 * 编译结果按 type 缓存；规则升级/删除时失效。
 */
@Singleton
class SourceManager @Inject constructor(
    private val sources: SourceRepository,
    private val compiler: RuleCompiler = RuleCompiler(),
) {
    private val cache = ConcurrentHashMap<Long, RuleSpec>()

    fun observeSources(): Flow<List<Source>> = sources.observeAll()

    fun observeEnabled(): Flow<List<Source>> = sources.observeEnabled()

    suspend fun source(type: Long): Source? = sources.get(type)

    suspend fun ref(type: Long): SourceRef? {
        val cached = cache[type]
        if (cached != null) return SourceRef(type, cached)
        val source = sources.get(type) ?: return null
        val rule = compileOrNull(source.json) ?: return null
        cache[type] = rule
        return SourceRef(type, rule)
    }

    suspend fun enabledRefs(): List<Pair<Source, SourceRef>> =
        sources.observeEnabled().first().mapNotNull { source ->
            ref(source.type)?.let { source to it }
        }

    suspend fun install(raw: String, origin: String? = null): InstallResult {
        val result = compiler.compile(raw)
        if (result is RuleCompileResult.Failure) return InstallResult.Invalid(result.issues)
        val rule = (result as RuleCompileResult.Success).rule
        val type = sources.install(
            InstalledRule(
                ruleId = rule.id,
                title = rule.name,
                version = rule.version,
                schemaVersion = rule.schemaVersion,
                engineMin = rule.minEngine,
                baseUrl = rule.baseUrl,
                json = raw,
                sha256 = sha256(raw),
                originUrl = origin,
            ),
        )
        cache[type] = rule
        return InstallResult.Ok(type, rule.name)
    }

    suspend fun setEnabled(type: Long, enabled: Boolean) = sources.setEnabled(type, enabled)

    suspend fun delete(type: Long) {
        sources.delete(type)
        cache.remove(type)
    }

    fun invalidate(type: Long) {
        cache.remove(type)
    }

    private fun compileOrNull(raw: String): RuleSpec? =
        (compiler.compile(raw) as? RuleCompileResult.Success)?.rule

    private fun sha256(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
