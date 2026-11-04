package io.github.saonte.mangareader.domain

import io.github.saonte.mangareader.data.repo.Source
import io.github.saonte.mangareader.data.repo.SourceRepository
import io.github.saonte.mangareader.source.SearchPage
import io.github.saonte.mangareader.source.SourceEngine
import io.github.saonte.mangareader.source.SourceFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SearchOutcome {
    data class Success(val source: Source, val page: SearchPage) : SearchOutcome
    data class Failure(val source: Source, val message: String, val retryable: Boolean) : SearchOutcome
    data object NoSources : SearchOutcome
    data object Finished : SearchOutcome
}

/**
 * 聚合搜索：每个启用源并发请求，**先到先发**（channelFlow），单源失败不影响其他源。
 * 成功后上报健康度；失败按错误码自增失败计数。
 */
@Singleton
class SearchCoordinator @Inject constructor(
    private val engine: SourceEngine,
    private val sources: SourceRepository,
    private val sourceManager: SourceManager,
) {
    fun search(keyword: String): Flow<SearchOutcome> = channelFlow {
        val enabled = sources.observeEnabled().first()
        if (enabled.isEmpty()) {
            send(SearchOutcome.NoSources)
            send(SearchOutcome.Finished)
            close()
            return@channelFlow
        }

        var remaining = enabled.size
        val lock = Any()
        enabled.forEach { source ->
            launch(Dispatchers.IO) {
                val ref = sourceManager.ref(source.type)
                if (ref == null) {
                    synchronized(lock) { remaining -= 1 }
                    send(SearchOutcome.Failure(source, "规则无法解析，请重新导入", retryable = false))
                    finishIfDone(remaining, lock)
                    return@launch
                }
                runCatching {
                    withTimeout(SEARCH_TIMEOUT_MS) { engine.search(ref, keyword, 1) }
                }.onSuccess { page ->
                    sources.recordSuccess(source.type)
                    synchronized(lock) { remaining -= 1 }
                    send(SearchOutcome.Success(source, page))
                    finishIfDone(remaining, lock)
                }.onFailure { throwable ->
                    sources.recordFailure(source.type, (throwable as? SourceFailure)?.code?.name ?: "UNKNOWN")
                    val failure = throwable as? SourceFailure
                    synchronized(lock) { remaining -= 1 }
                    send(
                        SearchOutcome.Failure(
                            source = source,
                            message = failure?.userMessage() ?: (throwable.message ?: "搜索失败"),
                            retryable = failure?.retryable ?: true,
                        ),
                    )
                    finishIfDone(remaining, lock)
                }
            }
        }
        awaitClose()
    }.flowOn(Dispatchers.Default)

    private suspend fun kotlinx.coroutines.channels.ProducerScope<SearchOutcome>.finishIfDone(
        remaining: Int,
        lock: Any,
    ) {
        val done = synchronized(lock) { remaining <= 0 }
        if (done) {
            send(SearchOutcome.Finished)
            close()
        }
    }

    companion object {
        const val SEARCH_TIMEOUT_MS = 15_000L
    }
}

fun SourceFailure.userMessage(): String = when (code) {
    io.github.saonte.mangareader.model.ErrorCode.NET_TIMEOUT -> "网络超时"
    io.github.saonte.mangareader.model.ErrorCode.NET_DNS -> "无法连接站点"
    io.github.saonte.mangareader.model.ErrorCode.NET_TLS -> "连接不安全（需要 HTTPS 或放行明文）"
    io.github.saonte.mangareader.model.ErrorCode.HTTP_403 -> "源拒绝了请求（可能需要更新规则）"
    io.github.saonte.mangareader.model.ErrorCode.HTTP_404 -> "页面不存在"
    io.github.saonte.mangareader.model.ErrorCode.HTTP_429 -> "请求过于频繁，请稍后重试"
    io.github.saonte.mangareader.model.ErrorCode.SELECTOR_EMPTY -> "页面结构可能已变化"
    io.github.saonte.mangareader.model.ErrorCode.RULE_INVALID -> "规则不完整或已失效"
    io.github.saonte.mangareader.model.ErrorCode.SCRIPT_TIMEOUT -> "规则脚本执行超时"
    io.github.saonte.mangareader.model.ErrorCode.SCRIPT_ERROR -> "规则脚本执行失败"
    io.github.saonte.mangareader.model.ErrorCode.IMAGE_FETCH_FAILED -> "图片加载失败"
    else -> message ?: "请求失败"
}
