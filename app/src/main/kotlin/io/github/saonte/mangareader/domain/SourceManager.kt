package io.github.saonte.mangareader.domain

import io.github.saonte.mangareader.data.repo.InstalledRule
import io.github.saonte.mangareader.data.repo.Source
import io.github.saonte.mangareader.data.repo.SourceRepository
import io.github.saonte.mangareader.source.RuleSourceRef
import io.github.saonte.mangareader.source.SourceRef
import io.github.saonte.mangareader.source.rule.RuleCompiler
import io.github.saonte.mangareader.source.rule.RuleCompileResult
import io.github.saonte.mangareader.source.rule.RuleIssue
import io.github.saonte.mangareader.source.rule.RevocationList
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
    private val revocations: RevocationList = RevocationList.EMPTY,
) {
    private val cache = ConcurrentHashMap<Long, RuleSpec>()

    fun observeSources(): Flow<List<Source>> = sources.observeAll()

    fun observeEnabled(): Flow<List<Source>> = sources.observeEnabled()

    suspend fun source(type: Long): Source? = sources.get(type)

    suspend fun ref(type: Long): SourceRef? {
        val cached = cache[type]
        if (cached != null) return RuleSourceRef(type, cached)
        val source = sources.get(type) ?: return null
        val rule = compileOrNull(source.json) ?: return null
        if (revocations.isRevoked(rule.id, rule.version)) return null
        cache[type] = rule
        return RuleSourceRef(type, rule)
    }

    suspend fun enabledRefs(): List<Pair<Source, SourceRef>> =
        sources.observeEnabled().first().mapNotNull { source ->
            ref(source.type)?.let { source to it }
        }

    suspend fun install(raw: String, origin: String? = null): InstallResult {
        val result = compiler.compile(raw)
        if (result is RuleCompileResult.Failure) return InstallResult.Invalid(result.issues)
        val rule = (result as RuleCompileResult.Success).rule
        if (revocations.isRevoked(rule.id, rule.version)) {
            return InstallResult.Invalid(
                listOf(
                    RuleIssue(
                        severity = io.github.saonte.mangareader.source.rule.IssueSeverity.ERROR,
                        pointer = "/id",
                        message = "该规则已被撤销，无法安装",
                    ),
                ),
            )
        }
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

    /**
     * 安装 CJS 模块源。
     *
     * 当前引擎（Rhino）不支持 `class` / `async`，因此这里只**登记**源，不编译也不启用；
     * 运行时 `ref()` 会返回 null，UI 提示「需要更高版本的引擎」。
     * 这样用户导入的源库不会白丢，未来接入 QuickJS 后可直接启用。
     */
    suspend fun installCjs(
        sourceId: String,
        name: String,
        version: String,
        script: String,
        origin: String?,
    ): InstallResult {
        if (sourceId.isBlank()) {
            return InstallResult.Invalid(
                listOf(
                    RuleIssue(
                        severity = io.github.saonte.mangareader.source.rule.IssueSeverity.ERROR,
                        pointer = "/key",
                        message = "源缺少 id/key",
                    ),
                ),
            )
        }
        val type = sources.install(
            InstalledRule(
                ruleId = sourceId,
                title = name,
                version = version,
                schemaVersion = CJS_SCHEMA_VERSION,
                engineMin = CJS_MIN_ENGINE,
                baseUrl = null,
                json = script,
                sha256 = sha256(script),
                originUrl = origin,
                trust = 1,
                kind = 1,
            ),
        )
        // 不启用：引擎尚不能执行
        sources.setEnabled(type, false)
        return InstallResult.Ok(type, name)
    }

    suspend fun setEnabled(type: Long, enabled: Boolean) = sources.setEnabled(type, enabled)

    suspend fun isRevoked(type: Long): Boolean {
        val source = sources.get(type) ?: return false
        val rule = cache[type] ?: compileOrNull(source.json)?.also { cache[type] = it } ?: return false
        return revocations.isRevoked(rule.id, rule.version)
    }

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

    private companion object {
        /** CJS 源记录的 schemaVersion 占位（与 JSON 规则的 1 区分）。 */
        const val CJS_SCHEMA_VERSION = 0

        /** CJS 源声明的最低引擎版本，未来由 QuickJS 引擎满足。 */
        const val CJS_MIN_ENGINE = 2
    }
}
