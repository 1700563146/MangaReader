# 05 源规则规范 v1

> 规则包是 MangaReader 唯一的「插件」形态：**一份 JSON 描述某个站点怎么搜、怎么解析、怎么取图**。
> 引擎用 Kotlin 实现；复杂站点通过受限 Rhino 脚本钩子扩展（ES5.1 语法，见 §6.5）。
> 实现与本文冲突时以 [Schema](schema/source-rule.schema.json) + 本文语义为准，两者同 PR 修订。

## 1. 设计目标

| 目标 | 手段 |
| --- | --- |
| 新增源不需要发版 | 规则是数据文件，运行期加载 |
| 80% 站点零代码 | 声明式选择器 + 管道变换 |
| 剩下 20% 有出口 | 受限 Rhino 钩子（超时、白名单、无设备能力） |
| 出错能定位 | 每个字段可报 `JSON Pointer`；三层校验 |
| 生态可治理 | 版本、哈希、签名、撤销列表、健康度 |
| 安全可控 | 域名白名单、响应体上限、禁止任意网络/文件/反射 |

## 2. 文件与顶层字段

单个 UTF-8 JSON（`.json`，可 gzip 为 `.json.gz`，≤ 256KB）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | int | ✓ | 规范版本，当前 `1` |
| `id` | string | ✓ | 全局唯一，建议反向域名：`community.example` |
| `name` | string | ✓ | 展示名（≤ 32 字） |
| `version` | string | ✓ | 语义化版本，同 id 高版本视为升级 |
| `minEngine` | int | ✓ | 最低引擎版本；当前引擎 `engine = 1` |
| `baseUrl` | string | ✓ | 绝对 URL，无尾斜杠 |
| `hosts` | string[] | ✓ | 允许请求的域名，`*.a.com` 表示子域通配 |
| `imageHosts` | string[] |  | 允许加载图片的域名（除 `hosts` 外） |
| `allowCleartext` | bool |  | 允许 `http://`（导入强提示，默认 false） |
| `adult` | bool |  | 成人标记，默认在列表隐藏，需设置开启 |
| `lang` | string |  | 规则界面语言，默认 `zh` |
| `author` / `description` / `homepage` | string |  | 元信息 |
| `headers` | object |  | 默认请求头（UA、Referer 等） |
| `http` | object |  | `timeoutMs`、`retry`、`rateLimit{rps,burst}`、`followRedirects` |
| `capabilities` | string[] |  | 实际实现的能力，见 §5；缺失则 UI 隐藏入口 |
| `search` / `detail` / `chapters` / `images` / `check` / `category` | object |  | 能力块 |
| `scripts` | object |  | 脚本钩子，见 §6 |
| `config` | object |  | 用户可配置项（v1 仅 `string` 提示） |

### 2.1 强类型模型（`:core:source`）

```kotlin
@Serializable
data class SourceRule(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val minEngine: Int,
    val baseUrl: String,
    val hosts: List<String>,
    val imageHosts: List<String> = emptyList(),
    val allowCleartext: Boolean = false,
    val adult: Boolean = false,
    val lang: String = "zh",
    val author: String? = null,
    val description: String? = null,
    val homepage: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val http: HttpSpec = HttpSpec(),
    val capabilities: Set<Capability>,
    val search: SearchSpec? = null,
    val detail: DetailSpec? = null,
    val chapters: ChaptersSpec? = null,
    val images: ImagesSpec? = null,
    val check: CheckSpec? = null,
    val category: CategorySpec? = null,
    @SerialName("scripts") val scripts: Map<String, String> = emptyMap(),
)
```

解析用 `Json { isLenient = false; ignoreUnknownKeys = false }`：**未知字段即报错**，防止拼写错误静默失效。错误信息用 `JsonDecodingException` 的 offset → 转 `JSON Pointer` 展示。

### 2.2 变量

模板字符串（`url`、`headers` 值、`body`）支持 `${expr}`：

| 变量 | 作用域 | 含义 |
| --- | --- | --- |
| `base` | 全局 | `baseUrl` |
| `keyword` | search | 搜索词 |
| `page` | search/category | 页码，从 1 开始 |
| `cid` | detail/chapters/images | 漫画 id |
| `chapterPath` | images | 章节 path |
| `index` | lazy | 页索引，0-based |
| `url` | 所有 | 当前页面/请求 URL |
| `category` | category | 分类 id |

过滤器用 `|` 串联，参数用 `:`：`${keyword|urlencode}`、`${page|default:1}`、`${url|urlencode:utf-8}`。

支持：`urlencode[:charset]`、`urldecode`、`base64`、`base64url`、`lower`、`upper`、`trim`、`replace:from:to`、`regex:pattern:group`、`default:value`、`htmlencode`、`md5`。

> 变量模板与选择器管道共用 `Pipe` 实现，避免两套语义漂移。

## 3. 请求定义

```json
"request": {
  "url": "${base}/search?q=${keyword|urlencode}&page=${page}",
  "method": "GET",
  "headers": { "Referer": "${base}/", "X-Requested-With": "XMLHttpRequest" },
  "body": null,
  "contentType": "application/x-www-form-urlencoded",
  "charset": "utf-8",
  "expect": "html",
  "redirects": 5,
  "cache": "default"
}
```

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `method` | GET | 仅 `GET`/`POST` |
| `body` | null | POST 体；模板化 |
| `charset` | `utf-8` | 缺失时按 `Content-Type`，再回退 GBK 嗅探 |
| `expect` | `html` | `html` / `json` / `text` / `bytes` |
| `cache` | `default` | `default` / `no-store` / `only-if-cached` |
| `redirects` | 5 | 0–5 |

限制：URL 必须绝对且 host 命中 `hosts`；重定向后再校验；响应体 > 8MB 失败（`RESPONSE_TOO_LARGE`）。

## 4. 取值 DSL

### 4.1 取值对象

```jsonc
// ① 简写：CSS 选择器（可带 @attr，可内联管道）
"title": "h1.title"
"cover": "img.cover@data-src | absUrl"

// ② 对象：显式来源 + 管道
"cover": { "select": "img.cover", "attr": "data-src", "index": 0, "pipeline": ["absUrl", "trim"] }

// ③ 常量
"finish": { "const": false }
```

| 字段 | 适用 | 说明 |
| --- | --- | --- |
| `select` | html | CSS 选择器（jsoup 语法） |
| `attr` | html | 取属性；省略取 `text`；`html` 取内 HTML |
| `index` | 两者 | 多节点取值下标，默认 0；负数从尾部 |
| `json` | json | JSONPath 子集：`$.a.b[0]`、`$.list[*]`、`$..name` |
| `const` | 两者 | 常量 |
| `pipeline` | 两者 | 变换数组，见 4.3 |
| `fallback` | 两者 | 取值对象数组，依次尝试 |
| `required` | 两者 | 默认 true；false 时空值不报错 |

数组简写即 `fallback`：

```json
"cover": ["img.cover@data-src", "img.cover@src", "meta[property='og:image']@content"]
```

### 4.2 列表与作用域

```json
"list": { "select": "ul.list > li", "limit": 40, "skip": 0 }
```

- `list` 选中节点逐条进入 `fields`，字段选择器**相对节点**。
- `limit` 默认 40、上限 500；超出丢弃并警告。
- `detail`/`images` 可用 `root` 限定字段作用域。
- 空列表报 `SELECTOR_EMPTY`（`allowEmpty: true` 可关闭）。

### 4.3 管道变换

按顺序执行，任一步返回 `null` 则整体 `null`。

| op | 参数 | 说明 |
| --- | --- | --- |
| `trim` | — | 去首尾空白（含 `\u00a0`、全角空格） |
| `lower` / `upper` | — | 大小写 |
| `absUrl` | — | 相对 → 绝对（基于当前页面 URL） |
| `prefix` / `suffix` | string | 拼接 |
| `replace` | pattern, replacement | 正则全量替换，支持 `$1` |
| `regex` | pattern, group? | 提取，无匹配 → null |
| `split` | sep, index | 分割取值 |
| `number` | — | 提取首个数字 |
| `toInt` / `toFloat` / `toBool` | — | 类型转换；`toBool` 识别 `true/1/是/完结` |
| `map` | object | 键值映射，未命中 → null |
| `decodeHtml` | — | HTML 实体反转义 |
| `decodeJs` | — | `\uXXXX`、`\xNN`、`\/` 反转义 |
| `decodeUri` / `urlencode` | — | 百分号编解码 |
| `base64` | — | Base64 解码为文本（失败保留原值） |
| `stripTags` | — | 去 HTML 标签 |
| `join` | sep | 多节点合并 |
| `default` | value | 空/null 时替换 |
| `trimSuffix` | string | 去固定后缀 |
| `slice` | start, end? | 字符串截取 |
| `date` | pattern?, zone? | 解析时间 → epoch ms（支持 `3天前`） |

对象形式带命名参数：

```json
"pipeline": [
  { "op": "regex", "pattern": "/comic/(\\d+)/", "group": 1 },
  { "op": "default", "value": "" }
]
```

### 4.4 常用模式

```jsonc
// 状态文本 → bool
"finish": { "select": "span.status", "pipeline": [["map", { "连载": false, "完结": true }]] }

// 懒加载封面 + 协议相对地址
"cover": { "select": "img.cover", "attr": "data-src", "pipeline": ["trim", "absUrl"] }

// 章节 path：去域名保留路径
"path": { "select": "a", "attr": "href", "pipeline": [["replace", "^https?://[^/]+", ""]] }

// 页码：从 URL 提取
"chapterPath": { "select": "link[rel=next]", "attr": "href", "pipeline": [["regex", "page=(\\d+)", 1]] }
```

## 5. 能力块

### 5.1 `search`

```json
"search": {
  "request": { "url": "${base}/search?q=${keyword|urlencode}&page=${page}" },
  "list": { "select": "ul.result li", "limit": 40 },
  "fields": {
    "cid":   { "select": "a", "attr": "href", "pipeline": [["regex", "/comic/(\\d+)", 1]] },
    "title": { "select": "a", "attr": "title", "fallback": ["a"] },
    "cover": { "select": "img", "attr": "data-src", "pipeline": ["absUrl"] },
    "author": "span.author",
    "update": "span.update",
    "finish": { "select": "span.status", "pipeline": [["map", { "完结": true, "连载": false }]] }
  },
  "paging": { "hasNext": { "select": "a.next", "attr": "href" }, "maxPage": 100 },
  "allowEmpty": false
}
```

- `cid` / `title` 必填；`cid` 重复保留第一条并告警。
- `paging.hasNext` 为真值或非空字符串即视为有下一页；`maxPage` 上限 500。

### 5.2 `detail`

```json
"detail": {
  "request": { "url": "${base}/comic/${cid}/" },
  "root": "div.comic-info",
  "fields": {
    "title": "h1",
    "cover": { "select": "img.cover", "attr": "src", "pipeline": ["absUrl"] },
    "author": "a.author",
    "intro": { "select": "div.intro", "attr": "html", "pipeline": ["stripTags", "trim"] },
    "finish": { "select": "span.status", "pipeline": [["map", { "完结": true }], "toBool"] },
    "update": "span.update",
    "url": { "const": "${base}/comic/${cid}/" }
  }
}
```

`title` 必填；缺失时用搜索结果标题兜底并记录 `SELECTOR_EMPTY`。

### 5.3 `chapters`

```json
"chapters": {
  "request": { "url": "${base}/comic/${cid}/" },
  "list": { "select": "ul.chapter-list li" },
  "fields": {
    "title": "a",
    "path": { "select": "a", "attr": "href", "pipeline": ["absUrl"] },
    "group": { "select": "span.volume", "pipeline": ["trim"] }
  },
  "reverse": true,
  "max": 5000,
  "dedupe": true
}
```

- `path` 必填且同源唯一。
- `group` 对应 `chapter.source_group`（分卷）。
- `reverse: true` 表示源为倒序，入库前反转。
- 超过 `max` 截断并提示。

### 5.4 `images`

```json
"images": {
  "request": { "url": "${chapterPath}", "headers": { "Referer": "${base}/" } },
  "expect": "html",
  "list": { "select": "div.reader img", "limit": 500 },
  "fields": {
    "url": { "select": "", "attr": "data-src", "fallback": ["img@src"] }
  },
  "multi": true,
  "order": "asc",
  "headers": { "Referer": "${chapterPath}" }
}
```

- `select: ""` 表示作用于列表节点自身。
- `multi: true` 时字段可返回多个 URL（备用源），落为 `ImageRef.urls`。
- `order`: `asc`（默认）/ `desc`。
- 图片 host 必须命中 `hosts ∪ imageHosts`，否则该页失败并记 `IMAGE_FETCH_FAILED`（不整体失败）。

### 5.5 `images.lazy`

```json
"images": {
  "request": { "url": "${chapterPath}" },
  "lazy": {
    "enabled": true,
    "total": { "select": "span.total-page", "pipeline": ["number"] },
    "request": {
      "url": "${base}/chapter/${cid}/${index}.json",
      "expect": "json",
      "headers": { "Referer": "${chapterPath}" }
    },
    "value": { "json": "$.data.url", "pipeline": ["absUrl"] },
    "batch": 1
  }
}
```

- `total` 可选；未知时 `pageCount = 0`，阅读器按「下一页可解析」推进。
- `batch > 1` 表示一次请求拿多页，`value` 返回数组。
- 惰性解析结果**只存内存**（token 会过期）。
- 单页失败重试 2 次，仍失败显示重试按钮，不影响其他页。

### 5.6 `check`

```json
"check": {
  "request": { "url": "${base}/comic/${cid}/" },
  "value": { "select": "span.update", "pipeline": ["trim"] }
}
```

返回值写入 `comic.update_text`，与旧值不同即 `highlight = 1`。失败**不清空**旧值。

### 5.7 `category`（可选）

```json
"category": {
  "items": [
    { "id": "hot", "title": "热门", "path": "/list/hot/${page}" },
    { "id": "new", "title": "最新", "path": "/list/new/${page}" }
  ],
  "request": { "url": "${base}${path}" },
  "list": { "select": "ul.list li" },
  "fields": { "cid": "…", "title": "…", "cover": "…" },
  "paging": { "hasNext": { "select": "a.next", "attr": "href" } }
}
```

`items.path` 已完成 `${page}` 替换；也允许 `url` 直接给绝对地址（需命中 hosts）。

## 6. 脚本钩子

### 6.1 声明与执行模型

```json
"scripts": {
  "detail": "function(ctx) { return { title: ctx.doc('h1').text() }; }",
  "images": "function(ctx) { var m = ctx.html.match(/var urls = (\\[.*?\\]);/); return JSON.parse(m[1]).map(function(u){ return { url: u }; }); }",
  "lazyUrl": "function(ctx) { return ctx.txt.base64Decode(ctx.html); }"
}
```

| 钩子 | 入参 | 返回 | 替换对象 |
| --- | --- | --- | --- |
| `search` | `ctx{keyword,page,url,html,json,doc()}` | `{items:[], hasNext:bool}` | 整个 search 块 |
| `detail` | `ctx{cid,url,html,json,doc()}` | `object` | 覆盖 `fields` 同名结果 |
| `chapters` | 同上 | `[{title,path,group}]` | 整个 chapters 块 |
| `images` | `ctx{cid,chapterPath,url,html,json,doc()}` | `[{url,urls?}]` | 整个 images 块 |
| `lazyUrl` | `ctx{index,url,text,json,doc()}` | `string` 或 `[{url}]` | 单页解析结果 |

规则：**先声明式，后脚本**。能写选择器就不要写脚本（可维护性、性能、审核成本）。

### 6.2 `ctx` 对象

| 成员 | 说明 |
| --- | --- |
| `ctx.base/keyword/page/cid/chapterPath/index` | 与变量同名 |
| `ctx.url` | 当前请求绝对 URL |
| `ctx.html` / `ctx.text` / `ctx.json` | 响应原文 / 文本 / 已解析对象 |
| `ctx.doc(css)` | 轻量 DOM：`{text(), attr(n), html(), find(css), first()}` |
| `ctx.resolve(href)` | 相对 → 绝对 |
| `ctx.decode(s)` | HTML 实体 + `\uXXXX` 反转义 |
| `ctx.log(msg)` | 规则调试日志（release 只留最近 200 条） |

### 6.3 全局能力（白名单）

| 名称 | 签名 | 限制 |
| --- | --- | --- |
| `http.fetch(req)` | `{url, method, headers, body, expect}` → `{status, headers, text(), json()}` | 仅 GET/POST；host 白名单；单次 10s；每钩子 ≤ 10 次 |
| `util.base64Decode/Encode` | string ↔ string | 单串 ≤ 1MB |
| `util.md5/sha256` | string → hex | |
| `util.aesDecrypt` | `(data, key, iv, mode, padding)` | 仅 AES/CBC|ECB + NoPadding|PKCS5；**禁止 DRM** |
| `util.xor` | `(data, key)` | Latin-1 字节串 |
| `util.unicode` | `\uXXXX` 反转义 | |
| `util.time` | 当前毫秒 | |
| `cache.get/set` | string | 每规则 1MB LRU，重启清空 |
| `log.info/warn/error` | | 仅本地 |

**不可用**（`ClassShutter` 强制拒绝）：`java.*`、`Packages`、`JavaAdapter`、反射、`Thread`、`File`、`Socket`、`Runtime`、`System`、`load`、`quit`、`print`、`XMLHttpRequest`。

### 6.4 沙箱参数

| 限制 | 值 | 失败错误 |
| --- | --- | --- |
| 单钩子墙钟时间 | 3s | `SCRIPT_TIMEOUT` |
| 解释器栈深 | 1000 | `SCRIPT_ERROR` |
| 脚本源码大小 | ≤ 64KB | `RULE_INVALID` |
| 单次返回数组 | ≤ 1000 项 | `SCRIPT_ERROR` |
| 字符串长度 | ≤ 1MB | `SCRIPT_ERROR` |
| 优化级别 | `-1`（解释执行，保证可中断） | — |

实现：`Context.setOptimizationLevel(-1)` + `setInstructionObserverThreshold(10_000)` + `observeInstructionCount` 中按 `SystemClock.elapsedRealtime()` 抛出 `EvaluatorException`；编译失败禁用规则但保留 JSON，UI 显示行号列号。

### 6.5 语言约束（Rhino / ES5.1）

- 支持：`var`、函数声明、闭包、`Array.map/filter`、`JSON`、正则、`try/catch`。
- **不支持**：`let/const`、箭头函数、模板字符串、`Promise`、`async/await`、模块、解构、可选链。规则作者需按 ES5.1 编写。
- 需要异步语义时用同步 `http.fetch`（引擎内部阻塞该源专用线程，不会卡 UI）。
- 脚本不得假设执行顺序；同一钩子可能被重试。

### 6.6 `ScriptRuntime` 接口

```kotlin
interface ScriptRuntime {
    fun compile(ruleId: String, hooks: Map<String, String>): CompiledRuleScript
}

interface CompiledRuleScript {
    /** 返回 JSON 值；超时/异常抛 SourceFailure。 */
    fun invoke(hook: String, ctx: ScriptContext): JsonElement?
}
```

Rhino 实现放在 `core:source/internal/script`；`ScriptContext` 只暴露白名单对象，禁止 `Context.javaToJS` 传入任意对象。

## 7. 校验、签名与分发

### 7.1 三层校验

| 层 | 内容 | 时机 |
| --- | --- | --- |
| L1 结构 | Schema、必填、类型、枚举、长度、未知字段 | 导入时 / CI |
| L2 语义 | id 合法且唯一、`capabilities` 与块一致、`baseUrl` 命中 `hosts`、URL 变量合法、脚本可编译、选择器可解析 | 导入时 |
| L3 冒烟 | 真实网络请求（作者本地，可选） | 规则作者自测 |

L2 错误必须给 `JSON Pointer`：

```
/search/fields/cover/pipeline/0: 未知操作 "absURL"（是否想用 "absUrl"？）
```

校验结果结构化，便于 UI 与 CLI 复用：

```kotlin
data class RuleIssue(
    val severity: Severity,        // ERROR / WARNING
    val pointer: String,           // "/images/fields/url"
    val message: String,           // 已本地化
    val hint: String? = null,
)
data class RuleValidation(val ok: Boolean, val issues: List<RuleIssue>)
```

### 7.2 索引与签名

```json
{
  "formatVersion": 1,
  "generatedAt": 1767225600000,
  "name": "MangaReader Community Index",
  "rules": [
    {
      "id": "community.example",
      "name": "示例源",
      "version": "1.0.0",
      "url": "https://rules.example.org/community.example.json",
      "sha256": "9f2c…",
      "minEngine": 1,
      "adult": false,
      "revoked": false
    }
  ],
  "signature": {
    "algorithm": "ed25519",
    "keyId": "mangareader-index-2026",
    "value": "base64…"
  }
}
```

- 待签名载荷：除 `signature` 外整棵树，按 **RFC 8785 (JCS)** 规范化后 UTF-8 字节。
- 公钥硬编码（`core:source/src/main/assets/index_pubkey.pem`），支持多 `keyId` 轮换。
- 规则文件按 `sha256` 校验；与索引不符 → 拒绝安装并提示可能被篡改。
- 本地导入跳过签名，标记 `trust = LOCAL`，危险能力二次确认。
- **撤销列表**双通道（索引 + App 资产）：命中 `id + version` 即禁用；用户强制启用需二次确认并记录。

### 7.3 版本兼容

| 场景 | 行为 |
| --- | --- |
| `schemaVersion > 引擎支持` | 拒绝导入，提示升级 App |
| `minEngine > 当前引擎` | 拒绝，提示规则需要更新版本 |
| 引擎升级后旧规则 | v1 规则保证在 v2 引擎可运行；破坏性变更同时提升 `schemaVersion` |
| 规则升级（同 id） | 保留 `type`/`enabled`/`health`，重置 `failStreak` |
| 规则降级 | 允许，UI 标记「较旧版本」 |

## 8. 健康度与撤销

```
              success                    success
  HEALTHY ───────────────▶ HEALTHY   （fail_streak 重置）
     │   fail_streak ≥ 3
     ▼
  DEGRADED ──fail_streak ≥ 10──▶ 建议禁用（UI 提示）
     │   success
     └──────────────▶ HEALTHY
```

- 失败权重：网络/HTTP 1 次；`SELECTOR_EMPTY` 计 2 次；`RULE_REVOKED` 直接禁用。
- 指数退避：`min(2^n, 60)s`。
- 列表排序：healthy 优先，degraded 显示黄色标记与最近错误码。
- 撤销：`health = DISABLED`，保留 JSON 供导出。

## 9. 安全红线（PR 会拒绝）

1. 请求登录态/付费接口，或自动登录。
2. DRM 解密、付费墙绕过、批量注册/签到等自动化。
3. 无理由声明 `allowCleartext`；请求非白名单 host。
4. 脚本发起大量并发请求（> 2 rps 或 > 10 次/钩子）。
5. 规则内嵌第三方 JS 压缩包做 `eval` 式加载。
6. 冒充官方 id（`official.*` 前缀保留）。
7. 携带任何漫画内容、图片、数据库。
8. 以「分类浏览」为名遍历全站。

## 10. 完整示例

见 [examples/demo-source.json](examples/demo-source.json)（`example.com` 占位，仅演示语法）。

## 11. 实现清单

| 组件 | 说明 | 位置 |
| --- | --- | --- |
| `RuleParser` | JSON → `SourceRule`，保留 pointer | core:source |
| `RuleValidator` | L1/L2 + 友好错误 + 修复建议 | core:source |
| `Pipe` | 模板变量与字符串变换 | core:source |
| `Selector` | jsoup / JSONPath 取值 | core:source |
| `RequestBuilder` | 模板化、鉴权头、限流、重定向校验 | core:source |
| `RhinoScriptRuntime` | 沙箱、超时、白名单 | core:source |
| `HealthTracker` | 状态机、退避、统计 | core:source + core:data |
| `RuleIndexClient` | 索引拉取、JCS 规范化、Ed25519 校验 | core:source |
| `RuleLint` | CLI 校验（`./gradlew :tools:rulelint:run --args=…`） | tools（M1） |
