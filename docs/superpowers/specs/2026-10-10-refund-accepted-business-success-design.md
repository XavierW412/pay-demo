# Airwallex `ACCEPTED` 与退款业务成功状态设计

## 1. 目标与依据

本设计把星辙业务退款结果、Airwallex 退款处理状态和 Airwallex 退款结算记录分开表达，避免把“业务退款已成功”误解成“结算报告已入账”或“用户银行卡账单已显示”。

Airwallex 当前退款状态包括 `RECEIVED`、`ACCEPTED`、`SETTLED`、`FAILED`。`RECEIVED` 表示退款请求已创建；`ACCEPTED` 表示付款方式提供方接受了退款，官方说明多数情况下代表退款已在 Airwallex 侧有效成功；`SETTLED` 表示退款进入服务商对账文件并显示在 Airwallex 结算报告/钱包记录；`FAILED` 表示退款失败。旧 API 曾用 `SUCCEEDED` 表示现行 `SETTLED`。Airwallex 无法确认退款何时显示在付款人的账户。

参考：[Airwallex 退款说明](https://www.airwallex.com/docs/payments/manage-payments/refunds)、[退款 API（2025-02-14）](https://www.airwallex.com/docs/api/2025-02-14/payments/refunds)、[Refund Webhooks](https://www.airwallex.com/docs/payments/reference/payments-webhooks)。

## 2. 产品口径

- 星辙仍独占业务退款审批权；支付项目仅执行已批准指令并报告可核实的服务商事实。
- 星辙可将 Airwallex `ACCEPTED` 定义为“业务退款成功”：此时退款已被付款方式提供方接受，业务侧无需等待之后的 `SETTLED` 才结束退款业务流程。
- `SETTLED` 是后续的服务商结算/对账进度，保留为独立事实，不回退或重复改变星辙业务退款成功状态。
- `RECEIVED` 仅表示请求创建成功，星辙业务仍显示“退款处理中”。
- `FAILED` 表示退款执行失败，支付项目通知星辙；是否重试、再次审批或联系用户由星辙决定。
- `NEEDS_REVIEW` 表示结果未知，不能通知为成功或失败，也不能用新退款编号重发。
- 付款人账户最终入账时间不由 Airwallex 状态 `ACCEPTED` 或 `SETTLED` 保证，本项目不声称已确认该时间。

## 3. 状态分层与通知契约

继续保留本地支付项目状态和 Airwallex 原始退款状态，不把二者压成一个枚举：

| Airwallex 原始状态 | 支付项目处理状态 | 星辙业务结果 | 星辙通知要求 |
|---|---|---|---|
| `RECEIVED` | `RECEIVED` | `PROCESSING` | 通知“退款请求已创建，处理中” |
| `ACCEPTED` | `ACCEPTED` | `SUCCEEDED` | 立即通知“退款业务成功”；同时保留原始状态 `ACCEPTED` |
| `SETTLED` | 本地内部维持兼容状态或映射后的完成态；原始状态保留 `SETTLED` | 保持 `SUCCEEDED` | 通知结算进展，不重复触发业务退款完成动作 |
| `FAILED` | `FAILED` | `FAILED` | 通知退款失败，由星辙决定后续业务处理 |
| 未知/无法查询 | `NEEDS_REVIEW` | `PENDING` | 通知待核实，不得伪造成功/失败 |

出站事件仍使用 `refund.status_changed`，增补明确的分层字段：

```json
{
  "eventId": "稳定且可区分退款状态变化的事件编号",
  "eventType": "refund.status_changed",
  "refundRequestId": "星辙退款指令编号",
  "refundId": "支付项目退款编号",
  "businessOrderId": "星辙业务订单号",
  "paymentOrderId": "支付项目支付单编号",
  "businessOutcome": "SUCCEEDED",
  "executionStatus": "ACCEPTED",
  "providerStatus": "ACCEPTED",
  "rawProviderStatus": "ACCEPTED",
  "status": "ACCEPTED",
  "amount": "111.00",
  "currency": "CNY",
  "occurredAt": "ISO-8601 时间"
}
```

`businessOutcome` 供星辙业务流程使用；`executionStatus` 表示支付项目处理阶段；`providerStatus` 是归一后的 Airwallex 状态；`rawProviderStatus` 保存 Airwallex 实际返回值。旧字段 `status` 暂作为 `executionStatus` 的兼容别名。星辙以 `eventId` 去重；同一退款在 `ACCEPTED` 与 `SETTLED` 两次状态变更必须产生不同事件编号。最终字段名和兼容策略仍属本地提议契约，接入真实星辙前需双方确认。

## 4. 页面和统计口径

- 模拟星辙：审批结果单列“已批准”；收到 `ACCEPTED` 后业务退款显示“退款成功”，旁注“Airwallex 已接受”；收到 `SETTLED` 后只增加“渠道已结算”时间线记录。
- 支付运营后台：同时显示“业务退款结果”和“Airwallex 原始状态”。`ACCEPTED` 可显示“业务退款成功 · 渠道已接受”；`SETTLED` 显示“业务退款成功 · 渠道已结算”。
- 金额摘要分开显示“业务退款成功金额”（`ACCEPTED` 与 `SETTLED`）、“渠道待结算金额”（仍为 `ACCEPTED`）和“渠道已结算金额”（`SETTLED`）。后两项是业务退款成功金额的组成部分，页面明确它们是分项而不是可相加总额。待确认、待提交、已接收和结果未知仍归入处理中/待核实，不归入成功金额。
- Airwallex `SETTLED` 事件不等于已接入完整 Airwallex Settlement Report；实际结算净额、费用和批次仍以正式报告接入为准。不得据此推算到账金额。
- 前端不直接展示裸 `ACCEPTED`/`SETTLED` 作为唯一文案；详情可保留原始枚举以供排障。

## 5. 兼容、幂等和异常处理

- 旧版 Airwallex `SUCCEEDED` 作为 `SETTLED` 兼容输入；出站契约统一表达 `providerStatus=SETTLED`，必要时保留 `rawProviderStatus=SUCCEEDED` 供审计。
- 同一 Airwallex 状态重复 webhook 或查询结果幂等处理，不重复触发星辙业务退款成功动作。
- `ACCEPTED` 后到达 `SETTLED` 只更新服务商状态和渠道结算统计，不重复扣减退款预留，不重复创建退款。
- 若服务商后续明确返回与已通知结果矛盾的状态，支付项目保留原事件、写入异常并通知星辙复核；不静默覆盖业务状态。具体是否允许 `ACCEPTED -> FAILED` 以生产账户可观察到的 Airwallex 合法状态转换和官方合同为准。
- 超时、查无记录、金额/币种/Payment Intent/`request_id` 不匹配均保持待核实，不转成 `SUCCEEDED`。

## 6. 当前实现差距和影响范围

实现后：Airwallex `ACCEPTED` 保持本地处理状态并立即通知业务成功；定时与人工核实继续查询；`SETTLED` 归一为本地终态 `SUCCEEDED`，仅通知渠道结算进展。模拟星辙分别保存业务结果、支付项目处理状态、归一状态与原始服务商状态。对应影响范围包括：

1. 退款通知 DTO、稳定事件编号和本地模拟星辙接收/去重存储。
2. 退款持久化状态映射、自动核实终态判断及退款预留释放规则。
3. 运营后台退款详情、列表标签、异常队列和按状态统计。
4. 模拟星辙退款历史与已批准退款页面的状态文案。
5. 退款架构、接口契约、产品状态说明和本地联调验收记录。

实现必须保持：只有星辙已批准且支付侧人工确认后才创建 Airwallex 退款；累计退款不超过已核实可退金额；未知结果继续占用金额；通知通过 outbox 重试；不新增人工改状态为成功的入口。

实现已落地于支付服务通知、自动/人工核实、模拟星辙本地历史和运营后台；模拟星辙字段由本地环境专用迁移 `V124` 增加，历史记录不回填。Java 21 编译、运营前端和模拟星辙前端构建已通过。未运行自动化测试、数据库迁移或沙盒端到端验收；真实星辙字段仍待甲方确认。

## 7. 验收标准

1. 星辙批准但支付项目尚未调用 Airwallex 时，不能显示业务退款成功。
2. Airwallex 返回 `RECEIVED` 时，星辙显示处理中，运营后台保留“已创建/待处理”。
3. Airwallex 返回 `ACCEPTED` 时，支付项目通知星辙业务结果 `SUCCEEDED`，原始服务商状态仍显示 `ACCEPTED`。
4. `ACCEPTED` 后收到 `SETTLED` 时，产生可区分的状态通知和历史事件，但不重复执行业务退款完成动作。
5. `FAILED` 和 `NEEDS_REVIEW` 不会被误显示为退款成功。
6. 重复 webhook、定时核实、手动刷新以及 outbox 重试不会产生重复退款或重复业务处理。
7. 资金总览清楚区分渠道已接受退款与渠道已结算退款；未接入完整结算报告时仍不显示推算到账额。

## 8. 决策记录

采用“`ACCEPTED` 时星辙业务退款成功，`SETTLED` 作为后续渠道结算留痕”的分层设计。此决策反映当前与用户确认的本地产品口径；真实星辙正式接口仍需在联调前确认其业务状态字段与失败补偿规则。
