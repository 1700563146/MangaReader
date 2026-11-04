package io.github.saonte.mangareader.source.rule

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class SelectorTest {

    private val html = """
        <html><body>
          <ul class="result">
            <li>
              <a class="cover" href="/comic/42/" title="示例漫画">
                <img data-src="/img/42.jpg">
              </a>
              <span class="status">连载</span>
              <span class="author">作者甲</span>
            </li>
            <li>
              <a class="cover" href="/comic/43/" title="第二部"><img src="/img/43.jpg"></a>
              <span class="status">完结</span>
            </li>
          </ul>
          <div class="intro"><p>简介 <b>正文</b></p><script>bad()</script></div>
        </body></html>
    """.trimIndent()

    private val pageUrl = "https://example.com/comic/42/"
    private val document = SelectorEngine.parseDocument(html)

    @Test
    fun `list selection understands limit and skip`() {
        val spec = ListSpec(select = "ul.result > li", limit = 2)
        assertThat(SelectorEngine.selectNodes(document, null, spec)).hasSize(2)

        val skipped = ListSpec(select = "ul.result > li", limit = 2, skip = 1)
        assertThat(SelectorEngine.selectNodes(document, null, skipped)).hasSize(1)
    }

    @Test
    fun `extract from node with attr and pipeline`() {
        val nodes = SelectorEngine.selectNodes(document, null, ListSpec(select = "ul.result > li"))
        val cid = SelectorEngine.extract(
            document,
            nodes[0],
            SelectSpec(css = "a.cover", attr = "href", ops = listOf(PipeOp("regex", listOf("/comic/(\\d+)/", "1")))),
            pageUrl,
        )
        assertThat(cid).isEqualTo("42")
    }

    @Test
    fun `extract falls back to second source`() {
        val nodes = SelectorEngine.selectNodes(document, null, ListSpec(select = "ul.result > li"))
        val cover = SelectorEngine.extract(
            document,
            nodes[1],
            SelectSpec(css = "img", attr = "data-src", fallback = listOf(SelectSpec(css = "img", attr = "src"))),
            "https://example.com/",
        )
        assertThat(cover).isEqualTo("/img/43.jpg")
    }

    @Test
    fun `html attr strips tags`() {
        val intro = SelectorEngine.extract(
            document,
            null,
            SelectSpec(css = "div.intro", attr = "html", ops = listOf(PipeOp("stripTags"))),
            pageUrl,
        )
        assertThat(intro).contains("简介")
        assertThat(intro).doesNotContain("<p>")
    }

    @Test
    fun `negative index selects from tail`() {
        val second = SelectorEngine.extract(
            document,
            null,
            SelectSpec(css = "ul.result > li", index = -1),
            pageUrl,
        )
        assertThat(second).contains("完结")
    }

    @Test
    fun `json path extracts array values`() {
        val root = Json.parseToJsonElement(
            """{"data":{"list":[{"url":"https://img.example.com/1.jpg"},{"url":"https://img.example.com/2.jpg"}]}}""",
        )
        val values = JsonPaths.read(root, "$.data.list[*].url")
        assertThat(values.map { (it as kotlinx.serialization.json.JsonPrimitive).content })
            .containsExactly("https://img.example.com/1.jpg", "https://img.example.com/2.jpg")
            .inOrder()

        val recursive = JsonPaths.read(root, "$..url")
        assertThat(recursive).hasSize(2)
    }
}
