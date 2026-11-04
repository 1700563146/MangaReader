package io.github.saonte.mangareader.reader

import com.google.common.truth.Truth.assertThat
import io.github.saonte.mangareader.model.ImageRef
import io.github.saonte.mangareader.model.ReadMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class ReaderControllerTest {

    private class FakeSource(
        pages: Int = 10,
        private val fail: Boolean = false,
    ) : PageSource {
        override val pageCount: StateFlow<Int> = MutableStateFlow(pages)
        override val chapterTitle: String = "测试章节"
        override suspend fun page(index: Int): ImageRef {
            if (fail) throw IOException("boom")
            return ImageRef(url = "https://img.example.com/$index.jpg")
        }
    }

    @Test
    fun `load marks ready and uses source page count`() = runTest {
        val controller = ReaderController(FakeSource(pages = 12))

        controller.load()

        assertThat(controller.state.value.loading).isFalse()
        assertThat(controller.state.value.error).isNull()
        assertThat(controller.state.value.pageCount).isEqualTo(12)
        assertThat(controller.state.value.chapterTitle).isEqualTo("测试章节")
    }

    @Test
    fun `load failure exposes retryable error`() = runTest {
        val controller = ReaderController(FakeSource(fail = true))

        controller.load()

        assertThat(controller.state.value.loading).isFalse()
        assertThat(controller.state.value.error).contains("boom")
    }

    @Test
    fun `page settle records progress and reports sink`() {
        val progress = mutableListOf<Triple<Int, String, Int>>()
        val controller = ReaderController(FakeSource()) { _, title, page, _ ->
            progress += Triple(0, title, page)
        }

        controller.onPageSettled(3)

        assertThat(controller.state.value.pageIndex).isEqualTo(3)
        assertThat(controller.state.value.loading).isFalse()
        assertThat(progress).containsExactly(Triple(0, "测试章节", 3))
    }

    @Test
    fun `menu toggles`() {
        val controller = ReaderController(FakeSource())
        assertThat(controller.state.value.isMenuVisible).isFalse()
        controller.toggleMenu()
        assertThat(controller.state.value.isMenuVisible).isTrue()
    }

    @Test
    fun `scale is clamped`() {
        val controller = ReaderController(FakeSource())
        controller.onScaleChanged(9f)
        assertThat(controller.state.value.scale).isEqualTo(ReaderController.MAX_SCALE)
        controller.onScaleChanged(0.1f)
        assertThat(controller.state.value.scale).isEqualTo(1f)
    }

    @Test
    fun `spread count pairs pages in double mode`() {
        assertThat(ReaderController.spreadCount(8, doublePage = true)).isEqualTo(4)
        assertThat(ReaderController.spreadCount(9, doublePage = true)).isEqualTo(5)
        assertThat(ReaderController.spreadCount(8, doublePage = false)).isEqualTo(8)
        assertThat(ReaderController.spreadCount(0, doublePage = true)).isEqualTo(0)
    }

    @Test
    fun `double page toggles`() {
        val controller = ReaderController(FakeSource())
        controller.toggleDoublePage()
        assertThat(controller.state.value.doublePage).isTrue()
        controller.toggleDoublePage()
        assertThat(controller.state.value.doublePage).isFalse()
    }

    @Test
    fun `mode switch preserves position`() {
        val controller = ReaderController(FakeSource())
        controller.onPageSettled(7)
        controller.setMode(ReadMode.WEBTOON)
        assertThat(controller.state.value.pageIndex).isEqualTo(7)
        assertThat(controller.state.value.mode).isEqualTo(ReadMode.WEBTOON)
    }
}
