package io.github.saonte.mangareader.source.rule

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PipeTest {

    @Test
    fun `template substitutes variables and filters`() {
        val vars = mapOf("base" to "https://example.com", "keyword" to "海贼 王", "page" to "2")

        assertThat(Template.render("${'$'}{base}/search?q=${'$'}{keyword|urlencode}&page=${'$'}{page}", vars))
            .isEqualTo("https://example.com/search?q=%E6%B5%B7%E8%B4%BC%20%E7%8E%8B&page=2")
    }

    @Test
    fun `unknown variable becomes empty string`() {
        assertThat(Template.render("/c/${'$'}{cid}/", emptyMap())).isEqualTo("/c//")
    }

    @Test
    fun `pipeline resolves relative urls against page url`() {
        val ops = listOf(PipeOp("trim"), PipeOp("absUrl"))

        assertThat(Pipes.apply("/img/1.jpg", ops, "https://example.com/comic/1/"))
            .isEqualTo("https://example.com/img/1.jpg")
        assertThat(Pipes.apply("//cdn.example.com/1.jpg", ops, "https://example.com/comic/1/"))
            .isEqualTo("https://cdn.example.com/1.jpg")
    }

    @Test
    fun `pipeline regex and map`() {
        val id = Pipes.apply(
            "https://example.com/comic/42/",
            listOf(PipeOp("regex", listOf("/comic/(\\d+)/", "1"))),
        )
        assertThat(id).isEqualTo("42")

        val finish = Pipes.apply(
            "连载",
            listOf(PipeOp("map", map = mapOf("连载" to "false", "完结" to "true")), PipeOp("toBool")),
        )
        assertThat(finish).isEqualTo("false")
    }

    @Test
    fun `pipeline decodes js escapes and html entities`() {
        assertThat(Pipes.apply("\\u6d77\\u8d3c&nbsp;\\/", listOf(PipeOp("decodeJs"), PipeOp("decodeHtml"))))
            .isEqualTo("海贼\u00a0/")
    }

    @Test
    fun `number extracts first numeric token`() {
        assertThat(Pipes.apply("共 128 页", listOf(PipeOp("number")))).isEqualTo("128")
    }
}
