package io.github.saonte.mangareader.cjs

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.quickjs.QuickJs
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QuickJS 能力实测（设备端）。
 *
 * 为什么必须设备端：QuickJS 是 native 库，JVM 单测跑不了。
 * 在 Android Studio 里右键本文件 → Run 即可（需连接真机或模拟器）。
 *
 * 关注点：venera 源全部依赖 `class` + `async/await`，而 Rhino 两者都不支持。
 * 这里要回答的核心问题是 **`await` 是否需要宿主驱动 microtask 队列**。
 */
@RunWith(AndroidJUnit4::class)
class QuickJsCapabilityTest {

    private fun eval(source: String): String =
        runCatching {
            val js = QuickJs.create()
            try {
                js.evaluate(source)?.toString() ?: "null"
            } finally {
                js.close()
            }
        }.getOrElse { "ERROR:${it::class.simpleName}:${it.message}" }

    @Test
    fun reportEngineCapabilities() {
        val results = linkedMapOf(
            "let/const" to "let a=1; const b=2; a+b",
            "arrow" to "const f=(x)=>x*2; f(3)",
            "template" to "`a\${1+1}b`",
            "destructuring" to "const {a,b}={a:1,b:2}; a+b",
            "spread" to "const [x,...r]=[1,2,3]; r.length",
            "optional chain" to "const o={a:1}; o?.a",
            "nullish" to "null ?? 7",
            "class" to "class A { constructor(){ this.v=1 } }; new A().v",
            "class extends" to "class B { get x(){return 2} }; class C extends B {}; new C().x",
            "class field" to "class D { name='n' }; new D().name",
            "static field" to "class E { static k='s' }; E.k",
            "for-of" to "let s=0; for (const v of [1,2,3]) s+=v; s",
            "Map" to "new Map([[1,2]]).get(1)",
        )
        println("========== QuickJS 语法能力 ==========")
        results.forEach { (name, src) ->
            val out = eval(src)
            val ok = !out.startsWith("ERROR")
            println("%-16s %s   => %s".format(name, if (ok) "OK  " else "FAIL", out.take(60)))
        }
    }

    @Test
    fun reportAsyncBehaviour() {
        println("========== QuickJS async/await 实测 ==========")

        val thenSync = eval(
            """
            (function(){
              let out = "PENDING";
              Promise.resolve("ok").then(v => { out = v; });
              return out;
            })()
            """.trimIndent(),
        )
        println("Promise.then 同步可见 = $thenSync")

        val asyncCall = eval("(async function(){ return 1; })()")
        println("async 调用返回     = $asyncCall")

        val awaitSync = eval(
            """
            (function(){
              let out = "PENDING";
              (async () => { out = await Promise.resolve("ok"); })();
              return out;
            })()
            """.trimIndent(),
        )
        println("await 后同步读取   = $awaitSync")

        val js = QuickJs.create()
        try {
            js.evaluate(
                """
                globalThis.__out = "PENDING";
                (async () => { globalThis.__out = await Promise.resolve("drained"); })();
                """.trimIndent(),
            )
            val first = js.evaluate("globalThis.__out")?.toString()
            val second = js.evaluate("globalThis.__out")?.toString()
            val third = js.evaluate("globalThis.__out")?.toString()
            println("连续 evaluate 读取 = $first / $second / $third")
        } finally {
            js.close()
        }

        println("============================================")
        println("判读：若 await 后仍为 PENDING，说明 microtask 未被驱动，")
        println("      需要在宿主侧用 Kotlin 实现 Promise 调度或改同步化封装。")
    }
}
