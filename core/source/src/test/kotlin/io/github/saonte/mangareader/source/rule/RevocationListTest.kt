package io.github.saonte.mangareader.source.rule

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RevocationListTest {

    private val list = RevocationList.parse(
        """
        {"formatVersion":1,"rules":[
          {"id":"community.bad"},
          {"id":"community.old","version":"1.2.0"}
        ]}
        """.trimIndent(),
    )

    @Test
    fun `revokes all versions when version omitted`() {
        assertThat(list.isRevoked("community.bad", "0.1.0")).isTrue()
        assertThat(list.isRevoked("community.bad", "9.9.9")).isTrue()
    }

    @Test
    fun `revokes up to specified version`() {
        assertThat(list.isRevoked("community.old", "1.1.9")).isTrue()
        assertThat(list.isRevoked("community.old", "1.2.0")).isTrue()
        assertThat(list.isRevoked("community.old", "1.2.1")).isFalse()
        assertThat(list.isRevoked("community.other", "1.0.0")).isFalse()
    }

    @Test
    fun `invalid document degrades to empty`() {
        assertThat(RevocationList.parse("not json").size()).isEqualTo(0)
    }
}
