# 03 技术架构

> 基线：**Kotlin + Jetpack Compose 单语言栈**（2026 年主流方案）。
> 原则：UI 声明式、状态单向流动、协程贯穿异步、模块化边界清晰、源能力数据化。

## 1. 架构总览

```
┌───────────────────────────────────────────────────────────────────────┐
│ :app  （Kotlin + Compose 单 Activity）                                │
│  Navigation-Compose（类型安全路由）· ViewModel · StateFlow · Hilt      │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐  │
│  │ shelf    │ search   │ detail   │ reader   │ offline  │ settings │  │
│  └──────────┴──────────┴──────────┴──────────┴──────────┴──────────┘  │
└──────────────┬───────────────────────────────┬────────────────────────┘
               │ suspend / Flow                │ Compose UI
┌──────────────▼──────────────┐  ┌─────────────▼──────────────────────┐
│ :core:source                │  │ :core:reader（Compose + Coil）      │
│  RuleEngine · Selector DSL  │  │  PagedReader · WebtoonReader       │
│  Rhino Sandbox · RateLimit  │  │  ReaderController · Prefetcher     │
│  OkHttp · Cookie · Index    │  │  ImagePipeline · GestureArbiter    │
└──────────────┬──────────────┘  └─────────────┬──────────────────────┘
               │                               │
┌──────────────▼──────────────┐  ┌─────────────▼──────────────────────┐
│ :core:data                  │  │ :core:storage                      │
│  Room(KSP) · DAO · Repo     │  │  WorkManager(CoroutineWorker)      │
│  DataStore · BackupCodec    │  │  SAF · LocalScanner · WebDAV · ZIP │
└──────────────┬──────────────┘  └─────────────┬──────────────────────┘
               └───────────────┬───────────────┘
                     ┌─────────▼──────────┐   ┌────────────────────────┐
                     │ :core:model        │   │ :core:designsystem     │
                     │ 数据模型 / 规则模型 │   │ Theme · Tokens · 组件  │
                     │ 错误模型 / 枚举     │   │ （仅 Compose 依赖）    │
                     └────────────────────┘   └────────────────────────┘
```

依赖方向：`app → (source, reader, data, storage, designsystem)`，`source/data/storage → model`，`designsystem` 无业务依赖。**禁止** `data → source`、`reader → data`（阅读器只通过回调/`Flow` 与外部交互）。

## 2. 模块职责

| 模块 | 职责 | 明确不做 |
| --- | --- | --- |
| `:app` | 单 Activity、导航、各 feature 的 Screen/ViewModel、Hilt 装配、权限与通知入口 | 解析、DB、文件细节 |
| `:core:designsystem` | Material3 主题、颜色/字体/间距 Token、通用组件（封面卡、空状态、错误页） | 业务逻辑 |
| `:core:model` | `Comic`/`Chapter`/`ImageRef`/`SourceRule`/`RulePack`/`AppError`/枚举，全部 `@Serializable` 或纯数据 | Android 依赖（保持纯 Kotlin/JVM） |
| `:core:data` | Room 实体/DAO/迁移、Repository、DataStore 设置、备份编解码 | 网络、UI |
| `:core:source` | 规则解析/校验、选择器 DSL、HTTP、限流、Cookie、Rhino 沙箱、索引签名、健康度 | 持久化（由 data 注入接口） |
| `:core:reader` | Compose 阅读器（翻页/条漫）、`ReaderController`、图片流水线、预取、手势仲裁 | DB、网络编排 |
| `:core:storage` | 下载队列、文件命名、SAF、本地扫描、WebDAV、CBZ/ZIP、备份落盘 | 阅读渲染 |

模块化取舍：`model` 保持纯 JVM 以便快速单测；`designsystem` 独立便于后续多端（平板/折叠屏）复用；feature 暂不拆模块（单人维护，拆模块收益低于成本），达到 6 个 feature 时再拆（ADR-015）。

## 3. 关键接口（Kotlin）

### 3.1 源引擎

```kotlin
package io.github.saonte.mangareader.source

interface SourceEngine {
    suspend fun health(ref: SourceRef): SourceHealth
    suspend fun search(ref: SourceRef, keyword: String, page: Int): SearchPage
    suspend fun detail(ref: SourceRef, cid: String): ComicInfo
    suspend fun chapters(ref: SourceRef, cid: String): List<ChapterInfo>
    suspend fun images(ref: SourceRef, cid: String, path: String): ImagePage
    suspend fun lazyImage(ref: SourceRef, cid: String, path: String, index: Int): ImageRef
    suspend fun checkUpdate(ref: SourceRef, cid: String): String?
    suspend fun category(ref: SourceRef, categoryId: String, page: Int): SearchPage
}

@Immutable
data class SourceRef(
    val type: Long,          // source.type
    val ruleId: String,
    val rule: SourceRule,    // 已解析的强类型规则
    val baseUrl: String,
    val http: HttpOptions,   // UA/timeout/限流/allowCleartext
    val cookieStore: CookieStore,
)
```

聚合搜索用协程流：

```kotlin
class SearchCoordinator(
    private val engines: Map<Long, SourceEngine>,
    private val sources: SourceRepository,
) {
    fun search(keyword: String): Flow<SearchEvent> = channelFlow {
        sources.enabled().forEach { source ->
            launch(Dispatchers.IO) {
                runCatching { engines.getValue(source.type).search(source.ref(), keyword, 1) }
                    .onSuccess { send(SearchEvent.Success(source, it)) }
                    .onFailure { send(SearchEvent.Failure(source, it.toAppError())) }
            }
        }
    }.flowOn(Dispatchers.Default)
}
```

要点：`channelFlow` 让各源**先到先发**；每个 `launch` 独立超时与取消；整体在 `viewModelScope` 内，用户改词时取消上游。

### 3.2 Repository（`:core:data`）

```kotlin
interface ComicRepository {
    fun shelf(query: ShelfQuery): Flow<List<ShelfItem>>
    fun pagingShelf(query: ShelfQuery): PagingSource<Int, ShelfItem>
    suspend fun upsert(comic: Comic): Long
    suspend fun findBySource(sourceType: Long, cid: String): Comic?
    suspend fun setFavorite(comicId: Long, favorite: Boolean)
    suspend fun saveProgress(comicId: Long, chapterPath: String, chapterIndex: Int, page: Int)
    suspend fun touchHistory(comicId: Long)
}
```

- 查询返回 `Flow`（Room 可观察），分页用 Paging 3。
- 写操作 `suspend`，内部 `withContext(ioDispatcher)`，Room 事务用 `@Transaction`。
- 所有实体/DAO 与业务模型分离：`ComicEntity` ↔ `Comic`（`EntityMapper`），避免 UI 被 DB 泄漏。

### 3.3 阅读器契约（`:core:reader`）

```kotlin
class ReaderController(
    private val pageSource: PageSource,
    private val repository: ProgressSink,
    private val scope: CoroutineScope,
) {
    val state: StateFlow<ReaderUiState>
    fun onPageSettled(chapterIndex: Int, pageIndex: Int)
    fun retryPage(pageIndex: Int)
    fun switchMode(mode: ReadMode)
    fun flushProgress()
}

interface PageSource {
    val pageCount: StateFlow<Int>      // 0 = 未知（惰性）
    suspend fun page(index: Int): ImageRef
}
```

`ReaderUiState` 是**单一不可变快照**：`chapterIndex/pageIndex/pageCount/mode/scale/isMenuVisible/error`。Compose 只做 `state.collectAsStateWithLifecycle()` 渲染，不持有业务状态。

## 4. 数据流

### 4.1 聚合搜索

```
SearchScreen ──(query)──▶ SearchViewModel
                            │ sources.enabled()（health != DISABLED）
                            ▼
                     SearchCoordinator.search(query): Flow<SearchEvent>
                            │ 每源 launch + withTimeout(15s) + 源级 Mutex
                            ▼
              SearchEvent.Success / Failure（交错到达）
                            │
                            ▼
        SearchUiState.sources: SnapshotStateList<SourceResult>
```

- 去重：`(sourceType, cid)`；UI 按源分组、按到达顺序追加。
- 失败：不弹全局错误，折叠为该源的一行状态，点开显示 `AppError.message/detail/pointer/retry`。
- 取消：关键词变化时 `viewModelScope.launch` 的 Job 被 `cancel()`，OkHttp `Call.cancel()` 通过 `suspendCancellableCoroutine` 的 `invokeOnCancellation` 传导。

### 4.2 阅读

```
DetailScreen 点击章节 ──▶ ReaderViewModel.open(chapterPath)
        │
        ├── 已下载？ → FilePageSource（本地文件）
        ├── 本地漫画？ → LocalPageSource（SAF）
        └── 在线 → RemotePageSource（懒解析 + 缓存）
                          │
                          ▼
                  ReaderController（纯 Kotlin，可单测）
                          │ StateFlow<ReaderUiState>
                          ▼
        ReaderScreen：HorizontalPager / LazyColumn + AsyncImage
                          │ onPageSettled（1s 防抖）
                          ▼
                 ComicRepository.saveProgress()
```

### 4.3 下载

```
DetailScreen 勾选 ──▶ DownloadScheduler.enqueue(chapterIds)
        │
        ├── 写 download_task（QUEUED）
        ├── WorkManager.enqueueUniqueWork("dl:$chapterId", KEEP, request)
        ▼
DownloadWorker : CoroutineWorker
        │ sourceEngine.images() → 逐页下载（并发 2）
        │ 写 .part → rename；Range 续传；指数退避重试
        ▼
ChapterDao.markDownloaded() + 通知进度
```

## 5. 协程与线程模型

| 场景 | Dispatcher | 约束 |
| --- | --- | --- |
| UI / ViewModel | `Dispatchers.Main.immediate` | 不执行 IO/解析 |
| 数据库 | `Dispatchers.IO`（Room 自带） | 事务短小；批量用 `@Transaction` |
| 网络（页面） | `Dispatchers.IO` + OkHttp `await` | 每源并发 ≤ 2，令牌桶 2 rps |
| 解析（jsoup/JSON） | `Dispatchers.Default.limitedParallelism(4)` | 大 HTML 先截断 |
| 脚本（Rhino） | 每源专用单线程 `limitedParallelism(1)` | 同 Context 不并发；3s 超时 |
| 图片解码 | Coil 内部 `Dispatchers.IO` | 目标尺寸约束 |
| 文件/SAF/ZIP | `Dispatchers.IO.limitedParallelism(4)` | 流式处理，避免整读 |

约定：
1. **没有全局可变状态**：依赖通过 Hilt 注入，协程作用域显式（`viewModelScope` / `ApplicationScope` / `Worker`）。
2. **取消即取消**：所有挂起点可取消；`suspendCancellableCoroutine` 必须处理 `invokeOnCancellation`。
3. **重试**：`retryWhen { _, attempt -> attempt < max && it.isRetryable }` + 指数退避 + 抖动。
4. **限流**：源级 `Semaphore(2)` + 令牌桶；下载任务独立限流，避免与阅读抢带宽。
5. **禁止 `GlobalScope`**、禁止在 `init` 里发请求、禁止 `runBlocking` 出现在主线程路径。

## 6. 错误模型

```kotlin
sealed interface AppError {
    val code: ErrorCode
    val retryable: Boolean
    val message: String          // 已本地化
    val detail: String?          // 原始信息，可复制上报
    val pointer: String?         // 规则错误位置，如 /search/fields/cover

    data class Network(override val code: ErrorCode, ...) : AppError
    data class Rule(override val pointer: String?, ...) : AppError
    data class Script(val line: Int?, ...) : AppError
    data class Image(val urlHost: String, ...) : AppError
    data class Storage(...) : AppError
}
```

| 错误类别 | 示例 code | UI 文案 | 建议动作 |
| --- | --- | --- | --- |
| 网络 | `NET_TIMEOUT` `NET_DNS` | 网络超时 / 无法连接 | 重试 |
| HTTP | `HTTP_403` `HTTP_429` `HTTP_5XX` | 源拒绝访问 / 请求过频 / 服务器错误 | 更新规则 / 稍后重试 |
| 解析 | `SELECTOR_EMPTY` `JSON_PATH_MISSING` | 页面结构可能已变化 | 报告规则问题（自动带 pointer） |
| 规则 | `RULE_INVALID` `RULE_UNSUPPORTED` | 规则格式错误 / 需要新版本 | 重新导入 / 升级 |
| 脚本 | `SCRIPT_TIMEOUT` `SCRIPT_ERROR` | 规则脚本执行失败 | 报告规则问题 |
| 图片 | `IMAGE_FETCH_FAILED` `IMAGE_DECODE` | 图片加载失败 | 单页重试 / 换源 |
| 存储 | `STORAGE_FULL` `PERMISSION_DENIED` | 空间不足 / 权限丢失 | 清理 / 重新授权 |
| 其他 | `CANCELLED` `UNKNOWN` | — | — |

日志脱敏：不记录 URL query（可能含 token）、Cookie、Header 值，只保留 host、路径模板、状态码、pointer。

## 7. 缓存与一致性

| 层 | 内容 | 大小/策略 | 失效 |
| --- | --- | --- | --- |
| Compose 状态 | 当前页/列表 | 内存，随组合销毁 | 离开页面 |
| L1 内存 | 解码后 Bitmap | Coil `MemoryCache`，25% 可用内存 | LRU |
| L2 磁盘 | 图片 | 512MB 默认（128–2048 可配） | LRU / 手动清 |
| HTTP | HTML/JSON | OkHttp `Cache` 50MB，尊重 `Cache-Control` | 源声明 `noCache` |
| 会话缓存 | 章节/图片列表 | `LruCache` 20 项 | 会话结束 |
| 持久化 | 收藏/进度/章节/下载/规则 | Room | 用户操作 |

一致性：
- 进度以 **`chapter.path` + page** 为准，章节刷新后按 path 重定位，找不到回退到同序号。
- 收藏 upsert 以 `(source, cid)` 唯一；冲突时更新元数据，保留 `favoriteAt/historyAt`。
- 更新检查只针对 `favoriteAt != null` 的漫画；失败不清空旧值。

## 8. 依赖基线

> M0 用 Version Catalog 锁定并写回本表；以下为**计划基线**，实施时替换为当时稳定版。

| 依赖 | 基线 | 用途 | 许可证 |
| --- | --- | --- | --- |
| AGP / Gradle | 8.7+ / 8.9+ | 构建 | Apache-2.0 |
| Kotlin | 2.1.x | 全栈语言 | Apache-2.0 |
| Compose BOM | 2025.01+ | UI | Apache-2.0 |
| Compose Compiler | 随 Kotlin（`compose` 插件） | 编译 | Apache-2.0 |
| Hilt | 2.52+（KSP） | DI | Apache-2.0 |
| Room | 2.6.1+（KSP） | DB | Apache-2.0 |
| Paging | 3.3+ | 列表分页 | Apache-2.0 |
| Navigation-Compose | 2.8+（类型安全路由） | 导航 | Apache-2.0 |
| OkHttp | 4.12+ | HTTP | Apache-2.0 |
| jsoup | 1.18+ | HTML | MIT |
| Rhino | 1.7.15+ | JS 沙箱 | MPL-2.0 |
| kotlinx-serialization | 1.7+ | JSON/规则/备份 | Apache-2.0 |
| kotlinx-coroutines | 1.9+ | 协程 | Apache-2.0 |
| Coil | 3.x | 图片 | Apache-2.0 |
| DataStore | 1.1+ | 设置 | Apache-2.0 |
| WorkManager | 2.9+ | 后台任务 | Apache-2.0 |
| Baseline Profile | AGP 内置 | 性能 | Apache-2.0 |
| JUnit5 + MockK + Turbine + MockWebServer | 最新 | 测试 | EPL-2.0 / Apache-2.0 |
| Robolectric / Compose UI Test | 最新 | Android 测试 | MIT / Apache-2.0 |
| Detekt / ktlint | 最新 | 静态检查 | Apache-2.0 |

**不引入**：Firebase 全家桶、广告 SDK、统计 SDK、闭源加固（ADR-008）。

## 9. 构建与变体

| 变体 | 用途 | 差异 |
| --- | --- | --- |
| `debug` | 开发 | 规则热加载（`filesDir/rules-dev/`）、StrictMode、LeakCanary、Compose 工具 |
| `release` | 分发 | R8 + resource shrink、签名、Baseline Profile |
| `benchmark` | 性能 | `profileable`、Macrobenchmark |

- `applicationId = io.github.saonte.mangareader`，`minSdk 26`、`targetSdk 35`、`compileSdk 35`，JDK 17 toolchain。
- Compose 开启 `buildFeatures.compose = true` + Kotlin 2.x `compose` 编译插件；强跳过模式（strong skipping）默认开启。
- 版本号：tag 驱动 `versionName`，`versionCode` 由 CI 依据提交数生成。
- 签名配置见 09；密钥不入库。

## 10. 安全设计

1. **脚本沙箱**：Rhino `ClassShutter` 拒绝所有 Java 类；仅注入 `http/text/json/crypto/cache/log` 白名单对象；禁用 `Packages/JavaAdapter/反射/Thread/File/Socket`。
2. **网络边界**：规则只能访问 `hosts`（含 `*.` 通配）+ `imageHosts`；每次重定向重新校验。
3. **明文 HTTP**：`usesCleartextTraffic=true` 以兼容老站，但引擎二次闸门——仅规则声明 `allowCleartext=true` 且用户导入时确认才放行（ADR-009）。
4. **响应限制**：单响应 ≤ 8MB，解压后 ≤ 32MB；HTML 解析前 4MB 截断；zip bomb 防护（解压比阈值）。
5. **完整性**：规则 SHA-256 + 索引 Ed25519 签名；信任等级 `OFFICIAL < COMMUNITY < LOCAL`；LOCAL 危险能力二次确认。
6. **凭据**：WebDAV 密码用 Android Keystore（AES-GCM）保存；Cookie 存私有目录；备份默认排除凭据。
7. **撤销**：`revoked.json` 随 App 资产与索引双通道更新，命中即禁用。

## 11. 关键决策记录（ADR）

### ADR-001 全栈 Kotlin + Compose（取代「Java 核心 + Compose UI」）

- **背景**：初版方案要求 Java 核心；实际评估后认为双语言带来的桥接成本（`CompletableFuture`/`suspend`、KSP/kapt 混用、两套格式化与 lint）高于收益。
- **决策**：**全 Kotlin**，UI 用 Compose；不再保留 Java 源集。
- **后果**：单语言、单构建链、协程贯穿；放弃「Java 优先」的原始约束。若未来需要 JVM 复用，`core:model`/`core:source` 仍保持低 Android 依赖。

### ADR-002 源插件采用「JSON 规则 + 受限 JS」

- **决策**：规则是数据文件；声明式选择器优先，复杂站点用 Rhino 钩子；不加载外部 DEX/APK。
- **后果**：新增源无需发版；需自建 DSL、校验器与沙箱；放弃任意代码的灵活性。

### ADR-003 许可证 GPL-3.0

- **后果**：衍生作品必须开源；与 Apache-2.0/MIT/BSD/MPL-2.0 兼容；不得链接闭源 SDK。

### ADR-004 minSdk 26

- **理由**：Compose 与通知渠道/SAF 稳定、协程无兼容负担、目标设备覆盖足够。
- **后果**：放弃 Android 5–7；如需下调到 24，需验证 Compose 与后台限制。

### ADR-005 KSP 取代 kapt

- **决策**：Room、Hilt、kotlinx-serialization 全部走 KSP。
- **后果**：构建更快；注解处理器兼容性需随 Kotlin 升级同步；禁止再引入 kapt 插件。

### ADR-006 阅读器用 Compose 实现（含降级预案）

- **背景**：分页 + 缩放是 Compose 的难点（手势冲突、过度绘制、大图内存）。
- **决策**：v1 用 `HorizontalPager` + 自定义 `zoomable` 修饰符 + `LazyColumn`（条漫）；`ReaderController` 纯 Kotlin，可脱离 UI 测试。M2 设置**决策门**：若首帧/掉帧/内存不达标，回退到「Compose 外壳 + 自定义 View 画布」的混合方案（不改变对外契约）。
- **后果**：开发效率高、状态管理简单；需重点处理 pager 与缩放的嵌套滚动、图片切片与预取。

### ADR-007 图片加载用 Coil 3

- **决策**：Coil 3 + 自定义 `Fetcher`/`Keyer` 注入源 Header；`AsyncImage` 复用 Compose 状态。
- **后果**：长图切片与超大图需自实现 Decoder 逻辑；Coil 3 的多平台 API 需锁定版本。

### ADR-008 不集成 Firebase/统计 SDK

- **理由**：隐私、F-Droid 友好、GPL 合规简单、包体小。
- **后果**：崩溃分析靠本地日志 + 用户主动导出诊断包。

### ADR-009 明文 HTTP 的受限支持

- **背景**：`network_security_config` 静态，无法按规则动态放行域名。
- **决策**：全局允许明文，引擎层二次闸门 + 导入确认 + 审计日志；后续可改为用户显式域名白名单。
- **后果**：理论滥用风险，用白名单与信任等级缓解。

### ADR-010 单一 OkHttpClient + 源级隔离

- **决策**：共享连接池/Dispatcher；按源注入 Interceptor、CookieJar、限流器；请求打 `sourceTag`。
- **后果**：连接复用好；需保证拦截器不串源（单测覆盖）。

### ADR-011 规则签名与信任等级

- **决策**：索引 Ed25519 签名，公钥内置；本地导入标记 LOCAL 并提示危险能力；撤销列表双通道。
- **后果**：防篡改与投毒，但无法约束签名者本身作恶。

### ADR-012 进度存储位置

- **决策**：`comic.last_*` 快路径 + `history_at` 时间线；章节内页码以 `chapter.path` 定位。
- **后果**：书架查询单表完成；章节重命名依赖 path 回退策略。

### ADR-013 v1 脚本运行时选 Rhino

- **背景**：可选 Rhino（纯 JVM、ClassShutter 可沙箱、ES5.1+ 部分 ES6）、QuickJS（JNI、ES2020、隔离更强、需 ABI 与 NDK 维护）、WebView（重、冷启动慢）。
- **决策**：v1 用 Rhino（无 NDK、构建简单、规则脚本以正则/JSON 为主，性能非瓶颈）；抽象 `ScriptRuntime` 接口，P1 评估 QuickJS 替换。
- **后果**：ES6 语法支持有限（模板字符串/let/const 可用，模块化不可用）；需在文档中明确「脚本按 ES5.1 编写」。

### ADR-014 采用 Version Catalog + Convention Plugins

- **决策**：`gradle/libs.versions.toml` 统一版本；`build-logic` 约定插件收敛 Android/Compose 配置。
- **后果**：新增模块成本低；升级版本单点修改。

### ADR-015 暂不拆 feature 模块

- **决策**：v1 的 feature 以 `:app` 内包结构组织（`feature/shelf`、`feature/reader`…），达到 6 个 feature 或编译时间 > 3min 时再拆。
- **后果**：构建简单；模块边界靠包可见性与 code review 维持。
