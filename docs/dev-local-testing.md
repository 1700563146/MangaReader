# 本地测试指南（无需自行开模拟器）

本项目所有**能跑在 JVM 上的测试**都已经这样设计；只有涉及 Android native 库
（QuickJS、Room、WorkManager）的测试才需要设备。

## 1. 纯 JVM 测试（最常用，秒级，不占用内存）

在 Android Studio 里：

- 右键 `core/source/src/test/` 目录 → **Run 'Tests in ...'**
- 或使用已配好的运行配置：**core:source 单测（纯 JVM，无需模拟器）**

命令行：

```bash
./gradlew :core:source:test :core:model:test
```

覆盖：规则编译与校验、选择器/管道、Rhino 沙箱、HTTP 客户端、
索引验签、撤销列表、源库索引解析（含真实 venera 索引 fixture）。

## 2. 需要设备的测试（QuickJS 能力、Room 迁移）

这两类依赖 native so 或 Android 框架，JVM 跑不了。

### 推荐：用真机（比模拟器省资源得多）

1. 手机开启「开发者选项 → USB 调试」
2. 数据线连电脑，`adb devices` 能看到设备
3. Android Studio 右上角设备下拉里选你的手机
4. 打开 `QuickJsCapabilityTest.kt` → 右键 **Run**

> 真机跑测试的内存占用远低于模拟器，笔记本不会卡死。

### 备选：Android Studio 内置模拟器（按需启动，用完关闭）

如果确实没有真机：

1. **Device Manager** → 选一个 AVD → **Cold Boot**
2. 建议把 AVD 内存调小：Edit → Show Advanced Settings →
   RAM 设为 2048MB，Internal Storage 2048MB
3. 跑完测试立刻 **Stop**（不要常驻）

### 备选：云端设备（完全不占本机资源）

- Firebase Test Lab（你已有账号）：
  ```bash
  # 需要先装 gcloud CLI
  gcloud firebase test android run \
    --type instrumentation \
    --app   core/source-cjs/build/outputs/apk/debug/source-cjs-debug.apk \
    --test  core/source-cjs/build/outputs/apk/androidTest/debug/source-cjs-debug-androidTest.apk \
    --device model=Pixel2,version=28 \
    --use-orchestrator
  ```
- 或用 Firebase 网页控制台上传两个 APK。

## 3. 当前待验证的关键问题

`QuickJsCapabilityTest.reportAsyncBehaviour()` 会打印：

```
Promise.then 同步可见 = ?
async 调用返回     = ?
await 后同步读取   = ?
连续 evaluate 读取 = ? / ? / ?
```

**判读标准**：

| 结果 | 含义 | 下一步 |
| --- | --- | --- |
| `await 后同步读取 = ok` | QuickJS 会自动驱动 microtask | 直接实现 CJS 源引擎 |
| `await 后同步读取 = PENDING` | microtask 未驱动 | 需在宿主用 Kotlin 实现 Promise 调度 |

后者是大概率情况（QuickJS 的 JNI 层未暴露 `JS_ExecutePendingJob`，已用
`llvm-objdump -T` 确认原生库无该符号）。若如此，方案是：

1. 在 JS 侧注入一个 `__hostCall__` 桥，把 `await networkGet(...)` 改写为
   「注册回调 → 宿主异步执行 → 完成后回填」；
2. 或在 Kotlin 侧维护一个 Promise 队列，每次 `evaluate` 后循环 drain。

## 4. 跑完把输出发我

把 `reportAsyncBehaviour` 的 4 行输出贴给我即可，我据此决定 CJS 引擎的实现方式。
