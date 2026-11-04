package io.github.saonte.mangareader.source

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HostMatcherTest {

    private val allowed = listOf("example.com", "*.example-cdn.com", "img.example.org")

    @Test
    fun `exact host matches`() {
        assertThat(HostMatcher.matches("example.com", allowed)).isTrue()
        assertThat(HostMatcher.matches("EXAMPLE.COM", allowed)).isTrue()
    }

    @Test
    fun `wildcard matches subdomains only`() {
        assertThat(HostMatcher.matches("m.example-cdn.com", allowed)).isTrue()
        assertThat(HostMatcher.matches("a.b.example-cdn.com", allowed)).isTrue()
        assertThat(HostMatcher.matches("example-cdn.com", allowed)).isFalse()
    }

    @Test
    fun `unlisted host is rejected`() {
        assertThat(HostMatcher.matches("evil.com", allowed)).isFalse()
        assertThat(HostMatcher.matches("example.com.evil.com", allowed)).isFalse()
        assertThat(HostMatcher.matches("", allowed)).isFalse()
    }

    @Test
    fun `trailing dot is normalized`() {
        assertThat(HostMatcher.matches("img.example.org.", allowed)).isTrue()
    }
}
