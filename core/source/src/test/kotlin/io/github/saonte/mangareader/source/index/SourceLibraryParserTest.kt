package io.github.saonte.mangareader.source.index

import com.google.common.truth.Truth.assertThat
import io.github.saonte.mangareader.source.SourceKind
import org.junit.jupiter.api.Test

class SourceLibraryParserTest {

    private val indexUrl = "https://cdn.example.org/gh/someone/configs@main/index.json"

    @Test
    fun `parses venera style array`() {
        val raw = """
            [
              {"name":"示例源甲","fileName":"alpha.js","key":"alpha","version":"1.4.3"},
              {"name":"示例源乙","fileName":"beta.js","key":"beta","version":"1.0.3"}
            ]
        """.trimIndent()

        val result = SourceLibraryParser.parse(raw, indexUrl)

        assertThat(result.entries).hasSize(2)
        val first = result.entries.first()
        assertThat(first.id).isEqualTo("alpha")
        assertThat(first.name).isEqualTo("示例源甲")
        assertThat(first.version).isEqualTo("1.4.3")
        assertThat(first.kind).isEqualTo(SourceKind.CJS)
        // fileName 必须解析为索引同目录下的绝对地址
        assertThat(first.url).isEqualTo("https://cdn.example.org/gh/someone/configs@main/alpha.js")
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `skips entries without key or fileName`() {
        val raw = """
            [
              {"name":"缺 key","fileName":"a.js","version":"1"},
              {"name":"缺 fileName","key":"b","version":"1"},
              {"name":"完整","fileName":"c.js","key":"c","version":"1"}
            ]
        """.trimIndent()

        val result = SourceLibraryParser.parse(raw, indexUrl)

        assertThat(result.entries.map { it.id }).containsExactly("c")
        assertThat(result.warnings).hasSize(2)
    }

    @Test
    fun `parses signed rule index`() {
        val raw = """
            {
              "formatVersion": 1,
              "rules": [
                {"id":"community.a","name":"甲","version":"1.0.0",
                 "url":"https://rules.example.org/a.json","sha256":"${"a".repeat(64)}"},
                {"id":"community.b","name":"乙","version":"2.0.0",
                 "url":"https://rules.example.org/b.json","sha256":"${"b".repeat(64)}","revoked":true}
              ],
              "signature": {"algorithm":"ed25519","keyId":"k","value":"sig"}
            }
        """.trimIndent()

        val result = SourceLibraryParser.parse(raw, indexUrl)

        assertThat(result.entries).hasSize(1)
        val entry = result.entries.single()
        assertThat(entry.id).isEqualTo("community.a")
        assertThat(entry.kind).isEqualTo(SourceKind.RULE)
        assertThat(entry.sha256).isEqualTo("a".repeat(64))
        // 撤销条目被剔除并给出提示
        assertThat(result.warnings.any { "community.b" in it }).isTrue()
    }

    @Test
    fun `absolute urls are kept as is`() {
        val raw = """[{"name":"x","fileName":"https://other.example.net/x.js","key":"x","version":"1"}]"""

        val result = SourceLibraryParser.parse(raw, indexUrl)

        assertThat(result.entries.single().url).isEqualTo("https://other.example.net/x.js")
    }

    @Test
    fun `invalid json reports warning instead of throwing`() {
        val result = SourceLibraryParser.parse("{not json", indexUrl)

        assertThat(result.entries).isEmpty()
        assertThat(result.warnings.first()).contains("不是合法 JSON")
    }

    @Test
    fun `empty index reports warning`() {
        val result = SourceLibraryParser.parse("   ", indexUrl)

        assertThat(result.entries).isEmpty()
        assertThat(result.warnings.first()).contains("为空")
    }

    @Test
    fun `single source object is accepted`() {
        val raw = """{"id":"solo","name":"单源","version":"1.0.0","url":"solo.js"}"""

        val result = SourceLibraryParser.parse(raw, indexUrl)

        assertThat(result.entries.single().id).isEqualTo("solo")
        assertThat(result.entries.single().kind).isEqualTo(SourceKind.CJS)
        assertThat(result.entries.single().url).endsWith("/solo.js")
    }

    @Test
    fun `nested directory fileName resolves relative to index`() {
        val raw = """[{"name":"x","fileName":"sub/dir/x.js","key":"x","version":"1"}]"""

        val result = SourceLibraryParser.parse(raw, indexUrl)

        assertThat(result.entries.single().url)
            .isEqualTo("https://cdn.example.org/gh/someone/configs@main/sub/dir/x.js")
    }
}
