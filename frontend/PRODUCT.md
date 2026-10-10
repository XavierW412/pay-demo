# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- 星辙支付运营员工：查询交易，核实支付与退款状态，跟进通知和对账差异，并留下操作记录。员工分为 `STAFF_ADMIN` 与 `STAFF_OPERATOR`。
- 项目所有者：使用 `OWNER_READONLY` 只读查看所有交易与员工操作记录。
- 付款用户只会经过本项目前端衔接进入 Airwallex 托管收银台；本项目不收集银行卡或钱包资料。

## Product Purpose

本项目连接星辙业务订单与星辙持有的 Airwallex 商户账户，负责建立支付记录、核实支付与退款状态、通知星辙并支持运营核查。运营后台帮助员工快速查清每笔交易的状态、关联信息和下一步处理动作。

## Positioning

这是服务单一星辙客户的独立支付接入与运营工具。星辙负责用户、业务订单、退款审批和服务开通；本项目负责支付通道、已批准退款的执行、状态核实、可靠通知和留痕。

## Operating Context

- 员工从运营后台处理待确认退款、退款结果待核实、支付状态差异和通知失败。
- 每笔记录关联星辙业务订单、本项目支付单及 Airwallex 交易标识。
- 项目所有者查看完整记录但没有写操作权限。
- 本地开发可使用模拟星辙和 Airwallex 沙盒；模拟器不属于生产运营后台。

## Capabilities and Constraints

- 首期支付服务商为 Airwallex；用户付款使用 Airwallex 托管收银台。
- 星辙批准退款后，`STAFF_ADMIN` 复核并确认支付项目提交；支付项目不审批业务退款。
- 支付、退款、通知和结算分别记录。未知结果不能被标成成功或失败；超时结果不可通过重复提交新退款来恢复。
- `STAFF_ADMIN` 可管理员工邀请；`STAFF_OPERATOR` 可查询并处理授权范围内的运营任务；`OWNER_READONLY` 只能读取。
- Airwallex 结算报告与星辙真实账本尚未接入。没有来源的数据必须明确显示未接入，不得估算或伪造到账金额。
- 生产后台不提供测试付款、模拟交易、测试账号或切换沙盒的入口。
- 运营后台可改版和补齐必要查询、状态核查及账号管理能力；支付核心与模拟星辙不属于本次后台重做范围。

## Evidence on Hand

- 产品基线：`docs/product/product-spec.md`
- 运营后台现状：`docs/architecture/operations/operations-console.md`
- 交易列表与详情：`docs/product/transaction-list.md`、`docs/product/transaction-detail.md`
- 退款边界：`docs/architecture/refund/refund-execution.md`
- 资金口径：`docs/architecture/operations/finance-overview.md`
- 星辙与 Airwallex 的字段、签名和异常恢复边界：`docs/architecture/integration/payment-interface-contracts.md`
- 后台由现有 React、TypeScript、Tailwind CSS 项目承载。

## Product Principles

- 先让员工知道哪件事需要处理，再展示汇总数字。
- 交易状态必须能追溯到来源、时间、关联编号和核实结果。
- 支付、退款、通知及结算各自独立显示。
- 高风险操作要复核、明确结果并留下审计记录。
- 只展示系统实际取得的数据，不把缺失资料包装成确定结论。
