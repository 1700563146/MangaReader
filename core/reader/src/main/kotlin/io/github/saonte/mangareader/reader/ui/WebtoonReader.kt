package io.github.saonte.mangareader.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saonte.mangareader.model.ReadMode
import io.github.saonte.mangareader.reader.PageSource
import io.github.saonte.mangareader.reader.ReaderController
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun WebtoonReader(
    controller: ReaderController,
    pageSource: PageSource,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.pageIndex.coerceAtLeast(0),
    )
    val prefetcher = rememberReaderPrefetcher(pageSource)
    val pageCount = state.pageCount.coerceAtLeast(1)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                controller.onPageSettled(index)
                prefetcher.prefetch((index - 1)..(index + 3))
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 72.dp),
    ) {
        items(
            count = pageCount,
            key = { it },
            contentType = { "page" },
        ) { index ->
            var aspect by remember(index) { mutableFloatStateOf(1.4f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect),
                contentAlignment = Alignment.Center,
            ) {
                PageImage(
                    pageSource = pageSource,
                    index = index,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillWidth,
                    tapEnabled = false,
                    onLoaded = { width, height ->
                        if (width > 0 && height > 0) aspect = width.toFloat() / height
                    },
                    onLongPress = { controller.setMode(ReadMode.PAGED_LTR) },
                )
            }
        }
        item(key = "end") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = { controller.toggleMenu() }) {
                    Text("到底了，打开菜单", color = Color.White)
                }
            }
        }
    }
}
