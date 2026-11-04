package io.github.saonte.mangareader.feature.local

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.saonte.mangareader.BuildConfig
import io.github.saonte.mangareader.designsystem.component.EmptyState
import io.github.saonte.mangareader.model.Comic
import io.github.saonte.mangareader.ui.components.ScreenHeader

@Composable
fun LocalScreen(
    onBack: () -> Unit,
    onOpenComic: (comicId: Long) -> Unit,
    viewModel: LocalViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val comics by viewModel.localComics.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.addRoot(it.toString())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("返回") }
            ScreenHeader(title = "本地漫画", modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { picker.launch(null) }) { Text("添加目录") }
            Button(enabled = !scanning && roots.isNotEmpty(), onClick = viewModel::scan) {
                Text(if (scanning) "扫描中…" else "扫描全部")
            }
        }

        if (BuildConfig.DEBUG) {
            TextButton(
                onClick = { viewModel.seedDemo(context) },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text("生成本地演示目录（Debug）")
            }
        }

        message?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
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

        if (roots.isNotEmpty()) {
            Text(
                text = "扫描目录",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            roots.forEach { root ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(root.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "上次扫描：${root.lastCount} 部",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.removeRoot(root.id) }) { Text("移除") }
                }
            }
            HorizontalDivider()
        }

        if (comics.isEmpty()) {
            EmptyState(
                title = "还没有本地漫画",
                body = "点击「添加目录」，选择存放图片的文件夹（每个漫画一个子目录，章节再分子目录）。ZIP/CBZ 支持顺延。",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(comics, key = { it.id }) { comic ->
                    LocalComicItem(
                        comic = comic,
                        onClick = { onOpenComic(comic.id) },
                        onDelete = { viewModel.deleteComic(comic.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalComicItem(comic: Comic, onClick: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = comic.cover,
            contentDescription = comic.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = comic.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = onDelete) { Text("移除", style = MaterialTheme.typography.labelSmall) }
    }
}
