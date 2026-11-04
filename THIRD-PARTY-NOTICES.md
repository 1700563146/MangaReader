# 第三方组件声明（THIRD-PARTY-NOTICES）

本文件列出 MangaReader 计划使用/已使用的第三方组件及其许可证。**未列出的组件视为未通过合规审查**。
发布流程会用 Gradle 许可证任务生成完整清单，并与本文件比对；新增依赖必须在 PR 中同时更新本文件。

## 运行时依赖（随 APK 分发）

| 组件 | 许可证 | 用途 | 备注 |
| --- | --- | --- | --- |
| Kotlin Standard Library / kotlinx.coroutines / kotlinx.serialization | Apache-2.0 | 语言与异步/序列化 | |
| AndroidX Core / Activity / Lifecycle / Navigation / Paging | Apache-2.0 | 平台与导航 | |
| Jetpack Compose（UI / Foundation / Material3） | Apache-2.0 | 界面 | |
| AndroidX Room | Apache-2.0 | 数据库 | |
| AndroidX DataStore | Apache-2.0 | 设置存储 | |
| AndroidX WorkManager | Apache-2.0 | 后台任务 | |
| Hilt / Dagger | Apache-2.0 | 依赖注入 | |
| OkHttp | Apache-2.0 | HTTP | |
| jsoup | MIT | HTML 解析 | |
| Rhino | MPL-2.0 | JS 沙箱（规则钩子） | 以库形式使用，不修改源码 |
| Coil | Apache-2.0 | 图片加载 | |
| EdDSA-Java（net.i2p.crypto:eddsa 0.3.0） | CC0-1.0 | 规则索引 Ed25519 验签 | 公共领域贡献 |
| kotlinx-datetime（如引入） | Apache-2.0 | 时间处理 | |

### 版本锁定（v1.0.0）

| 组件 | 版本 |
| --- | --- |
| Kotlin | 2.2.20 |
| kotlinx-coroutines / serialization | 1.10.2 / 1.9.0 |
| Compose BOM | 2025.11.01 |
| Room / WorkManager / DataStore / Paging | 2.8.3 / 2.10.5 / 1.1.7 / 3.4.2 |
| Hilt / androidx.hilt | 2.57.2 / 1.3.0 |
| Coil | 3.3.0 |
| OkHttp | 4.12.0 |
| jsoup / Rhino / eddsa | 1.22.1 / 1.7.15 / 0.3.0 |


Android 系统组件（`android.*` / `androidx.*` 中属于平台的部分）按其各自许可证分发；AOSP 部分为 Apache-2.0。

## 仅测试/构建期依赖（不随 APK 分发）

| 组件 | 许可证 |
| --- | --- |
| JUnit 5 | EPL-2.0 |
| MockK | Apache-2.0 |
| Turbine | Apache-2.0 |
| MockWebServer | Apache-2.0 |
| Robolectric | MIT |
| Roborazzi | Apache-2.0 |
| Detekt / ktlint | Apache-2.0 |
| Android Gradle Plugin / Gradle | Apache-2.0 |

## 明确不引入

- Firebase（Analytics / Crashlytics / Remote Config）
- 任何广告 SDK、统计/归因 SDK
- 闭源加固、闭源下载器、破解相关库
- GPL-2.0-only 或带「非商业」限制的组件（与 GPL-3.0 冲突）

## 许可证文本

- Apache-2.0：<https://www.apache.org/licenses/LICENSE-2.0>
- MIT：<https://opensource.org/license/mit>
- MPL-2.0：<https://www.mozilla.org/MPL/2.0/>
- EPL-2.0：<https://www.eclipse.org/legal/epl-2.0/>

完整的许可证原文可在各依赖的 Maven 构件或上游仓库中获取；发布包内「关于 → 开源许可」页面提供汇总入口。

## 合规说明

- 本项目整体以 GPL-3.0-only 发布，详见 [LICENSE](LICENSE) 与 [docs/10-legal.md](docs/10-legal.md)。
- 若你发现遗漏或错误，请开 issue 并附组件坐标与许可证证据。
