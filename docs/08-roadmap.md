# 08 开发路线图

> 目标：以**单人兼职**（每周 10–12 小时）估算，**23 周**达到 v1.0；全职约 9–10 周。
> 当前进度：**文档阶段完成（M-1）**。每个里程碑都必须满足 DoD 才能进入下一个。

## 1. 总览

| 里程碑 | 主题 | 周期 | 关键产物 | 状态 |
| --- | --- | --- | --- | --- |
| **M-1** | 方案与文档 | 1 周 | 本套文档、规则 Schema、路线图 | ✅ 完成 |
| **M0** | 工程骨架 | 2 周 | 可编译运行的 Compose 壳、CI、模块骨架 | ✅ 完成（2026-11） |
| **M1** | 数据与规则引擎 | 4 周 | Room、Repository、规则解析/校验/HTTP、单源可搜 | ✅ 完成（2026-11） |
| **M2** | 阅读器 | 4 周 | 翻页/条漫/缩放/预取/进度 | ✅ 功能完成（性能门禁→M7） |
| **M3** | 主流程闭环 | 3 周 | 搜索→详情→章节→阅读完整链路，书架/历史 | ✅ 核心闭环完成（验收细化→M4/M5/M7） |
| **M4** | 下载与本地 | 3 周 | 下载队列、离线阅读、本地漫画 | ✅ 下载/离线/目录型本地完成（ZIP 顺延 M7） |
| **M5** | 规则生态 | 2 周 | 规则管理、索引/签名、健康度、撤销 | ✅ 核心完成（索引导入 UI 顺延 M7） |
| **M6** | 备份与设置 | 2 周 | 本地/WebDAV 备份、设置项、主题 | 🚧 进行中 |
| **M7** | 硬化与发布 | 3 周 | 性能/稳定性/无障碍、v1.0 发布 | ⬜ |

```
周次  1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19  20  21  22  23
M0   ██ ██
M1         ██ ██ ██ ██
M2                     ██ ██ ██ ██
M3                                 ██ ██ ██
M4                                         ██ ██ ██
M5                                                 ██ ██
M6                                                     ██ ██
M7                                                         ██ ██ ██
版本        0.1-alpha ────────────────┘ 0.5-beta ────────┘  1.0
```

## 2. 里程碑详情

### M0 工程骨架（2 周）

**目标**：`./gradlew :app:assembleDebug` 可装可跑，CI 绿灯，模块边界就位。

交付物：
- 空壳 Compose App：4 个 Tab、主题（Material3 + 动态取色）、导航（类型安全路由）。
- 模块：`:app`、`:core:designsystem`、`:core:model`、`:core:data`、`:core:source`、`:core:reader`、`:core:storage`。
- 版本目录 `gradle/libs.versions.toml` + 模块级构建脚本（约定插件在 M1 抽到 `build-logic`）。
- Hilt、Room、KSP、Coil、OkHttp、kotlinx-serialization 接线。
- CI：`assembleDebug`、`lintDebug`、`test`（PR 门禁）。
- `.editorconfig`、Issue/PR 模板、`CONTRIBUTING.md`、`SECURITY.md`。

验收：
- [x] 新机器按 README 三条命令即可编译（含 JDK/SDK 版本说明）。
- [x] 单元测试与 Lint 在本地与 CI 配置中可通过。
- [x] 空壳可切换深色/浅色、动态取色；旋转不崩溃（模拟器验证，M1 起补真机矩阵）。
- [x] 依赖版本全部走 catalog；无未声明依赖。
- [x] 调试包可安装，Android 16 模拟器冷启动无崩溃（记录见 `docs/reports/m0.md`）。

### M1 数据与规则引擎（4 周）

**目标**：能加载一条 `example.com` 静态规则，完成「搜索 → 详情 → 章节 → 图片 URL」的离线 fixture 测试。

交付物：
- Room schema v1（04 全部表）+ DAO + Repository（Flow/suspend）+ 迁移测试骨架。
- `RuleParser` / `RuleValidator`（L1+L2，带 JSON Pointer）/ `Pipe` / `Selector`（jsoup + JSONPath 子集）。
- `RequestBuilder`（模板变量、Header、重定向 host 校验、限流、超时）。
- `RhinoScriptRuntime`（白名单、超时、栈深、编译缓存）。
- `SourceEngine` 实现 + `SearchCoordinator`（channelFlow 交错）。
- `tools:rulelint` CLI 的计划顺延到 M5（当前用单测夹具校验规则）。
- 测试：`MockWebServer` + 脱敏 HTML fixture（search/detail/chapters/images/lazy 各 1 份）。

验收：
- [x] 离线 fixture 下，示例规则 6 个能力（search/detail/chapters/images/lazy/check/category）全部通过单测。
- [x] 8 类错误（未知管道/能力缺失/明文 HTTP/坏选择器/JSON 语法/模板变量/限流返回/脚本异常）均给出 pointer 与可读原因。
- [x] 单源限流（令牌桶）与重试生效；host 白名单在请求前校验。
- [x] 脚本 3s 超时被中断，Java 互操作被拒绝。
- [x] Room v1→v2 在模拟器覆盖安装验证通过；无 `fallbackToDestructiveMigration`。

> 详细记录见 [`docs/reports/m1.md`](reports/m1.md)。聚合搜索协调器、健康度接线与 `MigrationTestHelper` 自动化测试顺延到 M3，`rulelint` 顺延到 M5。

### M2 阅读器（4 周，含决策门）

**目标**：在线章节与本地图片目录都能流畅阅读；翻页/条漫/缩放/预取/进度可用。

交付物：
- `ReaderRoute` + `ReaderViewModel` + `ReaderController`（纯 Kotlin，单测覆盖边界）。
- `PagedReader`（HorizontalPager/VerticalPager，RTL、双页）。
- `WebtoonReader`（LazyColumn、高度回填、长图切片）。
- `ZoomState` + 手势仲裁（缩放/翻页/点击区/双击/长按）。
- `ImagePipeline`（Coil 自定义 Fetcher/Keyer、Header 注入、降采样、备用 URL）。
- `ReaderPrefetcher`（窗口、取消、章末预取）。
- 进度持久化 + 恢复（含 `SavedStateHandle`）。
- **决策门报告**（顺延 M7 真机）：Macrobenchmark 数据 + 是否回退自定义 View 的结论（ADR-006）。

验收（功能性，M2 完成）：
- [x] 翻页 / 条漫 / 双页 / TTB 分支实现；双页单屏两页渲染。
- [x] 缩放后翻页禁用、平移边界 clamp；点击区与长按无冲突。
- [x] 单页失败可局部重试；切换模式保留页码。
- [x] 沉浸模式隐藏系统栏；退出恢复。
- [x] 模拟器冒烟无 FATAL/ANR（截图与数据见 `docs/reports/m2.md`）。

顺延至 M7 硬化（见 ADR-006 与报告）：
- [ ] 真机 Macrobenchmark：翻页首帧 ≤100ms、条漫掉帧 <5%、内存 <300MB。
- [ ] 旋转/进程重建后的页码恢复（SavedStateHandle）。
- [ ] 弱网（200KB/s）与 200 页条漫浸泡测试。

### M3 主流程闭环（3 周）

**目标**：不依赖 M4 的能力，用户可以「导入规则 → 搜索 → 收藏 → 阅读 → 回书架」走通。

交付物：
- 搜索页（流式结果、源分组、失败折叠、历史）。
- 详情页（元数据、章节列表、排序、继续阅读、收藏）。
- 书架（网格、排序、更新标记）、历史页。
- 规则导入（文件/粘贴）+ 规则列表（启停、删除、错误详情）。
- 错误 UI 统一组件（空/加载/错误/重试）。
- 埋点（本地日志）：搜索耗时、源失败率。

验收（核心闭环，M3 完成）：
- [x] 导入规则 → 搜索 → 详情 → 章节 → 阅读 → 进度入库 → 书架/历史全链路在模拟器跑通。
- [x] 聚合搜索按源分组、先到先发；单源失败不影响其他源，失败行可重试。
- [x] 规则导入错误定位到 JSON Pointer（编译器单测 + 导入弹窗展示）。
- [x] 更新检查：4 并发、失败不清空旧值、结果显示「检查完成」。
- [x] Room v1→v2 仪器化迁移测试通过（直接 adb instrument；UTP 在 Windows 下有工具问题）。

顺延：
- [ ] 5 条不同结构规则的手工验收矩阵（M5 规则生态）。
- [ ] 搜索首条 ≤2s、冷启动 ≤1.5s、500 收藏滚动、20 本更新 ≤30s（M7 真机基准）。
- [ ] 搜索历史 UI、惰性章节总页数探测（M4）。

### M4 下载与本地（3 周）

**目标**：按章下载可断点续传；本地目录/ZIP/CBZ 可读。

交付物：
- `DownloadWorker`（CoroutineWorker）+ 队列/暂停/继续/取消 + 前台服务通知。
- 文件命名、`.part` 原子写、Range 续传、SAF 支持、孤立文件清理。
- 离线阅读路径（`FilePageSource`）。
- `LocalScanner`（目录/ZIP/CBZ、自然排序、封面缩略图、增量扫描）。
- 本地库页面（扫描根管理、手动刷新）。

验收：
- [ ] 飞行模式中断后恢复，页数一致、无坏图。
- [ ] SAF 权限撤销后任务暂停并提示重新授权。
- [ ] 1000 文件本地库扫描 ≤ 10s；100 章 ZIP 打开 ≤ 300ms。
- [ ] 删除下载有二次确认，无残留目录。
- [ ] 下载 200 页章节内存峰值 < 150MB。

### M5 规则生态（2 周）

**目标**：规则可被社区分发、校验、撤销；源健康度可见。

交付物：
- 索引拉取、JCS 规范化、Ed25519 验签、sha256 校验。
- 信任等级与危险能力确认弹窗；规则升级/降级。
- `HealthTracker`（降级/退避/禁用建议）、撤销列表（资产 + 索引）。
- 规则详情页（JSON 查看、复制错误、导出）。
- `RuleLint` 发布到 GitHub Action（供规则仓库 CI 复用）。

验收（M5 核心）：
- [x] 篡改索引/规则被拒绝（签名或 sha256 校验），提示原因。
- [x] 无签名/未知 key 的索引被拒绝；本地导入仍可覆盖（标 LOCAL）。
- [x] 连续失败 3 次降级展示；撤销命中后自动禁用并锁定开关。
- [ ] 规则导出、10 次失败建议禁用、索引导入 UI → M7。

### M6 备份与设置（2 周）

**目标**：换机不丢数据；设置项完整可用。

交付物：
- 备份导出/导入（ZIP + manifest + 校验 + 三种恢复模式）。
- WebDAV 客户端（PROPFIND/MKCOL/PUT/GET/DELETE）+ 凭据 Keystore 加密。
- 设置页全部条目（阅读/外观/网络/存储/隐私/关于）+ 设置搜索。
- 关于页（许可证清单、免责声明、检查更新入口）。
- 每日自动备份（WorkManager 周期任务）。

验收：
- [ ] 备份 → 卸载重装 → 恢复：收藏/进度/标签/规则一致。
- [ ] 旧格式备份可迁移；高版本备份被拒绝并提示。
- [ ] WebDAV 在 Nextcloud/坚果云各跑通一次（含 401 处理）。
- [ ] 「清除全部数据」后无残留（DB、缓存、下载目录、规则）。

### M7 硬化与发布（3 周）

**目标**：v1.0 可发布；崩溃率、性能、无障碍达标。

交付物：
- Macrobenchmark 基线 + Baseline Profile；启动/滚动优化。
- 崩溃与 ANR 处理、诊断包导出（脱敏）。
- 无障碍（TalkBack、字体 200%）、i18n（zh-CN/zh-TW/en）。
- 深色/纯黑/横屏/折叠屏/平板适配；Android 15 edge-to-edge。
- 发布流程（tag → CI 签名 APK → GitHub Release）+ 升级说明。
- 安全审查清单、依赖许可证清单（THIRD-PARTY-NOTICES）。

验收：
- [ ] 02 文档中 P0 需求全部通过验收清单。
- [ ] NFR-01/02/04/07 达标并有数据。
- [ ] 全新安装全链路手工通过（6 步，记录在 release note）。
- [ ] 无高危 lint/security 问题；无未声明权限。
- [ ] Release APK 可安装、可覆盖升级、签名一致。

## 3. 首发工单（M0 可直接建 Issue）

| # | 标题 | 标签 | 估算 | 里程碑 |
| --- | --- | --- | --- | --- |
| 1 | chore: 初始化 Gradle 工程与版本目录 | build | S | M0 |
| 2 | chore: 搭建 7 个模块与约定插件 | build | M | M0 |
| 3 | feat: 应用主题与 4 Tab 空壳导航 | ui | M | M0 |
| 4 | ci: PR 门禁（spotless/detekt/lint/test/assemble） | ci | S | M0 |
| 5 | chore: 配置 Hilt/Room/KSP/Coil/OkHttp 依赖 | build | S | M0 |
| 6 | feat: 定义 core-model 领域模型与错误类型 | model | S | M0 |
| 7 | feat: Room schema v1 与 DAO 骨架 | data | M | M1 |
| 8 | feat: 规则解析与 L1 校验 | source | M | M1 |
| 9 | feat: 选择器 DSL（jsoup + JSONPath + pipeline） | source | L | M1 |
| 10 | feat: HTTP 客户端与源级限流/重定向校验 | source | M | M1 |
| 11 | feat: Rhino 沙箱与超时中断 | source | L | M1 |
| 12 | test: MockWebServer + 5 类 fixture | test | M | M1 |
| 13 | feat: 聚合搜索 coordinator（流式） | source | M | M1 |
| 14 | feat: ReaderController 状态机 + 单测 | reader | L | M2 |
| 15 | feat: 翻页视图与缩放仲裁 | reader | L | M2 |
| 16 | feat: 条漫视图 + 高度回填 + 长图切片 | reader | L | M2 |
| 17 | feat: Coil 自定义 Fetcher 与预取 | reader | M | M2 |
| 18 | feat: 搜索页与流式结果 | ui | M | M3 |
| 19 | feat: 详情页与章节列表 | ui | M | M3 |
| 20 | feat: 书架/历史 + 收藏 | ui | M | M3 |

> 估算：S ≤ 0.5 天，M 1–2 天，L 3–5 天（兼职口径）。

## 4. 完成定义（DoD）

一个工单只有同时满足下列条件才能关闭：

1. **代码**：实现符合 03/05/06 的约定；无 `TODO` 遗留（或已建 issue）。
2. **测试**：新增逻辑有单测；涉及 UI 有关键路径 Compose 测试；修 bug 附带回归用例。
3. **检查**：`spotlessCheck`、`detekt`、`lint` 通过；无新增 warning。
4. **文档**：行为变更同步 README/相关 docs；规则变更同步 Schema 与 05。
5. **评审**：PR 描述含「改了什么/为什么/怎么验证」，至少 1 次自查通过（单人项目用 checklist 代替）。
6. **可回滚**：数据库/规则格式变更提供迁移或版本拒绝路径。

里程碑级 DoD 额外要求：验收清单逐条勾选，性能数据写入 `docs/reports/`（或 PR 评论归档）。

## 5. 质量门禁

| 门禁 | 触发 | 阈值 |
| --- | --- | --- |
| 编译 | 每次 PR | debug + release 均成功 |
| 静态检查 | 每次 PR | 0 error；detekt 不新增 issue |
| 单测 | 每次 PR | 全绿；核心模块行覆盖 ≥ 60% |
| 迁移测试 | data 变更 | 全绿 |
| 规则 fixture | source 变更 | 全绿；新增规则必须附 fixture |
| 性能 | 里程碑结束 | 达 06 §12 预算 |
| 安全 | 发版前 | 06/10 清单全过 |

## 6. 范围调整顺序

进度落后时按以下顺序砍，**不砍质量门禁**：

1. 分类浏览（`category`）→ 1.1
2. 双页模式 → 1.1
3. WebDAV 备份 → 1.1（保留本地备份）
4. 换源 → 1.2
5. 标签 → 1.2
6. 规则索引签名 → 1.2（先只支持本地导入 + 官方推荐列表）

绝对不砍：规则校验与 pointer 错误、阅读器核心手势、下载断点续传、备份/恢复、免责声明与投诉通道。

## 7. v1.x 方向（非承诺）

| 方向 | 说明 |
| --- | --- |
| 规则调试器 | App 内「试运行」：粘贴 HTML/URL，逐步显示每个字段取值 |
| QuickJS 运行时 | 替换 Rhino，ES2020、更快、更强隔离（ADR-013 再评估） |
| 平板/墨水屏 | 双页默认、翻页动画关闭、A2 刷新适配 |
| 多源换源增强 | 按作者/别名模糊匹配，手动关联 |
| 本地库增强 | RAR/CBR（许可证评估）、ComicInfo 元数据、CBZ 导出 |
| 阅读统计 | 仅本地、可导出（不联网） |
| 无障碍 | 全流程 TalkBack、色弱模式 |
| 扩展 API | `mangareader://open`、分享目标、自动备份到网盘（仅用户配置） |

## 8. 时间与风险提示

- **最大不确定性在 M2**（Compose 阅读器性能）。决策门就是为此设置的：允许一次回退，不允许无限调优。
- **M4 的 SAF/ZIP 兼容性**易低估（厂商 ROM 差异），预留 3 天缓冲。
- **规则生态（M5）** 依赖社区；若无人贡献规则，v1.0 用 3 条自测规则（不随包分发）做验收即可，发布后靠 issue 引导。
- 禁止为赶进度跳过备份与迁移测试——这是用户数据，丢一次就没有第二次机会。
