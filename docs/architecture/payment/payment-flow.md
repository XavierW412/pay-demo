# Airwallex 支付创建、结果核实与星辙通知

## 文档状态

- 状态：托管衔接、同订单重试、Webhook处理与可选自动补查已实现；当前本地已暂停自动支付/退款状态补查和页面定时刷新。实际付款及Webhook送达仍待沙盒人工联调。
- 适用范围：本地模拟星辙 + Airwallex 沙盒的支付纵向链路。
- 外部边界：本地星辙接口是开发模拟契约；生产星辙接口待正式资料。已使用本机沙盒凭据完成 Payment Intent 创建与查询，实际付款和回调尚待人工联调。
- 官方资料：Airwallex Payment Intent API 与 Webhook 文档于 2026-10-09 核对。
- 范围更新：2026-10-09 已替换内嵌组件为官方 SDK 托管跳转，沿用 Payment Intent；未采用早期开放的 Checkout Session。正式产品边界以[产品说明](../../product/product-spec.md)为准。

## 1. 责任和调用边界

- 星辙创建业务订单、确定金额/币种、决定服务开通和退款审批。
- 本服务创建内部支付单，调用 Airwallex Payment Intent API，验证支付结果，并可靠通知星辙。
- 本项目短期入口仅初始化官方 SDK 并跳转 Airwallex 托管页面；付款资料由 Airwallex 收集。浏览器跳回不作为成功依据。
- `simulator` 包和模拟迁移只加载在 `local` 配置；它们不是星辙真实业务 API。

## 2. 支付主链路

1. 本地模拟星辙先保存业务订单与请求编号，再经带 HMAC 签名的 HTTP 请求调用 `POST /api/v1/integration/payment-orders`，并传递金额、币种和可选的 `shopperCountryCode`。本地 CNY 示例显式配置 `CN`，USD 示例显式配置 `US`；这不是生产币种到国家/地区的推导规则。
2. 支付服务验证时间戳、随机数和原始 JSON 签名，校验金额/币种/国家地区字段，按 `requestId` 与 `businessOrderId` 幂等保存支付单快照。
3. 支付服务使用服务端 Client ID/API Key 获取访问令牌，再调用 `POST /api/v1/pa/payment_intents/create`。请求映射金额、币种、`merchant_order_id` 和 `request_id`。
4. 内部 API 返回可信公开域名下的绝对 checkout 地址。浏览器通过短期不透明令牌读取付款上下文，再调用 `payments.redirectToCheckout` 打开托管收银台；上下文另含支付服务生成的 `returnPageUrl`。Client ID/API Key 不发给浏览器；`client_secret` 仅从 Airwallex 服务端查询后短暂返回付款页面。
5. Airwallex Webhook 使用原始请求体、`x-timestamp`、`x-signature` 和 Webhook secret 做 HMAC-SHA256 校验。时间戳按毫秒解析，验签在 JSON 解析前执行。Payment Intent 与 Refund 事件共用接收入口，按各自资源查询 Airwallex 当前状态并核验关联。
6. 验签通过后按 Airwallex 事件 `id` 唯一落库并快速返回 HTTP 200。后台处理器查询 `GET /api/v1/pa/payment_intents/{id}` 当前状态，并验证 Payment Intent ID、业务订单号、金额、币种与内部支付单关联。
7. 只有服务端读取并核实的状态才更新内部支付单。支付状态通知写入 outbox，由后台任务签名发送；本地模拟星辙按事件编号去重，只在状态为 `SUCCEEDED` 时模拟开通服务。入站事件处理和通知投递任务只消费本地待处理队列并负责重试，不向 Airwallex 定时查询交易状态。数据库事务提交后，SSE 向打开的运营后台提示重新读取。
8. Airwallex.js 的 `successUrl` 指向支付服务的结果页。页面以独立 24 小时返回令牌读取服务端状态和可信星辙订单地址，再请求支付服务主动核实；核实完成后尽快返回星辙。浏览器返回本身不代表付款成功。主动查询与 Webhook 使用相同订单字段核验及行锁；仅状态改变时写入通知 outbox，避免同一次状态变化重复通知。返回令牌不能读取付款上下文或 `client_secret`。

## 3. 状态、事件和数据库

| 数据表 | 用途 | 环境 |
| --- | --- | --- |
| `payment_order` | 星辙业务订单与支付服务订单、Airwallex Payment Intent 的关联和当前支付状态 | 全环境 |
| `checkout_access_token` | 短期付款上下文令牌的哈希和过期时间 | 全环境 |
| `checkout_return_token` | 结果页专用令牌的哈希、支付单关联和过期时间；不具备读取 `client_secret` 的权限 | 全环境 |
| `provider_webhook_event` | 验签后保留的事件最小字段（事件 ID、名称、Payment Intent ID）、处理状态、失败原因和下次重试时间；不持久化完整 Webhook 正文或 `client_secret` | 全环境 |
| `notification_outbox` | 待发送星辙通知、投递状态、失败原因和重试时间 | 全环境 |
| `payment_status_refresh_cursor` | 未决支付单后台补查的租约、下次查询时间和失败次数 | 全环境 |
| `mock_xingzhe_order` | 本地示例商品和业务订单 | 仅 local |
| `mock_xingzhe_payment_event` | 本地模拟星辙已接收的唯一支付事件 | 仅 local |

`payment_order.shopper_country_code` 保存星辙传入的可选 ISO 两字母国家/地区代码；旧订单和未提供该值的新订单保留为空。`payment_order.last_provider_refresh_at` 用于限制公开付款令牌的主动查询频率；`payment_order.provider` 区分 Airwallex 和本地模拟来源。迁移全局单调递增：公共迁移已到 `V123`，本地专属迁移与公共迁移共用 Flyway 版本序列；新增迁移从 `V124` 顺延，不能重用序号。生产只加载公共迁移，不装配模拟器。

内部状态为 `CREATING`、`AWAITING_PAYMENT`、`PROCESSING`、`SUCCEEDED`、`CANCELLED`、`FAILED`、`REQUIRES_REVIEW`。暂未映射或账户资料未确认的 Airwallex 状态进入待核实，不擅自标记成功。付款成功不代表已结算；结算核对在后续阶段处理。

## 4. 本地模拟接口

本地购买页创建订单时可指定 `AIRWALLEX_SANDBOX` 或 `LOCAL_SIMULATOR`，默认仍为沙盒。模拟支付单通过本地接口创建，结果提交接口为 `POST /mock-xingzhe/api/v1/orders/{businessOrderId}/simulated-payment`，请求接受 `SUCCEEDED`、`INSUFFICIENT_FUNDS`、`PROCESSING`、`TIMEOUT`。资金不足映射为本地模拟失败；处理中可继续等待；超时映射为 `REQUIRES_REVIEW`，结果未知而不是失败。待核实或处理中订单可再提交一次模拟成功/资金不足最终结果，其他非法转换和终态覆盖会拒绝。服务端不接受浏览器指定金额、币种或任意最终状态。具体场景写入事件类型，并仍经过 outbox 和现有签名通知入口。该接口由 `local` profile 限定。

- `GET /mock-xingzhe/api/v1/products`：本地示例商品列表。
- `POST /mock-xingzhe/api/v1/orders`：按模拟商品目录创建业务订单，浏览器不能传金额。
- `POST /mock-xingzhe/api/v1/orders/{businessOrderId}/payment`：对已保存订单发起或重试同一付款请求。
- `GET /mock-xingzhe/api/v1/orders/{businessOrderId}`：查询模拟订单状态。
- `POST /mock-xingzhe/api/v1/payment-events`：接收已签名的本地支付状态通知。

本地模拟器与支付项目当前共用一份[提议中的星辙对接契约](../integration/xingzhe-payment-contract.md)：签名输入为 `timestamp + 换行 + nonce + 换行 + 原始请求体`，入站和出站使用不同密钥。正式星辙尚未确认该协议；生产配置可指定真实通知地址，但仍需甲方实现或提出接口适配要求。

本地模拟支付创建请求还包含可选 `shopperCountryCode`。有值时要求两个大写英文字母，保存到支付单并用于 Airwallex.js `country_code`；缺省时保持为空并在 SDK 参数中省略。真实星辙字段名称和国家/地区取值规则以正式接口资料为准，支付服务不按币种推断。

## 5. 重试与故障处理

- 无效 Webhook 签名：返回 401，不保存事件，不更新订单。
- 重复 Webhook：唯一事件约束使其不重复入队；收到重复请求仍返回成功应答。
- Airwallex 查询暂时失败：事件保持可重试状态并延后再次处理；不更新为成功。
- 星辙通知失败：outbox 保留任务并按增长的等待间隔重试；有操作权限的员工可在运营后台对最新待重试通知重新排队，操作会留审计记录。
- 通知接收重复：本地模拟星辙按 `eventId` 唯一记录，只应用一次。
- 通知时间比较使用带时区的数据库时间，并以支付事件历史判断新旧顺序；不能使用业务订单 `updated_at`，因为重复获取付款入口也会更新该字段。
- 订单标识、Payment Intent、金额或币种不匹配：不通知业务成功，保留异常以待后续运营核查。
- 浏览器直接返回：不修改任何支付状态。
- 付款结果页主动查询：每个支付单最短间隔 10 秒；查询失败保留既有状态，不能把组件事件解释为成功。

### 托管跳转与付款方式边界

`CheckoutPage` 查询短期上下文，再以已锁定 Components SDK 初始化 `payments` 并调用托管跳转。Airwallex.js 的 `successUrl` 使用服务端生成的支付服务结果页地址；Payment Intent 创建仍带受控星辙 `return_url`，供 SDK 未能应用 `successUrl` 时使用。页面不展示自有卡片输入、方式选择和订单摘要。SDK 初始化失败或超时提供重试，不生成新订单或扣款，不判断支付结果。官方接口没有通用取消返回参数，取消时遵循托管页面行为或用户返回星辙查看原订单。

国家作为独立可选 `country_code`，未提供时省略；不硬编码付款方式。早期沙盒查询仅返回 `airwallex_pay`，该结果属于历史快照，后续账户配置已变化；可用方式应按实际账户和订单重新查询。方式开通属于商户账户配置。

核心链路不实现银行卡输入、绑卡或资料处理。Airwallex 沙盒联调经托管页完成付款人操作；另提供 `LOCAL_SIMULATOR` 本地结果模拟路径，跳过付款人操作但仍创建有来源标记的内部支付单。模拟结果允许成功、资金不足失败、处理中和等待超时；待核实或处理中订单可以再补到成功/资金不足最终结果。状态由支付状态事务更新并写入通知 outbox，再由模拟星辙消费通知；只有成功通知会开通示例服务。模拟星辙另有独立订单分页、订单详情和按事件编号去重的支付历史。模拟星辙退款申请按退款动作独立生成 `refundRequestId`，不确定结果重试时复用同一编号；已拒绝、失败或拦截的申请不占退款额度，其余申请按订单累计预留，不能超过原支付金额。模拟来源不带 Payment Intent，运营主动核实、后台补查和退款均拒绝或排除它；逐笔资金核对不适用，运营页明确显示本地模拟来源。该入口仅在 `local` profile 装配。正式付款方式仍由星辙账户能力和业务配置决定。

### 后台补查与状态并发

`PaymentStatusVerificationService` 根据内部支付单查询服务商，使用数据库 10 秒原子限频；运营人工操作和返回令牌主动核实复用它。网络请求在数据库事务外。`PaymentStatusApplicationService` 在一个事务中完成行锁、标识/金额/币种核验、状态变更和 outbox 写入。Webhook 也使用相同状态更新事务；成功和取消不被旧快照回退。FAILED 不冻结后续服务商核实成功的可能。

`PaymentStatusRefreshScheduler` 仍作为可选恢复能力保留，但当前默认关闭；需显式设置 `PAYMENT_STATUS_REFRESH_ENABLED=true` 才会定时查询。支付定时核查和退款定时补查也默认关闭。关停补查后，Airwallex 状态依赖已配置并实际送达的 Webhook，或员工点击人工核实；缺少 Webhook 事件时状态不会自行推进。`payment_status_refresh_cursor` 仍保存恢复任务游标；未启用对应开关时不会被调度器消费。浏览器页面通过 SSE 接收数据库提交后的变化提示，再调用原查询接口，不定时轮询。

创建阶段尚无外部编号的订单不由此任务创建新收款；星辙以原请求编号重试，复用 Airwallex `request_id`。关联外部编号使用行锁，重试响应不能覆盖已核实的最新状态。差异核查模块仍只保存比对证据，不执行收退款。

## 6. 配置和安全

- Airwallex sandbox API 根地址为 `https://api.sandbox.airwallex.com/api/v1`；生产地址只能通过部署配置切换。
- `AIRWALLEX_CLIENT_ID`、`AIRWALLEX_API_KEY`、`AIRWALLEX_WEBHOOK_SECRET` 只放在本地环境变量或部署密钥管理中，禁止提交到 Git。
- 本地模拟器仅由 `local` profile 加载；正式运行配置禁止开启。
- 付款上下文、状态及主动核实接口在全部环境装配，均要求有效的 15 分钟短期付款令牌；本地模拟购买接口仍仅在 `local` 配置装配。
- 支付创建接口和 outbox 通知分发器在全部环境装配；正式环境要求独立的星辙双向签名密钥和 HTTPS 通知地址。其字段与签名目前是提议契约，须与星辙正式资料核对。
- Webhook 固定路由为 `POST /api/v1/webhooks/airwallex`。本地通过 HTTPS 隧道接收沙盒回调；生产使用星辙正式域名和 HTTPS。具体操作见 README 与部署指南。

## 7. 当前验证范围与未决事项

截至 2026-10-09，本地已通过 Java 21 后端打包、前端构建、V110迁移、健康接口、独立双向签名方向检查和CNY/CN、USD/US沙盒建单/上下文查询/同单复用；后台自动补查已查询两笔新订单且无失败；托管跳转、实际付款事件及端到端开通尚未在浏览器验证。真实星辙契约、账户地区、付款方式、生产币种能力和结算/退款规则均待外部资料。

退款执行、差异核查、运营权限和审计已有独立模块与架构，实际资金结算对账尚未接入。

## 参考资料

- [Airwallex Payment Intent API](https://www.airwallex.com/docs/api/payments/payment_intents/api)
- [Airwallex Webhook 签名与事件说明](https://www.airwallex.com/docs/developer-tools/webhooks/listen-for-webhook-events)
- [Airwallex 托管收银台](https://www.airwallex.com/docs/payments/integration-options/web-checkout/hosted-payment-page)
- [Airwallex 可用付款方式配置查询](https://www.airwallex.com/docs/api/payments/config/payment_method_types)
- [Airwallex Pay 余额付款说明](https://www.airwallex.com/docs/payments/payment-methods/global/airwallex-pay)

## 通知乱序的当前边界

模拟星辙按事件号幂等接收并保护已开通和已取消订单；FAILED可接受后续可信核实结果。非终态不能简单按状态等级单向前进，因为仍可能回到等待付款。提议契约目前只有事件编号与发生时间，没有正式确认的单调版本字段；严格处理所有非终态乱序仍需与星辙确定顺序协议并持久化接收版本，不能认为该能力已经完成。刷新事件按每次真实状态变化生成唯一编号，不按状态名称永久去重。
