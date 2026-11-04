# M7 安全审查清单（v1.0.0）

审查对象：`app-release.apk`（versionName 1.0.0 / versionCode 10）。
方法：`dumpsys package` 实测 + 源码审计 + 依赖清单比对。

## 1. 权限（实测）

| 权限 | 来源 | 必要性 | 结论 |
| --- | --- | --- | --- |
| `INTERNET` | 应用声明 | 访问用户配置的源 | ✅ 必需 |
| `ACCESS_NETWORK_STATE` | 应用声明 | 网络状态与下载降级 | ✅ 必需 |
| `POST_NOTIFICATIONS` | 应用声明 | 下载进度通知（Android 13+ 运行时） | ✅ 可拒绝，拒绝后静默下载 |
| `FOREGROUND_SERVICE` | 应用声明 | 长下载前台服务 | ✅ 必需 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 应用声明 | Android 14+ 前台服务类型 | ✅ 必需 |
| `RECEIVE_BOOT_COMPLETED` | WorkManager 合并 | 重启后恢复未完成下载 | ⚠️ 由库引入，接受 |
| `WAKE_LOCK` | WorkManager 合并 | 下载期间持锁 | ⚠️ 由库引入，接受 |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX 合并 | 保护内部广播 | ✅ 自动生成，`exported=false` |

**未申请**（符合 docs/10 §5.1）：`READ_EXTERNAL_STORAGE`、`MANAGE_EXTERNAL_STORAGE`、`REQUEST_INSTALL_PACKAGES`、位置、通讯录、电话状态、相机、麦克风。

## 2. 组件暴露面

| 组件 | exported | 说明 |
| --- | --- | --- |
| `MainActivity` | true | 唯一 LAUNCHER 入口 |
| `SystemForegroundService`（WorkManager） | false | 合并声明，仅内部启动 |
| `InitializationProvider`（androidx.startup） | false | 移除 WorkManager 默认初始化 |
| 其他 Activity / Service / Receiver / Provider | 无 | `dumpsys` 未发现额外导出组件 |

Debug-only 组件 `DevInstallActivity` 仅在 `app/src/debug/`，release 包中不存在（实测 `pm dump` 命中 0 次）。

## 3. 明文流量（已知设计决定）

`android:usesCleartextTraffic="true"` 为全局允许。原因：用户导入的规则可能指向 http 站点，域名在运行时才可知，无法静态列入 `networkSecurityConfig`。

补偿措施（应用层门禁，已有实现）：

1. `RuleCompiler` 校验：`baseUrl` 为 `http://` 且未声明 `allowCleartext=true` → 拒绝导入。
2. `SourceHttpClient`：请求前校验 host 在规则 `hosts` 白名单内，重定向同样校验。
3. 规则索引用 Ed25519 签名 + 规则内容 SHA-256，防篡改。

风险接受记录：该项在 `docs/06-reader-kernel.md` 与 `docs/10-legal.md` 中均有说明；1.x 可评估改为「按源动态生成配置」的方案。

## 4. 数据与隐私（NFR-09）

| 检查项 | 结果 |
| --- | --- |
| 第三方分析/广告 SDK | 无（依赖清单无 Firebase/友盟/统计类） |
| 崩溃上报 SDK | 无（仅本地 logcat，用户可自行导出） |
| 后台自动上报 | 无（网络请求仅由用户操作或下载任务触发） |
| 账号体系 | 无 |
| 云端服务 | 无（无项目自有服务器） |
| 备份内容 | 不含图片、下载文件、Cookie、WebDAV 凭据（`BackupService` 明确排除） |
| 数据位置 | 应用私有目录；卸载即删除 |
| SAF 文件 | 只读扫描，不主动删除用户文件 |

## 5. 规则沙箱（NFR-08）

| 能力 | 状态 |
| --- | --- |
| 文件系统访问 | 禁止（Rhino 无 `java.io` 类白名单） |
| 设备信息 | 禁止（无 `android.*` 访问） |
| 任意网络 | 禁止（脚本无 socket；HTTP 由宿主按白名单发出） |
| 执行超时 | 3s，超时中断 |
| 输出大小 | 8MB 页上限、32MB 单图上限 |
| 语言级别 | ES5.1，禁用 Java interop |

单元测试覆盖：`RhinoScriptRuntimeTest`（超时、越权、异常路径）。

## 6. 依赖许可证（GPL-3.0 兼容性）

见 [THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md)。结论：全部为 Apache-2.0 / MIT / MPL-2.0 / CC0-1.0 / EPL，均与 GPL-3.0 兼容；无 GPL-2.0-only、无闭源 SDK。

## 7. 发布产物

| 项 | 结果 |
| --- | --- |
| Release APK 大小 | 2.7 MB（NFR-04 预算 ≤ 15MB） |
| 签名 | `MANGA_KEYSTORE_*` 提供时用正式密钥，否则回退 debug 签名并告警 |
| 混淆 | R8 minify + resource shrink，保留 Rhino / serialization / Hilt 规则 |
| 覆盖升级 | 同包名 `install -r` 成功，数据保留，冷启动 786ms，无崩溃 |
| Debug 代码泄漏 | 无（dev rule/server 仅在 debug source set 与 `tools/devserver`） |

## 8. 遗留风险与后续

| 项 | 等级 | 计划 |
| --- | --- | --- |
| 全局明文流量 | 中 | 1.x 评估按源生成 `networkSecurityConfig` 或改用系统代理白名单 |
| WorkManager 引入 `RECEIVE_BOOT_COMPLETED` / `WAKE_LOCK` | 低 | 保留（下载恢复所需） |
| 无正式签名密钥 | 高（发布前） | 维护者生成 keystore 并配置仓库 Secrets |
| 规则索引尚无正式公钥 | 中 | 建立社区索引仓库后内置公钥 |
