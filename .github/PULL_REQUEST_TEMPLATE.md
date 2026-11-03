## 改了什么

<!-- 一句话说明改动；关联 issue 用 "Closes #123" -->

## 为什么

<!-- 问题背景、用户价值、与路线图/ADR 的关系 -->

## 怎么验证

<!-- 命令、截图、录屏、基准数据、真机型号 -->

```bash
./gradlew spotlessCheck detekt lint testDebugUnitTest
```

## 影响面

- [ ] 涉及数据库 schema（已附迁移测试）
- [ ] 涉及规则规范/Schema（已同步 docs/05 与示例）
- [ ] 涉及架构决策（已新增/修订 ADR）
- [ ] 涉及权限/网络策略（已在 PR 说明）
- [ ] 涉及用户数据（已说明回滚方式）

## 合规确认

- [ ] 未包含漫画图片、章节正文、站点抓包数据或 Cookie
- [ ] 未包含指向具体站点的解析规则（示例仅使用 `example.com`）
- [ ] 未引入与 GPL-3.0 不兼容的依赖（已核对 `THIRD-PARTY-NOTICES.md`）
- [ ] 未实现破解付费墙 / 绕过登录 / 去除 DRM 相关能力
- [ ] 提交包含 `Signed-off-by`（DCO）
