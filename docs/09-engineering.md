# 09 工程规范

> 单人维护的项目，规范的价值在于**减少决策**：格式化、命名、提交、发布都走默认路径。

## 1. 环境

| 项 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17（toolchain），CI 用 21 | AGP 8.x 要求 17+；toolchain 保证一致 |
| Android SDK | compileSdk 35 / targetSdk 35 / minSdk 26 | 需 build-tools 35 |
| Gradle | 8.9+（Kotlin DSL） | 使用 wrapper，禁止本机 Gradle |
| Kotlin | 2.1.x | Compose 编译器插件随 Kotlin 版本 |
| AGP | 8.7+ | 与 Gradle 版本矩阵对应 |

本地准备：

```bash
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew spotlessApply        # 首次格式化
```

## 2. 工程结构

```
MangaReader/
├── app/                     # 单 Activity + feature 包
│   └── src/main/kotlin/.../feature/{shelf,search,detail,reader,offline,settings}
├── core/
│   ├── designsystem/        # 主题、Token、通用组件
│   ├── model/               # 纯 Kotlin 模型
│   ├── data/                # Room、Repository、DataStore
│   ├── source/              # 规则引擎、HTTP、脚本沙箱
│   ├── reader/              # Compose 阅读器、图片流水线
│   └── storage/             # 下载、本地、备份
├── build-logic/             # 约定插件
├── baselineprofile/         # Baseline Profile 生成
├── benchmark/               # Macrobenchmark
├── tools/rulelint/          # 规则校验 CLI
├── docs/                    # 本套文档
└── gradle/libs.versions.toml
```

包结构（feature 内）：

```
feature/<name>/
├── <Name>Route.kt          # 导航入口，注入 ViewModel
├── <Name>Screen.kt         # 纯 UI（@Composable，无副作用）
├── <Name>ViewModel.kt      # 状态与事件
├── <Name>UiState.kt        # @Immutable 状态 + 事件 sealed interface
└── components/             # 该 feature 专用组件
```

## 3. 代码约定

### 3.1 格式化与静态检查

- **Spotless**：`ktlint`（Kotlin）+ `ktfmt`? 二选一，**本项目用 ktlint**（`spotless` + `ktlint` 插件）。
- **Detekt**：`config/detekt/detekt.yml`，基线为「复杂度/长函数/魔法数字/命名」；新增 issue 不允许。
- **Android Lint**：`lint { warningsAsErrors = false; abortOnError = true; checkReleaseBuilds = true }`，`baseline.xml` 不提交（单人项目直接修）。
- 提交前 `./gradlew spotlessApply detekt`；CI 用 `spotlessCheck`。

### 3.2 Kotlin 风格

| 规则 | 说明 |
| --- | --- |
| 显式 API | 模块对外接口显式声明返回类型；`internal` 优先于 `public` |
| 不可变优先 | `val`、`data class`、`ImmutableList`（或只读接口）；状态类加 `@Immutable` |
| 空安全 | 禁止 `!!`（除非紧邻判空且注释说明）；平台类型显式转换 |
| 协程 | 不用 `GlobalScope`；注入 `CoroutineDispatcher`；挂起函数不吞异常 |
| Flow | 冷流用于查询；`StateFlow` 用于 UI 状态；`SharedFlow` 用于一次性事件 |
| 扩展函数 | 仅本模块内聚逻辑；不写隐藏副作用的扩展 |
| 常量 | 顶层 `private const val`；配置类参数集中 |
| 日志 | 统一 `Logger` 封装；禁止 `println`；release 不输出敏感信息 |

### 3.3 Compose 约定

1. **状态提升**：Screen 无状态化，`Route` 持有 ViewModel；参数 `state: UiState, onAction: (UiAction) -> Unit`。
2. **Modifier 参数**：`modifier: Modifier = Modifier` 必须是第一个可选参数。
3. **稳定性**：跨模块传递的 UI 模型标 `@Immutable`；列表项参数为不可变类型；避免在 item 内创建 lambda 捕获大对象（用 `remember`）。
4. **Key**：`LazyColumn`/`Pager` 必须提供稳定 `key`。
5. **预览**：每个 Screen 至少 1 个 `@Preview`（Normal / Dark / 大字体）。
6. **副作用**：`LaunchedEffect` 只做一次性；监听用 `DisposableEffect`；不要在组合中启动网络请求。
7. **导航**：类型安全路由（`@Serializable data object ShelfRoute`），参数不传复杂对象。
8. **字符串**：全部 `stringResource`；禁止硬编码中文/英文（含 `contentDescription`）。

### 3.4 分层与依赖

- UI 不直接依赖 `Room`/`OkHttp`/规则解析类；只依赖 ViewModel 暴露的状态与 `:core:model`。
- Repository 接口定义在 `:core:data`，实现也在该模块；跨模块注入通过 Hilt `@Binds`。
- `:core:source` 不依赖 `:core:data`；健康度上报通过接口 `HealthReporter` 由 `:app` 装配。
- 禁止 feature 之间直接 import；共享逻辑下沉到 `:core:*`。

## 4. 分支、提交与版本

| 项 | 约定 |
| --- | --- |
| 主分支 | `main`（受保护：必须通过 CI、线性历史） |
| 工作分支 | `feat/<slug>`、`fix/<slug>`、`docs/<slug>`、`chore/<slug>`，寿命 ≤ 3 天 |
| 提交 | Conventional Commits：`feat(reader): 支持 RTL 翻页` |
| scope | `reader` `source` `data` `storage` `ui` `build` `ci` `docs` `rules` |
| 合并 | Squash merge；PR 标题即最终提交信息 |
| 版本 | SemVer：`MAJOR.MINOR.PATCH`；tag `v1.0.0` |
| 变更日志 | `CHANGELOG.md`（Keep a Changelog 格式），发布时手动整理 |

**提交粒度**：一个提交只做一件事；格式变更与逻辑变更分开；禁止 `WIP` 提交进入 `main`。

## 5. 测试策略

```
        ╱  手工/真机  ╲         （发布前：旋转、弱网、SAF、平板）
       ╱  Macrobenchmark ╲      （里程碑：启动/滚动/翻页）
      ╱  Compose UI Test  ╲     （关键交互：点击区、菜单、错误态）
     ╱   Robolectric/截图   ╲   （composable 状态矩阵）
    ╱  单元测试（JUnit5）    ╲   （Controller/Engine/Pipe/Validator）
```

| 类型 | 工具 | 要求 |
| --- | --- | --- |
| 单元 | JUnit5 + MockK + Turbine + `kotlinx-coroutines-test` | 核心逻辑 ≥ 60% 行覆盖 |
| 数据 | Room in-memory + `MigrationTestHelper` | 每次 schema 变更必测 |
| 规则 | MockWebServer + 脱敏 HTML fixture | 每条内置/示例规则 1 套 fixture |
| UI | `createComposeRule` + Robolectric | 关键页面有语义测试 |
| 截图 | Roborazzi | 阅读器 4 种状态基准图 |
| 性能 | `androidx.benchmark.macro` | 3 个场景，趋势记录 |
| 手工 | 真机矩阵 | 发布检查单（见 08 M7） |

测试数据红线：**不得提交任何漫画图片、完整章节 HTML、站点 Cookie**。fixture 必须是脱敏、最小化的结构片段（≤ 50KB），并把域名替换为 `example.com`。

## 6. CI/CD

### 6.1 PR 流水线（`.github/workflows/pr.yml`）

```yaml
on: [pull_request]
jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: gradle }
      - run: ./gradlew spotlessCheck detekt lint testDebugUnitTest assembleDebug --stacktrace
      - uses: actions/upload-artifact@v4
        with: { name: debug-apk, path: app/build/outputs/apk/debug/*.apk }
```

目标时长 ≤ 8 分钟（Gradle 缓存 + 并行）。`schedule` 每日跑一次 `assembleRelease` 防 R8 规则回归。

### 6.2 发布流水线（`.github/workflows/release.yml`）

触发 `push tags: v*`：

1. 校验 `versionName` 与 tag 一致；
2. `./gradlew :app:assembleRelease`（Secrets 注入 keystore）；
3. 生成 `sha256` 与 `THIRD-PARTY-NOTICES.md` 快照；
4. `softprops/action-gh-release` 上传 APK + 校验和 + CHANGELOG 段落；
5. 附「校验安装」说明。

需要的 Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

### 6.3 依赖与安全

- Renovate/Dependabot 每周 PR；大版本升级单独分支验证。
- `gitleaks` pre-commit + CI secrets 扫描。
- `./gradlew dependencyUpdates` 每里程碑一次。
- 第三方许可证清单由 Gradle 任务生成（`licenseReport`），提交到 `THIRD-PARTY-NOTICES.md`；新增依赖必须补许可证且与 GPL-3.0 兼容。

## 7. 发布清单

- [ ] `CHANGELOG.md` 更新；版本号与 tag 一致。
- [ ] P0 需求验收清单（02 §6）逐条通过。
- [ ] 迁移测试、规则 fixture、Compose 测试全绿。
- [ ] Macrobenchmark 对比上一版无回退（±10%）。
- [ ] 全新安装 / 覆盖升级 / 降级安装（拒绝）各验证一次。
- [ ] 权限清单无新增（`aapt dump permissions`）。
- [ ] 包体 ≤ 15MB；`release` 无调试日志、无 `StrictMode`。
- [ ] 免责声明与许可证页可访问；投诉入口可达。
- [ ] Release Note 含：变更、已知问题、安装校验方式。

## 8. 文档与决策

- 文档与代码同 PR；行为变更未更新文档视为未完成。
- 架构决策走 ADR（追加到 03 §11），格式：背景 → 决策 → 后果。
- 规则规范变更必须同时改 05 + Schema + 示例，并在 CHANGELOG 标注 `rules:`。
- 每季度复查：依赖大版本、规则生态健康度、未关闭的高优 issue。

## 9. AI 协作约定（AGENTS.md 摘要）

- AI 生成代码同样适用本规范；提交前必须本地跑 `spotlessCheck detekt testDebugUnitTest`。
- 禁止 AI 生成或补全「真实站点解析规则」「漫画内容」「绕过 DRM」相关代码。
- AI 修改文档时不得改变 ADR 结论；如需变更，先提 issue 讨论。
- 所有 AI 产出需人工复核：安全边界、许可证、错误处理、测试有效性。
