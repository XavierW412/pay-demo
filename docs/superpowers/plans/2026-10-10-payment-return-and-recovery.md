# 付款返回核实与 10 秒状态兜底实施计划

> **执行要求：** 按任务顺序在当前工作区逐项实施；不新增或运行测试，最终执行 Java 编译和前端构建检查。当前工作区已有后台重构等未提交改动，必须保留，不得重置、覆盖或提交。

**目标：** 用户完成 Airwallex 托管付款后，支付服务立即核实并通知星辙；Webhook 为主要主动通道，未决状态由 10 秒补查兜底。

**架构：** 增加权限仅限状态读取/核实的独立返回令牌，不延长可读取 `client_secret` 的 15 分钟付款上下文令牌。Airwallex.js 返回支付项目结果页，由服务端核实后回到星辙；Webhook、结果页主动核实和后台补查共用现有状态应用及通知 outbox。补查任务协调调整扫描周期、订单发现门槛、单笔限频和下一次查询时间。

**技术栈：** Java 21、Spring Boot、PostgreSQL、Flyway、React、TypeScript、Airwallex.js、pnpm。

**设计说明：** `docs/superpowers/specs/2026-10-10-payment-return-and-recovery-design.md`

## 全局约束

- 通讯、界面和文档正文使用简体中文；`docs/` 文件名使用英文小写 kebab-case。
- 命令在 WSL Ubuntu 执行，Node.js 命令使用 pnpm，不生成 `package-lock.json`。
- 商户密钥、Webhook secret 和付款资料不得进入前端代码、日志或 Git。
- 浏览器返回、URL 参数和未经服务端核实的状态不得触发成功开通。
- 付款上下文的 `client_secret` 只允许由现有短期付款令牌读取；返回令牌不得读取该字段。
- Airwallex Webhook、浏览器返回核实、后台补查共用状态校验、状态事务与 outbox。
- 状态补查只负责支付状态，不改变财务/结算对账语义。
- 不改动或覆盖工作区已有未提交变更，不创建提交。
- 当前轮不新增或运行测试；最终只运行 Java 编译和前端构建。

## 重点审查项

- 过期、格式错误或随机猜测返回令牌只能得到不可用响应，不能读取订单、付款上下文或 `client_secret`。
- 返回令牌有效时只能作用于其绑定的支付单，不能通过参数替换业务订单或回跳地址。
- Airwallex 查询超时、未知状态或付款未完成时，支付服务不得将状态写成成功。
- Webhook 与返回页同时到达或重复返回时，不能重复生成同一状态变更通知或令状态回退。
- 10 秒补查不能查询终态单，也不能因 Airwallex 429/网络错误形成无退避高频重试。

---

### 任务 1：新增专用付款返回令牌

**文件：**
- 新增：`backend/src/main/resources/db/migration/V119__create_checkout_return_tokens.sql`
- 新增：`backend/src/main/java/com/startrail/payment/checkout/application/CheckoutReturnTokenService.java`
- 新增：`backend/src/main/java/com/startrail/payment/checkout/application/CheckoutReturnTokenRepository.java`
- 新增：`backend/src/main/java/com/startrail/payment/checkout/infrastructure/persistence/CheckoutReturnTokenEntity.java`
- 新增：`backend/src/main/java/com/startrail/payment/checkout/infrastructure/persistence/SpringDataCheckoutReturnTokenRepository.java`
- 新增：`backend/src/main/java/com/startrail/payment/checkout/infrastructure/persistence/JpaCheckoutReturnTokenRepository.java`

**接口：**
- `CheckoutReturnTokenService.issue(UUID paymentOrderId)` 返回随机、不透明的返回令牌。
- `CheckoutReturnTokenService.findValidPaymentOrderId(String token)` 只返回令牌绑定的 `paymentOrderId`。
- 数据库保存令牌摘要、支付单 ID、签发时间和 24 小时过期时间；不保存原始令牌。

- [x] 确认实体、仓储与 `checkout_access_token` 的现有 Spring Data/JPA 模式一致，新增 `checkout_return_token` 唯一索引及支付单外键。
- [x] 令牌使用 `SecureRandom` 生成，保存 SHA-256 摘要；查询时先校验固定长度，再按摘要和 `expires_at > now()` 查询。
- [x] 用 Java 编译确认迁移和依赖装配正确；不运行测试。

### 任务 2：后端生成结果页地址并提供专用核实接口

**文件：**
- 修改：`backend/src/main/java/com/startrail/payment/checkout/config/TrustedCheckoutRedirectConfiguration.java`
- 修改：`backend/src/main/java/com/startrail/payment/checkout/application/CheckoutContextQueryService.java`
- 修改：`backend/src/main/java/com/startrail/payment/checkout/interfaces/CheckoutContextResponse.java`
- 修改：`backend/src/main/java/com/startrail/payment/checkout/interfaces/CheckoutContextController.java`
- 新增：`backend/src/main/java/com/startrail/payment/checkout/application/CheckoutReturnRefreshService.java`
- 新增：`backend/src/main/java/com/startrail/payment/checkout/application/CheckoutReturnQueryService.java`
- 修改：`backend/src/main/java/com/startrail/payment/operations/security/OperationsSecurityConfiguration.java`

**接口：**
- 付款上下文响应新增 `returnPageUrl`，由后端可信 `payment.checkout.public-base-url` 与返回令牌构造。
- 新增 `GET /api/v1/checkout/return/{opaqueToken}`：只返回令牌绑定支付单的当前状态和可信 `businessReturnUrl`，不访问 Airwallex，不触发状态更新。
- 新增 `POST /api/v1/checkout/return/{opaqueToken}/refresh`：用返回令牌定位支付单，调用 `PaymentStatusVerificationService.verifyByPaymentOrderId`，随后返回核实后的支付状态和可信 `businessReturnUrl`。
- 返回接口不返回 `client_secret`，也不接受客户端传入回跳 URL。

- [x] 在读取有效付款上下文时签发独立返回令牌；上下文响应携带后端构造的结果页 URL。
- [x] 通过独立 Controller 路由实现返回核实，复用现有状态核验和通知 outbox，不新增另一套支付状态逻辑。
- [x] 提供返回令牌范围内的只读状态查询；返回页先取得可信回跳地址，确保后续核实超时也可安全返回。
- [x] 仅为带 24 小时高熵返回令牌的此 POST 路由豁免 CSRF；令牌本身仍是唯一授权凭证。
- [x] 核对错误路径：无效令牌使用现有 checkout not found 语义；Airwallex 查询失败保持既有支付状态。
- [x] 用 Java 编译确认 API、服务和 DTO 一致；不运行测试。

### 任务 3：Airwallex 返回后核实并回到星辙

**文件：**
- 修改：`frontend/src/shared/api/contracts.ts`
- 修改：`frontend/src/modules/checkout/api.ts`
- 修改：`frontend/src/modules/checkout/CheckoutPage.tsx`
- 修改：`frontend/src/modules/checkout/CheckoutReturnPage.tsx`
- 修改：`frontend/src/app/router.tsx`

**接口：**
- `CheckoutContext` 新增 `returnPageUrl: string`。
- 新增 `refreshCheckoutReturn(opaqueToken)`，调用专用 `POST /api/v1/checkout/return/{opaqueToken}/refresh`。
- 结果页路由使用返回令牌，不复用可读取 `client_secret` 的付款上下文令牌。

- [x] Airwallex.js `successUrl` 使用后端 `returnPageUrl`；保留 HPP 跳转本身，不增加自有付款方式或付款资料输入。
- [x] 结果页进入时只调用一次服务端核实；成功后显示已核实并跳回响应中的星辙订单 URL。
- [x] 结果仍为处理中时显示处理中并返回星辙；核实失败时明确说明未能核实，提供重试和手动返回入口，不假报成功。
- [x] 检查 URL 无查询参数时仍能回到已保存的订单；不信任 URL 提供的任意业务域名。
- [x] 运行 `pnpm --filter frontend build`，确认 TypeScript 与前端构建通过；不运行测试。

### 任务 4：支付状态补查节奏调整到 10 秒

**文件：**
- 修改：`backend/src/main/java/com/startrail/payment/payment/infrastructure/PaymentStatusRefreshScheduler.java`
- 修改：`backend/src/main/java/com/startrail/payment/payment/infrastructure/PaymentStatusRefreshStore.java`
- 修改：`backend/src/main/java/com/startrail/payment/payment/application/PaymentStatusVerificationService.java`
- 修改：`backend/src/main/resources/application-local.yml`
- 修改：`backend/src/main/resources/application-production.yml`

- [x] 将支付状态补查调度周期配置默认值从 60 秒改为 10 秒。
- [x] 将未决支付单发现和领取的创建年龄门槛从 60 秒改为 10 秒。
- [x] 将共享服务商查询最短间隔从 15 秒改为 10 秒，检查人工核实和调度器是否仍共用同一限频字段。
- [x] 将服务商成功查询但状态未终结后的下次检查间隔从 60 秒改为 10 秒。
- [x] 保留每轮最多 10 笔、数据库租约、终态过滤和异常退避；网络错误或 429 不使用固定 10 秒进行无退避重试。
- [x] 在 local 和 production profile 中显式配置 10 秒补查周期，避免环境配置覆盖代码默认值；不配置财务/结算核查周期。
- [x] 用 Java 编译确认调度器和 SQL 变更无编译错误；不运行测试。

### 任务 5：同步架构文档、README 与联调说明

**文件：**
- 修改：`docs/architecture/payment/payment-flow.md`
- 修改：`docs/architecture/reconciliation/payment-status-check.md`
- 修改：`README.md`
- 修改：`docs/operations/deployment.md`

- [x] 写明返回令牌与付款上下文令牌的权限和时效差异、结果页主动核实步骤与失败行为。
- [x] 写明后台支付状态兜底的 10 秒扫描/查询节奏、批次上限、限频和失败退避；明确财务对账周期未随之改变。
- [x] 写明本地沙盒需配置 HTTPS 隧道、Webhook 订阅、支付事件和 secret；Webhook 为主要主动通知，10 秒补查为兜底。
- [x] 核对文档没有声称“结算已核对”，也没有把浏览器返回作为支付成功凭证。

### 任务 6：静态构建复核

**文件：** 无额外代码文件。

- [x] 运行 `./backend/mvnw -f backend/pom.xml -DskipTests compile`（选择 Java 21 编译通过）。
- [x] 运行 `pnpm --filter frontend build`。
- [x] 用 `git diff --check` 检查本轮新增/修改的空白错误，并查看精确 diff；已有运营后台重构等改动继续保留。
- [x] 保留已有未提交改动，不提交、不推送。

## 自审结论

- 设计说明的返回令牌隔离、可信回跳、状态核实、Webhook、10 秒补查、批次限制、错误退避和文档更新均有对应任务。
- 文件路径均依据当前代码结构确认；本轮新增公共迁移 `V119` 后，数据库下一公共迁移号为 `V120`。
- 方案把支付状态补查和结算/财务对账区分开。
- 按项目与当前协作约束不新增、不运行测试，只做编译和构建检查。
