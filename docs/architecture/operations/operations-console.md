# 运营后台首版：账号、交易记录与异常操作

## 产品边界

运营后台读取支付项目记录，首页为只读资金总览并区分支付成功、退款执行、结算报告和异常状态。员工能核实 Airwallex 状态、重试失败通知、添加异常备注；`STAFF_ADMIN` 还可确认已核对通过的退款并提交 Airwallex。仅在本地沙盒环境，六小时临时测试员也有相同的运营处理权限。所有写操作验证角色并写审计。项目所有者账号可看全部记录和员工操作，服务端拒绝其所有写操作。后台不提供手工改成支付成功、业务退款审批、模拟付款或测试交易的入口。

星辙正式员工身份系统尚未提供。本项目先用独立账号：配置引导一名星辙员工管理员和一名所有者；员工管理员创建一次性邀请链接，员工自行设置账号密码。邀请链接由管理员自行交付，软件不擅自发送消息。正式接入后可适配星辙身份源。

## 认证与权限

- Spring Security 使用服务端会话 Cookie，密码以 BCrypt 摘要入库。登录和后台写操作使用 CSRF 令牌；支付创建和 Webhook 继续由各自签名保护，不依赖运营会话。
- `OWNER_READONLY`：可查询所有交易和审计，不可触发刷新、重试、备注、邀请或退款。
- `STAFF_ADMIN`：员工操作、查询、邀请，以及确认已通过支付核对的退款执行。
- `STAFF_OPERATOR`：员工操作和查询，不可邀请或确认退款执行。
- `TEMP_TESTER`：甲方临时测试账号，6 小时到期；可执行与员工管理员相同的运营动作，包括确认已获星辙批准且支付侧核验通过的退款，但不能邀请、撤销邀请、管理员工账号或生成其他临时账号。创建仅允许在 `local` 配置且 Airwallex 指向沙盒时进行；密码只在成功响应中返回一次，数据库仅存 BCrypt 摘要。
- 生产会话 Cookie 使用 Secure、HttpOnly、SameSite=Lax，必须同站 HTTPS 部署。开发环境允许本机 HTTP。

## 交易与操作

### 资金总览

`GET /api/v1/operations/finance/overview?from=YYYY-MM-DD&to=YYYY-MM-DD` 仅登录后可读，日期范围最多 93 天。页面及 API 按 CNY/USD 分开返回 Airwallex 支付单的成功金额/笔数、退款待确认/处理中/执行成功金额，以及最近支付状态核查差异和退款拦截/待核实数量。本地 `LOCAL_SIMULATOR` 不参与汇总。

实际结算入账金额和手续费字段当前为空，数据源状态为 `AIRWALLEX_SETTLEMENT_REPORT_NOT_CONNECTED`。未接入结算报告时不从支付成功额和退款额推算到账；用户界面明确提示“待接入”。支付成功、退款 API 的 `SUCCEEDED` 和 Airwallex 实际结算批次是分别记录的事实。

列表基于 `payment_order`，按创建时间倒序分页，支持星辙订单号或本项目支付单号搜索、支付状态筛选。支付通知状态只从付款事件的 `notification_outbox` 读取，退款通知分开显示；退款状态栏按待确认、待核实、拦截、处理中、终态的优先级显示最需关注的一笔，不代表最近创建的一笔；详情列出该支付单的全部退款指令。尚无退款指令时只说明“本项目未收到指令”，不能推断星辙没有业务退款请求。结算报表尚未接入时显示“待接入”，不能伪造“已结算”。详情展示关联编号、金额、币种、Airwallex Payment Intent 编号、支付与通知状态、退款记录、核查历史、处理备注及审计。

运营页面在可见时自动重新读取当前页面已打开的查询：列表与工作台每 10 秒一次，交易详情每 3 秒一次；切到其他浏览器标签页时暂停，并提供手动刷新。该刷新只读取支付服务数据库，不会因此主动查询 Airwallex。支付和退款的服务商状态仍由 Webhook、服务端定时核实或员工点击即时核实更新。

员工主动查询 Airwallex 时仍校验 Payment Intent ID、业务订单号、金额、币种后更新状态；查询频率受服务端限制。通知异常队列显示可重试的 `RETRY` 项和到期未投递的 `PENDING` 项。通知重试只针对服务端允许重试的记录，不能重新发送已送达项。备注只留痕，不改变资金状态。所有动作记录操作者、目标、时间、结果和安全的简短说明。

## 数据与外部条件

Flyway 创建运营账号、邀请、备注和审计表。启动时按环境变量首次创建固定所有者与首位星辙员工管理员，已有账号不覆盖密码。邀请令牌只保存哈希，设置账号后立即失效。真实星辙员工目录和角色授权规则尚待甲方确认；本地账号系统是当前软件可运行的独立方案。

退款申请与业务审批由星辙负责。运营后台的管理员确认只发生在星辙已批准并通过支付侧核对之后；运营后台不批准业务退款，也不允许跳过星辙批准步骤。逐笔支付核查会记录差异，但 Airwallex 结算报告与星辙正式账本仍待接入。

## 后台重做后的接口与页面结构

后台首页为待办优先的运营工作台，待办从真实异常查询接口读取，CNY/USD 资金摘要单独调用财务接口；页面故障彼此隔离。工作台队列包含待确认退款、退款待核实/异常、支付核查差异和通知失败，每项带支付单、业务订单、金额币种及原记录状态。近期活动只读取通知 outbox、退款更新时间和员工审计，不补造支付历史。

现有运营查询接口：

- `GET /api/v1/operations/workbench?from&to`：返回日期范围、四类队列计数与最近五条明细、可追溯近期活动。待办计数与异常队列使用相同查询口径；财务摘要由页面独立调用 `/finance/overview`，防止一类查询失败遮蔽另一类数据。
- `GET /api/v1/operations/exceptions?type&page&size`：`type` 仅允许 `REFUND_CONFIRMATION`、`REFUND_REVIEW`、`PAYMENT_DIFFERENCE`、`NOTIFICATION_FAILURE`；返回服务端分页及精确关联编号。所有资金相关队列排除 `LOCAL_SIMULATOR`。
- `GET /api/v1/operations/transactions?query&status&currency&provider&refundStatus&notificationStatus&from&to&page&size`：后端白名单校验筛选值，按创建时间和支付单 UUID 稳定排序；搜索可匹配业务订单号、本项目支付单、Payment Intent 和关联退款编号。日期按 `Asia/Shanghai`，最多 93 天。
- `GET /api/v1/operations/audit?from&to&actor&action&outcome&businessOrderId&paymentOrderId&page&size`：只读服务端分页审计；员工/邀请动作可以没有支付单关联。保留旧 `limit` 参数作为过渡兼容。
- `GET /api/v1/operations/team/accounts?page&size`、`GET /api/v1/operations/team/invitations?page&size`：员工与邀请目录。账号响应含 `expiresAt`（永久账号为 `null`），不含密码。邀请响应不含一次性令牌及其摘要。
- `POST /api/v1/operations/team/temporary-accounts`：仅 `STAFF_ADMIN` 在本地沙盒环境可调用；无请求体，生成固定 6 小时的 `TEMP_TESTER`。成功响应一次性包含 `accountId`、`username`、`initialPassword`、`role`、`expiresAt`；不要记录、持久化或通过日志转发 `initialPassword`。
- `DELETE /api/v1/operations/team/invitations/{invitationId}`：只撤销尚未使用且未过期的邀请。
- `PATCH /api/v1/operations/team/accounts/{accountId}/active`：请求体为 `{ "active": true|false }`；仅员工管理员可停用或恢复普通员工，或提前停用临时测试账号。临时账号停用后不能恢复，需重新生成；服务端在每个运营请求中重新检查状态与到期时间，并注销停用或过期会话。过期账号保留记录供审计。

### 退款即时核实

`POST /api/v1/operations/refunds/{refundId}/refresh` 只允许 `STAFF_OPERATOR` 与 `STAFF_ADMIN`，只用于 `SUBMITTING`、`RECEIVED`、`ACCEPTED` 或 `NEEDS_REVIEW` 的 Airwallex 退款。已有服务商退款编号时只调用按编号查询；没有编号时只按原 Payment Intent 和已保存的服务商 `request_id` 查找。它不调用退款创建接口，也不生成新 `request_id`。

服务端响应包含退款/业务/支付关联编号、金额、币种、本地与服务商状态、最近查询时间、星辙通知状态及 `outcome`。结果区分 `VERIFIED`、`NOT_FOUND`、`MISMATCH`、`PROVIDER_UNAVAILABLE` 与 `NOT_SUPPORTED`。未找到记录或服务商不可用都不等同于退款失败，原额度继续保留；编号、原支付、金额或币种不一致时转为 `NEEDS_REVIEW` 并保留说明。所有结果写入员工审计，不记录 Airwallex 凭证或完整远端响应。

后台导航为运营工作台、交易、对账与异常、操作记录、员工与权限；`/operations/finance` 保留原资金详情书签。所有者仍只读，用户界面隐藏不能替代服务端授权。结算报告未接入前，到账与手续费保持空值并显示“待接入”，不使用支付减退款估算。

实现范围不改变星辙业务退款审批权，不增加后台测试支付入口，也不把模拟星辙页面混入支付运营后台。真实星辙员工身份、生产字段与访问策略仍待甲方确认。
