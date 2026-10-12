# 退款 Webhook 改造实施计划

> **执行方式：** 由当前会话逐项实施；不拆分给其他执行者。

**目标：** Airwallex 退款 Webhook 到达后尽快核实并更新项目状态，可靠通知本地模拟星辙，后台页面近实时显示进度，同时保留轮询补查。

**架构：** 复用现有 Airwallex Webhook 验签、事件持久化去重、异步处理器、退款状态事务及星辙 outbox。新增退款事件分支，按退款编号查询服务商当前状态并与本地 `request_id`、Payment Intent、金额、币种核对；前端仅在非终态时短间隔重新读取。

**技术栈：** Java 21、Spring Boot、PostgreSQL、React、TypeScript、TanStack Query、pnpm。

**设计依据：** [退款 Webhook 与星辙状态通知设计](../specs/2026-10-12-refund-webhook-design.md)

## 全局约束

- 沿用 Airwallex HMAC 签名校验和事件编号幂等处理。
- 不让 Webhook 事件本身越过 `RefundStateService` 的退款对象核验及状态推进规则。
- `ACCEPTED` 表示退款业务结果成功、Airwallex 已接受；`SETTLED` 表示 Airwallex 侧对账结算状态；两者都不表示已观察到用户银行入账。
- 星辙退款通知通过现有 `notification_outbox` 及签名发送器；本地模拟星辙是本次端到端目标。
- 保留退款轮询，Webhook 与轮询复用同一状态核验路径。
- 不增加运行依赖，不提交代码，不运行测试套件。

## 评审重点

- 同一 Airwallex 事件重复发送时只处理一次，退款通知不会产生重复副作用。
- `refund.settled` 先于 `refund.accepted` 到达时，不允许后到的旧状态使退款回退。
- Webhook 到达时本地尚未持久化服务商退款编号时，能按稳定 `request_id` 找到退款记录。
- 退款关联记录暂时找不到或 Airwallex API 暂时不可用时，Webhook 任务可重试，轮询仍可补查。
- 退款进入 `SETTLED` 或 `FAILED` 后，后台自动刷新停止；`ACCEPTED` 时继续观察渠道结算状态。

---

### 任务一：处理 Airwallex 退款 Webhook

**文件：**
- 修改：`backend/src/main/java/com/startrail/payment/integrations/airwallex/webhook/AirwallexWebhookEventService.java`
- 修改：`backend/src/main/java/com/startrail/payment/refund/infrastructure/RefundJdbcRepository.java`
- 复用：`backend/src/main/java/com/startrail/payment/refund/infrastructure/AirwallexRefundGateway.java`
- 复用：`backend/src/main/java/com/startrail/payment/refund/application/RefundStateService.java`
- 复用：`backend/src/main/java/com/startrail/payment/integrations/airwallex/webhook/AirwallexWebhookEventProcessor.java`

**接口：** `AirwallexWebhookEventService` 注入退款查询网关、退款仓储和 `RefundStateService`。事件处理分派 `payment_intent.*` 与四种退款事件；后者在数据库事务外通过 `AirwallexRefundGateway.retrieve(providerRefundId)` 获取当前 Refund。

- [x] **步骤一：** 在 `RefundJdbcRepository` 增加按 `provider_refund_id` 和 `provider_request_id` 唯一查找退款的方法，供 Webhook 关联服务端记录。
- [x] **步骤二：** 扩展 Webhook 事件服务，收到 `refund.*` 后依据对象编号查询 Airwallex Refund；优先按服务商退款编号关联本地记录，未关联时按服务商 `request_id` 兜底。
- [x] **步骤三：** 只有唯一找到本地记录时才调用 `RefundStateService.applyProviderResult`；未找到或查询失败时抛出可重试错误，不把事件标记为已处理。
- [x] **步骤四：** 通过 `RefundStateService` 校验服务商退款编号、Payment Intent、`request_id`、金额和币种，并复用当前事务更新及退款通知 outbox。
- [x] **步骤五：** 只处理四种已知退款事件；未知退款事件安全记录处理，不执行支付/退款副作用。保持外部 Airwallex 查询在事务外，状态变化和 outbox 写入仍由状态服务原子提交。
- [x] **步骤六：** 运行 `./backend/mvnw -f backend/pom.xml -DskipTests package` 编译后端，检查编译结果。

### 任务二：运营后台显示近实时退款进度

**文件：**
- 修改：`frontend/src/modules/operations/OperationsTransactionDetailPage.tsx`
- 修改：`frontend/src/modules/operations/OperationsTransactionListPage.tsx`
- 修改：`frontend/src/modules/operations/format.ts`
- 修改：`mock-xingzhe/src/modules/mock-xingzhe/MockXingzheOrderPage.tsx`

**行为：** 详情页和交易列表查询只在含 `SUBMITTING`、`RECEIVED`、`ACCEPTED` 或 `NEEDS_REVIEW` 退款时每 5 秒重新读取；`SETTLED` 对应本地 `SUCCEEDED`，与 `FAILED` 一样停止自动刷新。隐藏页面时停止后台刷新。

- [x] **步骤一：** 给详情页退款查询增加条件 `refetchInterval`；从当前退款结果判断是否仍需刷新。
- [x] **步骤二：** 给交易列表查询增加相同的条件刷新，只在当前页存在非终态退款时启动。
- [x] **步骤三：** 调整退款状态说明文字，明确 `ACCEPTED` 是 Airwallex 已接受退款，`SETTLED` 是 Airwallex 侧已结算；用户银行入账需以银行/支付渠道为准。
- [x] **步骤四：** 在模拟星辙退款记录中使用相同语义文案，保留现有每 4 秒刷新事件/申请记录的行为。
- [x] **步骤五：** 运行 `pnpm --filter frontend build`、`pnpm --filter frontend lint` 和 `pnpm --filter mock-xingzhe build`，检查类型、构建和代码规范。

### 任务三：同步文档与配置说明

**文件：**
- 修改：`docs/architecture/refund/refund-execution.md`
- 修改：`docs/operations/deployment.md`（如现有 Airwallex Webhook 配置段可直接补充）
- 修改：`docs/superpowers/specs/2026-10-12-refund-webhook-design.md`

- [x] **步骤一：** 记录退款 Webhook 接收、查询核验、状态入库、星辙 outbox 通知和轮询补查之间的关系。
- [x] **步骤二：** 补充 Airwallex 控制台需订阅 `refund.received`、`refund.accepted`、`refund.settled`、`refund.failed`；继续使用现有公开 HTTPS Webhook 地址与独立 Webhook 密钥。
- [x] **步骤三：** 明确星辙模拟接收端和生产星辙契约边界，以及 HTTP 已送达与业务订单已更新的差别。
- [x] **步骤四：** 执行 `git diff --check`，并分别检查后端构建、前端构建和格式检查输出；记录无法由本地验证的 Airwallex 沙盒事件和真实星辙接入事项。

## 完成标准

- Airwallex 退款 Webhook 能触发服务端 Refund 查询与状态核验，并产生正确的本地退款阶段及 outbox 通知。
- 重复、乱序、未关联及服务商暂时不可用的事件不会造成重复退款或错误终态，并能通过重试/轮询恢复。
- 本地模拟星辙能接收和去重退款状态事件；运营页面能自动显示变化并在终态停止频繁查询。
- 后端与前端构建、代码差异检查通过；沙盒真实投递和生产验收范围单独说明。
