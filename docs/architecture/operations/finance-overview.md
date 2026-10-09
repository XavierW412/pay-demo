# 支付资金总览与 Airwallex 结算数据边界

## 目标与口径

运营后台首页用一处页面查看支付项目已记录的收款、退款和异常，按 CNY/USD 和日期范围分别统计。它提供运营观察入口，不替代星辙业务账本、Airwallex Wallet 对账或银行流水核对。

- 支付成功金额只计 `payment_provider=AIRWALLEX` 且本地状态已核实为 `SUCCEEDED` 的支付单。
- `LOCAL_SIMULATOR` 仅用于本地业务联调，不进入资金总览。
- 退款待确认、处理中、执行成功金额分别统计。退款的 `SUCCEEDED` 具有 Airwallex API 的退款对账语义，但仍须通过结算批次/报告追踪实际资金记录。
- 支付核查差异按每笔支付最近一次核查快照计数；退款异常包括 `BLOCKED` 和 `NEEDS_REVIEW`。
- 各币种独立统计，不做无来源换算。
- 日期按中国标准时间（Asia/Shanghai）自然日解释；接口最多查询 93 天，结束日期不能晚于当前日期。

## 当前接口

```http
GET /api/v1/operations/finance/overview?from=2026-10-01&to=2026-10-09
```

接口要求已登录运营账号，只有读取能力。响应包含日期范围、币种摘要和 `settlementSourceStatus`。当前状态为 `AIRWALLEX_SETTLEMENT_REPORT_NOT_CONNECTED`；`settlementAmount` 与 `settlementFee` 返回 `null`，页面明确显示“待接入”。

支付成功金额不能减去退款成功金额来估算结算净额。结算净额还受结算批次、退款、争议、预留、手续费、币种转换和服务商时间窗口影响。

## 后续 Airwallex 数据接入

Airwallex 官方提供[结算批次 API](https://www.airwallex.com/docs/api/2022-09-09/finance/settlements)列出指定结算日期和币种的批次，以及[Settlement Records API](https://www.airwallex.com/docs/api/2022-09-09/payments/settlement_records)按批次或 Payment Intent 查询明细。官方[报告说明](https://www.airwallex.com/docs/payments/manage-payments/reporting)区分支付活动、结算报告、费用报告与金融交易对账报告。批次金额、费用及交易行项目需以实际账户权限和接口返回字段为准。

接入前需要用星辙持有的沙盒账户核实 API 权限、返回字段、结算币种、结算时间、报告行项目和可关联的 Payment Intent/Refund 标识。之后新增只读同步适配器、分页/游标、导入去重、批次和行项目数据、逐笔匹配及未匹配异常；数据来源与报告时间需可追溯。当前项目没有查询真实结算 API，也未从账号导入财务记录。

## 验证范围

当前代码可静态构建并查询本地支付、退款和核查表；尚未执行数据库迁移或页面联调。沙盒报告是否可用、生产报表权限和实际到账均未验证。
