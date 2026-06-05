# 测试报告

## 1. 结论

- 结论：通过
- 自动化测试（接口级）：8 通过 / 8 总计（0 失败，0 错误）
- 自动化测试（UI 级）：3 通过 / 3 总计（0 失败，0 错误）
- 关键链路：管理员导入用户与设置次数、用户三要素+短信验证码登录、购物车结算拆单、后台发货、用户端物流入口与轨迹接口、兑换数据导出

## 2. 测试环境

- 时间：2026-06-03（Asia/Shanghai）
- 系统：macOS
- Java：11（Temurin 11）
- 数据库：H2 内存库（自动化测试 create-drop）
- 短信：Mock（返回 debugCode）

## 3. 自动化测试清单

- [AuthControllerTest](file:///Users/novick/Desktop/jf/points-exchange-system/src/test/java/com/novick/points/web/AuthControllerTest.java)
  - shouldLoginBySmsCode：三要素校验通过 → 获取 debugCode → 验证码登录成功
- [MallServiceTest](file:///Users/novick/Desktop/jf/points-exchange-system/src/test/java/com/novick/points/service/MallServiceTest.java)
  - shouldCreateExchangeOrderAndConsumeQuota：单品下单扣减次数
  - shouldRejectOrderWhenQuotaInsufficient：次数不足拦截
  - shouldCheckoutCartAndConsumeQuota：购物车结算拆单 + 扣减次数
  - shouldRejectCheckoutWhenQuotaInsufficient：结算次数不足拦截
- [FullFlowE2ETest](file:///Users/novick/Desktop/jf/points-exchange-system/src/test/java/com/novick/points/e2e/FullFlowE2ETest.java)
  - shouldRunFullFlow：管理员登录 → CSV 导入用户 → 设置次数 → 用户短信登录 → 结算下单 → 后台发货
- [AdminReportExportTest](file:///Users/novick/Desktop/jf/points-exchange-system/src/test/java/com/novick/points/web/admin/AdminReportExportTest.java)
  - shouldExportReportXlsx：导出兑换报表（已兑换汇总 + 未兑换名单）
- [OrderTrackingTest](file:///Users/novick/Desktop/jf/points-exchange-system/src/test/java/com/novick/points/web/app/OrderTrackingTest.java)
  - shouldGetTrackingFromMockedClient：物流轨迹接口返回（使用 mock 的快递100客户端，避免外部依赖）

## 4. 运行方式与结果

### 4.1 接口级（JUnit/MockMvc）

```bash
cd /Users/novick/Desktop/jf/points-exchange-system
mvn test
```

结果摘要：

- BUILD SUCCESS
- Tests run: 8, Failures: 0, Errors: 0, Skipped: 0

### 4.2 UI 级（Playwright）

```bash
cd /Users/novick/Desktop/jf/points-exchange-system
npm i
npm run e2e:install
npm run e2e
```

结果摘要：

- 3 passed
- 覆盖：用户登录与下单、后台发货与用户端物流入口、后台导入用户与设置次数、导出报表下载

## 5. 手工测试用例

- 详见：[test-cases.md](file:///Users/novick/Desktop/jf/docs/test-cases.md)

## 6. 已知限制与建议

- 当前短信为 Mock：生产需对接短信平台并关闭 debugCode 回显
- UI 自动化为本地验证：UI 用例基于本地内存库，适合回归；上线前建议再跑一遍针对线上地址的 smoke 用例
- 物流轨迹 UI 未直连真实快递100：当前通过接口级测试验证轨迹接口行为，避免外部依赖导致不稳定
- 并发与幂等未压测：例如多人同时抢同一库存、重复提交结算等，生产建议加幂等控制与压测
