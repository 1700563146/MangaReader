package io.github.saonte.mangareader.reader.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saonte.mangareader.model.ReadMode
import io.github.saonte.mangareader.model.TapAction
import io.github.saonte.mangareader.reader.PageSource
import io.github.saonte.mangareader.reader.ReaderController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 翻页模式：HorizontalPager / VerticalPager，缩放下禁用翻页，窗口内预取。
 * - RTL 用 reverseLayout 实现（逻辑页码不变）
 * - 双页：每屏两页，最后一页单独占位；横图独占等配对策略留待 M3
 */
@Composable
fun PagedReader(
    controller: ReaderController,
    pageSource: PageSource,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val doublePage = state.doublePage && state.mode != ReadMode.PAGED_TTB
    // 自动裁剪：优先铺满屏幕，牺牲留白换可读性（用户可在菜单切换）
    val scale = if (state.autoCrop) ContentScale.Crop else ContentScale.Fit
    val totalPages = state.pageCount.coerceAtLeast(1)
    val pageCount = ReaderController.spreadCount(totalPages, doublePage).coerceAtLeast(1)
    val initialSpread = if (doublePage) state.pageIndex / 2 else state.pageIndex
    val pagerState = rememberPagerState(
        initialPage = initialSpread.coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )
    var zoomed by remember { mutableStateOf(false) }
    val prefetcher = rememberReaderPrefetcher(pageSource)
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { spread ->
                zoomed = false
                val logicalPage = if (doublePage) spread * 2 else spread
                controller.onPageSettled(logicalPage)
                prefetcher.prefetch((logicalPage - 1)..(logicalPage + 4))
            }
    }

    val reverse = state.mode == ReadMode.PAGED_RTL
    val turn: (Int) -> Unit = { delta ->
        val target = pagerState.currentPage + delta
        if (target in 0 until pageCount) {
            scope.launch { pagerState.animateScrollToPage(target) }
        }
    }
    val onTapZone: (TapZone) -> Unit = { zone ->
        val action = when (zone) {
            TapZone.LEFT -> state.tapConfig.left
            TapZone.CENTER -> state.tapConfig.center
            TapZone.RIGHT -> state.tapConfig.right
        }
        when (action) {
            TapAction.PREV -> turn(if (reverse) 1 else -1)
            TapAction.NEXT -> turn(if (reverse) -1 else 1)
            TapAction.MENU -> controller.toggleMenu()
        }
    }

    val toggleMode = { controller.setMode(ReadMode.WEBTOON) }
    val page: @Composable (Int) -> Unit = { spread ->
        if (doublePage) {
            Row(modifier = Modifier.fillMaxSize()) {
                PageImage(
                    pageSource = pageSource,
                    index = spread * 2,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentScale = scale,
                    onTapZone = onTapZone,
                    onLongPress = toggleMode,
                    onZoomChanged = { zoomed = it },
                )
                if (spread * 2 + 1 < totalPages) {
                    PageImage(
                        pageSource = pageSource,
                        index = spread * 2 + 1,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = scale,
                        onTapZone = onTapZone,
                        onLongPress = toggleMode,
                        onZoomChanged = { zoomed = it },
                    )
                }
            }
        } else {
            PageImage(
                pageSource = pageSource,
                index = spread,
                modifier = Modifier.fillMaxSize(),
                contentScale = scale,
                onTapZone = onTapZone,
                onLongPress = toggleMode,
                onZoomChanged = { zoomed = it },
            )
        }
    }

    if (state.mode == ReadMode.PAGED_TTB) {
        VerticalPager(
            state = pagerState,
            modifier = modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed,
            key = { it },
        ) { spread -> page(spread) }
    } else {
        HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed,
            reverseLayout = reverse,
            key = { it },
        ) { spread -> page(spread) }
    }
}
