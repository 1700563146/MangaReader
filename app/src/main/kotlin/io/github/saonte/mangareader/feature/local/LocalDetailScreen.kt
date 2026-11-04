package io.github.saonte.mangareader.feature.local

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

@Composable
fun LocalDetailScreen(
    onBack: () -> Unit,
    onOpenReader: (comicId: Long, chapterPath: String) -> Unit,
    viewModel: LocalDetailViewModel = hiltViewModel(),
) {
    val comic by viewModel.comic.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("返回") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = comic?.cover,
                contentDescription = comic?.title,
                modifier = Modifier
                    .size(width = 96.dp, height = 136.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(comic?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "本地 · ${chapters.size} 话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    enabled = chapters.isNotEmpty(),
                    onClick = {
                        chapters.firstOrNull()?.let { onOpenReader(it.comicId, it.path) }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("开始阅读") }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(chapters, key = { _, chapter -> chapter.path }) { _, chapter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenReader(chapter.comicId, chapter.path) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(chapter.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = " ${chapter.count} 页",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
