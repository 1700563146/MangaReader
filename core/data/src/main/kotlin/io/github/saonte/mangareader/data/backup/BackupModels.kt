package io.github.saonte.mangareader.data.backup

import kotlinx.serialization.Serializable

const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupManifest(
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val appVersion: String = "0.8.0",
    val dbVersion: Int = 2,
    val exportedAt: Long = 0,
    val counts: BackupCounts = BackupCounts(),
    val checksum: String = "",
)

@Serializable
data class BackupCounts(
    val sources: Int = 0,
    val comics: Int = 0,
    val chapters: Int = 0,
    val tags: Int = 0,
)

@Serializable
data class BackupSource(
    val ruleId: String,
    val title: String,
    val version: String,
    val schemaVersion: Int,
    val engineMin: Int,
    val baseUrl: String? = null,
    val json: String,
    val sha256: String,
    val enabled: Boolean = true,
    val trust: Int = 1,
    val sort: Int = 0,
    val health: Int = 0,
    val failStreak: Int = 0,
)

@Serializable
data class BackupProgress(
    val chapterPath: String,
    val chapterTitle: String,
    val chapterIndex: Int,
    val page: Int,
)

@Serializable
data class BackupChapter(
    val path: String,
    val title: String,
    val group: String = "",
    val count: Int = 0,
    val readAt: Long? = null,
    val lastPage: Int = 0,
)

@Serializable
data class BackupComic(
    val sourceRuleId: String,
    val cid: String,
    val title: String,
    val cover: String = "",
    val author: String? = null,
    val intro: String? = null,
    val updateText: String? = null,
    val finish: Boolean? = null,
    val favoriteAt: Long? = null,
    val historyAt: Long? = null,
    val progress: BackupProgress? = null,
    val chapters: List<BackupChapter> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class BackupTag(
    val name: String,
)

@Serializable
data class BackupLibrary(
    val comics: List<BackupComic> = emptyList(),
)

@Serializable
data class BackupTags(
    val tags: List<BackupTag> = emptyList(),
)

@Serializable
data class BackupSettings(
    val themeMode: String? = null,
)

@Serializable
data class BackupBundle(
    val sources: List<BackupSource> = emptyList(),
    val library: BackupLibrary = BackupLibrary(),
    val tags: BackupTags = BackupTags(),
    val settings: BackupSettings = BackupSettings(),
)

enum class ImportMode {
    MERGE,
    REPLACE,
    RULES_ONLY,
}

data class BackupResult(
    val success: Boolean,
    val message: String,
    val importedSources: Int = 0,
    val importedComics: Int = 0,
)
