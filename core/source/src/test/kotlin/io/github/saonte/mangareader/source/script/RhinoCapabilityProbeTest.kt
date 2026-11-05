package io.github.saonte.mangareader.source.script

import org.junit.jupiter.api.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import com.google.common.truth.Truth.assertThat

/**
 * Rhino 版本能力探测：决定能否直接运行 venera 风格的 CJS 类源。
 * 这里的断言是「当前引擎的真实边界」，升级 Rhino 或换引擎时必须同步更新。
 */
class RhinoCapabilityProbeTest {

    private fun run(source: String): Any? = Context.enter().use { cx ->
        cx.optimizationLevel = -1
        cx.languageVersion = Context.VERSION_ES6
        val scope = cx.initStandardObjects()
        cx.evaluateString(scope, source, "probe", 1, null)
    }

    private fun canParse(source: String): Boolean =
        runCatching { run(source) }.isSuccess

    @Test
    fun `reports actual engine capabilities`() {
        val cases = linkedMapOf(
            "let/const" to "let a = 1; const b = 2; a + b",
            "arrow function" to "const f = (x) => x * 2; f(3)",
            "template literal" to "const n = 'x'; `a\${n}b`",
            "class" to "class A { constructor() { this.v = 1 } }; new A().v",
            "class extends" to "class B { get x() { return 2 } }; class C extends B {}; new C().x",
            "class field" to "class D { name = 'n' }; new D().name",
            "destructuring" to "const { a, b } = { a: 1, b: 2 }; a + b",
            "spread" to "const arr = [...[1, 2], 3]; arr.length",
            "for-of" to "let s = 0; for (const v of [1, 2, 3]) s += v; s",
            "Map/Set" to "new Map([[1, 2]]).get(1)",
            "Promise" to "typeof Promise",
            "async function" to "async function f() { return 1 }; typeof f",
            "await" to "async function g() { return await 1 }; 1",
            "generator" to "function* gen() { yield 1 }; 1",
            "optional chaining" to "const o = { a: 1 }; o?.a",
            "nullish coalescing" to "null ?? 3",
            "Object.assign" to "Object.assign({}, { a: 1 }).a",
            "JSON" to "JSON.parse('{\"a\":1}').a",
        )

        val report = cases.map { (name, src) -> name to canParse(src) }
        println("=== Rhino 能力探测 ===")
        report.forEach { (name, ok) -> println("%-22s %s".format(name, if (ok) "OK" else "UNSUPPORTED")) }

        // JSON 规则引擎依赖的基础子集必须可用
        val supported = report.filter { it.second }.map { it.first }.toSet()
        assertThat(supported).containsAtLeast("let/const", "JSON", "Object.assign", "Promise")

        // 若哪天 class/async 变为支持，说明引擎已升级，应同步更新 CJS 方案与文档
        val classOk = "class" in supported
        val asyncOk = "async function" in supported
        println("class=$classOk async=$asyncOk")
        if (classOk && asyncOk) {
            println("提示：引擎已支持 class/async，可评估直接运行 CJS 源")
        }
    }

    @Test
    fun `async await support decides cjs viability`() {
        val asyncOk = canParse("async function f() { return 1 }; f()")
        val awaitOk = canParse("async function g() { return await 1 }; g()")
        println("async=$asyncOk await=$awaitOk")
        // 不断言必须支持：若未来换 QuickJS 则两者皆 true
    }
}
