package io.github.saonte.mangareader.feature.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.saonte.mangareader.R
import io.github.saonte.mangareader.designsystem.component.EmptyState
import io.github.saonte.mangareader.model.Comic
import io.github.saonte.mangareader.ui.components.ScreenHeader

@Composable
fun ShelfScreen(
    onOpenDetail: (sourceType: Long, cid: String) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShelfViewModel = hiltViewModel(),
) {
    val shelf by viewModel.shelf.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val updatesOnly by viewModel.updatesOnly.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            ScreenHeader(
                title = stringResource(R.string.nav_shelf),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = !checking && shelf.isNotEmpty(),
                onClick = viewModel::checkUpdates,
            ) {
                Text(if (checking) "检查中…" else "检查更新")
            }
            TextButton(onClick = viewModel::toggleUpdatesOnly) {
                Text(if (updatesOnly) "全部" else "仅更新")
            }
        }
        message?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::dismissMessage) { Text("知道了") }
            }
        }
        val visibleShelf = if (updatesOnly) shelf.filter { it.highlight } else shelf
        if (shelf.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.shelf_empty_title),
                body = stringResource(R.string.shelf_empty_body),
                actionLabel = stringResource(R.string.nav_browse),
                onAction = onBrowse,
            )
        } else if (visibleShelf.isEmpty()) {
            EmptyState(
                title = "暂无更新",
                body = "点击「检查更新」后，有新章节的漫画会出现在这里。",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(visibleShelf, key = { it.id }) { comic ->
                    ShelfItem(comic, onClick = { onOpenDetail(comic.source, comic.cid) })
                }
            }
        }
    }
}

@Composable
private fun ShelfItem(comic: Comic, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth(),
    ) {
        AsyncImage(
            model = comic.cover,
            contentDescription = comic.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = comic.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = comic.lastChapterTitle ?: comic.updateText.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
