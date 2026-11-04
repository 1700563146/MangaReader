package io.github.saonte.mangareader.source.rule

import com.google.common.truth.Truth.assertThat
import io.github.saonte.mangareader.source.SourceRef
import io.github.saonte.mangareader.source.http.SourceHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JsonRuleEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var engine: JsonRuleEngine
    private lateinit var ref: SourceRef

    private val searchHtml = """
        <ul class="result">
          <li>
            <a class="cover title" href="/comic/42/">示例漫画<img data-src="/img/42.jpg"></a>
            <span class="status">连载</span><span class="author">作者甲</span><span class="update">更新至第2话</span>
          </li>
          <li>
            <a class="cover title" href="/comic/43/">第二部<img src="/img/43.jpg"></a>
            <span class="status">完结</span><span class="author">作者乙</span><span class="update">已完结</span>
          </li>
        </ul>
        <a class="next" href="/search?page=2">下一页</a>
    """.trimIndent()

    private val comicHtml = """
        <div class="comic-info">
          <h1>示例漫画</h1>
          <img class="cover" src="/img/42.jpg">
          <a class="author">作者甲</a>
          <div class="intro"><p>简介 <b>正文</b></p><script>bad()</script></div>
          <span class="status">连载</span>
          <span class="update">更新至第2话</span>
        </div>
        <ul class="chapter-list">
          <li><a href="/chapter/2/">第2话</a></li>
          <li><a href="/chapter/1/">第1话</a></li>
        </ul>
    """.trimIndent()

    private val imagesHtml = """
        <div class="reader">
          <img data-src="/img/1.jpg">
          <img data-src="/img/2.jpg">
        </div>
    """.trimIndent()

    private val lazyJson = """{"data":{"url":"/img/lazy-0.jpg"}}"""

    private val categoryHtml = """
        <ul class="list">
          <li><a class="title" href="/comic/42/">示例漫画</a><img data-src="/img/42.jpg"></li>
        </ul>
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/search") -> MockResponse().setResponseCode(200).setBody(searchHtml)
                    path.startsWith("/comic/42/") -> MockResponse().setResponseCode(200).setBody(comicHtml)
                    path.endsWith(".json") -> MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(lazyJson)
                    path.startsWith("/chapter/") -> MockResponse().setResponseCode(200).setBody(imagesHtml)
                    path.startsWith("/list/hot/") -> MockResponse().setResponseCode(200).setBody(categoryHtml)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()

        val base = server.url("/").toString().trimEnd('/')
        val raw = checkNotNull(javaClass.getResource("/rules/demo-source.json")).readText()
            .replace("https://example.com", base)
            .replace("\"hosts\": [\"example.com\"]", "\"hosts\": [\"127.0.0.1\"]")
            .replace("\"allowCleartext\": false", "\"allowCleartext\": true")
        val compiled = RuleCompiler().compile(raw)
        check(compiled is RuleCompileResult.Success) {
            (compiled as RuleCompileResult.Failure).issues.joinToString()
        }
        ref = SourceRef(1, compiled.rule)
        engine = JsonRuleEngine(SourceHttpClient(SourceHttpClient.defaultClient()))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `search parses list and paging`() = runBlocking {
        val page = engine.search(ref, "海贼", 1)

        assertThat(page.items).hasSize(2)
        assertThat(page.items[0].cid).isEqualTo("42")
        assertThat(page.items[0].title).isEqualTo("示例漫画")
        assertThat(page.items[0].cover).isEqualTo("${server.url("/")}img/42.jpg")
        assertThat(page.items[0].finish).isFalse()
        assertThat(page.items[1].finish).isTrue()
        assertThat(page.hasNext).isTrue()
    }

    @Test
    fun `detail merges chapters and reverses order`() = runBlocking {
        val detail = engine.detail(ref, "42")

        assertThat(detail.summary.title).isEqualTo("示例漫画")
        assertThat(detail.summary.author).isEqualTo("作者甲")
        assertThat(detail.intro).contains("简介")
        assertThat(detail.intro).doesNotContain("<p>")
        assertThat(detail.chapters.map { it.title }).containsExactly("第1话", "第2话").inOrder()
        assertThat(detail.chapters[0].path).isEqualTo("${server.url("/")}chapter/1/")
    }

    @Test
    fun `chapters can be loaded independently`() = runBlocking {
        val chapters = engine.chapters(ref, "42")

        assertThat(chapters).hasSize(2)
        assertThat(chapters.map { it.title }).containsExactly("第1话", "第2话").inOrder()
    }

    @Test
    fun `images resolve absolute urls with referer`() = runBlocking {
        val page = engine.images(ref, "42", "${server.url("/")}chapter/1/")

        assertThat(page.lazy).isFalse()
        assertThat(page.images.map { it.url }).containsExactly(
            "${server.url("/")}img/1.jpg",
            "${server.url("/")}img/2.jpg",
        ).inOrder()
        assertThat(page.images.first().headers["Referer"]).isEqualTo("${server.url("/")}chapter/1/")
    }

    @Test
    fun `lazy image resolves through json request`() = runBlocking {
        val raw = checkNotNull(javaClass.getResource("/rules/demo-source.json")).readText()
            .replace("https://example.com", server.url("/").toString().trimEnd('/'))
            .replace("\"hosts\": [\"example.com\"]", "\"hosts\": [\"127.0.0.1\"]")
            .replace("\"allowCleartext\": false", "\"allowCleartext\": true")
            .replace("\"enabled\": false", "\"enabled\": true")
        val compiled = RuleCompiler().compile(raw) as RuleCompileResult.Success
        val lazyRef = SourceRef(2, compiled.rule)

        val image = engine.lazyImage(lazyRef, "42", "${server.url("/")}chapter/1/", 0)

        assertThat(image.url).isEqualTo("${server.url("/")}img/lazy-0.jpg")
        assertThat(image.lazy).isTrue()
    }

    @Test
    fun `check update returns trimmed text`() = runBlocking {
        assertThat(engine.checkUpdate(ref, "42")).isEqualTo("更新至第2话")
    }

    @Test
    fun `category parses page items`() = runBlocking {
        val page = engine.category(ref, "hot", 1)

        assertThat(page.items).hasSize(1)
        assertThat(page.items[0].cid).isEqualTo("42")
        assertThat(page.items[0].title).isEqualTo("示例漫画")
    }
}
