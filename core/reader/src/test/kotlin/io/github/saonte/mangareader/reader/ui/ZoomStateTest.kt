package io.github.saonte.mangareader.reader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ZoomStateTest {

    private val view = IntSize(1000, 2000)

    @Test
    fun `zoom is clamped to bounds`() {
        val zoom = ZoomState()
        zoom.applyGesture(Offset.Zero, 3f, view)
        zoom.applyGesture(Offset.Zero, 3f, view)

        assertThat(zoom.scale).isEqualTo(ZoomState.MAX_SCALE)
    }

    @Test
    fun `pan is limited to scaled bounds`() {
        val zoom = ZoomState()
        zoom.applyGesture(Offset.Zero, 2f, view)
        zoom.applyGesture(Offset(5000f, 5000f), 1f, view)

        assertThat(zoom.offset).isEqualTo(Offset(500f, 1000f))
    }

    @Test
    fun `double tap toggles zoom`() {
        val zoom = ZoomState()
        zoom.toggle(view)
        assertThat(zoom.isZoomed).isTrue()
        zoom.toggle(view)
        assertThat(zoom.isZoomed).isFalse()
        assertThat(zoom.offset).isEqualTo(Offset.Zero)
    }

    @Test
    fun `zoom out below one resets offset`() {
        val zoom = ZoomState()
        zoom.applyGesture(Offset.Zero, 2f, view)
        zoom.applyGesture(Offset.Zero, 0.1f, view)

        assertThat(zoom.scale).isEqualTo(ZoomState.MIN_SCALE)
        assertThat(zoom.offset).isEqualTo(Offset.Zero)
    }
}
