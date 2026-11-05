package io.github.saonte.mangareader.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.saonte.mangareader.data.db.ChapterDao
import io.github.saonte.mangareader.data.db.ChapterEntity
import io.github.saonte.mangareader.data.db.ComicDao
import io.github.saonte.mangareader.data.db.ComicEntity
import io.github.saonte.mangareader.data.db.MangaDatabase
import io.github.saonte.mangareader.data.db.SourceDao
import io.github.saonte.mangareader.data.db.SourceEntity
import io.github.saonte.mangareader.data.db.TagDao
import io.github.saonte.mangareader.data.db.TagEntity
import io.github.saonte.mangareader.data.db.TagRefDao
import io.github.saonte.mangareader.data.db.TagRefEntity
import io.github.saonte.mangareader.data.settings.SettingsRepository
import io.github.saonte.mangareader.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份/恢复：JSON + ZIP（manifest/sources/library/tags/settings）。
 * 不包含图片、Cookie、WebDAV 凭据；本地漫画的元数据不入库备份（文件不可迁移）。
 */
@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MangaDatabase,
    private val sources: SourceDao,
    private val comics: ComicDao,
    private val chapters: ChapterDao,
    private val tags: TagDao,
    private val tagRefs: TagRefDao,
    private val settings: SettingsRepository,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun export(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val bundle = collectBundle()
            val sourcesJson = json.encodeToString(ListSerializer(BackupSource.serializer()), bundle.sources)
            val libraryJson = json.encodeToString(BackupLibrary.serializer(), bundle.library)
            val tagsJson = json.encodeToString(BackupTags.serializer(), bundle.tags)
            val settingsJson = json.encodeToString(BackupSettings.serializer(), bundle.settings)
            val checksum = checksum(listOf(sourcesJson, libraryJson, tagsJson, settingsJson))
            val manifest = BackupManifest(
                exportedAt = System.currentTimeMillis(),
                counts = BackupCounts(
                    sources = bundle.sources.size,
                    comics = bundle.library.comics.size,
                    chapters = bundle.library.comics.sumOf { it.chapters.size },
                    tags = bundle.tags.tags.size,
                ),
                checksum = checksum,
            )
            val manifestJson = json.encodeToString(BackupManifest.serializer(), manifest)

            context.contentResolver.openOutputStream(uri)?.buffered()?.use { output ->
                ZipOutputStream(output).use { zip ->
                    writeEntry(zip, "manifest.json", manifestJson)
                    writeEntry(zip, "sources.json", sourcesJson)
                    writeEntry(zip, "library.json", libraryJson)
                    writeEntry(zip, "tags.json", tagsJson)
                    writeEntry(zip, "settings.json", settingsJson)
                }
            } ?: error("无法写入备份文件")
            BackupResult(
                success = true,
                message = "备份完成：${bundle.sources.size} 个规则，${bundle.library.comics.size} 部漫画",
                importedSources = bundle.sources.size,
                importedComics = bundle.library.comics.size,
            )
        }.getOrElse { error ->
            BackupResult(false, "备份失败：${error.message}")
        }
    }

    suspend fun import(uri: Uri, mode: ImportMode): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val entries = readEntries(uri)
            val manifestJson = entries["manifest.json"] ?: error("备份缺少 manifest.json")
            val manifest = json.decodeFromString(BackupManifest.serializer(), manifestJson)
            if (manifest.formatVersion > BACKUP_FORMAT_VERSION) {
                error("备份版本 ${manifest.formatVersion} 高于当前支持（$BACKUP_FORMAT_VERSION）")
            }
            val sourcesJson = entries["sources.json"] ?: "[]"
            val libraryJson = entries["library.json"] ?: "{}"
            val tagsJson = entries["tags.json"] ?: "{}"
            val settingsJson = entries["settings.json"] ?: "{}"
            val actual = checksum(listOf(sourcesJson, libraryJson, tagsJson, settingsJson))
            if (manifest.checksum.isNotBlank() && actual != manifest.checksum) {
                error("校验和不匹配，备份可能损坏或被修改")
            }
            val backupSources = json.decodeFromString(ListSerializer(BackupSource.serializer()), sourcesJson)
            val library = json.decodeFromString(BackupLibrary.serializer(), libraryJson)
            val backupTags = json.decodeFromString(BackupTags.serializer(), tagsJson)
            val backupSettings = json.decodeFromString(BackupSettings.serializer(), settingsJson)

            var importedSources = 0
            var importedComics = 0
            database.withTransaction {
                if (mode == ImportMode.REPLACE) {
                    comics.deleteAll()
                    tags.deleteAll()
                }
                val sourceMap = mutableMapOf<String, Long>()
                backupSources.forEach { source ->
                    val existing = sources.findByRuleId(source.ruleId)
                    val type = if (existing == null) {
                        sources.insert(
                            SourceEntity(
                                ruleId = source.ruleId,
                                title = source.title,
                                version = source.version,
                                schemaVersion = source.schemaVersion,
                                engineMin = source.engineMin,
                                baseUrl = source.baseUrl,
                                json = source.json,
                                sha256 = source.sha256,
                                enabled = source.enabled,
                                trust = source.trust,
                                installedAt = System.currentTimeMillis(),
                                sort = source.sort,
                            ),
                        )
                    } else {
                        sources.updateInstalled(
                            type = existing.type,
                            title = source.title,
                            version = source.version,
                            schemaVersion = source.schemaVersion,
                            engineMin = source.engineMin,
                            baseUrl = source.baseUrl,
                            json = source.json,
                            sha256 = source.sha256,
                            originUrl = existing.originUrl,
                            trust = source.trust,
                            usedAt = existing.lastUsedAt,
                            sourceKind = existing.sourceKind,
                        )
                        existing.type
                    }
                    sourceMap[source.ruleId] = type
                    importedSources += 1
                }

                if (mode != ImportMode.RULES_ONLY) {
                    backupTags.tags.forEach { tag ->
                        if (tags.findByName(tag.name) == null) {
                            tags.insert(TagEntity(name = tag.name))
                        }
                    }
                    library.comics.forEach { backup ->
                        val type = sourceMap[backup.sourceRuleId] ?: return@forEach
                        val existing = comics.find(type, backup.cid)
                        val favoriteAt = maxOfNullable(existing?.favoriteAt, backup.favoriteAt)
                        val historyAt = maxOfNullable(existing?.historyAt, backup.historyAt)
                        val base = existing ?: ComicEntity(source = type, cid = backup.cid, title = backup.title)
                        val merged = base.copy(
                            title = backup.title,
                            cover = backup.cover,
                            author = backup.author ?: base.author,
                            intro = backup.intro ?: base.intro,
                            updateText = backup.updateText ?: base.updateText,
                            finish = backup.finish ?: base.finish,
                            favoriteAt = favoriteAt,
                            historyAt = historyAt,
                            lastChapterPath = if ((existing?.historyAt ?: 0) <= (backup.historyAt ?: 0)) {
                                backup.progress?.chapterPath ?: existing?.lastChapterPath
                            } else {
                                existing?.lastChapterPath
                            },
                            lastChapterTitle = if ((existing?.historyAt ?: 0) <= (backup.historyAt ?: 0)) {
                                backup.progress?.chapterTitle ?: existing?.lastChapterTitle
                            } else {
                                existing?.lastChapterTitle
                            },
                            lastChapterIndex = if ((existing?.historyAt ?: 0) <= (backup.historyAt ?: 0)) {
                                backup.progress?.chapterIndex ?: existing?.lastChapterIndex ?: 0
                            } else {
                                existing?.lastChapterIndex ?: 0
                            },
                            lastPage = if ((existing?.historyAt ?: 0) <= (backup.historyAt ?: 0)) {
                                backup.progress?.page ?: existing?.lastPage ?: 0
                            } else {
                                existing?.lastPage ?: 0
                            },
                            chapterCount = backup.chapters.size,
                        )
                        val comicId = comics.upsert(merged)
                        backup.chapters.forEach { chapter ->
                            val found = chapters.findByPath(comicId, chapter.path)
                            if (found == null) {
                                chapters.insertIgnore(
                                    ChapterEntity(
                                        comicId = comicId,
                                        title = chapter.title,
                                        path = chapter.path,
                                        sourceGroup = chapter.group,
                                        count = chapter.count,
                                        complete = chapter.count > 0,
                                        readAt = chapter.readAt,
                                        lastPage = chapter.lastPage,
                                    ),
                                )
                            } else if ((chapter.readAt ?: 0) >= (found.readAt ?: 0)) {
                                chapters.savePage(found.id, chapter.lastPage, chapter.readAt ?: 0)
                            }
                        }
                        val tagIds = backup.tags.mapNotNull { tags.findByName(it)?.id }
                        if (tagIds.isNotEmpty()) {
                            tagRefs.unlinkComic(comicId)
                            tagIds.forEach { tagId -> tagRefs.link(TagRefEntity(comicId, tagId)) }
                        }
                        importedComics += 1
                    }
                }

                backupSettings.themeMode?.let { raw ->
                    ThemeMode.entries.firstOrNull { it.name == raw }?.let { settings.setThemeMode(it) }
                }
            }

            BackupResult(
                success = true,
                message = "恢复完成：${importedSources} 个规则，${importedComics} 部漫画",
                importedSources = importedSources,
                importedComics = importedComics,
            )
        }.getOrElse { error ->
            BackupResult(false, "恢复失败：${error.message}")
        }
    }

    private suspend fun collectBundle(): BackupBundle {
        val sourceList = sources.listAll().map { source ->
            BackupSource(
                ruleId = source.ruleId,
                title = source.title,
                version = source.version,
                schemaVersion = source.schemaVersion,
                engineMin = source.engineMin,
                baseUrl = source.baseUrl,
                json = source.json,
                sha256 = source.sha256,
                enabled = source.enabled,
                trust = source.trust,
                sort = source.sort,
                health = source.health,
                failStreak = source.failStreak,
            )
        }

        val tagList = tags.listAll()
        val tagNames = tagList.associate { it.id to it.name }
        val refs = tagRefs.listAll().groupBy({ it.comicId }, { it.tagId })
        val typeToRule = sources.listAll().associate { it.type to it.ruleId }

        val comicEntities = comics.listAll().filter { !it.local }
        val libraryComics = comicEntities.map { comic ->
            val chapterList = chapters.list(comic.id)
            val progress = comic.lastChapterPath?.let { path ->
                BackupProgress(
                    chapterPath = path,
                    chapterTitle = comic.lastChapterTitle.orEmpty(),
                    chapterIndex = comic.lastChapterIndex,
                    page = comic.lastPage,
                )
            }
            BackupComic(
                sourceRuleId = typeToRule[comic.source] ?: run {
                    // 源已被删除的孤立漫画：不备份，避免导入后无源可用。
                    return@map null
                },
                cid = comic.cid,
                title = comic.title,
                cover = comic.cover,
                author = comic.author,
                intro = comic.intro,
                updateText = comic.updateText,
                finish = comic.finish,
                favoriteAt = comic.favoriteAt,
                historyAt = comic.historyAt,
                progress = progress,
                chapters = chapterList.map { chapter ->
                    BackupChapter(
                        path = chapter.path,
                        title = chapter.title,
                        group = chapter.sourceGroup,
                        count = chapter.count,
                        readAt = chapter.readAt,
                        lastPage = chapter.lastPage,
                    )
                },
                tags = refs[comic.id].orEmpty().mapNotNull { tagNames[it] },
            )
        }.filterNotNull()

        val usedTagNames = libraryComics.flatMap { it.tags }.toSet()
        return BackupBundle(
            sources = sourceList,
            library = BackupLibrary(libraryComics),
            tags = BackupTags(tagList.filter { it.name in usedTagNames }.map { BackupTag(it.name) }),
            settings = BackupSettings(themeMode = settings.themeMode.first().name),
        )
    }

    private fun readEntries(uri: Uri): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var total = 0L
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val bytes = zip.readBytes()
                    total += bytes.size
                    if (total > MAX_ENTRY_BYTES) error("备份文件过大")
                    result[entry.name] = bytes.toString(Charsets.UTF_8)
                    zip.closeEntry()
                }
            }
        } ?: error("无法读取备份文件")
        return result
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun checksum(parts: List<String>): String =
        MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("").toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun maxOfNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    private companion object {
        const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
    }
}
