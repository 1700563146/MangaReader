# 贡献指南

感谢愿意参与。MangaReader 是一个**只做阅读器**的开源项目，贡献前请先明确边界。

## 1. 先读这三份

| 文档 | 为什么 |
| --- | --- |
| [docs/01-product.md](docs/01-product.md) | 项目定位与非目标，避免做出「内容聚合器」 |
| [docs/09-engineering.md](docs/09-engineering.md) | 分支、提交、CI、测试与发布规范 |
| [docs/10-legal.md](docs/10-legal.md) | 合规红线与 GPL 义务 |

## 2. 可以贡献什么

- 🐛 缺陷修复、性能优化、无障碍与国际化改进
- 🧩 规则引擎能力（新管道操作、校验更友好、脚本 API）
- 📄 文档、示例规则（**只能使用 `example.com`**）、规则 Schema 改进
- 🧪 测试与 fixture（脱敏、最小化，≤ 50KB，不含真实站点结构）
- 🎨 UI/交互改进（遵循 Compose 约定，附截图/录屏）

## 3. 不接受什么

- 任何漫画图片、章节正文、站点抓包数据、Cookie
- 指向具体站点的规则、域名清单、「内置源」
- 破解付费墙、去 DRM、自动登录/签到、批量抓取
- 与 GPL-3.0 不兼容的依赖或许可证声明
- 纯格式化/重排大 diff（请单独 PR 并说明）

## 4. 流程

1. 大改动先开 issue 对齐方案（避免白做）。
2. Fork → 分支 `feat/<slug>` / `fix/<slug>`。
3. 提交遵循 Conventional Commits；一个提交只做一件事。
4. 本地通过：

```bash
./gradlew spotlessApply detekt lint testDebugUnitTest
```

5. 提 PR，填写模板（含「怎么验证」「风险与回滚」「合规勾选」）。
6. Review 合并后由维护者统一发布。

## 5. DCO

所有提交需带 `Signed-off-by`：

```bash
git commit -s -m "feat(source): 支持 JSONPath 通配"
```

即表示你同意以 GPL-3.0 授权你的贡献，并确认有权提交该内容。

## 6. 规则贡献

规则**不进入主仓库**。请在独立仓库维护，并通过规则索引分发。提交到本仓库的示例必须是：

- 域名 `example.com`；
- 不含真实接口、Token、Cookie；
- 通过 `docs/schema/source-rule.schema.json` 校验。

## 7. 安全与合规问题

请勿公开披露可利用细节，走 [SECURITY.md](SECURITY.md) 的私密渠道。侵权投诉使用仓库 Issue 模板中的「侵权下架申请」。
