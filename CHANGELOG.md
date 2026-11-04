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
