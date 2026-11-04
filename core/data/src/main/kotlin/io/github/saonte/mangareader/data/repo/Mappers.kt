package io.github.saonte.mangareader.data.repo

import io.github.saonte.mangareader.data.db.ChapterEntity
import io.github.saonte.mangareader.data.db.ComicEntity
import io.github.saonte.mangareader.data.db.SourceEntity
import io.github.saonte.mangareader.model.Chapter
import io.github.saonte.mangareader.model.Comic
import io.github.saonte.mangareader.model.SourceHealth

internal fun ComicEntity.toDomain(): Comic = Comic(
    id = id,
    source = source,
    cid = cid,
    title = title,
    cover = cover,
    author = author,
    intro = intro,
    updateText = updateText,
    updateAt = updateAt,
    finish = finish,
    highlight = highlight,
    local = local,
    favoriteAt = favoriteAt,
    historyAt = historyAt,
    downloadedAt = downloadedAt,
    lastChapterPath = lastChapterPath,
    lastChapterTitle = lastChapterTitle,
    lastChapterIndex = lastChapterIndex,
    lastPage = lastPage,
    chapterCount = chapterCount,
    url = url,
)

internal fun Comic.toEntity(): ComicEntity = ComicEntity(
    id = id,
    source = source,
    cid = cid,
    title = title,
    cover = cover,
    author = author,
    intro = intro,
    updateText = updateText,
    updateAt = updateAt,
    finish = finish,
    highlight = highlight,
    local = local,
    favoriteAt = favoriteAt,
    historyAt = historyAt,
    downloadedAt = downloadedAt,
    lastChapterPath = lastChapterPath,
    lastChapterTitle = lastChapterTitle,
    lastChapterIndex = lastChapterIndex,
    lastPage = lastPage,
    chapterCount = chapterCount,
    url = url,
)

internal fun ChapterEntity.toDomain(): Chapter = Chapter(
    id = id,
    comicId = comicId,
    title = title,
    path = path,
    sourceGroup = sourceGroup,
    count = count,
    complete = complete,
    downloaded = downloaded,
    lastPage = lastPage,
    readAt = readAt,
    sizeBytes = sizeBytes,
)

internal fun Chapter.toEntity(): ChapterEntity = ChapterEntity(
    id = id,
    comicId = comicId,
    title = title,
    path = path,
    sourceGroup = sourceGroup,
    count = count,
    complete = complete,
    downloaded = downloaded,
    lastPage = lastPage,
    readAt = readAt,
    sizeBytes = sizeBytes,
)

internal fun SourceEntity.toDomain(): Source = Source(
    type = type,
    ruleId = ruleId,
    title = title,
    version = version,
    schemaVersion = schemaVersion,
    engineMin = engineMin,
    baseUrl = baseUrl,
    json = json,
    enabled = enabled,
    trust = trust,
    health = when (health) {
        SourceEntity.HEALTH_HEALTHY -> SourceHealth.HEALTHY
        SourceEntity.HEALTH_DEGRADED -> SourceHealth.DEGRADED
        else -> SourceHealth.DISABLED
    },
    failStreak = failStreak,
    lastErrorCode = lastErrorCode,
    installedAt = installedAt,
    lastUsedAt = lastUsedAt,
    sort = sort,
)
