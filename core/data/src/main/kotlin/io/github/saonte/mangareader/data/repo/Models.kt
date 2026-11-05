package io.github.saonte.mangareader.data.repo

import io.github.saonte.mangareader.model.SourceHealth

/**
 * 已安装源的业务视图（不含规则 JSON 的解析结果）。
 */
data class Source(
    val type: Long,
    val ruleId: String,
    val title: String,
    val version: String,
    val schemaVersion: Int,
    val engineMin: Int,
    val baseUrl: String?,
    val json: String,
    val enabled: Boolean,
    val trust: Int,
    val health: SourceHealth,
    val failStreak: Int,
    val lastErrorCode: String?,
    val installedAt: Long,
    val lastUsedAt: Long?,
    val sort: Int,
)

/**
 * 待安装/升级的规则包。解析后的强类型 [io.github.saonte.mangareader.model.SourceRule]
 * 由 :core:source 生成，data 层只负责持久化。
 */
data class InstalledRule(
    val ruleId: String,
    val title: String,
    val version: String,
    val schemaVersion: Int,
    val engineMin: Int,
    val baseUrl: String?,
    val json: String,
    val sha256: String,
    val originUrl: String?,
    val trust: Int = 1,
    /** 0 = JSON 规则，1 = CJS 模块源。 */
    val kind: Int = 0,
)
