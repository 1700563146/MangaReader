package io.github.saonte.mangareader.reader.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import io.github.saonte.mangareader.model.ImageRef
import io.github.saonte.mangareader.reader.PageSource
import io.github.saonte.mangareader.reader.R

enum class TapZone { LEFT, CENTER, RIGHT }

/**
 * 单页渲染：加载、错误重试、缩放、点击区。
 * 页数据由 [PageSource] 按需提供（在线/本地/已下载统一）。
 */
@Composable
fun PageImage(
    pageSource: PageSource,
    index: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    tapEnabled: Boolean = true,
    onLoaded: (width: Int, height: Int) -> Unit = { _, _ -> },
    onTapZone: (TapZone) -> Unit = {},
    onLongPress: () -> Unit = {},
    onZoomChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val pageDescription = stringResource(R.string.reader_page_description, index + 1)
    val zoom = remember(pageSource, index) { ZoomState() }
    var image by remember(pageSource, index) { mutableStateOf<ImageRef?>(null) }
    var error by remember(pageSource, index) { mutableStateOf<String?>(null) }
    var attempt by remember(pageSource, index) { mutableIntStateOf(0) }

    LaunchedEffect(pageSource, index, attempt) {
        error = null
        runCatching { pageSource.page(index) }
            .onSuccess { image = it }
            .onFailure { error = it.message ?: "图片加载失败" }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .zoomable(zoom, onZoomChanged)
            .then(
                if (tapEnabled) {
                    Modifier.pointerInput(pageSource, index) {
                        detectTapGestures(
                            onTap = { offset ->
                                val zone = when {
                                    offset.x < size.width * 0.3f -> TapZone.LEFT
                                    offset.x > size.width * 0.7f -> TapZone.RIGHT
                                    else -> TapZone.CENTER
                                }
                                onTapZone(zone)
                            },
                            onDoubleTap = {
                                zoom.toggle(size)
                                onZoomChanged(zoom.isZoomed)
                            },
                            onLongPress = { onLongPress() },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = image
        when {
            error != null -> ErrorContent(
                message = error.orEmpty(),
                onRetry = { attempt += 1 },
            )

            loaded == null -> CircularProgressIndicator(color = Color.White)

            else -> AsyncImage(
                model = imageRequest(context, loaded),
                contentDescription = pageDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = pageDescription }
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                        transformOrigin = TransformOrigin.Center
                    },
                contentScale = contentScale,
                onSuccess = { state: AsyncImagePainter.State.Success ->
                    val result = state.result.image
                    onLoaded(result.width, result.height)
                },
                onError = { state: AsyncImagePainter.State.Error ->
                    error = state.result.throwable.message
                },
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text("重试", color = Color.White)
        }
    }
}
