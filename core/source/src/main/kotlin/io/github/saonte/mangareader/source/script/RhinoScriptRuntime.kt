package io.github.saonte.mangareader.source.script

import io.github.saonte.mangareader.model.ErrorCode
import io.github.saonte.mangareader.source.SourceFailure
import io.github.saonte.mangareader.source.SourceRef
import io.github.saonte.mangareader.source.http.HttpResponse
import io.github.saonte.mangareader.source.http.SourceHttpClient
import io.github.saonte.mangareader.source.rule.PipeOp
import io.github.saonte.mangareader.source.rule.Pipes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 脚本调用上下文：只读数据 + 可选的 HTTP 能力（用于多请求/解密类站点）。
 */
data class ScriptCall(
    val ruleId: String,
    val hook: String,
    val source: String,
    val vars: Map<String, String>,
    val url: String,
    val html: String? = null,
    val json: String? = null,
    val ref: SourceRef? = null,
    val httpClient: SourceHttpClient? = null,
    val timeoutMs: Long = 3_000,
)

interface ScriptRuntime {
    fun invoke(call: ScriptCall): JsonElement?
}

/**
 * Rhino 沙箱：拒绝所有 Java 类、3s 超时、栈深限制、白名单 API。
 * 语言级别 ES5.1；脚本返回 JSON.stringify 后的结果再解析为 JsonElement。
 */
class RhinoScriptRuntime : ScriptRuntime {

    private val caches = ConcurrentHashMap<String, MutableMap<String, String>>()
    private val logLines = ArrayDeque<String>()

    override fun invoke(call: ScriptCall): JsonElement? {
        val factory = TimeoutContextFactory(call.timeoutMs)
        val cx = factory.enterContext()
        try {
            cx.optimizationLevel = -1
            cx.maximumInterpreterStackDepth = 1_000
            cx.instructionObserverThreshold = 10_000
            cx.languageVersion = Context.VERSION_1_8
            cx.setClassShutter(object : ClassShutter {
                override fun visibleToScripts(fullClassName: String): Boolean = false
            })

            val scope = cx.initStandardObjects()
            val hookFn = cx.compileFunction(scope, call.source, "${call.ruleId}:${call.hook}", 1, null)
            val ctxObject = buildContextObject(cx, scope, call)

            val result = hookFn.call(cx, scope, scope, arrayOf(ctxObject))
            if (result == null || result === Undefined.instance) return null
            return stringify(cx, scope, result)
        } catch (e: SourceFailure) {
            throw e
        } catch (e: EvaluatorException) {
            val timeout = e.message?.contains("script timeout") == true || e.message?.contains("timeout") == true
            throw SourceFailure(
                code = if (timeout) ErrorCode.SCRIPT_TIMEOUT else ErrorCode.SCRIPT_ERROR,
                message = e.message ?: "脚本执行失败",
                step = call.hook,
                pointer = "/scripts/${call.hook}",
                retryable = false,
                cause = e,
            )
        } catch (e: Exception) {
            throw SourceFailure(
                code = ErrorCode.SCRIPT_ERROR,
                message = "脚本执行失败：${e.message}",
                step = call.hook,
                pointer = "/scripts/${call.hook}",
                cause = e,
            )
        } finally {
            Context.exit()
        }
    }

    fun recentLogs(limit: Int = 50): List<String> = synchronized(logLines) {
        logLines.takeLast(limit)
    }

    private fun buildContextObject(cx: Context, scope: Scriptable, call: ScriptCall): Scriptable {
        val ctx = cx.newObject(scope)
        call.vars.forEach { (k, v) -> ScriptableObject.putProperty(ctx, k, v) }
        ScriptableObject.putProperty(ctx, "base", call.vars["base"] ?: "")
        ScriptableObject.putProperty(ctx, "url", call.url)
        ScriptableObject.putProperty(ctx, "html", call.html ?: "")
        ScriptableObject.putProperty(ctx, "text", call.html?.let { Jsoup.parse(it).text() } ?: "")
        call.json?.let { raw ->
            val parsed = runCatching { jsonParse(cx, scope, raw) }.getOrNull()
            ScriptableObject.putProperty(ctx, "json", parsed)
        }

        val document = call.html?.let { Jsoup.parse(it) }
        ScriptableObject.putProperty(ctx, "doc", fn { args ->
            val css = args.firstOrNull()?.toString().orEmpty()
            val element = runCatching { document?.selectFirst(css) }.getOrNull()
            wrapElement(cx, scope, element)
        })
        ScriptableObject.putProperty(ctx, "resolve", fn { args ->
            resolveUrl(args.firstOrNull()?.toString().orEmpty(), call.url)
        })
        ScriptableObject.putProperty(ctx, "decode", fn { args ->
            Pipes.apply(args.firstOrNull()?.toString().orEmpty(), listOf(PipeOp("decodeHtml"), PipeOp("decodeJs")))
        })
        ScriptableObject.putProperty(
            ctx,
            "log",
            fn { args ->
                val line = "${call.ruleId}/${call.hook}: ${args.joinToString(" ")}"
                synchronized(logLines) {
                    logLines.addLast(line)
                    while (logLines.size > 200) logLines.removeFirst()
                }
                Undefined.instance
            },
        )

        ScriptableObject.putProperty(ctx, "http", buildHttp(cx, scope, call))
        ScriptableObject.putProperty(ctx, "util", buildUtil(cx, scope))
        ScriptableObject.putProperty(ctx, "cache", buildCache(cx, scope, call.ruleId))
        return ctx
    }

    private fun buildHttp(cx: Context, scope: Scriptable, call: ScriptCall): Scriptable {
        val obj = cx.newObject(scope)
        val counter = AtomicInteger(0)
        ScriptableObject.putProperty(
            obj,
            "fetch",
            fn { args ->
                val req = args.firstOrNull() as? Scriptable ?: throw EvaluatorException("http.fetch 需要对象参数")
                if (counter.incrementAndGet() > MAX_HTTP_PER_HOOK) {
                    throw EvaluatorException("http.fetch 超过单次钩子上限（$MAX_HTTP_PER_HOOK）")
                }
                val url = ScriptableObject.getProperty(req, "url")?.toString()
                    ?: throw EvaluatorException("http.fetch 缺少 url")
                val method = ScriptableObject.getProperty(req, "method")?.toString() ?: "GET"
                val body = ScriptableObject.getProperty(req, "body")?.toString()
                val headersObj = ScriptableObject.getProperty(req, "headers") as? Scriptable
                val headers = mutableMapOf<String, String>()
                headersObj?.let { h ->
                    h.ids.forEach { id -> headers[id.toString()] = ScriptableObject.getProperty(h, id.toString()).toString() }
                }
                val client = call.httpClient ?: throw EvaluatorException("http.fetch 不可用")
                val ref = call.ref ?: throw EvaluatorException("http.fetch 不可用")
                val response: HttpResponse = try {
                    client.fetchBlocking(ref, url, method, headers, body)
                } catch (e: Exception) {
                    throw EvaluatorException("http.fetch 失败：${e.message}")
                }
                val resObj = cx.newObject(scope)
                ScriptableObject.putProperty(resObj, "status", response.status)
                ScriptableObject.putProperty(resObj, "body", response.body)
                val headerObj = cx.newObject(scope)
                response.headers.forEach { (k, v) -> ScriptableObject.putProperty(headerObj, k, v) }
                ScriptableObject.putProperty(resObj, "headers", headerObj)
                ScriptableObject.putProperty(resObj, "text", fn { _ -> response.body })
                ScriptableObject.putProperty(resObj, "json", fn { _ -> jsonParse(cx, scope, response.body) })
                resObj
            },
        )
        return obj
    }

    private fun buildUtil(cx: Context, scope: Scriptable): Scriptable {
        val util = cx.newObject(scope)
        ScriptableObject.putProperty(util, "base64Decode", fn { args ->
            String(Base64.getDecoder().decode(args.firstOrNull()?.toString().orEmpty()), StandardCharsets.UTF_8)
        })
        ScriptableObject.putProperty(util, "base64Encode", fn { args ->
            Base64.getEncoder().encodeToString(args.firstOrNull()?.toString().orEmpty().toByteArray())
        })
        ScriptableObject.putProperty(util, "md5", fn { args -> digest("MD5", args.firstOrNull()?.toString().orEmpty()) })
        ScriptableObject.putProperty(util, "sha256", fn { args -> digest("SHA-256", args.firstOrNull()?.toString().orEmpty()) })
        ScriptableObject.putProperty(util, "unicode", fn { args ->
            Pipes.apply(args.firstOrNull()?.toString().orEmpty(), listOf(PipeOp("decodeJs")))
        })
        ScriptableObject.putProperty(util, "time", fn { _ -> System.currentTimeMillis() })
        ScriptableObject.putProperty(util, "xor", fn { args ->
            val data = Base64.getDecoder().decode(args.getOrNull(0)?.toString().orEmpty())
            val key = Base64.getDecoder().decode(args.getOrNull(1)?.toString().orEmpty())
            val out = ByteArray(data.size) { i -> (data[i].toInt() xor key[i % key.size].toInt()).toByte() }
            Base64.getEncoder().encodeToString(out)
        })
        ScriptableObject.putProperty(util, "aesDecrypt", fn { args ->
            val data = Base64.getDecoder().decode(args.getOrNull(0)?.toString().orEmpty())
            val keyBytes = Base64.getDecoder().decode(args.getOrNull(1)?.toString().orEmpty())
            val iv = args.getOrNull(2)?.toString().orEmpty()
            val mode = args.getOrNull(3)?.toString()?.uppercase() ?: "CBC"
            val padding = args.getOrNull(4)?.toString() ?: "PKCS5Padding"
            val transformation = "AES/$mode/$padding"
            val cipher = Cipher.getInstance(transformation)
            val keySpec = SecretKeySpec(keyBytes, "AES")
            if (mode == "CBC") {
                val ivBytes = Base64.getDecoder().decode(iv)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(ivBytes))
            } else {
                cipher.init(Cipher.DECRYPT_MODE, keySpec)
            }
            Base64.getEncoder().encodeToString(cipher.doFinal(data))
        })
        return util
    }

    private fun buildCache(cx: Context, scope: Scriptable, ruleId: String): Scriptable {
        val cache = cx.newObject(scope)
        val map = caches.computeIfAbsent(ruleId) { ConcurrentHashMap() }
        ScriptableObject.putProperty(cache, "get", fn { args -> map[args.firstOrNull()?.toString().orEmpty()] ?: Undefined.instance })
        ScriptableObject.putProperty(cache, "set", fn { args ->
            val key = args.getOrNull(0)?.toString().orEmpty()
            map[key] = args.getOrNull(1)?.toString().orEmpty()
            if (map.size > MAX_CACHE_ENTRIES) {
                map.keys.take(map.size - MAX_CACHE_ENTRIES).forEach { map.remove(it) }
            }
            Undefined.instance
        })
        return cache
    }

    private fun wrapElement(cx: Context, scope: Scriptable, element: Element?): Any {
        if (element == null) return Undefined.instance
        val obj = cx.newObject(scope)
        ScriptableObject.putProperty(obj, "text", fn { _ -> element.text() })
        ScriptableObject.putProperty(obj, "html", fn { _ -> element.html() })
        ScriptableObject.putProperty(obj, "attr", fn { args -> element.attr(args.firstOrNull()?.toString().orEmpty()) })
        ScriptableObject.putProperty(obj, "find", fn { args ->
            wrapElement(cx, scope, element.selectFirst(args.firstOrNull()?.toString().orEmpty()))
        })
        ScriptableObject.putProperty(obj, "first", fn { _ ->
            wrapElement(cx, scope, element.selectFirst("*") ?: element)
        })
        return obj
    }

    private fun stringify(cx: Context, scope: Scriptable, value: Any): JsonElement? {
        val text = if (value is String || value is Number || value is Boolean) {
            value.toString()
        } else {
            val jsonObj = ScriptableObject.getProperty(scope, "JSON") as? Scriptable
            val stringify = jsonObj?.let { ScriptableObject.getProperty(it, "stringify") } as? Function
                ?: throw EvaluatorException("JSON.stringify 不可用")
            stringify.call(cx, scope, jsonObj, arrayOf(value))?.toString()
                ?: throw EvaluatorException("脚本返回值无法序列化")
        }
        return runCatching { Json.parseToJsonElement(text) }.getOrNull()
    }

    private fun jsonParse(cx: Context, scope: Scriptable, raw: String): Any {
        val jsonObj = ScriptableObject.getProperty(scope, "JSON") as? Scriptable
        val parse = jsonObj?.let { ScriptableObject.getProperty(it, "parse") } as? Function
            ?: throw EvaluatorException("JSON.parse 不可用")
        return parse.call(cx, scope, jsonObj, arrayOf(raw))
    }

    private fun resolveUrl(value: String, base: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return runCatching { java.net.URI(base).resolve(trimmed).toString() }.getOrDefault(trimmed)
    }

    private fun digest(algorithm: String, input: String): String =
        MessageDigest.getInstance(algorithm).digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun fn(block: (Array<Any?>) -> Any?): BaseFunction = object : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? = block(args)
        override fun getFunctionName(): String = "fn"
    }

    companion object {
        const val MAX_HTTP_PER_HOOK = 10
        const val MAX_CACHE_ENTRIES = 200
    }
}

/**
 * Rhino Context 子类：在解释执行时按墙钟时间中断脚本。
 */
private class TimeoutContextFactory(private val timeoutMs: Long) : ContextFactory() {
    override fun makeContext(): Context = TimeoutContext(timeoutMs)
}

private class TimeoutContext(private val timeoutMs: Long) : Context() {
    private var deadline: Long = 0

    override fun observeInstructionCount(instructionCount: Int) {
        if (deadline == 0L) {
            deadline = System.nanoTime() + timeoutMs * 1_000_000
        }
        if (System.nanoTime() > deadline) {
            throw EvaluatorException("script timeout")
        }
    }
}
