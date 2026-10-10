# 星辙批准退款的执行与核实

## 边界与契约状态

星辙负责退款申请、审批、部分退款顺序及用户政策。本模块只接收星辙已批准的指令。支付项目核对签名、原支付和金额后先记录指令；核对异常标记为 `BLOCKED` 并通知星辙，核对通过则标记 `PENDING_OPERATOR_CONFIRMATION`。支付后台 `STAFF_ADMIN` 可确认后调用 Airwallex；仅在本地沙盒联调环境，六小时有效的 `TEMP_TESTER` 也可执行该确认。两种角色都不能绕过星辙批准、支付核验或金额核对。其他角色只能查看；任何人都不能手工把退款标为成功。

以下星辙入站与出站字段均为**提议契约，待星辙确认**。当前可用于本地模拟，不代表真实星辙已接入。Airwallex 接口字段和状态依据[官方退款接口](https://www.airwallex.com/docs/api/payments/refunds)及[官方 API 变更记录](https://www.airwallex.com/docs/api/2022-09-09/changelog)。星辙商户账户是否开放退款、具体币种与方式仍需该账户在沙盒和生产分别核实。

## 提议入站指令

`POST /api/v1/integration/refunds`。请求头沿用付款集成的时间戳、随机数和签名算法，但使用独立的退款入站密钥。先对原始正文验签，再解析：

```json
{
  "refundRequestId": "星辙已批准退款指令的稳定唯一编号",
  "paymentOrderId": "本项目支付单编号",
  "businessOrderId": "星辙业务订单号",
  "amount": "10.00",
  "currency": "USD",
  "reason": "星辙提供的退款原因"
}
```

`refundRequestId` 重试时必须保持所有业务字段相同。星辙负责保证指令已批准；本模块不接受“待审批”或页面发起的指令。金额必须大于零且精度符合当前项目所支持币种的两位小数约束，币种、业务订单号和原支付单快照必须一致。接收指令时若原支付或 Airwallex 数据无法核对，记录为 `BLOCKED`，不调用退款 API；核对通过时只预留金额并等待管理员确认。

管理员确认时再次查询 Airwallex Payment Intent 并检查支付编号、状态、金额、币种、商户订单号及所有未失败退款的预留金额。事务内锁定退款和支付单、写入审计并原子地认领 `SUBMITTING` 状态，然后在事务外调用 Airwallex。结果未知时进入 `NEEDS_REVIEW` 并由服务端补查，不允许通过重复点击创建新退款。

## 提议通知

独立事件类型 `refund.status_changed`。提议载荷含 `eventId`、`eventType`、`refundRequestId`、`refundId`、`paymentOrderId`、`businessOrderId`、`providerRefundId`、`businessOutcome`、`executionStatus`、`providerStatus`、`rawProviderStatus`、`amount`、`currency`、`occurredAt`；旧字段 `status` 保留为 `executionStatus` 的兼容别名。沿用当前星辙通知地址、出站密钥及通知队列；星辙以 `eventId` 去重。真实接收地址、验签和字段需星辙确认。Airwallex `RECEIVED` 表示退款请求已创建，`ACCEPTED` 表示付款方式提供方已接受退款，`SETTLED` 表示退款已纳入对账/结算报告，`FAILED` 表示服务商拒绝。星辙业务结果在 `ACCEPTED` 时即为 `SUCCEEDED`；支付项目继续定时查询，直到取得后续渠道状态。`SETTLED` 映射为本地兼容终态 `SUCCEEDED`，不再次触发星辙业务退款完成；旧版 API 的 `SUCCEEDED` 对外归一为 `providerStatus=SETTLED`，并通过 `rawProviderStatus` 留存原值。`NEEDS_REVIEW` 表示本地无法判断，不能视为失败后立即重发新指令。

| Airwallex 状态 | `businessOutcome` | `executionStatus` / 兼容 `status` | `providerStatus` | `rawProviderStatus` |
| --- | --- | --- | --- | --- |
| `RECEIVED` | `PROCESSING` | `RECEIVED` | `RECEIVED` | `RECEIVED` |
| `ACCEPTED` | `SUCCEEDED` | `ACCEPTED` | `ACCEPTED` | `ACCEPTED` |
| `SETTLED` | `SUCCEEDED` | `SUCCEEDED` | `SETTLED` | `SETTLED` |
| 旧版 `SUCCEEDED` | `SUCCEEDED` | `SUCCEEDED` | `SETTLED` | `SUCCEEDED` |
| `FAILED` | `FAILED` | `FAILED` | `FAILED` | `FAILED` |

事件编号由退款编号、业务结果、支付项目处理状态和原始服务商状态组成；相同状态重复查询得到同一编号，`ACCEPTED` 与 `SETTLED` 可分别投递。对历史 `ACCEPTED` 退款，服务端重复查询也会确保新业务成功事件入队；outbox 唯一键负责去重。已接受退款遇到后续查询超时、结果不匹配或核查次数耗尽时，保留业务成功与 `ACCEPTED`，只记录核查异常；后续矛盾的 `FAILED` 结果不自动覆盖成功，需要人工复核。

## 数据与并发

每条指令保存本项目退款编号、星辙退款指令编号、支付单、金额、币种、服务商 `request_id`、服务商退款编号、状态及时间。星辙编号和服务商编号均唯一。服务商 `request_id` 使用本项目退款编号，长度不超过 64 字符。数据库约束保证金额为正。接收通过核验的指令时，在数据库事务中锁定支付单并预留退款金额；管理员确认时再次计算所有未失败退款金额，不得超过已核实的捕获金额。未知或处理中指令始终占用额度，防止并发超额。

本地提交先写 `SUBMITTING` 再在事务外请求 Airwallex。同一指令重复到达只返回现有记录，不发起第二笔退款。远端调用超时或本地写入响应失败时，保留占用额度，通过 Airwallex 列表按 Payment Intent 与稳定 `request_id` 找回；未找到时保持待核实，不使用新的 `request_id` 重发。服务商返回的退款编号、Payment Intent、金额、币种和 `request_id` 必须与记录一致。状态只沿已知顺序推进，未知状态保留待核实并告警。

## 核实与恢复

定时任务查询未终结退款：已知服务商退款编号时按编号查询，未知编号时分页列出原 Payment Intent 的退款并匹配 `request_id`。`FAILED` 释放额度；`ACCEPTED` 和本地 `SUCCEEDED` 均继续占用额度，直到退款不再产生未结清责任。Airwallex 文档说明最多可查询创建后两年的退款，因此超过查询期限且尚未核实的记录需要人工对账处理，不得自动假设失败。每次状态变化和通知入队在同一数据库事务；投递失败由现有通知队列重试。业务退款在 `ACCEPTED` 时成功；后续 `SETTLED` 表示进入 Airwallex 对账/结算报告，不等于已经接入完整结算批次或确认银行到账，资金总览仍以正式结算报告为准。

定时任务只将 Airwallex 请求阶段的异常记录为服务商暂不可用；本地数据库读取、状态写入或通知入队失败应保留为应用错误并写入日志，不能伪装成 Airwallex 查询失败。

运营详情的 `POST /api/v1/operations/refunds/{refundId}/refresh` 是人工即时核实入口。只查询已存在的 Airwallex 退款，不创建新退款；核对服务商退款编号、原 Payment Intent、原 `request_id`、金额与币种。结果未知或未找到时继续保留退款额度，防止重复退款。`last_checked_at` 单独记录最近查询时间，不覆盖退款状态更新时间 `updated_at`。本地 `ACCEPTED`、`SUCCEEDED` 或 `FAILED` 不因服务商暂不可用、未知状态或数据不匹配而回退；如发现矛盾应保留原状态并留下复核记录。运营详情响应明确分开展示 `businessOutcome`、本地处理状态、归一后的 `providerStatus` 和原始 `rawProviderStatus`。

## 实施与验证边界

实现包括已批准指令接收、服务端校验、金额预留、Airwallex 创建与查询、状态记录、通知入队及定时核实。星辙正式契约、商户账户权限、沙盒实际退款结果、生产验收与结算报表对账均未确认。此轮按任务要求不新增或运行测试；实际验收需覆盖重复指令、并发部分退款、远端超时、服务商失败、通知重试和沙盒状态推进。
