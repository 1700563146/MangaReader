# 更新日志

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循 [SemVer](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- 完整的开发文档体系（产品、需求、架构、数据、规则规范、阅读器、下载/本地/备份、路线图、工程、合规、风险）。
- 源规则规范 v1 与 JSON Schema、占位示例规则。
- 仓库基础设施：README、AGENTS、CONTRIBUTING、SECURITY、THIRD-PARTY-NOTICES、Issue/PR 模板。

### Changed

- 技术路线由「Java 核心 + Compose UI」调整为 **Kotlin + Compose 单语言栈**（ADR-001）。

### Security

- 明确零内置源、规则沙箱、签名与撤销机制、实名投诉通道。

## [Unreleased]

### Added

- 源库浏览：设置 →「源库」，用户自填索引地址，拉取并逐条安装。
- 索引解析兼容 venera 数组格式与带签名的 JSON 规则索引。
- `SourceKind` 与 `SourceRef` 密封接口，为多引擎做准备。
- CJS 源可导入并登记（`enabled=false`），UI 明确提示需 ES2020 引擎。

### Changed

- Room v2→v3：`source.source_kind` 列，附迁移测试。
- Rhino 升级到 1.9.2（新增展开运算符、可选链、空值合并支持；仍不支持 class/async）。

### Docs

- 新增 ADR-007（CJS 引擎预留与实测能力边界）、M8 报告。

## [1.0.0] - 2026-11-04

### Added

- 规则导入三通道：剪贴板、SAF 文件（≤1MB）、HTTPS 地址（`SourceHttpClient.fetchRuleText`，仅 https、大小与超时限制）。
- v1.0 全链路验收记录（6 步）与截图：搜索 → 详情 → 收藏/下载 → 阅读 → 断网重读。

### Changed

- 规则导入入口在导入对话框内提供「粘贴 / 从文件 / URL 下载」三种方式。

## [0.9.0-M7] - 2026-11-04

### Added

- 版本 1.0.0（versionCode 10）；Release 启用 R8 + 资源压缩，Rhino/serialization keep 规则。
- GitHub Release 工作流（tag `v*`）：可选 keystore 签名、APK 产物与 Release note；PR 门禁增加 release 构建。
- 阅读设置：默认翻页模式（左→右/右→左/上下/条漫）与双页开关持久化，阅读器即时生效。
- 阅读菜单模式循环；详情页「下载全部」；书架「仅更新」筛选。

### Added (P0 补齐)

- 搜索分页：每源「加载更多」+ 去重 + 到底停止（F-203）。
- 章节正序/倒序切换并持久化（F-302）。
- 点击区动作可配置（左/中/右 → 上一页/下一页/菜单）（F-405）。
- 阅读菜单亮度滑杆、夜间滤镜、自动裁剪（F-406）。
- 书架长按多选：全选/取消/批量取消收藏（F-101）。
- 阅读历史单条删除与清空（二次确认）（F-106）。
- 进入详情清除「有新章节」高亮，「仅更新」无更新时显示空态（F-104）。
- 章节长按多选批量下载（F-305）。
- 上次章节被源删除时按索引回退并提示（F-303）。

### Accessibility & security

- 阅读器文案抽到 `core:reader` 资源（zh / en / zh-rTW），页面图片补充语义描述。
- `dataExtractionRules`：设备迁移保留规则/书架/进度/设置，排除下载图片与缓存。
- 移除未使用字符串，lint 警告 53 → 47（0 error）。
- 新增 `docs/reports/m7-security.md` 安全审查清单与 `NOTICE` 文件。

### i18n

- 界面文案抽离到 `strings.xml`：书架、历史、下载、搜索、详情、设置、规则、本地、备份、阅读菜单。
- 新增 `values-en`（英文）与 `values-zh-rTW`（繁体中文）各 133 条；默认语言声明为 `zh`。

### Fixed

- R8 缺失 jsoup 可选 Re2J 类导致 release 构建失败。

## [0.8.0-M6] - 2026-11-04

### Added

- 本地备份/恢复（ZIP + manifest + SHA-256 校验）：规则、收藏、章节已读、进度、标签、主题。
- 三种恢复模式（合并/替换/仅规则）与冲突合并策略。
- 备份与恢复页面（SAF 导出/导入）与设置入口。

### Deferred

- WebDAV 备份、自动备份与保留策略、阅读设置落库 → M7。

## [0.7.0-M5] - 2026-11-04

### Added

- 规则索引签名：JCS 规范化 + Ed25519 验签（keyId 轮换）、规则内容 sha256 校验。
- 撤销列表（资产下发）：安装/加载拒绝，规则页自动禁用并锁定开关。
- `tools:rulelint` CLI：多文件校验、JSON Pointer、CI 可用退出码。
- 规则页展示 health / failStreak / 最近错误。

### Deferred

- 索引导入 UI、索引拉取客户端、规则导出、10 次失败建议禁用 → M7。

## [0.6.0-M4 本地] - 2026-11-04

### Added

- 本地目录扫描（SAF tree / file://）：漫画/章节识别、自然排序、封面、增量 upsert。
- 本地库页面：添加/移除目录、扫描全部、本地漫画网格、移除漫画。
- `LocalPageSource`：file:// 与 content:// 本地章节阅读；Reader 自动选择本地页源。
- Debug 本地演示目录生成（仅 Debug 入口，无真实内容）。

### Deferred

- ZIP/CBZ、封面缩略图、下载并发/仅 Wi-Fi 设置、批量下载 → M7。

## [0.5.0-M4] - 2026-11-04

### Added

- WorkManager 下载队列：`DownloadWorker`（HiltWorker）、前台通知、暂停/继续/删除、断点续传、`.part` 原子写入。
- 图片下载走 host 白名单 + 页级 Referer；32MB 单图上限。
- 离线阅读：`FilePageSource`；`ReaderViewModel` 优先使用已下载目录。
- 离线详情：缓存优先，刷新失败提示「显示已缓存内容」。
- 下载管理页（进度/暂停/继续/删除）与详情页逐章下载按钮。

### Fixed

- WorkManager 网络约束在未通过系统校验的网络下永不执行：改为 `NOT_REQUIRED` 并 `REPLACE` 旧任务。
- `SystemForegroundService` 缺少 `foregroundServiceType=dataSync` 导致下载启动崩溃。
- 详情页离线时强制刷新导致整页失败：改为缓存优先。

## [0.4.0-M3] - 2026-11-04

### Added

- 规则管理：列表、启停、删除、剪贴板导入、JSON Pointer 错误提示、编译缓存（`SourceManager`）。
- 聚合搜索：`SearchCoordinator` 并发按源请求、先到先发、单源超时与失败隔离、健康度上报。
- 搜索页 / 详情页 / 书架 / 历史 / 规则页 Compose UI，导航闭环。
- `ReaderRoute` 在线章节阅读：规则引擎取图（含 lazy）、进度写库、继续阅读。
- 书架检查更新（4 并发，失败不覆盖旧值）。
- Debug 本地假源（`tools/devserver` + `DevInstallActivity`/`dev_rule.json`，release 不含）。
- Room 迁移仪器化测试（v1→v2）。

### Fixed

- Android ICU 不支持未转义 `}` 的正则：修复模板变量正则导致的启动崩溃。
- `ImageRef` 统一为 `core-model` 类型，消除 source/reader 双模型转换。

## [0.3.0-M2] - 2026-11-04

### Added

- Compose 阅读器：翻页（LTR/RTL/TTB）、条漫、双页、点击区、长按切模式、沉浸模式。
- 自定义 `ZoomState` + `zoomable` 手势仲裁（双指缩放、放大后平移、不干扰 Pager/滚动）。
- `ReaderPrefetcher` 窗口预取；Coil 3 `httpHeaders` 注入 Referer/Cookie；单页失败重试。
- `ProgressSink` 进度出口；Debug「阅读器演示」入口（本地占位图，无漫画内容）。
- 单元测试：`ReaderControllerTest` 8 项、`ZoomStateTest` 4 项；模拟器截图与冒烟数据。

## [0.2.0-M1] - 2026-11-04

### Added

- Room v2：源、漫画、章节、标签、下载、搜索历史、本地扫描根，含显式迁移与 DAO/Repository。
- 规则编译器：模板变量、CSS/JSONPath 选择器、管道变换、L1/L2 校验与 JSON Pointer 报错。
- HTTP 客户端：模板请求、host 白名单、明文闸门、重定向校验、令牌桶限流、重试、Cookie。
- Rhino 脚本沙箱：拒绝 Java 互操作、3s 超时、栈深限制、`doc()/util/http/cache/log` 白名单 API。
- `JsonRuleEngine`：search/detail/chapters/images/lazy/check/category 全能力，MockWebServer 端到端测试。

### Changed

- `SourceEngine` 契约改为携带强类型 `RuleSpec`，供引擎直接执行。

## [0.1.0-M0] - 2026-11-04

### Added

- Gradle 8.14 / AGP 8.13.1 / Kotlin 2.2.20 的多模块工程骨架（7 个模块）。
- Compose 单 Activity 壳：4 个一级页面（书架/历史/浏览/我的）、Material3 主题、动态取色、深色模式、类型安全导航。
- Hilt + KSP + Room（schema 导出）+ DataStore 接入；`SettingsRepository` 持久化主题。
- `core:model` 领域模型与错误模型、`core:source` 源引擎契约与域名白名单匹配、`core:reader` 阅读状态机（含单测）。
- PR CI（`assembleDebug` + `lintDebug` + `test`）、验证记录 `docs/reports/m0.md`、截图。

### Fixed

- 修正 Gradle 插件版本冲突（模块内使用无版本插件 id，版本统一由根构建声明）。


## [0.0.0] - 2026-01-01

- 项目立项，文档阶段开始。

[Unreleased]: https://github.com/1700563146/MangaReader/compare/v0.0.0...HEAD
