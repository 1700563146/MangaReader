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
