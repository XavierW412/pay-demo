# 星辙与支付项目接口约定（待甲方确认）

## 定位

本约定是当前软件可运行的对接协议，由本地星辙模拟器和支付项目共同实现。真实星辙接口资料尚未取得，因此它是交给星辙讨论的**提议契约**，不是已获甲方确认的生产协议。正式资料到来后可在集成适配层调整，不改变支付单、Airwallex 核实与通知留痕的核心职责。

## 星辙发起付款

`POST /api/v1/integration/payment-orders`，请求正文：

```json
{
  "requestId": "星辙本次支付请求的唯一编号",
  "businessOrderId": "星辙业务订单号",
  "amount": "39.00",
  "currency": "USD",
  "shopperCountryCode": "US"
}
```

`requestId` 与业务订单用于防重复；同一请求重试必须保持金额、币种和国家或地区不变。`shopperCountryCode` 是付款人国家或地区的可选 ISO 两字母代码，不按币种推断。金额与币种由星辙决定，支付项目校验后保存不可修改的快照。成功响应为 HTTP 201，含 `paymentOrderId`、`businessOrderId`、`status`、`checkoutUrl`、`createdAt`；`checkoutUrl` 是由 `PAYMENT_CHECKOUT_PUBLIC_BASE_URL` 生成的公开绝对地址，由星辙将用户导向该地址。若支付服务商暂时失败，星辙应以相同 `requestId` 重试，不能创建新业务订单来掩盖原订单状态。

## 支付项目通知星辙

生产配置 `STARTRAIL_PAYMENT_NOTIFICATION_URL` 指向星辙提供的 HTTPS 接收地址。本项目在核实 Airwallex 状态变化后异步发送 JSON，含 `eventId`、`eventType`、`businessOrderId`、`paymentOrderId`、`status`、`amount`、`currency`、`occurredAt`。星辙先验签，再核对订单编号和金额币种，并按 `eventId` 去重；只有可信的 `SUCCEEDED` 才允许触发星辙自己的服务开通。成功处理返回 HTTP 2xx；非 2xx 或网络失败由本项目 outbox 继续重试。

付款状态、通知送达状态和星辙服务状态彼此独立。浏览器从托管收银台返回、页面显示成功、`PaymentIntent` 建单成功都不能替代服务端核实。

## 双向签名

两个方向都使用下列请求头：`X-Startrail-Timestamp`（秒级 Unix 时间）、`X-Startrail-Nonce`（每次请求唯一）、`X-Startrail-Signature`（小写十六进制 HMAC-SHA256）。签名原文为 `timestamp + "\n" + nonce + "\n" + 原始 JSON 正文`，按 UTF-8 计算。接收方验证签名、时间窗口和随机数防重放；不能先重新序列化 JSON 再验签。

星辙发起付款使用 `STARTRAIL_INBOUND_SECRET`，本项目通知星辙使用独立的 `STARTRAIL_OUTBOUND_SECRET`。本地模拟器对应 `STARTRAIL_LOCAL_INBOUND_SECRET` 和 `STARTRAIL_LOCAL_OUTBOUND_SECRET`；生产不得沿用本地密钥或默认值。两个方向的密钥由星辙与本项目经安全渠道交换，不放进浏览器或 Git。

## 正式接入仍需星辙提供

- 正式业务订单状态与重复支付、取消、过期规则。
- 正式付款发起地址/字段/鉴权，以及星辙通知接收地址与签名规则。
- 星辙服务开通的回执或查询接口、员工身份和操作权限来源。
- 正式商户账户、部署域名、HTTPS、各币种和付款方式开通情况。
- 退款批准指令与对账数据接口。上述能力不会因本提议契约存在而被视为已经接通。

## 付款返回地址

星辙提供可信订单页模板，通过服务端 `STARTRAIL_BUSINESS_ORDER_URL_TEMPLATE` 配置；本项目编码订单号后代入唯一的 `{businessOrderId}`，不接受客户端任意 URL。正式环境必须为真实 HTTPS 地址。本地模拟业务订单创建和发起支付是两个接口；发起支付失败不丢失已建业务单，后续继续使用同一付款请求编号。
