# 星辙与 Airwallex 支付接口契约

## 1. 文档状态与使用规则

- 本文描述当前代码、本地星辙模拟器和 Airwallex 官方接口之间的字段与交互。
- 星辙字段和签名是本地模拟约定及生产对接建议，**不代表星辙真实协议**。接入生产前必须由星辙确认接口地址、认证、字段、状态、错误码、回执和重试规则。
- Airwallex 字段依据本文列出的官方文档；最终使用的 API 版本、账户能力、付款方式、币种和 Webhook 事件仍需在星辙持有的沙盒账户验证。
- JSON 字段按线上序列化名称书写；金额在星辙到支付项目的 JSON 中使用十进制字符串，在 Airwallex API 中使用 JSON 数字，服务端以十进制精度转换，不使用二进制浮点计算。
- 本文的 `SHOULD` 表示推荐约定；“当前实现”说明代码现状；“待确认”说明不能据此上线。

## 2. 参与方、职责与权威数据

| 参与方 | 提供/接收 | 权威信息 | 不负责 |
| --- | --- | --- | --- |
| 星辙 | 创建业务订单、发送支付请求和已批准的退款指令；接收支付/退款事件 | 业务订单、购买金额和币种、退款审批、服务是否开通 | Airwallex 实际支付结果 |
| 支付项目 | 建立支付关联、调用服务商、核实状态、通知星辙、保存审计 | 本项目支付单、退款记录、接口投递记录和核查快照 | 业务退款审批、服务开通、用户支付资料 |
| Airwallex | 接收 Payment Intent/Refund 请求，提供托管收银台、Webhook 和查询 | 服务商 Payment Intent、Payment Attempt、Refund 的原始标识与状态 | 星辙业务订单状态 |
| 付款用户 | 在 Airwallex 托管收银台完成付款和必要验证 | 仅完成用户授权/付款操作 | 决定星辙业务订单金额或收款商户 |

权威状态来源：支付成功必须由 Airwallex Webhook 或服务端查询核实；通知已送达只说明星辙接口返回了约定成功响应；星辙订单是否更新、服务是否开通由星辙订单查询/回执确认；实际结算金额只由已接入的结算报告确认。

## 3. 标识、金额、币种和国家/地区字段

| 字段 | 由谁生成 | 含义与约束 | 传递规则 |
| --- | --- | --- | --- |
| `businessOrderId` | 星辙 | 星辙业务订单唯一编号；当前长度最多 64 | 从星辙请求、本地支付单、Airwallex `merchant_order_id` 到星辙通知保持可关联 |
| `requestId` | 星辙 | 星辙到支付项目这次支付创建请求的幂等编号；当前长度最多 64 | 同一业务请求重试时必须原样复用且业务字段不可变 |
| `paymentOrderId` | 支付项目 | 支付项目内部支付单 UUID | 支付项目通知和后台记录使用；星辙保存关联，不得代替 `businessOrderId` |
| `intentId` | Airwallex | Airwallex Payment Intent 标识，通常以 `int_` 开头 | 保存原值；后续 Webhook、查询、退款关联使用 |
| `refundRequestId` | 星辙 | 星辙已批准退款指令的稳定唯一编号 | 原请求重试必须保持编号和全部业务字段不变 |
| `refundId` | 支付项目 | 支付项目内部退款 UUID | 作为支付项目记录主键，并关联星辙指令及 Airwallex Refund |
| `providerRefundId` | Airwallex | Airwallex Refund 标识，通常以 `rfd_` 开头 | 保存服务商原值，查询和对账使用 |
| `amount` | 星辙 | 正数十进制金额，首期最多两位小数；币种精度仍需按 Airwallex currency 规则验证 | 星辙 JSON 用字符串；Airwallex JSON 用 number；全链路必须精确相等后才确认匹配 |
| `currency` | 星辙 | ISO 4217 三字母大写代码；首期目标 CNY、USD | 订单币种是星辙决定并传入；不得根据国家/地区猜币种 |
| `shopperCountryCode` | 星辙 | 可选的付款人所在国家/地区，ISO 3166-1 alpha-2 大写代码 | 缺失时为 `null`；它不是商户注册国家，也不是 `AIRWALLEX_CHECKOUT_COUNTRY_CODE` 配置 |

当前代码把支付项目 `requestId` 同时用作 Airwallex Payment Intent `request_id`。Airwallex 快速接入文档建议服务商 `request_id` 使用 UUID v4。实施接口适配时应评估并明确拆分 `requestId`（星辙幂等键）和 `providerRequestId`（本项目生成并持久化的 Airwallex 幂等键），同一服务商调用重试始终复用同一个 `providerRequestId`。不得在超时后生成新键盲目重建 Payment Intent。

**特别区分国家字段：**订单中的 `shopperCountryCode` 是付款人国家/地区。Airwallex HPP 的一般跳转参数使用 Payment Intent 的 `client_secret` 与 `currency`；苹果支付选项中的 `countryCode` 则是商户国家/地区。不得把付款人国家直接塞入商户国家字段，也不得由 CNY 推断 CN、由 USD 推断 US。

## 4. 正向支付接口

### 4.1 星辙创建支付请求 → 支付项目

**当前本地及代码接口**：`POST /api/v1/integration/payment-orders`。

模拟签名请求头：

```http
Content-Type: application/json
X-Startrail-Timestamp: <Unix 秒时间戳>
X-Startrail-Nonce: <8 至 100 位随机标识>
X-Startrail-Signature: <小写十六进制 HMAC-SHA256>
```

当前模拟签名原文是 `timestamp + "\n" + nonce + "\n" + 原始 HTTP body`，用支付入站密钥计算 HMAC-SHA256；时间容差五分钟，随机数十分钟内不可重用。必须对原始正文验签，再解析 JSON。此算法和请求头是当前模拟协议，星辙生产认证方案**待星辙确认**。

请求正文：

```json
{
  "requestId": "XZ-PAY-20261010-000001",
  "businessOrderId": "XZ-ORDER-20261010-000001",
  "amount": "99.00",
  "currency": "CNY",
  "shopperCountryCode": "CN"
}
```

| 字段 | 必填 | 校验/处理 | 错误时行为 |
| --- | --- | --- | --- |
| `requestId` | 是 | 非空，最长 64；幂等键 | 同键同内容返回既有支付单；同键不同内容冲突 |
| `businessOrderId` | 是 | 非空，最长 64；当前一笔业务订单只允许关联一笔支付单 | 已被不同支付请求关联时冲突，不覆盖旧记录 |
| `amount` | 是 | 正数十进制字符串，最多两位小数 | 拒绝创建，不调用 Airwallex |
| `currency` | 是 | 当前只接受 `CNY`、`USD` | 拒绝创建，不调用 Airwallex |
| `shopperCountryCode` | 否 | 空值归一为 `null`；否则两个大写字母 | 拒绝创建；不可从币种默认推断 |

成功响应当前为首次创建 `201 Created`，相同请求重放 `200 OK`：

```json
{
  "paymentOrderId": "2f0b1234-5678-4abc-9def-0123456789ab",
  "businessOrderId": "XZ-ORDER-20261010-000001",
  "status": "AWAITING_PAYMENT",
  "checkoutUrl": "https://payment.example/checkout/<opaque-token>",
  "createdAt": "2026-10-10T02:00:00Z"
}
```

实际状态可能是创建中或待付款；星辙应保留 `paymentOrderId` 和 `checkoutUrl`。`checkoutUrl` 由支付项目生成的短期不透明入口，不应包含密钥、完整 Airwallex 响应或可篡改金额。正式响应字段、错误码和业务订单重复策略需星辙确认。

### 4.2 支付项目 → Airwallex 创建 Payment Intent

当前后端服务端请求 `POST /api/v1/pa/payment_intents/create`，先以 `Client ID` 和 `API Key` 调用 Airwallex 登录接口取得访问令牌。凭证和令牌只保存在服务端。

当前请求映射：

```json
{
  "amount": 99.00,
  "currency": "CNY",
  "merchant_order_id": "XZ-ORDER-20261010-000001",
  "request_id": "稳定的服务商幂等编号",
  "return_url": "https://支付项目可信域名/checkout/return"
}
```

必要字段来源：`amount`、`currency`、`merchant_order_id` 对应星辙订单快照；`request_id` 是服务商调用幂等编号；`return_url` 来自支付项目可信服务端配置，不能接受浏览器随意传入的任意地址。Airwallex 官方创建接口要求金额、币种、商户订单号和请求编号。响应至少保存 `id`、`request_id`、`amount`、`currency`、`merchant_order_id`、`status`、`client_secret`（仅为短期 HPP 跳转所需）。

Payment Intent 创建成功不代表付款成功。创建超时或响应丢失时，本地状态进入“创建结果待核实”；使用同一服务商 `request_id` 查询/重试，不新建另一条支付单。

### 4.3 支付项目 → Airwallex 托管收银台

Airwallex 当前官方 HPP 流程：后端创建 Payment Intent；支付项目的轻量页面取得短期 `client_secret` 和 `currency`，调用 Airwallex.js `redirectToCheckout`；用户在 Airwallex 托管页面选择该账户启用的付款方式并完成支付；支付项目不收集或存储银行卡资料。

提供给浏览器的最小上下文：

```json
{
  "intentId": "int_example",
  "clientSecret": "短期服务商 client_secret",
  "paymentOrderId": "2f0b1234-5678-4abc-9def-0123456789ab",
  "businessOrderId": "XZ-ORDER-20261010-000001",
  "amount": "99.00",
  "currency": "CNY",
  "shopperCountryCode": "CN"
}
```

`clientSecret` 只用于必要的浏览器托管跳转，不写日志、不作为页面查询参数长期保存、不返回 Airwallex API key。付款成功依据服务端 Webhook/查询，不依据浏览器返回。

### 4.4 Airwallex Webhook → 支付项目

当前接收地址：`POST /api/v1/webhooks/airwallex`。官方通知头为 `x-timestamp`（毫秒时间戳）与配置了密钥时的 `x-signature`。签名是 HMAC-SHA256(`secret`, `timestamp + 原始 JSON body`) 的十六进制摘要。不同 Webhook 订阅有各自密钥；必须按原始 body 验签后再解析，并应用合理时间窗。

事件 envelope 至少识别：

```json
{
  "id": "evt_example",
  "name": "payment_intent.succeeded",
  "account_id": "airwallex-account-id",
  "data": { "object": {
    "id": "int_example",
    "request_id": "provider-request-id",
    "merchant_order_id": "XZ-ORDER-20261010-000001",
    "amount": 99.0,
    "currency": "CNY",
    "status": "SUCCEEDED"
  }},
  "created_at": "2026-10-10T02:04:00Z",
  "version": "2024-02-22"
}
```

事件 ID `id` 用于去重；事件可能重复且不保证按产生顺序送达。先快速持久化事件/待处理任务，成功接收后返回 HTTP 200；处理状态时按 Payment Intent ID 查询 Airwallex 并核对 `id`、`merchant_order_id`、金额、币种和状态。查询/数据库失败不能把事件标成功处理；Webhook 重试应可恢复。

| Airwallex Payment Intent 状态 | 支付项目状态 | 行为 |
| --- | --- | --- |
| `REQUIRES_PAYMENT_METHOD` | `AWAITING_PAYMENT` | 等用户继续付款；不是失败 |
| `REQUIRES_CUSTOMER_ACTION` | `AWAITING_PAYMENT` | 用户需完成跳转/验证；等待后续状态 |
| `PENDING` | `PROCESSING` | 等服务商最终结果 |
| `PENDING_REVIEW` | `REQUIRES_REVIEW` | 风险审核中，不通知成功 |
| `REQUIRES_CAPTURE` | `PROCESSING` | 本项目当前不自动捕获；账户若出现需人工/产品决策，不能谎报成功 |
| `SUCCEEDED` | `SUCCEEDED` | 核对金额、币种和订单关联后才通知星辙支付成功 |
| `CANCELLED` | `CANCELLED` | 关闭；不等于服务已开通 |
| `payment_intent.payment_failed` 或未知状态 | `FAILED` 或 `REQUIRES_REVIEW`，依官方对象当前状态判断 | 失败事件可能只代表一个 Payment Attempt；须查询整个 Intent，不能直接把多次尝试的 Intent 标为失败 |

上述状态映射是本项目归一规则，不把 Airwallex 原状态丢弃。Airwallex 明确指出单个 Payment Intent 可能含多个 Payment Attempt，且事件乱序，因此状态推进必须以服务端查询的当前 Intent 为准。

## 5. 支付项目通知星辙

通知由本项目 outbox 异步投递到配置的星辙接收地址。当前使用 `X-Startrail-Timestamp`（Unix 秒）、`X-Startrail-Nonce` 和 `X-Startrail-Signature`；签名为本地约定 HMAC-SHA256(`secret`, `timestamp + "\n" + nonce + "\n" + 原始 body`)。它与 Airwallex Webhook 签名完全不同。付款/退款入站密钥、支付/退款通知出站密钥必须区分。

支付事件正文：

```json
{
  "eventId": "payment:2f0b1234-5678-4abc-9def-0123456789ab:SUCCEEDED",
  "eventType": "payment.status_changed",
  "businessOrderId": "XZ-ORDER-20261010-000001",
  "paymentOrderId": "2f0b1234-5678-4abc-9def-0123456789ab",
  "status": "SUCCEEDED",
  "amount": 99.00,
  "currency": "CNY",
  "occurredAt": "2026-10-10T02:04:02Z"
}
```

当前模拟器以 `eventId` 去重；事件成功响应代表星辙已接收事件，不等于星辙业务单已更新或服务已开通。星辙生产接口必须确认：成功响应的 HTTP 状态/正文、幂等策略、重试冲突处理、业务订单状态查询或处理回执。未取得确认前，后台应把“HTTP 已送达”和“星辙业务已确认”显示为两个状态。

支付项目返回用户浏览器的页面只读查询当前支付状态并提供返回星辙订单页的入口。浏览器刷新只触发状态读取/核查，不能代替 Airwallex Webhook 或星辙通知回执。

## 6. 退款接口

### 6.1 星辙退款指令 → 支付项目

当前本地接口：`POST /api/v1/integration/refunds`。认证使用退款入站专属密钥，不得复用支付入站或出站通知密钥。正文：

```json
{
  "refundRequestId": "XZ-REFUND-20261010-000001",
  "paymentOrderId": "2f0b1234-5678-4abc-9def-0123456789ab",
  "businessOrderId": "XZ-ORDER-20261010-000001",
  "amount": "20.00",
  "currency": "CNY",
  "reason": "星辙已批准的退款原因"
}
```

支付项目核对签名、指令唯一号、订单/支付关联、已核实支付、币种和可退余额。核对异常写入 `BLOCKED` 并通知星辙；核对通过只写为 `PENDING_OPERATOR_CONFIRMATION`，等待 `STAFF_ADMIN` 复核并确认调用 Airwallex。星辙重放同一退款号和相同字段时返回既有退款记录；同号不同字段冲突。返回 HTTP 202 表示已受理，HTTP 200 表示已有记录；都不表示 Airwallex 已退款。

生产接口尚待星辙确认是否在退款审批后调用本接口、重复/超时语义和退款编号规则。支付项目不替星辙批准退款。

### 6.2 支付项目 → Airwallex Refund

当前后端使用 `POST /api/v1/pa/refunds/create`，请求字段：

```json
{
  "payment_intent_id": "int_example",
  "amount": 20.00,
  "reason": "星辙已批准的退款原因",
  "request_id": "稳定且唯一的支付项目退款幂等编号"
}
```

Airwallex 官方 Refund 创建返回至少包含 `id`、`request_id`、`payment_intent_id`、`amount`、`currency`、`reason`、`status`、`created_at`、`updated_at`。支付项目需逐字段校验退款编号、原 Payment Intent、请求编号、金额和币种；不匹配时进入 `NEEDS_REVIEW`，不发起第二次退款。

Airwallex Refund `request_id` 必须由支付项目为该退款动作生成并持久化，所有同一动作的重试复用它；不直接把星辙 `refundRequestId` 当作服务商请求编号，除非经版本、格式和唯一性验证确认适用。

### 6.3 退款状态与未知结果

Airwallex 当前退款管理文档列出 `RECEIVED`、`ACCEPTED`、`SETTLED`、`FAILED`，并分别有 `refund.received`、`refund.accepted`、`refund.settled`、`refund.failed` 事件。部分 API 版本文档将最终对账状态写作 `SUCCEEDED`。上线前必须固定该 Airwallex 账户的 API/Webhook 版本，并用沙盒样本验证；适配器保存原始状态，再归一到本项目状态。

| 服务商状态/情形 | 本项目处理 | 额度与星辙通知 |
| --- | --- | --- |
| `RECEIVED` | `PROCESSING` | 保留退款额度；通知星辙处理中 |
| `ACCEPTED` | 依版本语义保存为处理中/服务商已接受 | 保留额度；不能说资金已进入结算报告 |
| `SETTLED` 或该版本定义的 `SUCCEEDED` | 本项目 `SUCCEEDED` | 保留服务商原值；通知星辙退款已核实；仍不代表银行账户已入账 |
| `FAILED` | 本项目 `FAILED` | 核对失败终态后释放相应额度并通知星辙 |
| 创建超时、查询失败、事件不匹配、状态未识别 | `NEEDS_REVIEW` | 继续保留额度；按 Refund ID 或原 Payment Intent + 原 request_id 查找；不得生成新 request_id 再提交 |

本地与线上各有状态来源：Airwallex Refund 查询/事件是服务商退款事实；本项目退款记录保存核实后的映射；星辙事件只更新星辙退款历史。支付状态、退款状态、通知投递和结算到账不能合并成一个状态。

### 6.4 支付项目通知星辙退款结果

当前退款事件格式：

```json
{
  "eventId": "refund:refund-uuid:SUCCEEDED",
  "eventType": "refund.status_changed",
  "refundRequestId": "XZ-REFUND-20261010-000001",
  "refundId": "refund-uuid",
  "paymentOrderId": "2f0b1234-5678-4abc-9def-0123456789ab",
  "businessOrderId": "XZ-ORDER-20261010-000001",
  "providerRefundId": "rfd_example",
  "status": "SUCCEEDED",
  "amount": 20.00,
  "currency": "CNY",
  "occurredAt": "2026-10-10T02:10:00Z"
}
```

星辙按 `eventId` 去重并把事件附加到相应业务订单退款历史。投递成功不代表星辙已完成其业务退款记录更新，除非星辙提供并确认处理回执。

## 7. 失败与恢复契约

| 场景 | 本项目状态/记录 | 下一步 | 禁止行为 |
| --- | --- | --- | --- |
| 星辙支付请求验签失败或字段无效 | 拒绝请求，记录安全类别，不存敏感正文 | 星辙修正后使用新请求 | 创建支付单、调用 Airwallex |
| 星辙支付请求响应超时 | 星辙用同一 `requestId` 和完全相同正文重试 | 返回同一内部支付单和付款入口 | 创建第二笔业务订单支付单 |
| Airwallex 创建 PI 超时 | `CREATING`/待核实并保留同一 provider request id | 服务端按原幂等键恢复/查询 | 生成新键盲目再创建 |
| 用户从 Airwallex 返回但 Webhook 未到 | 状态仍以服务端核实结果为准 | 查询 PI、等待 Webhook、状态刷新 | 浏览器参数直接写支付成功 |
| Airwallex Webhook 重复或乱序 | 以 event id 去重，查询当前 PI 状态 | 应用单调状态规则、留下事件记录 | 按到达次序倒退本地状态 |
| 支付项目通知星辙超时 | outbox `RETRY`，支付状态保持原值 | 退避重试同一 event id | 新建付款或重复开通服务 |
| 退款调用 Airwallex 后超时 | `NEEDS_REVIEW`，保留可退额度 | 按原 provider request id 查 Refund | 用新编号重新发退款 |
| Airwallex 状态无法映射/金额不符 | `REQUIRES_REVIEW` 或 `NEEDS_REVIEW` | 人工查看原始安全字段并再次查询 | 由员工手工改为成功 |
| 星辙通知 HTTP 成功但业务历史未更新 | 投递状态与业务回执状态分开 | 以星辙提供的订单查询/回执接口核实 | 将 HTTP 200 等同服务已开通 |

## 8. 星辙生产对接确认清单

接入生产前必须由星辙技术/业务负责人确认并留档：

1. 支付创建 API 的生产与测试地址、HTTP 方法、认证签名规范、密钥交付与轮换、时间戳/随机数规则。
2. 业务订单号格式与唯一范围、支付重试条件、同一业务单是否允许重新支付、取消/过期规则。
3. 金额类型与精度、支持币种列表；`shopperCountryCode` 是否提供、来源含义、缺失处理。币种与国家字段不得互相推断。
4. 支付项目响应中的 `checkoutUrl` 如何交还浏览器，星辙何时重定向，用户最终应返回的订单页面地址由谁配置。
5. 支付通知接收地址、签名规范、HTTP/业务响应格式、幂等字段、重试策略、超时和通知处理回执。
6. 星辙查询业务订单支付/退款状态的接口、字段、状态枚举、更新时间与最终服务开通证据。
7. 退款指令发送时机、退款编号、全额/部分退款支持、重复/超时重试及付款成功前后限制；支付项目员工确认属于甲方同意的支付安全流程。
8. 员工身份/角色是否由星辙提供；谁能操作退款以及管理员停用员工边界。

任何一项未确认时，模拟系统可以使用显式标记的本地约定继续开发，但生产适配必须阻止上线或明确为未完成，不能静默沿用模拟值。

## 9. Airwallex 沙盒验证清单

由星辙持有的 Airwallex 沙盒账户核验：

1. Payment Intent 创建和查询权限、CNY/USD 实际启用状态、账户所属国家、商户订单号限制及金额精度。
2. HPP SDK 版本与 `redirectToCheckout` 参数，付款入口返回/取消地址、`client_secret` 有效期和重新进入策略。
3. Webhook 通知 URL 的独立 secret、API version、启用事件；至少覆盖 Payment Intent 创建、需客户操作、处理中、成功、取消/失败及 Refund 事件。
4. Payment Intent Webhook 与当前 Retrieve API 的对象字段是否一致，事件 ID、`data.object` 字段和实际签名样本。
5. Refund API 的状态枚举版本：当前账户到底回 `SETTLED` 还是 `SUCCEEDED`；Refund 查询/列表按 ID、Payment Intent 和 request id 的可用性与权限。
6. 重复相同 request id、并发退款、网络超时后查询、服务商拒绝退款的实际沙盒行为。
7. 结算报告和退款结算行项目的账户权限、字段和结算批次关联；未经报告核实不得展示实际到账数。

沙盒通过不等于生产开通。生产须单独核对凭证、Webhook secret、URL、事件订阅、账户能力和 HTTPS 部署。

## 10. 官方依据

- [Airwallex Payment Intent 创建与查询 API](https://www.airwallex.com/docs/api/payments/payment_intents/create)
- [Airwallex 托管收银台 Hosted Payment Page](https://www.airwallex.com/docs/js/payments/hosted-payment-page)
- [Airwallex Payment Intent 状态](https://www.airwallex.com/docs/payments/reference/payment-statuses)
- [Airwallex 支付 Webhook 事件](https://www.airwallex.com/docs/payments/reference/payments-webhooks)
- [Airwallex Webhook 配置、签名与投递](https://www.airwallex.com/docs/developer-tools/webhooks/listen-for-webhook-events)
- [Airwallex Refund API](https://www.airwallex.com/docs/api/2022-09-09/payments/refunds)
- [Airwallex 退款状态及事件](https://www.airwallex.com/docs/payments/manage-payments/refunds)
