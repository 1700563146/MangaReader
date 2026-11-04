# 本地开发假源（仅测试）

只提供占位 HTML/PNG，用于验证「规则 → 搜索 → 详情 → 阅读」链路，**不含任何真实漫画内容**。

```bash
python tools/devserver/serve.py --port 8765
```

Android 模拟器通过 `http://10.0.2.2:8765` 访问。

Debug 构建内置一个安装入口（release 不包含）：

```bash
adb shell am start -n io.github.saonte.mangareader.debug/io.github.saonte.mangareader.DevInstallActivity
```

它会读取 `app/src/debug/assets/dev_rule.json`、替换为本地地址并安装规则；规则内容与
`docs/examples/demo-source.json` 的选择器一致。

> 该入口与规则仅存在于 `src/debug`，不会进入 release APK。
