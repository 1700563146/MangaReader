package io.github.saonte.mangareader.reader.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/**
 * 单页缩放状态：scale ∈ [1, 5]，offset 限制在放大后可平移的范围内。
 * 纯数据类，可单测（不依赖 Composable）。
 */
@Stable
class ZoomState(
    initialScale: Float = MIN_SCALE,
) {
    var scale by mutableFloatStateOf(initialScale.coerceIn(MIN_SCALE, MAX_SCALE))
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean get() = scale > ZOOM_EPSILON

    fun applyGesture(pan: Offset, zoom: Float, viewSize: IntSize) {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        if (newScale <= ZOOM_EPSILON) {
            scale = MIN_SCALE
            offset = Offset.Zero
            return
        }
        val boundX = (viewSize.width * (newScale - 1f)) / 2f
        val boundY = (viewSize.height * (newScale - 1f)) / 2f
        val next = offset + pan
        offset = Offset(
            x = clamp(next.x, boundX),
            y = clamp(next.y, boundY),
        )
        scale = newScale
    }

    fun toggle(viewSize: IntSize) {
        if (isZoomed) {
            reset()
        } else {
            scale = DOUBLE_TAP_SCALE
            offset = Offset.Zero
        }
        clampOffset(viewSize)
    }

    fun reset(viewSize: IntSize = IntSize.Zero) {
        scale = MIN_SCALE
        offset = Offset.Zero
        clampOffset(viewSize)
    }

    fun clampOffset(viewSize: IntSize) {
        val boundX = (viewSize.width * (scale - 1f)) / 2f
        val boundY = (viewSize.height * (scale - 1f)) / 2f
        offset = Offset(clamp(offset.x, boundX), clamp(offset.y, boundY))
    }

    private fun clamp(value: Float, bound: Float): Float =
        if (bound <= 0f || abs(value) <= bound) value else if (value > 0) bound else -bound

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.2f
        const val ZOOM_EPSILON = 1.01f
    }
}
