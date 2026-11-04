package io.github.saonte.mangareader.source.rule

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

class RuleCompilerTest {

    private val compiler = RuleCompiler()

    private fun demoJson(): JsonObject {
        val raw = checkNotNull(javaClass.getResource("/rules/demo-source.json")).readText()
        return Json.parseToJsonElement(raw).jsonObject
    }

    private fun mutate(block: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): String {
        val map = demoJson().toMutableMap()
        block(map)
        return JsonObject(map).toString()
    }

    @Test
    fun `demo rule compiles`() {
        val result = compiler.compile(demoJson().toString())

        assertThat(result).isInstanceOf(RuleCompileResult.Success::class.java)
        val rule = (result as RuleCompileResult.Success).rule
        assertThat(rule.id).isEqualTo("community.example")
        assertThat(rule.capabilities).contains(Capability.SEARCH)
        assertThat(rule.search?.fields).containsKey("cid")
        assertThat(rule.images?.lazy?.enabled).isFalse()
        assertThat(rule.summary().capabilities).contains("search")
    }

    @Test
    fun `unknown pipeline op reports pointer and hint`() {
        val json = mutate { map ->
            val search = Json.parseToJsonElement(map.getValue("search").toString()).jsonObject.toMutableMap()
            val fields = Json.parseToJsonElement(search.getValue("fields").toString()).jsonObject.toMutableMap()
            val cover = Json.parseToJsonElement(fields.getValue("cover").toString()).jsonObject.toMutableMap()
            cover["pipeline"] = Json.parseToJsonElement("""["absURL"]""")
            fields["cover"] = JsonObject(cover)
            search["fields"] = JsonObject(fields)
            map["search"] = JsonObject(search)
        }

        val result = compiler.compile(json)
        val failure = result as RuleCompileResult.Failure
        val issue = failure.issues.first { it.pointer.contains("/search/fields/cover/pipeline/0") }

        assertThat(issue.message).contains("未知管道操作")
        assertThat(issue.hint).contains("absUrl")
    }

    @Test
    fun `capability without block fails`() {
        val json = mutate {
            it.remove("images")
            it.remove("scripts")
        }

        val failure = compiler.compile(json) as RuleCompileResult.Failure

        assertThat(failure.issues.any { it.pointer == "/capabilities" && it.message.contains("images") }).isTrue()
    }

    @Test
    fun `cleartext base url requires explicit flag`() {
        val json = mutate {
            it["baseUrl"] = Json.parseToJsonElement("\"http://example.com\"")
            it["allowCleartext"] = Json.parseToJsonElement("false")
        }

        val failure = compiler.compile(json) as RuleCompileResult.Failure

        assertThat(failure.issues.any { it.pointer == "/allowCleartext" }).isTrue()
    }

    @Test
    fun `unknown template variable is warning not error`() {
        val json = mutate { map ->
            val search = Json.parseToJsonElement(map.getValue("search").toString()).jsonObject.toMutableMap()
            val request = Json.parseToJsonElement(search.getValue("request").toString()).jsonObject.toMutableMap()
            request["url"] = Json.parseToJsonElement(
                "\"https://example.com/search?q=\${keyword|urlencode}&page=\${page}&x=\${mystery}\"",
            )
            search["request"] = JsonObject(request)
            map["search"] = JsonObject(search)
        }

        val result = compiler.compile(json)

        assertThat(result).isInstanceOf(RuleCompileResult.Success::class.java)
        val issues = (result as RuleCompileResult.Success).issues
        assertThat(issues.any { it.severity == IssueSeverity.WARNING && it.message.contains("mystery") }).isTrue()
    }

    @Test
    fun `invalid selector is rejected`() {
        val json = mutate { map ->
            val search = Json.parseToJsonElement(map.getValue("search").toString()).jsonObject.toMutableMap()
            val fields = Json.parseToJsonElement(search.getValue("fields").toString()).jsonObject.toMutableMap()
            fields["title"] = Json.parseToJsonElement("\"a[href\"")
            search["fields"] = JsonObject(fields)
            map["search"] = JsonObject(search)
        }

        val failure = compiler.compile(json) as RuleCompileResult.Failure

        assertThat(failure.issues.any { it.message.contains("CSS 选择器无法解析") }).isTrue()
    }

    @Test
    fun `json parse errors are reported`() {
        val failure = compiler.compile("{ not json") as RuleCompileResult.Failure

        assertThat(failure.issues.first().pointer).isEqualTo("/")
        assertThat(failure.issues.first().message).contains("JSON 解析失败")
    }
}
