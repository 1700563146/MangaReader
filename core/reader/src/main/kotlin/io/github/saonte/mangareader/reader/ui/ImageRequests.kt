package io.github.saonte.mangareader.reader.ui

import android.content.Context
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import io.github.saonte.mangareader.model.ImageRef

/**
 * 图片请求构造：把章节页的 Referer/Cookie 注入图片请求，避免防盗链导致 403。
 * 失败页由 [ImageRef.urls] 提供备用地址（M3 接入失败回退）。
 */
fun imageRequest(
    context: Context,
    ref: ImageRef,
    width: Int? = null,
    height: Int? = null,
    url: String = ref.url,
): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .data(url)
    if (ref.headers.isNotEmpty()) {
        val headers = NetworkHeaders.Builder().apply {
            ref.headers.forEach { (key, value) -> set(key, value) }
        }.build()
        builder.httpHeaders(headers)
    }
    if (width != null && height != null && width > 0 && height > 0) {
        builder.size(width, height)
    }
    return builder.build()
}
