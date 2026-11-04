package io.github.saonte.mangareader.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ComicTest {

    @Test
    fun `new comic defaults to local id and zero progress`() {
        val comic = Comic(cid = "123", title = "示例漫画")

        assertThat(comic.id).isEqualTo(0L)
        assertThat(comic.source).isEqualTo(0L)
        assertThat(comic.lastPage).isEqualTo(0)
        assertThat(comic.favoriteAt).isNull()
        assertThat(comic.local).isFalse()
    }

    @Test
    fun `copy keeps identity and changes only requested fields`() {
        val comic = Comic(cid = "123", title = "示例漫画", source = 7)
        val favorited = comic.copy(favoriteAt = 1_767_225_600_000L, highlight = true)

        assertThat(favorited.cid).isEqualTo(comic.cid)
        assertThat(favorited.source).isEqualTo(7L)
        assertThat(favorited.favoriteAt).isNotNull()
        assertThat(favorited.highlight).isTrue()
    }

    @Test
    fun `source rule defaults match engine contract`() {
        val rule = SourceRule(id = "community.example", name = "示例", version = "1.0.0", baseUrl = "https://example.com", hosts = listOf("example.com"))

        assertThat(rule.schemaVersion).isEqualTo(SourceRule.SCHEMA_VERSION)
        assertThat(rule.minEngine).isEqualTo(SourceRule.ENGINE_VERSION)
        assertThat(rule.allowCleartext).isFalse()
    }
}
