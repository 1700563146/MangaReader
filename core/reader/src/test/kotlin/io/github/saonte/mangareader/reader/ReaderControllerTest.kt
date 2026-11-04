package io.github.saonte.mangareader.reader

import com.google.common.truth.Truth.assertThat
import io.github.saonte.mangareader.model.ImageRef
import io.github.saonte.mangareader.model.ReadMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

class ReaderControllerTest {

    private val source = object : PageSource {
        override val pageCount: StateFlow<Int> = MutableStateFlow(10)
        override suspend fun page(index: Int): ImageRef = ImageRef(url = "https://img.example.com/$index.jpg")
    }

    @Test
    fun `page settle records progress`() {
        val controller = ReaderController(source)

        controller.onPageSettled(3)

        assertThat(controller.state.value.pageIndex).isEqualTo(3)
        assertThat(controller.state.value.loading).isFalse()
        assertThat(controller.state.value.error).isNull()
    }

    @Test
    fun `menu toggles`() {
        val controller = ReaderController(source)
        assertThat(controller.state.value.isMenuVisible).isFalse()
        controller.toggleMenu()
        assertThat(controller.state.value.isMenuVisible).isTrue()
    }

    @Test
    fun `scale is clamped`() {
        val controller = ReaderController(source)
        controller.onScaleChanged(9f)
        assertThat(controller.state.value.scale).isEqualTo(ReaderController.MAX_SCALE)
        controller.onScaleChanged(0.1f)
        assertThat(controller.state.value.scale).isEqualTo(1f)
    }

    @Test
    fun `mode switch preserves position`() {
        val controller = ReaderController(source)
        controller.onPageSettled(7)
        controller.setMode(ReadMode.WEBTOON)
        assertThat(controller.state.value.pageIndex).isEqualTo(7)
        assertThat(controller.state.value.mode).isEqualTo(ReadMode.WEBTOON)
    }
}
