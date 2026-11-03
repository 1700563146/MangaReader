# MangaReader 仓库协作说明（AGENTS.md）

本文件约束在本仓库工作的 AI 代理与协作者。**优先级**：用户直接指令 > 本文件 > 通用习惯。

## 1. 项目定位（不可偏离）

- 这是一个**阅读器**：不提供、不内置、不推荐任何漫画资源或具体站点规则。
- 规则是数据，由用户/社区导入；仓库内示例只能使用 `example.com`。
- **禁止**实现或协助实现：破解付费墙、绕过登录、去除 DRM、批量抓取、内容分发服务。
- 发现需求与上述冲突时，停止并说明原因，不改代码。

## 2. 技术基线

| 项 | 约定 |
| --- | --- |
| 语言 | Kotlin（全栈）；禁止新增 Java 源文件 |
| UI | Jetpack Compose + Material3；禁止新增 XML 布局（除必要资源） |
| 异步 | 协程 + Flow；禁止 `GlobalScope`、`runBlocking` 出现在主线程路径 |
| DI | Hilt（KSP） |
| 存储 | Room（KSP）+ DataStore |
| 网络 | OkHttp；图片 Coil |
| 构建 | Gradle Kotlin DSL + Version Catalog + `build-logic` 约定插件 |

文档基线：`docs/03-architecture.md`、`docs/05-source-rule-spec.md`、`docs/06-reader-kernel.md`。改动涉及这些契约时，必须同步文档与 Schema。

## 3. 改动流程

1. 先读相关文档与现有实现，再动手；不确定时先写 issue/计划，不猜。
2. 小步提交；遵循 Conventional Commits（见 `docs/09-engineering.md`）。
3. 提交前必须运行：

```bash
./gradlew spotlessApply detekt lint testDebugUnitTest
```

4. 行为变更必须附带测试；数据库变更必须附带迁移测试；规则变更必须更新 Schema 与示例。
5. 不提交：密钥、签名文件、`local.properties`、真实站点规则、受版权保护的图片/HTML、抓包 Cookie。

## 4. 代码约定

- 公共 API 显式返回类型；模块内优先 `internal`。
- Compose：状态提升、`@Immutable`、稳定 `key`、`modifier` 为首个可选参数、无副作用组合。
- 错误：统一 `AppError`，禁止吞异常；日志不得包含 URL query/Cookie/Header。
- 命名：`feature/<name>` 包；`XxxScreen` / `XxxViewModel` / `XxxRoute`；测试类 `XxxTest`。
- 注释：解释「为什么」，不复述代码；复杂规则用 `TODO(#issue)`。

## 5. 文档与 ADR

- 架构决策写入 `docs/03-architecture.md` 的 ADR 段（背景 → 决策 → 后果），不写进代码注释就完事。
- 不得擅自推翻已有 ADR；需要变更时先提 issue 讨论并新增 ADR 记录取代关系。
- 文档中的示例域名统一 `example.com`，不得出现真实盗版站点。

## 6. 提交与 PR 描述

- 标题：`feat(reader): ...` / `fix(source): ...`
- 正文必须含：改了什么、为什么、怎么验证（命令/截图/数据）、风险与回滚。
- PR 模板中的合规勾选项必须如实填写。

## 7. 禁止事项

- 引入与 GPL-3.0 不兼容的依赖（GPL-2.0-only、闭源 SDK、广告/统计 SDK）。
- 提交生成物（APK/AAB/构建目录）、IDE 配置、本机路径。
- 用「先合并再修」绕过 CI；CI 红了先修再提交。
- 在仓库内讨论、索要或分享具体盗版站点与规则。
