package io.github.saonte.mangareader.source.http

import com.google.common.truth.Truth.assertThat
import io.github.saonte.mangareader.model.ErrorCode
import io.github.saonte.mangareader.source.SourceFailure
import io.github.saonte.mangareader.source.SourceRef
import io.github.saonte.mangareader.source.rule.Capability
import io.github.saonte.mangareader.source.rule.HttpSpec
import io.github.saonte.mangareader.source.rule.RequestSpec
import io.github.saonte.mangareader.source.rule.RuleSpec
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI

class SourceHttpClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: SourceHttpClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = SourceHttpClient(SourceHttpClient.defaultClient())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun rule(
        hosts: List<String> = listOf(hostOf(server.url("/").toString())),
        allowCleartext: Boolean = true,
        retry: Int = 0,
        timeoutMs: Int = 5_000,
    ) = RuleSpec(
        schemaVersion = 1,
        id = "community.test",
        name = "测试源",
        version = "1.0.0",
        minEngine = 1,
        baseUrl = server.url("/").toString().trimEnd('/'),
        hosts = hosts,
        imageHosts = emptyList(),
        allowCleartext = allowCleartext,
        adult = false,
        lang = "zh",
        author = null,
        description = null,
        homepage = null,
        headers = emptyMap(),
        http = HttpSpec(timeoutMs = timeoutMs, retry = retry),
        capabilities = setOf(Capability.SEARCH),
        search = null,
        detail = null,
        chapters = null,
        images = null,
        check = null,
        category = null,
        scripts = emptyMap(),
        config = emptyMap(),
    )

    private fun hostOf(url: String): String = URI(url).host

    @Test
    fun `get renders template and sends headers`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val ref = SourceRef(1, rule())
        val spec = RequestSpec(
            url = "${'$'}{base}/search?q=${'$'}{keyword|urlencode}&page=${'$'}{page}",
            headers = mapOf("X-Page" to "${'$'}{page}"),
            expect = io.github.saonte.mangareader.source.rule.Expect.HTML,
        )

        val response = client.execute(ref, spec, mapOf("keyword" to "海贼", "page" to "2"))

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo("ok")
        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/search?q=%E6%B5%B7%E8%B4%BC&page=2")
        assertThat(recorded.getHeader("X-Page")).isEqualTo("2")
        assertThat(recorded.getHeader("User-Agent")).contains("MangaReader")
    }

    @Test
    fun `host outside whitelist is rejected before network`() = runBlocking {
        val ref = SourceRef(1, rule(hosts = listOf("example.com")))

        val error = runCatching {
            client.execute(ref, RequestSpec(url = "${'$'}{base}/x"), emptyMap())
        }.exceptionOrNull() as SourceFailure

        assertThat(error.code).isEqualTo(ErrorCode.RULE_INVALID)
        assertThat(error.message).contains("不在白名单")
    }

    @Test
    fun `cleartext requires explicit opt in`() = runBlocking {
        val ref = SourceRef(1, rule(allowCleartext = false))

        val error = runCatching {
            client.execute(ref, RequestSpec(url = "${'$'}{base}/x"), emptyMap())
        }.exceptionOrNull() as SourceFailure

        assertThat(error.code).isEqualTo(ErrorCode.NET_TLS)
    }

    @Test
    fun `redirect to disallowed host is rejected`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", "http://evil.example.org/x"),
        )
        val ref = SourceRef(1, rule(hosts = listOf(hostOf(server.url("/").toString()), "example.org")))

        val error = runCatching {
            client.execute(ref, RequestSpec(url = "${'$'}{base}/x"), emptyMap())
        }.exceptionOrNull() as SourceFailure

        assertThat(error.code).isEqualTo(ErrorCode.RULE_INVALID)
    }

    @Test
    fun `retryable 5xx is retried then reported`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        val ref = SourceRef(1, rule(retry = 1))

        val error = runCatching {
            client.execute(ref, RequestSpec(url = "${'$'}{base}/x"), emptyMap())
        }.exceptionOrNull() as SourceFailure

        assertThat(error.code).isEqualTo(ErrorCode.HTTP_5XX)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `cookies are persisted across requests`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Set-Cookie", "sid=abc123; Path=/; HttpOnly")
                .setBody("one"),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("two"))
        val ref = SourceRef(1, rule())

        client.execute(ref, RequestSpec(url = "${'$'}{base}/login"), emptyMap())
        client.execute(ref, RequestSpec(url = "${'$'}{base}/next"), emptyMap())

        server.takeRequest()
        val second = server.takeRequest()
        assertThat(second.getHeader("Cookie")).contains("sid=abc123")
    }

    @Test
    fun `charset from content type is honored`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=gbk")
                .setBody(okio.Buffer().write("中文".toByteArray(charset("GBK")))),
        )
        val ref = SourceRef(1, rule())

        val response = client.execute(ref, RequestSpec(url = "${'$'}{base}/gbk"), emptyMap())

        assertThat(response.charset).isEqualTo("GBK")
        assertThat(response.body).isEqualTo("中文")
    }
}
