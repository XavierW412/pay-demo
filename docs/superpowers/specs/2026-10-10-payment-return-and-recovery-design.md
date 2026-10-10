# 付款返回核实与 10 秒状态兜底设计

## 1. 目标

付款人完成 Airwallex 托管收银台付款后，支付项目应立即主动核实结果，并尽快通知星辙；Airwallex Webhook 作为服务端主动通知通道，后台状态补查作为兜底。不得根据浏览器返回或 URL 参数直接把订单标记为成功。

本地体验目标：正常浏览器返回时先由支付服务核实，再跳回模拟星辙订单页；如 Airwallex 查询暂时失败或结果仍未完成，页面显示状态未知/处理中并安全返回，后续由 Webhook 或补查推进。

后台兜底目标：未决 Airwallex 支付单在付款后以约 10 秒节奏继续核查，正常状态变化无需等待一分钟；终态停止扫描，限频、租约、批次上限、失败退避和重复通知保护继续有效。

## 2. 当前问题证据

- `frontend/src/modules/checkout/CheckoutPage.tsx` 将 Airwallex.js `successUrl` 设为 `context.businessReturnUrl`，所以浏览器直接回模拟星辙，没有经过支付服务的付款结果页。
- `CheckoutReturnPage` 已能在返回时调用受服务端控制的状态核实接口，但当前托管跳转没有使用它。
- `PaymentStatusRefreshScheduler` 默认每 60 秒运行一次；扫描发现新未决单也要求支付单创建至少 60 秒。
- `PaymentStatusVerificationService` 当前对同一支付单设置 15 秒服务商查询限频；调度器发现限频碰撞后仅等待 5 秒重试，因此只改扫描间隔不能保证 10 秒查询。
- 本次本地数据库中 `provider_webhook_event` 没有收到事件；状态通过员工主动核实更新，并在状态更新后立即投递了星辙通知。

Airwallex 官方说明，配置 `successUrl` 后会跳回该地址；未配置时才使用 PaymentIntent 的 `return_url`。Airwallex 也建议通过 Webhook 推进成功订单处理，避免依赖浏览器重定向。

## 3. 方案

### 3.1 浏览器付款返回

1. 结账上下文服务为支付单签发单独的、不可猜测的返回令牌。返回令牌仅允许读取该单支付状态/可信回跳地址和请求一次服务端核实，不允许读取 PaymentIntent `client_secret` 或重新发起付款。
2. 返回令牌应有足够的有效期供付款人完成托管付款；有效期建议 24 小时。存储令牌摘要，不保存明文。现有 15 分钟付款上下文令牌保持原有权限和时效。
3. 后端返回受信任支付项目域名下的结果页地址。前端把该地址作为 Airwallex.js `successUrl`；不得由浏览器拼接未验证的回跳地址。
4. 结果页先通过只读接口读取当前状态和可信回跳地址，再调用支付服务端核实 PaymentIntent，核验 ID、业务订单号、金额、币种和状态；服务端应用状态并写通知 outbox。浏览器不提交“成功”状态。
5. 服务端核实完成后尽快跳回星辙订单页。若状态仍处理中也显示真实状态后返回；主动核实失败时保留安全的手动重试与返回入口，Webhook 与后台补查继续推进，不在浏览器中无限等待。
6. 令牌过期、返回页不可用或用户直接关闭页面时，不改写支付状态，依赖 Webhook 与后台补查。

### 3.2 Airwallex Webhook

- 正式环境由 Airwallex Webhook 向支付项目 HTTPS 入口推送支付状态，支付服务验签、幂等落库、服务端查询核实并更新状态，再通过 outbox 通知星辙。
- 本地沙盒联调通过 HTTPS 隧道暴露 Webhook 地址；需要在 Airwallex 沙盒配置付款事件订阅和对应 secret。隧道未运行、订阅未配置或验签失败时，后台补查仍可恢复状态。
- Webhook 是主要主动通知机制；托管收银台返回核实是用户在场时的快速补充；轮询是最后兜底。三者共用同一支付状态应用和通知 outbox。

### 3.3 10 秒后台补查

- 将 `PaymentStatusRefreshScheduler` 扫描间隔调整为 10 秒，并将新未决支付单进入扫描的等待门槛调整为 10 秒。
- 将后台补查与共享支付状态核实的最短单笔查询间隔调整到 10 秒，避免调度器频繁醒来却一直被 15 秒限频挡住。人工核实也受该共享限频约束。
- 补查器每轮最多处理 10 笔，使用现有数据库租约避免多实例重复查询；不并发或无上限地请求 Airwallex。
- Airwallex 返回处理中时，下一次检查间隔为 10 秒；终态移出队列。连接错误、服务端错误或 429 使用退避重试，不通过 10 秒定时器反复重打失败请求。
- 保留付款状态与通知投递状态分离、金额/币种/订单核验、终态防回退、事件幂等和星辙通知重试逻辑。
- 本设计只调整支付状态补查，不调整财务/结算对账任务。支付成功不代表资金结算完成。

## 4. 令牌与返回安全

- 返回令牌由密码学安全随机数产生，数据库仅保存哈希；仅能映射到一笔内部支付单。
- 返回接口不得返回 PaymentIntent `client_secret`、商户凭证或敏感付款资料。
- 返回令牌的只读接口只返回该支付单的当前状态和服务端构造的星辙回跳地址；主动核实由独立 POST 接口触发。
- 继续使用可信域名配置构造结果页和星辙订单回跳；不接受任意 `returnUrl` 查询参数。
- 核实接口应用与 Webhook 相同的服务端字段校验、状态机和 outbox 事务。
- 令牌不可用、Airwallex 超时、未知服务商状态和未完成付款都保持处理中/待核实，不伪造成功。

## 5. 数据和文件影响

- 新增返回令牌的摘要及有效期存储，使用新的 Flyway 迁移版本；现有短期付款上下文令牌不扩大权限。
- 更新结账上下文响应，包含后端生成的结果页地址；前端托管跳转使用该地址。
- 更新返回接口/应用服务以接受返回令牌并执行服务端核实。
- 调整支付状态补查任务的扫描周期、可发现年龄门槛、单笔核实限频和未决结果重试节奏。
- 更新 `docs/architecture/payment/payment-flow.md`、`docs/architecture/reconciliation/payment-status-check.md` 和本地 Webhook 配置说明。

## 6. 取舍与风险

- 只把定时器从 60 秒改成 10 秒，仍会被旧的 60 秒发现门槛和 15 秒单笔限频抵消，因此必须一起调整这些配套条件。
- 10 秒补查会提高 Airwallex 查询量。每轮批次仍封顶，单笔间隔保持 10 秒，429 与临时错误退避；生产上线前还应按实际交易量和账户限额复核参数。
- Webhook 不可达时，浏览器返回核实和 10 秒补查能改善状态恢复；付款人关闭浏览器时仍由 Webhook 和补查负责。
- 浏览器回跳只是触发服务端查询的机会，不是支付事实来源；最终状态仍来自 Airwallex 服务端核实。

## 7. 后续实施顺序

1. 创建只读状态/主动核实专用的长效返回令牌与数据库迁移。
2. 后端生成可信结果页地址，Airwallex 托管付款完成后先返回支付项目。
3. 返回页触发服务端核实、展示处理中/失败状态，并安全跳转回星辙。
4. 将后台支付状态兜底扫描、订单发现门槛和单笔查询限频协调到 10 秒；保留批次和错误退避。
5. 更新架构说明与 README 的沙盒 Webhook 设置步骤。

## 8. 方案复核

- 未将浏览器返回作为成功凭证。
- 未扩大现有可读取 `client_secret` 的付款上下文令牌权限。
- 未混淆支付核实、业务服务开通、通知送达和资金结算。
- 明确了 Webhook、浏览器返回核实和轮询兜底各自职责。
- 10 秒调整覆盖了扫描、订单发现门槛和单笔查询限频，而不只是定时器配置。

## 官方资料

- [Airwallex.js Hosted Payment Page](https://www.airwallex.com/docs/js/payments/hosted-payment-page)
- [Airwallex Payment Intents 集成指南](https://www.airwallex.com/docs/payments/get-started/using-payments-intent-api)
- [Airwallex Webhooks 概览](https://www.airwallex.com/docs/developer-tools/webhooks/webhooks-overview)
- [Airwallex Webhook 事件监听与签名](https://www.airwallex.com/docs/developer-tools/webhooks/listen-for-webhook-events)
