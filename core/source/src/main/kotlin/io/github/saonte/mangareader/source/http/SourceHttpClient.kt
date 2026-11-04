package io.github.saonte.mangareader.source.http

import io.github.saonte.mangareader.model.ErrorCode
import io.github.saonte.mangareader.source.HostMatcher
import io.github.saonte.mangareader.source.SourceFailure
import io.github.saonte.mangareader.source.SourceRef
import io.github.saonte.mangareader.source.rule.Expect
import io.github.saonte.mangareader.source.rule.HttpCache
import io.github.saonte.mangareader.source.rule.RequestSpec
import io.github.saonte.mangareader.source.rule.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.CacheControl
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

data class HttpResponse(
    val url: String,
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
    val contentType: String?,
    val charset: String,
)

/**
 * 源 HTTP 客户端：模板化请求、host 白名单、明文闸门、重定向校验、限流、重试、Cookie。
 * 不持有 Android 依赖，可在 JVM 单测中直接跑 MockWebServer。
 */
class SourceHttpClient(
    private val client: OkHttpClient,
) {
    private val buckets = ConcurrentHashMap<String, TokenBucket>()
    private val cookieJar = SourceCookieJar()

    private val httpClient: OkHttpClient = client.newBuilder()
        .cookieJar(cookieJar)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun execute(
        ref: SourceRef,
        spec: RequestSpec,
        vars: Map<String, String>,
        extraHeaders: Map<String, String> = emptyMap(),
        allowImageHosts: Boolean = false,
    ): HttpResponse {
        val renderedUrl = Template.render(spec.url, vars)
        val resolved = resolveUrl(renderedUrl, ref.baseUrl)
        validateUrl(ref, resolved, allowImageHosts)

        val timeout = ref.rule.http.timeoutMs.toLong()
        val attempts = ref.rule.http.retry + 1
        var lastError: SourceFailure? = null

        for (attempt in 0 until attempts) {
            if (attempt > 0) delay(1_000L shl (attempt - 1))
            try {
                bucketFor(ref).acquire()
                return withTimeout(timeout) {
                    fetchWithRedirects(ref, spec, vars, resolved, extraHeaders, allowImageHosts)
                }
            } catch (e: SourceFailure) {
                lastError = e
                if (!e.retryable) throw e
            } catch (e: IOException) {
                lastError = SourceFailure(
                    code = ErrorCode.NET_TIMEOUT,
                    message = "请求失败：${e.message}",
                    step = "http",
                    retryable = true,
                    cause = e,
                )
            }
        }
        throw lastError ?: SourceFailure(ErrorCode.UNKNOWN, "请求失败", step = "http")
    }

    /**
     * 下载图片：校验 host（hosts ∪ imageHosts）、注入页级 Header，返回原始字节。
     * 单图上限 32MB，失败抛 SourceFailure。
     */
    fun fetchImageBytes(ref: SourceRef, image: io.github.saonte.mangareader.model.ImageRef): ByteArray {
        val resolved = resolveUrl(image.url, ref.baseUrl)
        validateUrl(ref, resolved, allowImageHosts = true)
        val builder = Request.Builder().url(resolved)
        buildMap {
            putAll(ref.headers())
            putAll(image.headers)
        }.forEach { (key, value) -> builder.header(key, value) }
        val call = httpClient.newCall(builder.build())
        call.timeout().timeout(30, java.util.concurrent.TimeUnit.SECONDS)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw SourceFailure(
                        code = httpErrorCode(response.code),
                        message = "HTTP ${response.code}",
                        step = "download",
                        retryable = response.code == 429 || response.code >= 500,
                    )
                }
                val length = response.body?.contentLength() ?: -1
                if (length > MAX_IMAGE_BYTES) {
                    throw SourceFailure(ErrorCode.RESPONSE_TOO_LARGE, "图片超过 32MB", step = "download")
                }
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (bytes.isEmpty()) {
                    throw SourceFailure(ErrorCode.IMAGE_FETCH_FAILED, "图片为空", step = "download", retryable = true)
                }
                if (bytes.size > MAX_IMAGE_BYTES) {
                    throw SourceFailure(ErrorCode.RESPONSE_TOO_LARGE, "图片超过 32MB", step = "download")
                }
                bytes
            }
        } catch (e: SourceFailure) {
            throw e
        } catch (e: IOException) {
            throw SourceFailure(
                code = ErrorCode.NET_TIMEOUT,
                message = "图片下载失败：${e.message}",
                step = "download",
                retryable = true,
                cause = e,
            )
        }
    }

    /**
     * 规则导入用的纯文本抓取：仅 https，不绑定已安装源（规则尚未入库），
     * 因此不使用 host 白名单；限制大小与超时，避免被当作通用下载器。
     */
    suspend fun fetchRuleText(url: String, maxBytes: Long): String {
        if (!url.startsWith("https://")) {
            throw SourceFailure(ErrorCode.NET_TLS, "规则地址必须为 https", step = "import")
        }
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return withContext(Dispatchers.IO) {
            val call = httpClient.newCall(request)
            call.timeout().timeout(15, java.util.concurrent.TimeUnit.SECONDS)
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw SourceFailure(httpErrorCode(resp.code), "HTTP ${resp.code}", step = "import")
                    }
                    val declared = resp.body?.contentLength() ?: -1
                    if (declared > maxBytes) {
                        throw SourceFailure(ErrorCode.UNKNOWN, "规则文件超过上限", step = "import")
                    }
                    val bytes = readBounded(resp, maxBytes)
                    bytes.toString(Charsets.UTF_8)
                }
            } catch (e: SourceFailure) {
                throw e
            } catch (e: IOException) {
                throw SourceFailure(ErrorCode.NET_TIMEOUT, "下载规则失败：${e.message}", step = "import", cause = e)
            }
        }
    }

    /**
     * 供脚本钩子使用的同步请求：独立 10s 超时，仍走 host 白名单与明文闸门，不做重试。
     */
    fun fetchBlocking(
        ref: SourceRef,
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
    ): HttpResponse {
        val resolved = resolveUrl(url, ref.baseUrl)
        validateUrl(ref, resolved, allowImageHosts = true)
        val builder = Request.Builder().url(resolved)
        buildMap {
            putAll(ref.headers())
            headers.forEach { (k, v) -> put(k, v) }
        }.forEach { (k, v) -> builder.header(k, v) }
        if (method.equals("POST", true)) {
            val type = (headers["Content-Type"] ?: "application/x-www-form-urlencoded").toMediaType()
            builder.post((body ?: "").toRequestBody(type))
        }
        val call = httpClient.newCall(builder.build())
        call.timeout().timeout(10, java.util.concurrent.TimeUnit.SECONDS)
        return try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw SourceFailure(httpErrorCode(resp.code), "HTTP ${resp.code}", step = "script")
                }
                val bytes = readBounded(resp)
                val contentType = resp.header("Content-Type")
                val charset = charsetOf(contentType, "utf-8")
                HttpResponse(
                    url = resolved,
                    status = resp.code,
                    headers = resp.headers.toMap(),
                    body = bytes.toString(Charset.forName(charset)),
                    contentType = contentType,
                    charset = charset,
                )
            }
        } catch (e: SourceFailure) {
            throw e
        } catch (e: IOException) {
            throw SourceFailure(ErrorCode.NET_TIMEOUT, "脚本请求失败：${e.message}", step = "script", cause = e)
        }
    }

    private suspend fun fetchWithRedirects(
        ref: SourceRef,
        spec: RequestSpec,
        vars: Map<String, String>,
        startUrl: String,
        extraHeaders: Map<String, String>,
        allowImageHosts: Boolean,
    ): HttpResponse {
        var currentUrl = startUrl
        var redirects = 0
        val maxRedirects = spec.redirects

        while (true) {
            val request = buildRequest(ref, spec, vars, currentUrl, extraHeaders)
            val response = withContext(Dispatchers.IO) {
                val call = httpClient.newCall(request)
                call.timeout().timeout(ref.rule.http.timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                call.execute()
            }
            response.use { resp ->
                val location = resp.header("Location")
                if (resp.isRedirect && location != null) {
                    if (redirects >= maxRedirects) {
                        throw SourceFailure(ErrorCode.HTTP_5XX, "重定向次数过多", step = "http")
                    }
                    val next = resolveUrl(location, currentUrl)
                    validateUrl(ref, next, allowImageHosts)
                    currentUrl = next
                    redirects += 1
                    return@use
                }

                if (!resp.isSuccessful) {
                    val code = httpErrorCode(resp.code)
                    throw SourceFailure(
                        code = code,
                        message = "HTTP ${resp.code}",
                        step = "http",
                        retryable = resp.code == 429 || resp.code >= 500,
                    )
                }

                val bytes = readBounded(resp)
                val contentType = resp.header("Content-Type")
                val charset = charsetOf(contentType, spec.charset)
                val body = bytes.toString(Charset.forName(charset))
                return HttpResponse(
                    url = currentUrl,
                    status = resp.code,
                    headers = resp.headers.toMap(),
                    body = body,
                    contentType = contentType,
                    charset = charset,
                )
            }
        }
    }

    private fun buildRequest(
        ref: SourceRef,
        spec: RequestSpec,
        vars: Map<String, String>,
        url: String,
        extraHeaders: Map<String, String>,
    ): Request {
        val builder = Request.Builder().url(url)
        val headers = buildMap {
            putAll(ref.headers())
            spec.headers.forEach { (k, v) -> put(k, Template.render(v, vars)) }
            putAll(extraHeaders)
        }
        headers.forEach { (k, v) -> builder.header(k, v) }

        when (spec.method.uppercase()) {
            "POST" -> {
                val body = Template.render(spec.body.orEmpty(), vars)
                val mediaType = (spec.contentType ?: "application/x-www-form-urlencoded").toMediaType()
                builder.post(body.toRequestBody(mediaType))
            }

            else -> builder.get()
        }
        if (spec.cache == HttpCache.NO_STORE) {
            builder.cacheControl(CacheControl.Builder().noStore().build())
        }
        if (spec.expect == Expect.JSON) {
            builder.header("Accept", "application/json, text/plain, */*")
        }
        return builder.build()
    }

    private fun validateUrl(ref: SourceRef, url: String, allowImageHosts: Boolean) {
        val uri = runCatching { URI(url) }.getOrElse {
            throw SourceFailure(ErrorCode.RULE_INVALID, "非法 URL：$url", step = "http")
        }
        val scheme = uri.scheme?.lowercase() ?: throw SourceFailure(ErrorCode.RULE_INVALID, "URL 缺少 scheme", step = "http")
        if (scheme != "http" && scheme != "https") {
            throw SourceFailure(ErrorCode.RULE_INVALID, "不支持的协议：$scheme", step = "http")
        }
        if (scheme == "http" && !ref.rule.allowCleartext) {
            throw SourceFailure(ErrorCode.NET_TLS, "源使用明文 HTTP，但未声明 allowCleartext", step = "http")
        }
        val host = uri.host ?: throw SourceFailure(ErrorCode.RULE_INVALID, "URL 缺少 host", step = "http")
        val allowed = if (allowImageHosts) ref.rule.hosts + ref.rule.imageHosts else ref.rule.hosts
        if (!HostMatcher.matches(host, allowed)) {
            throw SourceFailure(
                code = ErrorCode.RULE_INVALID,
                message = "host 不在白名单：$host",
                step = "http",
                retryable = false,
            )
        }
    }

    private fun readBounded(response: Response, limit: Long = MAX_BYTES): ByteArray {
        val contentLength = response.body?.contentLength() ?: -1
        if (contentLength > limit) {
            throw SourceFailure(ErrorCode.RESPONSE_TOO_LARGE, "响应体过大：$contentLength", step = "http")
        }
        val bytes = response.body?.bytes() ?: ByteArray(0)
        if (bytes.size > limit) {
            throw SourceFailure(ErrorCode.RESPONSE_TOO_LARGE, "响应体超过上限：${bytes.size}", step = "http")
        }
        return bytes
    }

    private fun charsetOf(contentType: String?, fallback: String): String {
        val fromHeader = contentType?.substringAfter("charset=", "")?.substringBefore(';')?.trim()?.trim('"')
        val candidate = fromHeader?.takeIf { it.isNotBlank() } ?: fallback
        return runCatching { Charset.forName(candidate).name() }.getOrDefault("utf-8")
    }

    private fun httpErrorCode(status: Int): ErrorCode = when (status) {
        401 -> ErrorCode.HTTP_401
        403 -> ErrorCode.HTTP_403
        404 -> ErrorCode.HTTP_404
        429 -> ErrorCode.HTTP_429
        in 500..599 -> ErrorCode.HTTP_5XX
        else -> ErrorCode.UNKNOWN
    }

    private fun bucketFor(ref: SourceRef): TokenBucket =
        buckets.computeIfAbsent("${ref.type}:${ref.ruleId}") {
            TokenBucket(ref.rule.http.rateLimit.rps, ref.rule.http.rateLimit.burst)
        }

    private fun resolveUrl(value: String, base: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return URI(base).resolve(trimmed).toString()
    }

    companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
        const val MAX_IMAGE_BYTES = 32L * 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}

/**
 * 简单令牌桶：每源独立，避免社区规则把站点打挂。
 */
class TokenBucket(
    private val rps: Double,
    private val burst: Int,
) {
    private val mutex = Mutex()
    private var tokens: Double = burst.toDouble()
    private var lastRefill: Long = System.nanoTime()

    suspend fun acquire() {
        while (true) {
            val waitNanos = mutex.withLock {
                refill()
                if (tokens >= 1.0) {
                    tokens -= 1.0
                    0L
                } else {
                    ((1.0 - tokens) / rps * 1_000_000_000.0).toLong().coerceAtLeast(1_000_000L)
                }
            }
            if (waitNanos == 0L) return
            delay(waitNanos / 1_000_000)
        }
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedSeconds = (now - lastRefill) / 1_000_000_000.0
        if (elapsedSeconds > 0) {
            tokens = (tokens + elapsedSeconds * rps).coerceAtMost(burst.toDouble())
            lastRefill = now
        }
    }
}

class SourceCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = key(url)
        val list = store.computeIfAbsent(key) { mutableListOf() }
        synchronized(list) {
            cookies.forEach { cookie ->
                list.removeAll { it.name == cookie.name && it.path == cookie.path }
                list.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = store[key(url)] ?: return emptyList()
        synchronized(list) {
            return list.filter { it.matches(url) }
        }
    }

    private fun key(url: HttpUrl): String = "${url.scheme}://${url.host}"

    fun clear() {
        store.clear()
    }
}
