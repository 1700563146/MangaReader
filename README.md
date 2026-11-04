# MangaReader

> 一款**只做阅读器、不提供任何漫画内容**的 Android 漫画阅读应用。
> 把「漫画从哪来」交给用户与社区规则，「怎么读得舒服」由我们负责。

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%203.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84.svg)](https://developer.android.com)
[![Status](https://img.shields.io/badge/status-docs%20phase-orange.svg)](docs/08-roadmap.md)

> **当前状态：M4 完成（下载/离线/目录型本地漫画），M5 规则生态进行中。** 详见 [`docs/08-roadmap.md`](docs/08-roadmap.md)。

![书架空状态（M0）](docs/screenshots/m0-shelf.png)

---

## 这是什么

MangaReader 是一个**本地优先、无账号、无云端**的漫画阅读器：

- **阅读**：翻页 / 条漫 / 双页 / 缩放，本地文件与在线章节共用同一套阅读内核。
- **来源**：应用**默认零内置源**。漫画搜索与章节解析由用户导入的 **JSON 规则包**驱动，规则由内置解析引擎解释执行；进阶场景允许受限 JS 钩子。
- **数据**：书架、历史、标签、阅读进度、下载全部留在设备上；可选 WebDAV 备份。
- **边界**：不解析需要登录/付费/DRM 的内容，不内置、不缓存、不分发任何漫画资源，不做内容聚合服务端。

## 法律声明（请先读）

1. 本项目**不提供、不存储、不分发**任何漫画作品，也不内置任何指向具体站点的解析规则。
2. 所有漫画内容均由用户自行通过第三方站点访问，**内容合法性由用户与站点自行负责**。
3. 本项目**不具备**破解付费墙、绕过登录、去除 DRM 的能力，也不接受相关功能的贡献。
4. 如权利人认为本项目存在侵权内容（例如社区规则包），请通过 [侵权投诉模板](.github/ISSUE_TEMPLATE/takedown.yml) 联系，并附权利证明；我们将在核实后 72 小时内处理。安全漏洞请走 [SECURITY.md](SECURITY.md) 的私密渠道。
5. 详细合规设计见 [`docs/10-legal.md`](docs/10-legal.md)。**使用本软件产生的一切法律后果由使用者自行承担。**

## 功能规划（v1.0 范围）

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 搜索（多源聚合、交错加载） | 规划 | 规则驱动，无内置源 |
| 漫画详情 / 章节列表 | 规划 | 支持分卷、正序倒序、惰性章节 |
| 阅读器：翻页模式 | 规划 | 左→右 / 右→左 / 上下，双页拼合 |
| 阅读器：条漫模式 | 规划 | 连续滚动、预取、长图切片 |
| 阅读进度 / 历史 / 收藏 / 标签 | 规划 | Room 本地库，秒级恢复 |
| 下载与离线阅读 | 规划 | 按章下载、断点续传、目录可配置 |
| 本地漫画 | 规划 | 图片目录 / ZIP / CBZ |
| 规则管理 | 规划 | 导入、校验、启停、健康度、远程撤销 |
| 备份与恢复 | 规划 | 本地 JSON+ZIP，WebDAV 可选 |
| 阅读设置 | 规划 | 点击区、手势、亮度、裁剪、双页等 |
| 自更新 | 后置 | 走 GitHub Releases 检查，不静默安装 |

非目标（v1 明确不做）：账号体系、云端书库、内置资源站、破解/去 DRM、社交与评论、漫画上传。

## 技术栈

| 层 | 选型 | 说明 |
| --- | --- | --- |
| 语言 | **Kotlin**（全栈单语言） | 协程 + Flow 贯穿，无 Java/Kotlin 混编成本 |
| UI | Jetpack Compose + Material 3 | 阅读器也用 Compose 原生实现；M2 设性能决策门（见 ADR-006） |
| 架构 | 单 Activity + Navigation-Compose + MVVM | `StateFlow` 单向状态，Hilt（KSP） |
| 网络 | OkHttp 4 + 协程 + 自建限流 | 每源独立 CookieJar、Header、令牌桶 |
| 解析 | jsoup + 自研选择器 DSL + Rhino 沙箱 | 声明式优先，ES5.1 脚本兜底 |
| 存储 | Room（KSP）+ DataStore | 导出 schema，显式迁移 |
| 图片 | Coil 3 + 自定义 Fetcher | 按源注入 Referer/Cookie，LRU 磁盘缓存 |
| 后台 | WorkManager + 前台服务 | 下载、检查更新、备份 |
| 构建 | Gradle 8.x（Kotlin DSL）+ Version Catalog | JDK 17 工具链，CI 用 JDK 21 |
| 最低版本 | minSdk 26 / targetSdk 35 | Android 8.0 起 |

依赖版本基线见 [`docs/03-architecture.md#8-依赖基线`](docs/03-architecture.md#8-依赖基线)。

## 文档地图

| 文档 | 内容 |
| --- | --- |
| [docs/00-index.md](docs/00-index.md) | 文档索引、阅读顺序、术语表 |
| [docs/01-product.md](docs/01-product.md) | 产品定位、目标用户、范围与非目标、竞品参考 |
| [docs/02-requirements.md](docs/02-requirements.md) | 需求规格：页面清单、功能矩阵、交互与验收 |
| [docs/03-architecture.md](docs/03-architecture.md) | 总体架构、模块划分、线程模型、关键决策（ADR） |
| [docs/04-data-model.md](docs/04-data-model.md) | 数据库设计、DDL、索引、迁移策略 |
| [docs/05-source-rule-spec.md](docs/05-source-rule-spec.md) | **源规则规范 v1**：JSON 结构、选择器 DSL、脚本沙箱、签名与校验 |
| [docs/06-reader-kernel.md](docs/06-reader-kernel.md) | 阅读器内核：模式、手势、预取、图片流水线、性能预算 |
| [docs/07-download-local-backup.md](docs/07-download-local-backup.md) | 下载、本地漫画、备份与恢复 |
| [docs/08-roadmap.md](docs/08-roadmap.md) | 里程碑 M0–M7、排期、验收标准、首批工单 |
| [docs/09-engineering.md](docs/09-engineering.md) | 工程规范、分支模型、CI/CD、测试策略、发布流程 |
| [docs/10-legal.md](docs/10-legal.md) | 法律合规、免责声明、许可证与第三方声明、投诉流程 |
| [docs/11-risks.md](docs/11-risks.md) | 风险登记册与应对预案 |

配套产物：

- [`docs/schema/source-rule.schema.json`](docs/schema/source-rule.schema.json) —— 规则包 JSON Schema（可接入 CI 校验）
- [`docs/examples/demo-source.json`](docs/examples/demo-source.json) —— 演示规则（example.com，仅语法示例）

## 参考文献

功能与形态参考（**仅参考设计与产品思路，净室实现，不复制代码**）：

- [Haleydu/Cimoc](https://github.com/Haleydu/Cimoc)（GPL-3.0）—— 翻页/条漫双模式、图源解析、下载与本地阅读的经典实现
- [Tachiyomi / Mihon](https://github.com/mihonapp/mihon) —— 扩展生态与阅读器交互
- [Kotatsu](https://github.com/KotatsuApp/Kotatsu) —— 规则与源管理产品化

## 构建

环境：JDK 17+、Android SDK 35。

```bash
./gradlew :app:assembleDebug        # 调试包
./gradlew test :app:lintDebug       # 单元测试 + Lint
./gradlew :app:assembleRelease      # Release（当前使用本地 debug 签名，M7 接入正式签名）
```

M0 的构建与运行验证记录见 [`docs/reports/m0.md`](docs/reports/m0.md)。

## 贡献

- 提交前请阅读 [`docs/09-engineering.md`](docs/09-engineering.md) 与 [`AGENTS.md`](AGENTS.md)（AI 协作约定）。
- 规则包问题请使用 [规则问题模板](.github/ISSUE_TEMPLATE/rule-report.yml)；**不接受**携带版权内容的 PR（截图、整章图片、站点打包）。
- 安全与合规问题请走私密渠道，勿公开披露可被滥用的细节。

## 许可证

代码以 **GPL-3.0** 发布，详见 [LICENSE](LICENSE)。选择该许可证的原因与义务见 [`docs/10-legal.md#3.2 许可证选择`](docs/10-legal.md#32-许可证选择)。

应用内不包含任何漫画内容；第三方库许可证清单见 [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md)。

## 致谢

感谢 Cimoc、Tachiyomi/Mihon、Kotatsu 等开源项目为 Android 漫画阅读器打下的产品与工程基础；感谢所有贡献规则包与反馈的社区成员。
