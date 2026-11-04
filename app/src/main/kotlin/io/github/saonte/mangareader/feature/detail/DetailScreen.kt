package io.github.saonte.mangareader.feature.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.saonte.mangareader.R
import io.github.saonte.mangareader.model.Chapter
import io.github.saonte.mangareader.storage.DownloadState
import io.github.saonte.mangareader.storage.DownloadTask
import io.github.saonte.mangareader.ui.components.ScreenHeader

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onOpenReader: (comicId: Long, chapterPath: String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val comic = state.comic

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.action_back)) }
            Spacer(modifier = Modifier.weight(1f))
            if (comic != null) {
                TextButton(onClick = viewModel::toggleFavorite) {
                    Text(if (state.favorite) stringResource(R.string.detail_favorited) else stringResource(R.string.detail_favorite))
                }
            }
        }

        if (state.loading) {
            ScreenHeader(title = stringResource(R.string.detail_loading))
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (state.selectionMode) {
                TextButton(onClick = viewModel::clearChapterSelection) { Text(stringResource(R.string.action_cancel)) }
                TextButton(onClick = viewModel::downloadSelected) {
                    Text(stringResource(R.string.detail_download_count, state.selectedChapters.size))
                }
            } else {
                TextButton(onClick = viewModel::toggleChapterOrder) {
                    Text(if (state.chaptersDescending) stringResource(R.string.detail_order_asc) else stringResource(R.string.detail_order_desc))
                }
            }
            TextButton(
                enabled = state.chapters.isNotEmpty(),
                onClick = viewModel::downloadAll,
            ) { Text(stringResource(R.string.detail_download_all)) }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        state.error?.let { error ->
            Column(modifier = Modifier.padding(24.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::load) { Text(stringResource(R.string.action_retry_short)) }
            }
            return@Column
        }

        if (comic == null) return@Column

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = comic.cover,
                contentDescription = comic.title,
                modifier = Modifier
                    .size(width = 96.dp, height = 136.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                comic.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                comic.updateText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    enabled = state.chapters.isNotEmpty(),
                    onClick = { viewModel.continueReading(onOpenReader) },
                ) {
                    Text(if (comic.lastChapterPath != null) stringResource(R.string.detail_continue_reading) else stringResource(R.string.detail_start_reading))
                }
            }
        }

        comic.intro?.takeIf { it.isNotBlank() }?.let { intro ->
            Text(
                text = intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.detail_chapters_count, state.chapters.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (comic.lastChapterTitle != null) stringResource(R.string.detail_last_chapter, comic.lastChapterTitle.orEmpty()) else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.chapters, key = { _, chapter -> chapter.path }) { index, chapter ->
                ChapterRow(
                    chapter = chapter,
                    download = state.downloads[chapter.id],
                    selected = chapter.id in state.selectedChapters,
                    selectionMode = state.selectionMode,
                    onClick = {
                        if (state.selectionMode) {
                            viewModel.toggleChapterSelection(chapter.id)
                        } else {
                            viewModel.openChapter(chapter, index, onOpenReader)
                        }
                    },
                    onLongClick = { viewModel.toggleChapterSelection(chapter.id) },
                    onDownloadAction = { viewModel.onDownloadAction(chapter) },
                )
                HorizontalDivider()
            }
            if (state.chapters.isEmpty()) {
                item { Text(stringResource(R.string.detail_no_chapters), modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: Chapter,
    download: DownloadTask?,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = chapter.title, style = MaterialTheme.typography.bodyLarge)
            if (chapter.sourceGroup.isNotBlank()) {
                Text(
                    text = chapter.sourceGroup,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        if (chapter.readAt != null) {
            Text(
                text = stringResource(R.string.detail_read),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onDownloadAction) {
            Text(
                text = when (download?.state) {
                    null -> stringResource(R.string.detail_download)
                    DownloadState.COMPLETED -> stringResource(R.string.detail_downloaded)
                    DownloadState.RUNNING, DownloadState.QUEUED ->
                        if (download.total > 0) "${download.done}/${download.total}" else stringResource(R.string.detail_downloading)

                    DownloadState.PAUSED -> stringResource(R.string.action_resume)
                    DownloadState.FAILED -> stringResource(R.string.action_retry_short)
                    DownloadState.CANCELLED -> stringResource(R.string.detail_redownload)
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
