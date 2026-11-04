package io.github.saonte.mangareader.reader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 缩放感知的手势修饰符：
 * - 双指缩放 / 已放大时单指平移；未放大且单指拖动时**不消费**事件，交给 Pager/LazyColumn；
 * - 放大后拖动到边界继续拖，由调用方决定是否翻页（M2 暂不实现边缘翻页）。
 */
fun Modifier.zoomable(
    zoomState: ZoomState,
    onZoomChanged: (Boolean) -> Unit = {},
): Modifier = pointerInput(zoomState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var multiTouch = false
        do {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) multiTouch = true
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            val shouldHandle = multiTouch || zoomState.isZoomed
            if (shouldHandle && (zoomChange != 1f || panChange != Offset.Zero)) {
                zoomState.applyGesture(pan = panChange, zoom = zoomChange, viewSize = size)
                onZoomChanged(zoomState.isZoomed)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
