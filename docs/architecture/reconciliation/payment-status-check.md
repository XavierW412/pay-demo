# 支付状态核查与结算对账边界

## 目标和证据范围

本模块定时或由星辙员工主动对本地支付单逐笔执行 Airwallex Payment Intent 服务端查询，保留查询时间、本地快照、Airwallex 返回字段和差异代码。查询不改写支付单，不把未知状态推断为成功，也不自动重发通知或退款。支付、通知、退款、结算分别是不同状态。

本期完成的是**支付状态核查**。Airwallex 的 Payment Intent 查询提供付款金额、币种、`merchant_order_id` 和状态；它不能证明资金已结算。Airwallex 的结算报告包含结算批次和相关付款、退款等项目，财务交易对账报告覆盖账户金融交易。生产账户、结算报告权限、星辙正式订单与回执接口均未取得，因此本期接口明确返回 `checkType=PAYMENT_STATUS_CHECK`、`settlementReconciled=false`，不标记“已对账”或“已结算”。

官方依据：[Payment Intents API](https://www.airwallex.com/docs/api/payments/payment_intents)、[支付报表说明](https://www.airwallex.com/docs/payments/manage-payments/reporting)、[Financial Reports API](https://www.airwallex.com/docs/api/finance/financial_reports)、[结算与对账说明](https://www.airwallex.com/docs/payments/about-airwallex-payments/settle-into-your-airwallex-balance)。

## 数据流

本模块保存逐笔核查证据，不改写支付状态。它的财务/差异核查周期、全局查询配额和失败退避保持原配置；独立的 `PaymentStatusRefreshScheduler` 才负责未决支付状态更新，兜底周期为 10 秒。两种任务不能混为一谈。

1. 调度每分钟选取最多十笔到期支付单；新建支付单经过五分钟后纳入扫描。员工可对单笔支付单立即发起核查，不受自动任务的下次计划时间限制。
2. 数据库租约防止同一支付单在多个实例并发查询；数据库单行计数器限制所有实例合计每六十秒最多十次 Airwallex 查询。若该支付单已有核查正在执行，手动操作会收到冲突提示；服务商全局查询限额达到时会收到限流提示。查询失败按五分钟起的递增间隔重试，最长六十分钟。
3. 对有关联 Payment Intent 的支付单调用现有 Airwallex 网关 `GET /api/v1/pa/payment_intents/{id}`。查询后重新读取本地支付单，比较本地与远端的 Payment Intent ID、业务订单号、金额、币种、映射状态和原始状态。未知远端状态单独标记。没有 Payment Intent ID 的本地单记录为无法查询。
4. 从运营查询服务取得该支付单最新的通知 outbox 状态。若本地支付已成功但最新通知尚未显示 `DELIVERED`，记录 `NOTIFICATION_UNDELIVERED`。该状态仅代表本项目收到通知 HTTP 成功响应；正式星辙订单是否已处理，仍须正式回执或订单查询接口核实。
5. 每次结果追加到 `payment_reconciliation_check`，保留差异代码和非敏感失败类别；`payment_reconciliation_cursor` 记录下次运行时间。某笔远端查询失败只影响这一笔。任务重试不会生成支付、退款或通知副作用。

## 接口和权限

| 接口 | 用途 | 权限 |
| --- | --- | --- |
| `POST /api/v1/operations/transactions/{paymentOrderId}/reconciliation-checks` | 立即执行一次单笔支付核查并返回快照 | 员工管理员、员工操作员；需要现有会话与 CSRF |
| `GET /api/v1/operations/transactions/{paymentOrderId}/reconciliation-checks` | 分页查询该单核查历史，单页最多一百条 | 已登录员工及只读所有者 |
| `GET /api/v1/operations/reconciliation/differences` | 分页查询每笔支付单最新一次非一致结果，单页最多一百条 | 已登录员工及只读所有者 |

两个查询接口均接受 `limit` 和 `offset`，按核查时间与记录编号倒序返回数组；返回数量少于 `limit` 表示已到最后一页。

`outcome` 为 `MATCH`、`DIFFERENCE`、`QUERY_FAILED`、`NOT_CHECKABLE`。`MATCH` 只说明此次 Payment Intent 字段和本地记录一致且本地成功通知已投递；不说明星辙业务订单状态或资金结算一致。人工触发动作写现有运营审计。

## 异常与后续接入

- Airwallex 超时、HTTP 错误或响应缺字段记为 `QUERY_FAILED`，不覆盖本地状态；失败类别不保存原始响应、认证头或密钥。若数据库不可用，无法持久化本次快照，租约到期后可再试。
- 外部查询与本地快照之间仍有并发变化窗口。保存的是查询时的观察结果，可由下一轮核查纠正，运营处理差异时应查看当前支付详情。
- 租约十分钟；Airwallex 网关需要明确连接和读取超时，才能防止网络请求长期占用调度线程。此配置在共享网关处理。
- 正式星辙接口提供后，增加业务订单查询与通知业务回执核验；当前只比对本地订单引用与 Airwallex `merchant_order_id`。
- 生产结算报告权限和字段确认后，另建结算批次导入、唯一交易关联、退款/手续费/汇兑差额和报告版本处理。不能把 Payment Intent 金额与净结算金额直接等同。

## 验证边界

本期需由集成方确认 Airwallex 沙盒返回字段及查询限额、星辙真实订单/回执契约、生产账户报表可用性。静态编译只能确认代码结构，不能证明远端核查或财务对账通过。
