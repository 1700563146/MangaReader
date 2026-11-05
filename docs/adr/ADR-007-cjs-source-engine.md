# ADR-007：源格式与 CJS 引擎预留

- 状态：已接受（2026-11）
- 关联：`docs/05-source-rule-spec.md`、`docs/10-legal.md`、`docs/02-requirements.md` F-206

## 背景

社区存在成熟的第三方源库（例如 venera 的 `venera-configs`），其源文件是 **CJS 模块**：

```js
class CopyManga extends ComicSource {
    name = "…"
    key = "copy_manga"
    version = "1.4.3"
    async search(keyword, page) { … }
}
```

特征：`class` 继承、类字段、`async/await`、getter、`static` 字段，并依赖宿主注入的
`this.networkGet` / `loadData` / `saveData` / `Convert.hmacString` 等 API。

而本项目 v1.0 的规则引擎是 **JSON 声明式规则 + Rhino 脚本钩子**。

### 实测结论（Rhino 能力边界）

用 `RhinoCapabilityProbeTest` 在 Rhino 1.7.15 与 1.9.2（当前最新）上分别探测：

| 特性 | 1.7.15 | 1.9.2 |
| --- | --- | --- |
| let/const、箭头函数、模板字符串 | ✅ | ✅ |
| 解构、Map/Set、Promise、generator | ✅ | ✅ |
| 展开运算符 | ❌ | ✅ |
| 可选链、空值合并 | ❌ | ✅ |
| **`class` / `class extends` / 类字段** | ❌ | **❌** |
| **`async` / `await`** | ❌ | **❌** |
| `for-of` | ❌ | ❌ |

Rhino 从未实现 ES6 `class` 与 `async/await`，**升级版本无法解决**。因此 venera 源在当前引擎上
无法运行——这是硬约束，不是工作量问题。

## 决策

1. **v1.0 不实现 CJS 执行引擎**。JSON 规则仍是唯一可运行的源格式。
2. **但先把格式抽象做出来**，避免未来重构：
   - `SourceKind`（`RULE` / `CJS`）+ `source.source_kind` 列（Room v3）。
   - `SourceRef` 改为密封接口，`RuleSourceRef` / `CjsSourceRef` 各自承载载荷；
     引擎按子类型分派，遇到不支持的类型抛 `SourceFailure(RULE_INVALID)` 而非静默失败。
3. **CJS 源可以导入但不启用**：`SourceManager.installCjs()` 登记元数据与脚本，
   `enabled = false`，UI 明确提示「需要 ES2020 引擎，当前版本无法运行」。
   这样用户导入的源库不会白丢，未来接引擎后可直接启用。
4. **源库索引由用户自填 URL**，不内置任何索引地址（零内置原则）。解析器同时兼容
   venera 数组格式与带签名的 JSON 规则索引格式。

## 后果

**正面**

- 用户现在就能浏览并「登记」社区源库，清楚知道哪些能跑、哪些不能。
- 引入 CJS 引擎时不需要再动数据库、`SourceRef` 与 UI 层，只需新增一个 `SourceEngine` 实现。
- 格式判断基于内容（`extends ComicSource` / `class`）而非扩展名，索引写错也能正确分流。

**负面 / 代价**

- `SourceRef` 由 data class 改为密封接口，所有调用点需用 `RuleSourceRef` 或 `asRule()`。
- 数据库多一次迁移（v2→v3），需保证迁移测试覆盖。
- 用户可能误以为「已安装 = 能用」，因此提示文案必须显式说明「未启用」。

## 未来接入 CJS 的路径（不在 v1.0 范围）

1. 引入 **QuickJS**（`app.cash.quickjs:quickjs-android`，0.9.2）或等价 ES2020 引擎。
   Rhino 不可行（见上表）。
2. 实现 `ComicSource` 宿主基类：把 `networkGet/Post`、`loadData/saveData/deleteData`、
   `loadSetting`、`Convert.*` 映射到 Kotlin 侧，并保留现有 host 白名单与限流。
3. `async/await` → 协程桥接：QuickJS 无 Promise 调度，需要宿主驱动 microtask 队列。
4. 安全：CJS 能力比 JSON 规则大得多，必须重新评估沙箱边界（禁止文件/设备访问、
   限制请求目标、超时与内存上限），并更新 `docs/06`、`docs/10`。

在完成上述工作前，CJS 源一律保持「已登记、未启用」。
