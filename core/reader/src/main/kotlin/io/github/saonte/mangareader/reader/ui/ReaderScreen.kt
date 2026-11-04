package io.github.saonte.mangareader.reader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saonte.mangareader.model.ReadMode
import io.github.saonte.mangareader.model.TapConfig
import io.github.saonte.mangareader.reader.PageSource
import io.github.saonte.mangareader.reader.R
import io.github.saonte.mangareader.reader.ReaderController
import kotlinx.coroutines.launch

/**
 * 阅读器入口：全屏、沉浸、按模式切换分页/条漫；菜单浮层独立于内容。
 */
@Composable
fun ReaderScreen(
    controller: ReaderController,
    pageSource: PageSource,
    onExit: () -> Unit,
    onBrightnessChanged: (Float) -> Unit = {},
    onNightFilterChanged: (Boolean) -> Unit = {},
    onAutoCropChanged: (Boolean) -> Unit = {},
    onModeChanged: (ReadMode) -> Unit = {},
    onDoublePageChanged: (Boolean) -> Unit = {},
    tapConfig: TapConfig = TapConfig.DEFAULT,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val windowInsets = window?.let { WindowCompat.getInsetsController(it, view) }
        windowInsets?.hide(WindowInsetsCompat.Type.systemBars())
        windowInsets?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            windowInsets?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = true) {
        if (state.isMenuVisible) controller.toggleMenu() else onExit()
    }

    LaunchedEffect(controller) { controller.load() }
    LaunchedEffect(tapConfig) { controller.setTapConfig(tapConfig) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (state.mode) {
            ReadMode.WEBTOON -> WebtoonReader(controller, pageSource)
            else -> PagedReader(controller, pageSource)
        }

        // 亮度：黑色遮罩（不修改系统亮度，退出即恢复）
        if (state.brightness < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1f - state.brightness).coerceIn(0f, 0.85f))),
            )
        }
        // 夜间滤镜：降低蓝光，暖色叠加
        if (state.nightFilter) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33FF8A00)),
            )
        }

        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        state.error?.let { message ->
            ErrorOverlay(
                message = message,
                onRetry = { scope.launch { controller.retry() } },
                onDismiss = controller::dismissError,
            )
        }

        AnimatedVisibility(
            visible = state.isMenuVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderMenu(
                state = state,
                onToggleMode = {
                    val next = when (state.mode) {
                        ReadMode.PAGED_LTR -> ReadMode.PAGED_RTL
                        ReadMode.PAGED_RTL -> ReadMode.PAGED_TTB
                        ReadMode.PAGED_TTB -> ReadMode.WEBTOON
                        ReadMode.WEBTOON -> ReadMode.PAGED_LTR
                    }
                    controller.setMode(next)
                    onModeChanged(next)
                },
                onToggleDoublePage = {
                    controller.toggleDoublePage()
                    onDoublePageChanged(!state.doublePage)
                },
                onBrightness = {
                    controller.setBrightness(it)
                    onBrightnessChanged(it)
                },
                onToggleNightFilter = {
                    controller.toggleNightFilter()
                    onNightFilterChanged(!state.nightFilter)
                },
                onToggleAutoCrop = {
                    controller.toggleAutoCrop()
                    onAutoCropChanged(!state.autoCrop)
                },
                onExit = onExit,
                onClose = controller::toggleMenu,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.reader_retry), color = Color.White) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_back), color = Color.White) }
        }
    }
}
