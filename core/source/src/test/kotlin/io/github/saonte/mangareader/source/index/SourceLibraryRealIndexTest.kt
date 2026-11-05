package io.github.saonte.mangareader.source.index

import com.google.common.truth.Truth.assertThat
import io.github.saonte.mangareader.source.SourceKind
import org.junit.jupiter.api.Test

/**
 * 用仓库里真实抓取的 venera 索引做回归，确保格式兼容性有据可查。
 * fixture 见 `src/test/resources/fixtures/venera-index.json`（33 条，抓取于 2026-11）。
 */
class SourceLibraryRealIndexTest {

    private val indexUrl =
        "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json"

    private fun fixture(): String =
        checkNotNull(javaClass.getResource("/fixtures/venera-index.json")).readText()

    @Test
    fun `parses all entries of the real venera index`() {
        val result = SourceLibraryParser.parse(fixture(), indexUrl)

        assertThat(result.entries).isNotEmpty()
        assertThat(result.entries).hasSize(33)
        assertThat(result.warnings).isEmpty()

        // 所有条目都应解析为 CJS，且 URL 指向 jsDelivr 同目录
        assertThat(result.entries.map { it.kind }.toSet()).containsExactly(SourceKind.CJS)
        result.entries.forEach { entry ->
            assertThat(entry.url).startsWith("https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/")
            assertThat(entry.url).endsWith(".js")
            assertThat(entry.id).isNotEmpty()
            assertThat(entry.name).isNotEmpty()
            assertThat(entry.version).isNotEmpty()
        }
    }

    @Test
    fun `every entry id is unique`() {
        val result = SourceLibraryParser.parse(fixture(), indexUrl)

        assertThat(result.entries.map { it.id }.toSet()).hasSize(result.entries.size)
    }
}
