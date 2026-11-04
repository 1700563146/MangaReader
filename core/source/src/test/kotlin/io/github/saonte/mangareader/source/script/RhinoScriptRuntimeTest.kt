package io.github.saonte.mangareader.source.script

import com.google.common.truth.Truth.assertThat
import io.github.saonte.mangareader.model.ErrorCode
import io.github.saonte.mangareader.source.SourceFailure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class RhinoScriptRuntimeTest {

    private val runtime = RhinoScriptRuntime()

    private fun call(
        source: String,
        html: String? = null,
        timeoutMs: Long = 3_000,
    ) = ScriptCall(
        ruleId = "community.test",
        hook = "detail",
        source = source,
        vars = mapOf("base" to "https://example.com"),
        url = "https://example.com/comic/42/",
        html = html,
        timeoutMs = timeoutMs,
    )

    @Test
    fun `hook returns object with doc helper`() {
        val html = "<html><body><h1>示例漫画</h1><span class='author'>作者甲</span></body></html>"

        val result = runtime.invoke(
            call("function(ctx){ return { title: ctx.doc('h1').text(), author: ctx.doc('span').text() }; }", html),
        ) as JsonObject

        assertThat(result["title"]?.jsonPrimitive?.content).isEqualTo("示例漫画")
        assertThat(result["author"]?.jsonPrimitive?.content).isEqualTo("作者甲")
    }

    @Test
    fun `hook returns array of urls`() {
        val result = runtime.invoke(
            call("function(ctx){ return [ { url: ctx.resolve('/a.jpg') }, { url: ctx.resolve('/b.jpg') } ]; }"),
        )

        val array = (result as JsonElement).jsonArray
        assertThat(array).hasSize(2)
        assertThat(array[0].jsonObject["url"]?.jsonPrimitive?.content).isEqualTo("https://example.com/a.jpg")
    }

    @Test
    fun `util base64 and md5 are available`() {
        val result = runtime.invoke(
            call("function(ctx){ return { decoded: ctx.util.base64Decode('5L2g5aW9'), md5: ctx.util.md5('abc') }; }"),
        ) as JsonObject

        assertThat(result["decoded"]?.jsonPrimitive?.content).isEqualTo("你好")
        assertThat(result["md5"]?.jsonPrimitive?.content)
            .isEqualTo("900150983cd24fb0d6963f7d28e17f72")
    }

    @Test
    fun `infinite loop is interrupted`() {
        val error = runCatching {
            runtime.invoke(call("function(ctx){ while (true) {} }", timeoutMs = 150))
        }.exceptionOrNull() as SourceFailure

        assertThat(error.code).isEqualTo(ErrorCode.SCRIPT_TIMEOUT)
        assertThat(error.pointer).isEqualTo("/scripts/detail")
    }

    @Test
    fun `java interop is unavailable`() {
        val error = runCatching {
            runtime.invoke(call("function(ctx){ return Java.type('java.io.File'); }"))
        }.exceptionOrNull() as SourceFailure

        assertThat(error.code).isEqualTo(ErrorCode.SCRIPT_ERROR)
    }

    @Test
    fun `undefined return becomes null`() {
        assertThat(runtime.invoke(call("function(ctx){ return undefined; }"))).isNull()
    }
}
