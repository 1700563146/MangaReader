# 06 阅读器内核

> 阅读器是这类产品的生死线：**翻页跟手、条漫不断流、任何异常都不白屏**。
> v1 用 **Compose 原生实现**（`HorizontalPager` / `LazyColumn` + 自定义缩放修饰符），M2 设决策门（ADR-006）：若性能不达标，退回「Compose 外壳 + 自定义 View 画布」，对外契约不变。

## 1. 设计约束

| 约束 | 影响 |
| --- | --- |
| 图片来自不可控第三方站点 | 处理防盗链、403、超时、半张图、错误格式 |
| 章节可能是惰性的 | 页数未知，边读边解析 |
| 长图可达 20000px | 切片解码，不整张进内存 |
| 竖屏/横屏/平板/折叠屏 | 模式与双页自适应 |
| 弱网/地铁 | 预取 + 缓存 + 重试 + 明确状态 |
| Compose 手势体系 | 缩放与 pager 嵌套滚动需自行仲裁 |

## 2. 模式矩阵

| 模式 | 滚动 | 手势 | 默认场景 |
| --- | --- | --- | --- |
| `PAGED_LTR` | 横向，左→右 | 横向拖动、点击左右区 | 日漫 |
| `PAGED_RTL` | 横向，右→左 | 同上（视觉反向） | 右开本 |
| `PAGED_TTB` | 纵向分页 | 纵向拖动 | 韩漫单页 |
| `WEBTOON` | 纵向连续 | 自由滚动、缩放 | 条漫 |
| 双页 | 横向 | 双页拼合 | 平板/横屏 |

双页不是独立模式，是翻页模式的布局策略（`doublePage: Boolean`），由屏幕宽度自动建议。

## 3. 结构

```
ReaderRoute
└── ReaderScreen (Box)
    ├── ReaderContent
    │    ├── PagedReader   (HorizontalPager / VerticalPager)
    │    └── WebtoonReader (LazyColumn)
    ├── ReaderOverlay      (页码、章节名、加载/错误提示)
    └── ReaderMenu         (顶部栏、底部进度条、设置面板)  ← 独立 Composable
```

| 组件 | 职责 |
| --- | --- |
| `ReaderViewModel` | 打开章节、加载页源、持久化进度、错误重试 |
| `ReaderController` | 纯 Kotlin 状态机：页切换、预取窗口、章节边界、模式切换 |
| `PageSource` | 页数据抽象：`RemotePageSource` / `FilePageSource` / `LocalPageSource` |
| `ZoomState` | `scale + offset`，`Animatable`，提供 clamp 与双击目标 |
| `PageImage` | 单页渲染：`AsyncImage` + 缩放/平移 + 占位/错误 |
| `ReaderPrefetcher` | 基于 `LazyListState.layoutInfo` / `pagerState` 的窗口预取 |

## 4. 状态与契约

```kotlin
@Immutable
data class ReaderUiState(
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,          // 0 = 未知（惰性）
    val mode: ReadMode = ReadMode.PAGED_LTR,
    val doublePage: Boolean = false,
    val isMenuVisible: Boolean = false,
    val scale: Float = 1f,
    val loading: Boolean = true,
    val error: AppError? = null,
)

class ReaderController(
    private val pageSource: PageSource,
    private val progressSink: ProgressSink,
    private val scope: CoroutineScope,
) {
    val state: StateFlow<ReaderUiState>
    fun onPageSettled(index: Int)
    fun onZoomChanged(scale: Float)
    fun retry()
    fun switchMode(mode: ReadMode)
    fun flushProgress()
}

interface PageSource {
    val pageCount: StateFlow<Int>                 // 0 表示未知
    val currentChapter: ChapterInfo
    suspend fun page(index: Int): ImageRef
    fun hasNextChapter(): Boolean
    suspend fun loadNextChapter(): Boolean
}
```

单向数据流：UI 事件 → `ReaderViewModel` → `Controller` → `StateFlow` → Compose 重组。**Composable 不持有业务状态**，缩放状态可 `rememberSaveable` 保存在 UI 层（属于瞬时交互状态）。

## 5. 翻页实现（PagedReader）

```kotlin
val pagerState = rememberPagerState(pageCount = { uiState.pageCount })

HorizontalPager(
    state = pagerState,
    beyondViewportPageCount = 1,              // 前后各预组合 1 页
    userScrollEnabled = zoomState.scale <= 1.01f,   // 缩放时禁止翻页
    pageSpacing = 0.dp,
    key = { it },
) { page ->
    PageImage(
        pageSource = pageSource,
        index = page,
        zoomState = zoomState,
        onRetry = { viewModel.retryPage(page) },
    )
}
```

- **RTL**：用 `LocalLayoutDirection` 切换为 RTL，而非反转数据；页码仍按逻辑递增。
- **TTB**：换成 `VerticalPager`，`beyondViewportPageCount = 1`。
- **双页**：`pageCount = ceil(total / 2)`，每页渲染 `Row` 两图；横图独占一页（`isWide` 判定宽高比 ≥ 1.05）。
- **跳页**：进度条拖动 → `pagerState.scrollToPage`；不要 `animateScrollToPage` 跨多页（耗时）。
- **章节边界**：`pagerState.currentPage == last` 且继续后滑时触发 `loadNextChapter()`，成功后 `pageCount` 更新并保持位置。

### 5.1 缩放与手势仲裁

```kotlin
Modifier.pointerInput(Unit) {
    detectTransformGestures { centroid, pan, zoom, _ ->
        if (zoomState.scale > 1f || zoom > 1f) {
            zoomState.applyGesture(centroid, pan, zoom, size)
        }
    }
}.pointerInput(Unit) {
    detectTapGestures(
        onTap = { offset -> handleTap(offset, size) },       // 点击区
        onDoubleTap = { if (settings.doubleTapZoom) zoomState.toggle(2f) },
        onLongPress = { viewModel.toggleModeByGesture() },
    )
}
```

仲裁规则：
1. `scale <= 1.01`：手势交给 pager（翻页）；点击/双击由页面层处理。
2. `scale > 1.01`：pager `userScrollEnabled = false`，单指拖动平移；拖动到边界后继续拖动（阈值 48dp + 速度）才翻页。
3. 双指缩放期间禁止 pager 滚动；缩放结束后按 `scale` 决定是否恢复。
4. 双击与单击 250ms 互斥判定。
5. 长按 500ms 触发，移动超 `touchSlop` 取消。
6. 菜单可见时，所有手势优先交给菜单层。

`ZoomState` 关键逻辑：

```kotlin
fun applyGesture(centroid: Offset, pan: Offset, zoom: Float, size: IntSize) {
    val newScale = (scale * zoom).coerceIn(1f, 5f)
    val newOffset = offset + pan
    val bound = maxOffset(newScale, size)       // (size * (scale - 1) / 2)
    offset = Offset(newOffset.x.coerceIn(-bound.x, bound.x), newOffset.y.coerceIn(-bound.y, bound.y))
    scale = newScale
}
```

### 5.2 点击区

| 区域（宽/高占比） | 默认动作 | 可配置 |
| --- | --- | --- |
| 左 0–30% | 上一页 | 无 / 上一页 / 上一章 / 菜单 |
| 中 30–70% | 菜单 | 菜单 / 无 |
| 右 70–100% | 下一页 | 无 / 下一页 / 下一章 / 菜单 |
| 条漫上/下区 | 滚动一屏 | 可关 |

条漫模式下点击中区同样显示菜单；「上下区」仅 `WEBTOON` 生效。

### 5.3 音量键、返回键

- 音量键：`Modifier.onPreviewKeyEvent` 拦截 `KEYCODE_VOLUME_DOWN/UP`，翻页并消费事件；可关。
- 返回键：`BackHandler` 优先关闭菜单 → 退出阅读；可配置。

## 6. 条漫实现（WebtoonReader）

```kotlin
LazyColumn(
    state = listState,
    contentPadding = PaddingValues(0.dp),
    beyondBoundsItemCount = 2,
    key = { index -> pageKeys[index] },       // pageKey = chapterPath#index
) {
    items(count = uiState.pageCount, contentType = { "page" }) { index ->
        PageImage(pageSource, index, zoomState, Modifier.fillMaxWidth())
    }
}
```

- **高度估计**：先用占位比（宽 × 1.4，封面类图可按已知宽高比），`AsyncImage` 成功回调后写入 `aspectRatios[index]`，`LazyColumn` 自动校正且保持 `firstVisibleItem` + offset（避免跳动）。
- **长图切片**：单页高 > 4096px 时切成 `ceil(h / 2048)` 个 item；`PageImage` 按 `tileIndex` 只解码该区域（`BitmapRegionDecoder`），拼接处 `0.dp` 间隙。
- **缩放**：整表级别 `graphicsLayer`（缩放 + 平移）；缩放期间禁用滚动；双击还原。
- **边界**：到末尾显示「下一章」分隔；`autoNextChapter` 开启时自动追加下一章前 3 页（预取成功才追加，避免空占位）。
- **回收**：依赖 `LazyColumn` 的复用 + Coil 内存缓存；不额外持有 Bitmap 引用。

## 7. 图片流水线（Coil 3）

```kotlin
ImageLoader.Builder(context)
    .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.25).build() }
    .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("image"))
        .maxSizeBytes(settings.imageCacheMb * 1024L * 1024L).build() }
    .components {
        add(SourceHeaderFetcher.Factory())     // 注入 Referer/Cookie
        add(SliceDecoder.Factory())            // 长图切片
    }
    .build()
```

| 环节 | 策略 |
| --- | --- |
| 请求键 | `url + headersHash + targetSize + sliceIndex`，避免不同源串缓存 |
| Fetcher | OkHttp + 源 Header；失败自动尝试 `ImageRef.urls` 备用地址 |
| 解码 | 按目标尺寸 `size()` 下采样；`allowHardware(false)` 用于超长图；低内存设备降 RGB_565 |
| 后处理 | 白边裁剪（可选，缓存结果）、夜间滤镜（`ColorFilter` 叠加，不重解码） |
| 缓存 | 内存 25%、磁盘 512MB（可配 128–2048） |
| 失败 | 单页占位 + 重试；同页 3 次失败停止自动重试 |
| 动图 | v1 只取首帧；GIF/APNG 播放列 P2 |

要点：
- **绝不整张解码长图**：先 bounds，再按 tile 区域解码。
- Header 只认 `ImageRef.headers`，不回退全局 Header（防串源）。
- 已下载章节用 `FileFetcher`（`file://`），不走网络。

## 8. 预取

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `forward` | 3 | 之后预取页数 |
| `backward` | 1 | 之前预取页数 |
| `chapterPrefetch` | 3 | 章末预取下一章前 3 页 |
| `queueSize` | 8 | 有界，挤掉最远页 |
| `wifiOnlyPrefetch` | false | 移动网络降为 1 页 |

- 触发：`snapshotFlow { listState.layoutInfo.visibleItemsInfo }` / `pagerState.currentPage` + `debounce(80)`。
- 动作：`imageLoader.enqueue(ImageRequest)` 预热，不渲染；命中后由 `AsyncImage` 直接拿缓存。
- 取消：窗口外的未完成请求 `Disposable` 取消；快速翻页时只保留落点 ±2。
- 惰性章节：预取同时触发 `lazyImage` 解析；解析结果进内存 `LruCache`，失败不阻塞。
- 预取预算：每次窗口变化最多入队 3 个，避免滚动时与当前页抢带宽。

## 9. 进度与状态恢复

```kotlin
LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }
        .debounce(1000)
        .distinctUntilChanged()
        .collect { page -> controller.onPageSettled(page) }
}
```

- 退出、切章、`ON_STOP` 时 `flushProgress()` 立即写库。
- 恢复：优先 `comic.last_chapter_path` 定位章节，找不到用 `last_chapter_index`，页码 clamp。
- 进程死亡：`SavedStateHandle` 存 `chapterPath + page + mode`；重启重新加载页源（不恢复 Bitmap）。
- 已读：翻到末页停留 > 1s 或显式标记。

## 10. 双页布局

```
单页模式：[    page n    ]
双页模式：[ page n ][ page n+1 ]   （竖图两两成对）
          [    wide page    ]      （横图独占）
```

- 判定：`width >= height * 1.05` 为横图。
- 模式：`AUTO`（宽 ≥ 720dp 且横屏）/ `FORCE` / `OFF`。
- 跨章不配对（v1），章末单页居中。
- 双页在同一 `Row` 内渲染，缩放/平移作用于整组，避免缝隙。

## 11. 异常与边界

| 情况 | 处理 |
| --- | --- |
| 图片 403（防盗链） | 用 `ImageRef.headers` 重试一次；仍失败提示「需要更新规则」 |
| 图片返回 HTML | 解码失败 → 错误占位 + `IMAGE_DECODE` |
| 章节只有 1 页 | 双页退化为单页 |
| 尺寸怪异（1×20000） | 切片 + 按宽度适配 |
| 快速甩动 50 页 | 只加载落点 ±2，中间页占位 |
| 旋转/折叠屏 | 保存状态重建，不重取已缓存图 |
| 低内存 | 清非当前章缓存，降 RGB_565 |
| 章节被删 | 提示并跳下一章；无则退出详情 |
| 网络切换 | 预取降级；失败页手动重试 |
| 菜单打开时滑动 | 菜单层消费触摸，不翻页 |

## 12. 性能预算与验证

| 指标 | 预算 | 方法 |
| --- | --- | --- |
| 翻页首帧（缓存命中） | ≤ 100ms | Macrobenchmark + FrameTiming |
| 条漫滚动掉帧 | < 5% | `JankStats` / Macrobenchmark |
| 内存峰值（条漫 200 页） | < 300MB | Profiler 30 分钟浸泡 |
| 预取命中率 | ≥ 80% | debug 本地统计 |
| 退出阅读到书架 | ≤ 300ms | 手工 + 基准 |

- `:app:benchmark` 模块：冷启动、条漫滚动、翻页、章节切换。
- Compose 专项：开启 strong skipping、`@Immutable` 状态、稳定的 `key`、避免在 item 内 `remember` 大对象。
- `ReaderMetrics`（debug）：页加载耗时、命中率、失败率；release 只写本地环形日志。

### 12.1 M2 实现状态与决策门

**M2 已实现**：Compose Pager/LazyColumn 双模式、双页、自定义 `zoomable` 手势仲裁、预取、Coil Header 注入、沉浸模式。
**顺延**：真机 Macrobenchmark 与 SavedState 恢复在 M7；届时若「翻页首帧 >150ms」或「条漫掉帧 >10%」，执行下方回退方案。

若连续两轮基准测试中「翻页首帧 > 150ms」或「条漫掉帧 > 10%」，执行回退方案：

- 保留 `ReaderRoute`/`ReaderViewModel`/`ReaderController` 契约；
- 将 `PagedReader` 替换为 `AndroidView(PagedReaderView)`（自定义 View，复用同一 `PageSource` 与 `ZoomState` 接口）；
- 回退范围可控（1 个 Composable + 1 个 View），不推翻架构。

## 13. 测试策略

| 层级 | 内容 |
| --- | --- |
| 单元 | `ReaderController`：页切换、边界、模式切换、进度 flush（`runTest` + Turbine + 假 `PageSource`） |
| Compose UI | 点击区、双击缩放、菜单显隐、错误占位（`createComposeRule` + 语义节点） |
| 截图 | 关键状态（加载/正常/错误/双页）用 Roborazzi 做基准图 |
| 性能 | Macrobenchmark 三个场景，CI 只跑 debug 版本 |
| 手工 | 弱网（限速 200KB/s）、飞行模式、旋转、分屏、折叠屏 |
