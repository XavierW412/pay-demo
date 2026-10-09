# 本地支付场景与模拟星辙订单历史计划

**状态：已确认并实施。**

**目标：** 本地模拟付款能表达成功、资金不足失败、处理中、等待超时/结果未知和超时后补到最终结果；模拟星辙提供订单列表、详情和完整的支付/退款历史，所有业务状态由支付项目的签名通知更新。

**设计依据：** [支付软件、模拟星辙、退款复核与资金总览边界设计](../specs/2026-10-09-separated-simulator-refund-finance-design.md)。本计划依赖[支付后台与本地模拟星辙应用拆分计划](2026-10-09-separate-simulator-application.md)。

## 现状与选择

- `MockSimulatedPaymentResult` 目前只有 `SUCCEEDED`、`FAILED`；`LocalPaymentOutcomeSimulator` 只接受 `AWAITING_PAYMENT` 后写终态。
- `mock_xingzhe_order` 保存当前快照；支付事件表只存 event ID 与接收时间，没有可读时间线查询。模拟接口只有按业务订单号查询，没有列表与历史详情 API。
- 不模拟可消费钱包余额，也不收集银行卡信息。资金不足是本地明确选择的失败原因，不代表 Airwallex 拒绝原因。
- 超时表示“结果未知”，进入待核实状态；不会释放支付单关联、自动认定失败或生成第二笔支付。补到最终状态必须经过仅本地的模拟控制，再走现有支付状态事务和 outbox。

## 涉及文件

- `backend/src/main/java/com/startrail/payment/simulator/domain/MockSimulatedPaymentResult.java`、`LocalPaymentOutcomeSimulator.java`、`MockBusinessOrderService.java`：扩展结果场景及允许的状态转换。
- `backend/src/main/java/com/startrail/payment/payment/application/PaymentStatusApplicationService.java`、`PaymentOrderProviderUpdateTransaction.java`：只为 `LOCAL_SIMULATOR` 开放模拟处理中、超时待核实和一次性最终结果转换；不改变 Airwallex 状态映射。
- `backend/src/main/java/com/startrail/payment/simulator/interfaces/MockXingzheController.java` 及请求/响应 DTO：公开本地场景操作、订单分页列表、详情与事件历史；列表字段含业务订单号、服务、金额币种、来源、支付状态、服务状态和时间。
- `backend/src/main/resources/db/migration-local/`：新增本地迁移，扩充模拟事件/订单的场景和历史字段；不把模拟业务表加入公共生产迁移。
- `backend/src/main/java/com/startrail/payment/simulator/application/MockPaymentEventService.java`、持久化仓储：按事件 ID 去重并保留支付状态变化和退款通知时间线；订单状态只由有效通知推进。
- 新模拟应用的订单列表、订单详情和结果控制组件/API；支付状态以服务端查询响应为准。
- `docs/architecture/payment/payment-flow.md`、`docs/product/product-spec.md`、`README.md`、路线图：记录场景语义、模拟数据标识和本地边界。

## 状态规则

| 模拟操作 | 允许的当前状态 | 目标状态 | 结果 |
| --- | --- | --- | --- |
| 资金不足 | `AWAITING_PAYMENT` | `FAILED` | 不开通服务，原因标记为本地模拟 |
| 付款处理中 | `AWAITING_PAYMENT` | `PROCESSING` | 等待；不可创建重复支付 |
| 等待超时/未收到结果 | `AWAITING_PAYMENT` 或 `PROCESSING` | `REQUIRES_REVIEW` | 明确提示待核实，不认定成功或失败 |
| 超时后补到成功/失败 | `REQUIRES_REVIEW` 或 `PROCESSING` | `SUCCEEDED` 或 `FAILED` | 仅本地控制入口可执行一次，继续走状态通知 |
| 成功 | `AWAITING_PAYMENT` 或 `PROCESSING` | `SUCCEEDED` | 模拟星辙收到签名通知后开通服务 |

任何终态不能覆盖；重复事件不重复开通服务；浏览器关闭、刷新或返回地址不改变结果。

## 实施步骤

1. 定义本地场景枚举、失败原因和合法状态迁移；检查现有支付状态聚合与 outbox 的终态约束，不影响 Airwallex webhook 映射。
2. 扩展仅 `local` profile 可调用的场景 API；入参只表达场景，不接收付款金额、币种或目标业务状态。
3. 为模拟星辙订单和事件添加本地迁移与分页查询，基于现有支付/退款通知事件生成时间线；以 event ID 保证接收幂等。
4. 在独立模拟应用新增“服务选购 / 我的订单 / 订单详情”视图，支持按订单号和状态筛选；页面醒目标识“本地模拟”或“Airwallex 沙盒”。
5. 页面只调用后端结果控制与查询接口；状态变化后重新读取订单详情，不由前端直接改状态。
6. 同步产品、架构、README 和路线图，明确资金不足、处理中和超时均为开发演练，不能代表真实付款体验。

## 验收与验证

- 付款成功经通知后只开通一次；资金不足不开放服务；处理中保持等待；超时为待核实；补到结果后状态与模拟星辙历史一致。
- 一个业务订单不能重复创建支付单；刷新、重复结果提交、重复通知和终态覆盖都不能产生不一致状态。
- Airwallex 沙盒托管付款不出现模拟控制按钮；模拟来源不会被标为 Airwallex 收款。
- `pnpm build:web`、`pnpm build:mock-xingzhe`、Java 21 下 `./backend/mvnw -f backend/pom.xml -DskipTests package` 与 `git diff --check`。
- 不新增或运行自动化测试；不启动服务、不模拟真实资金，不调用真实 Airwallex。

## 风险

- `REQUIRES_REVIEW` 已服务于真实支付核查语义，UI/API 必须注明本地模拟原因，不能让模拟“超时”覆盖已有真实支付。
- 公共 Flyway 与 `migration-local` 版本空间分开；本地新增结构只能放在本地迁移目录。
- 真实支付通知与退款事件的现有保存字段有限，设计历史 API 时先区分“已存事实”和“由现有记录推导的展示”，不伪造未保存的尝试记录。
