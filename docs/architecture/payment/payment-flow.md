# Airwallex 支付创建、结果核实与星辙通知

## 文档状态

- 状态：本地开发链路已实现；运行构建与手动接口核验进行中。
- 适用范围：本地模拟星辙 + Airwallex 沙盒的支付纵向链路。
- 外部边界：本地星辙接口是开发模拟契约；生产星辙接口待正式资料。沙盒付款尚未联调，因为本地没有配置账户凭据。
- 官方资料：Airwallex Payment Intent API 与 Webhook 文档于 2026-10-09 核对。

## 1. 责任和调用边界

- 星辙创建业务订单、确定金额/币种、决定服务开通和退款审批。
- 本服务创建内部支付单，调用 Airwallex Payment Intent API，验证支付结果，并可靠通知星辙。
- Airwallex 托管收银台负责收集付款资料。浏览器回跳只用于展示处理中，不作为支付成功依据。
- `simulator` 包和模拟迁移只加载在 `local` 配置；它们不是星辙真实业务 API。

## 2. 支付主链路

1. 本地模拟星辙依据服务端商品目录创建业务订单，经带 HMAC 签名的 HTTP 请求调用 `POST /api/v1/integration/payment-orders`。
2. 支付服务验证时间戳、随机数和原始 JSON 签名，校验金额/币种，按 `requestId` 与 `businessOrderId` 幂等保存支付单。
3. 支付服务使用服务端 Client ID/API Key 获取访问令牌，再调用 `POST /api/v1/pa/payment_intents/create`。请求映射金额、币种、`merchant_order_id` 和 `request_id`。
4. 内部 API 返回自身的 checkout 地址。浏览器通过短期不透明令牌读取付款上下文，再使用 Airwallex.js 进入托管收银台。Client ID/API Key 不发给浏览器；`client_secret` 仅从 Airwallex 服务端查询后短暂返回付款页面。
5. Airwallex Webhook 使用原始请求体、`x-timestamp`、`x-signature` 和 Webhook secret 做 HMAC-SHA256 校验。时间戳按毫秒解析，验签在 JSON 解析前执行。
6. 验签通过后按 Airwallex 事件 `id` 唯一落库并快速返回 HTTP 200。后台处理器查询 `GET /api/v1/pa/payment_intents/{id}` 当前状态，并验证 Payment Intent ID、业务订单号、金额、币种与内部支付单关联。
7. 只有服务端读取并核实的状态才更新内部支付单。支付状态通知写入 outbox，由后台任务签名发送；本地模拟星辙按事件编号去重，只在状态为 `SUCCEEDED` 时模拟开通服务。

## 3. 状态、事件和数据库

| 数据表 | 用途 | 环境 |
| --- | --- | --- |
| `payment_order` | 星辙业务订单与支付服务订单、Airwallex Payment Intent 的关联和当前支付状态 | 全环境 |
| `checkout_access_token` | 短期付款上下文令牌的哈希和过期时间 | 全环境 |
| `provider_webhook_event` | 验签后保留的事件最小字段（事件 ID、名称、Payment Intent ID）、处理状态、失败原因和下次重试时间；不持久化完整 Webhook 正文或 `client_secret` | 全环境 |
| `notification_outbox` | 待发送星辙通知、投递状态、失败原因和重试时间 | 全环境 |
| `mock_xingzhe_order` | 本地示例商品和业务订单 | 仅 local |
| `mock_xingzhe_payment_event` | 本地模拟星辙已接收的唯一支付事件 | 仅 local |

迁移版本采用单调递增序列：公共迁移 `V1`—`V4`、`V102`—`V103`；local 专属迁移 `V100`—`V101`。生产 profile 只使用公共迁移位置，不装配模拟器。后续新增迁移必须从 `V104` 起按全局序号递增，不能因某个 profile 跳过文件而重用版本号。

内部状态为 `CREATING`、`AWAITING_PAYMENT`、`PROCESSING`、`SUCCEEDED`、`CANCELLED`、`FAILED`、`REQUIRES_REVIEW`。暂未映射或账户资料未确认的 Airwallex 状态进入待核实，不擅自标记成功。付款成功不代表已结算；结算核对在后续阶段处理。

## 4. 本地模拟接口

- `GET /mock-xingzhe/api/v1/products`：本地示例商品列表。
- `POST /mock-xingzhe/api/v1/orders`：按模拟商品目录创建业务订单，浏览器不能传金额。
- `GET /mock-xingzhe/api/v1/orders/{businessOrderId}`：查询模拟订单状态。
- `POST /mock-xingzhe/api/v1/payment-events`：接收已签名的本地支付状态通知。

本地集成签名输入为 `timestamp + 换行 + nonce + 换行 + 原始请求体`，使用独立的入站/出站密钥。此格式属于项目的本地模拟约定，不代表星辙生产鉴权方式。

## 5. 重试与故障处理

- 无效 Webhook 签名：返回 401，不保存事件，不更新订单。
- 重复 Webhook：唯一事件约束使其不重复入队；收到重复请求仍返回成功应答。
- Airwallex 查询暂时失败：事件保持可重试状态并延后再次处理；不更新为成功。
- 星辙通知失败：outbox 保留任务，按增长的等待间隔重试；员工重试入口将在运营后台阶段设计。
- 通知接收重复：本地模拟星辙按 `eventId` 唯一记录，只应用一次。
- 订单标识、Payment Intent、金额或币种不匹配：不通知业务成功，保留异常以待后续运营核查。
- 浏览器直接返回：不修改任何支付状态。

## 6. 配置和安全

- Airwallex sandbox API 根地址为 `https://api.sandbox.airwallex.com/api/v1`；生产地址只能通过部署配置切换。
- `AIRWALLEX_CLIENT_ID`、`AIRWALLEX_API_KEY`、`AIRWALLEX_WEBHOOK_SECRET` 和结账国家代码只放在本地环境变量或部署密钥管理中，禁止提交到 Git。
- 本地模拟器仅由 `local` profile 加载；正式运行配置禁止开启。
- Webhook 路由应由 Airwallex 通过 HTTPS 回调。当前仅完成本地服务处理，公网域名、TLS、隧道和账户侧 Webhook 配置待部署/沙盒联调阶段确认。

## 7. 当前验证范围与未决事项

本地可检查 Java 构建、前端构建/静态检查、数据库迁移、健康接口、模拟商品接口和有效/无效 Webhook 签名行为。没有 Airwallex 沙盒凭据时，不能验证 Payment Intent 创建、托管收银台、实际支付事件或端到端开通。真实星辙契约、账户地区、支付方式、币种可用性和生产结算/退款规则均待外部资料。

退款执行、对账、异常操作台、账号权限和审计页面不属于本链路；这些模块按产品路线图另行形成架构和计划。

## 参考资料

- [Airwallex Payment Intent API](https://www.airwallex.com/docs/api/payments/payment_intents/api)
- [Airwallex Webhook 签名与事件说明](https://www.airwallex.com/docs/developer-tools/webhooks/listen-for-webhook-events)
