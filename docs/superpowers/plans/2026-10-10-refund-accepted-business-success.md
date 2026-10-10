# 退款在 Airwallex `ACCEPTED` 时完成业务口径实施计划

> **实施要求：** 按下列任务逐项实施；退款状态与通知契约先后端对齐，再更新本地模拟星辙和运营页面。不得把 Airwallex 原始状态、星辙业务结果和渠道结算状态重新合并成一个字段。

**目标：** Airwallex 退款进入 `ACCEPTED` 后立即通知星辙业务退款成功，同时继续追踪 `SETTLED` 作为后续渠道结算记录。

**架构：** 支付项目持久化原始服务商状态并派生业务结果；通知同时携带业务结果、支付项目处理状态和 Airwallex 原始状态。模拟星辙按业务结果更新申请状态，运营后台分开展示业务成功与渠道结算进度。

**技术栈：** Java 21、Spring Boot、PostgreSQL、Flyway、React、TypeScript、TanStack Query、pnpm。

**设计规格：** [`docs/superpowers/specs/2026-10-10-refund-accepted-business-success-design.md`](../specs/2026-10-10-refund-accepted-business-success-design.md)

## 全局约束

- 星辙负责业务退款审批；支付项目只能执行星辙已批准且支付侧管理员确认的指令。
- `RECEIVED` 仍是处理中；`ACCEPTED` 对星辙业务结果映射为 `SUCCEEDED`；`SETTLED` 只更新渠道结算事实；`FAILED` 是失败；未知结果保持待核实。
- 原始 Airwallex 状态必须保存并出站，不以业务结果覆盖。
- 状态变更通过现有 outbox 可靠投递；同一状态变化产生稳定且唯一的事件编号。
- 累计退款不得超过已核实可退金额；待核实状态继续占用额度。
- 不新增手工改退款状态、业务审批或生产模拟操作入口。
- 依照当前开发约束，不新增或运行自动化测试；完成后执行相关模块构建和 `git diff --check`。

## 评审重点

1. 重复查询结果和 outbox 重试只能触发一次星辙业务成功处理；归属任务 1、2。
2. `ACCEPTED` 到 `SETTLED` 期间业务结果保持成功、渠道结算未完成；归属任务 1、3。
3. 旧版 `SUCCEEDED` 输入必须按 `SETTLED` 兼容处理；归属任务 1。
4. `NEEDS_REVIEW`、请求超时和 `FAILED` 不得被误显示为业务成功；归属任务 1、2、3。
5. `ACCEPTED` 退款不能从待处理金额消失而又不出现在业务成功/待结算统计中；归属任务 3。

---

### 任务 1：支付服务状态归一与退款通知契约

**文件：**
- 修改：`backend/src/main/java/com/startrail/payment/refund/application/RefundStateService.java`
- 修改：`backend/src/main/java/com/startrail/payment/refund/application/RefundNotificationWriter.java`
- 修改：`backend/src/main/java/com/startrail/payment/refund/infrastructure/RefundJdbcRepository.java`（保持服务商原始状态可读并确保状态推进更新）
- 修改：`backend/src/main/java/com/startrail/payment/refund/application/RefundStatusReconciliationJob.java`
- 修改：`backend/src/main/java/com/startrail/payment/operations/application/OperationsRefundStatusQueryService.java`
- 修改：`backend/src/main/java/com/startrail/payment/refund/application/RefundExecutionService.java`（退款执行遥测结果与 `ACCEPTED` 业务成功口径）

**接口：**
- 消费：`ProviderRefund(status, id, requestId, paymentIntentId, amount, currency)`。
- 产出：退款通知载荷 `businessOutcome`、`executionStatus`、`providerStatus` 及退款关联编号。

- [x] **步骤 1：** 检查 Airwallex 退款创建响应、`RefundStatusReconciliationJob` 和 `OperationsRefundStatusQueryService` 调用 `RefundStateService.applyProviderResult` 的全部路径，确认旧 `SUCCEEDED` 与新版 `SETTLED` 兼容入口；退款状态当前由 API 查询推进，本任务不引入 Airwallex 退款 Webhook。
- [x] **步骤 2：** 将业务结果映射集中在退款状态服务：`ACCEPTED` 和 `SETTLED`/旧 `SUCCEEDED` 对星辙输出 `businessOutcome=SUCCEEDED`；`RECEIVED` 输出 `PROCESSING`；`FAILED` 输出 `FAILED`；`NEEDS_REVIEW` 输出 `PENDING`。
- [x] **步骤 3：** 保持 `provider_status` 为 Airwallex 原始值；`SETTLED` 的本地处理终态仍保持现有 `SUCCEEDED` 兼容状态，避免破坏现有轮询终止语义。
- [x] **步骤 4：** 扩充退款通知载荷，包含 `businessOutcome`、`executionStatus`、`providerStatus`；事件编号至少包含退款编号、业务结果、处理状态和原始服务商状态，保证 `ACCEPTED` 与后续 `SETTLED` 可分别投递，而相同状态重试仍幂等。
- [x] **步骤 5：** 保持 `ACCEPTED` 可被自动及人工核实；`SETTLED` 仍终止重复核查；不得因星辙业务结果成功而停止渠道状态核实。
- [x] **步骤 6：** 只读检查所有状态分支与通知唯一键，确认 `ACCEPTED -> SETTLED` 不会再次执行退款或覆盖失败/待核实分支。

### 任务 2：模拟星辙接收与业务结果历史

**文件：**
- 修改：`backend/src/main/java/com/startrail/payment/simulator/application/MockPaymentEventService.java`
- 修改：`backend/src/main/java/com/startrail/payment/simulator/application/MockRefundInstructionService.java`
- 修改：`backend/src/main/java/com/startrail/payment/simulator/interfaces/MockRefundRequestResponse.java`
- 创建：`backend/src/main/resources/db/migration-local/V124__add_refund_status_layers.sql`（仅本地模拟星辙）
- 修改：`mock-xingzhe/src/modules/mock-xingzhe/MockXingzheOrderPage.tsx`

**接口：**
- 消费：任务 1 的新退款通知字段，同时兼容旧通知中只有 `status` 的本地历史数据。
- 产出：模拟星辙退款申请行及事件历史中的业务结果、支付项目状态、服务商状态。

- [x] **步骤 1：** 在仅本地环境执行的 `V124__add_refund_status_layers.sql` 为申请表增加可空 `business_outcome`、归一服务商状态和原始服务商状态，为事件表增加可空业务结果、处理状态及两种服务商状态；不改写历史记录，旧行由前端兼容展示。
- [x] **步骤 2：** 验签后验证三层状态枚举和值的组合；验证失败时拒绝通知且不写退款状态。
- [x] **步骤 3：** 用 `eventId` 幂等记录状态事件；同一退款 `ACCEPTED` 与 `SETTLED` 事件都保留，但只有第一次 `businessOutcome=SUCCEEDED` 将业务成功事件落入一次性业务处理标记。
- [x] **步骤 4：** 更新退款申请行：`ACCEPTED` 时显示“业务退款成功 / 渠道已接受”；`SETTLED` 到达时业务结果不变，更新渠道结算状态和时间线；旧记录仍按原字段读取。
- [x] **步骤 5：** 模拟星辙详情的业务审批状态独立显示，退款状态使用中文标签；详细信息仍展示原始服务商枚举便于排障。
- [x] **步骤 6：** 检查重复事件和乱序到达时，较旧事件不能回退业务成功或渠道结算状态。

### 任务 3：运营后台状态文案与资金摘要

**文件：**
- 修改：`backend/src/main/java/com/startrail/payment/operations/application/OperationsFinanceOverviewQueryService.java`
- 修改：`frontend/src/modules/operations/api.ts`
- 修改：`frontend/src/modules/operations/OperationsFinanceOverviewPage.tsx`
- 修改：`frontend/src/modules/operations/OperationsWorkbenchPage.tsx`
- 修改：`frontend/src/modules/operations/format.ts`
- 修改：`frontend/src/modules/operations/OperationsTransactionListPage.tsx`
- 修改：`frontend/src/modules/operations/OperationsTransactionDetailPage.tsx`

**接口：**
- 消费：支付服务退款本地状态和保留的 `provider_status`。
- 产出：按币种分别显示业务退款成功总额（`ACCEPTED + SETTLED`）、渠道待结算分项（`ACCEPTED`）和渠道已结算分项（`SETTLED`）；后两项不与成功总额相加；完整结算净额与手续费仍显示未接入。

- [x] **步骤 1：** 盘点 `refundSucceededAmount`、`refundProcessingAmount` 和退款状态筛选的现有语义及全部 UI 消费点。
- [x] **步骤 2：** 响应提供 `refundBusinessSucceededAmount`（状态为 `ACCEPTED` 或 `SUCCEEDED`）、`refundChannelPendingSettlementAmount`（状态为 `ACCEPTED`）和 `refundChannelSettledAmount`（原始 `provider_status` 为 `SETTLED` 或旧版 `SUCCEEDED`）。后两者为业务成功总额的互斥分项，不再把 `refundSucceededAmount` 当唯一成功口径；同步更新全部响应消费者。
- [x] **步骤 3：** 将 `ACCEPTED` 文案改为“业务退款成功 · 渠道已接受”；将本地 `SUCCEEDED` 文案改为“业务退款成功 · 渠道已结算”；待处理与待核实文案保持独立。
- [x] **步骤 4：** 在列表、详情和工作台展示两层状态；运营资金净额、手续费仍不从退款状态估算，继续显示完整结算报告未接入。
- [x] **步骤 5：** 核对空值、零值、仅 `ACCEPTED`、`ACCEPTED` 后 `SETTLED`、`FAILED` 与 `NEEDS_REVIEW` 的展示条件。

### 任务 4：架构文档、接口契约与构建核验

**文件：**
- 修改：`docs/architecture/refund/refund-execution.md`
- 修改：`docs/architecture/operations/operations-console.md`
- 修改：`docs/architecture/integration/payment-interface-contracts.md`
- 修改：`docs/product/product-spec.md`（只补充状态口径，不改业务责任边界）
- 修改：`docs/superpowers/specs/2026-10-10-refund-accepted-business-success-design.md`（补记实现后的字段与限制）

- [x] **步骤 1：** 同步正式提议契约字段、Airwallex 原始状态映射、星辙业务结果含义、事件编号幂等规则和迁移兼容策略。
- [x] **步骤 2：** 搜索所有退款成功文案、状态映射、统计查询和状态枚举引用，消除“`ACCEPTED` 仍在处理中”与“`SETTLED` 才是业务成功”的陈旧说明。
- [x] **步骤 3：** 执行 `./backend/mvnw -f backend/pom.xml -DskipTests compile`、`pnpm --filter frontend build`、`pnpm --filter mock-xingzhe build` 与 `git diff --check`；不启动数据库或服务，不新增/运行测试。
- [x] **步骤 4：** 报告静态构建结果及未完成的 Airwallex 沙盒、真实星辙契约验收，不把本地构建描述成端到端通过。
