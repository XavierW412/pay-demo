# 本地星辙与 Airwallex 沙盒支付链路实施计划

> **执行约定：** 主代理负责沟通、后端、服务边界、接口契约和最终集成审查；两个子代理分别实现互不重叠的前端模块。任务按依赖顺序推进，界面子任务在前端骨架和接口类型稳定后并行开始。

**目标：** 建成一条本地模拟星辙创建业务订单、支付服务通过 Airwallex 沙盒收款、核实后通知星辙并由星辙开通模拟服务的可运行链路。

**架构：** 一个 Java/Spring Boot 模块化单体承载支付服务及仅在本地开发配置启用的星辙模拟 API；React 前端提供模拟星辙购买页和支付引导页。支付服务通过 Airwallex 官方服务端 API 创建 Payment Intent，浏览器使用 Airwallex.js 进入托管收银台；支付结果由已验签 Webhook 和服务端核实驱动。

**技术栈：** React、TypeScript、Vite、Tailwind CSS、shadcn/ui、pnpm；Java 21、Spring Boot 4.1.x、Maven Wrapper、PostgreSQL 18.x、Flyway。版本按技术栈文档推荐线，在初始化时复核兼容稳定版本并锁定补丁版本。

**设计依据：** [本地星辙模拟与 Airwallex 支付链路设计](../specs/2026-10-09-local-xingzhe-airwallex-payment-design.md)

## 全局约束

- 本地契约采用 JSON camelCase；星辙真实协议取得正式资料后另做映射。
- 本地付款契约字段为 `requestId`、`businessOrderId`、`amount`、`currency`；金额使用十进制字符串，首期仅允许 `CNY`、`USD`。
- 相同 `requestId` 和相同请求体重试返回原支付单；同一请求标识对应不同请求体时返回冲突。
- Airwallex `client_secret` 只在对应付款页面临时用于 Airwallex.js；API Key、Client ID、Webhook secret 只保存在服务端配置中。
- 付款结果以验签后的 Airwallex 通知及服务端核实为准；浏览器返回不得更新支付成功状态或开通服务。
- 本地星辙模拟器和沙盒配置只用于开发；生产配置必须拒绝启用模拟器，缺少沙盒配置时失败关闭。
- 模拟购买页的服务名称、价格和订单均标注“本地开发模拟数据，不代表真实星辙业务”。
- 使用 WSL Ubuntu；Node.js 通过 fnm、前端命令使用 pnpm、Java 版本通过 SDKMAN；不生成 npm 锁文件。
- 页面样式使用 Tailwind CSS 和项目设计令牌；不添加 Sass/SCSS 或平行 UI 库。
- 不新增产品内的测试付款、模拟交易管理或测试数据管理功能。

## 评审重点

1. 同一支付创建请求重试不能重复创建内部支付单或 Airwallex Payment Intent；金额或币种变化必须冲突拒绝。
2. Airwallex 支付失败尝试不能被误认作整个 Payment Intent 的最终失败；未知状态不能自动成功。
3. 重复或乱序 Webhook 不能重复通知或重复开通服务；星辙只按唯一事件编号处理一次。
4. 用户返回支付页面不代表付款成功；只有服务端核实的成功状态可触发星辙业务开通。
5. 本地模拟 API/密钥不能进入正式配置；沙盒无法连接时不得回退到正式账户或虚构支付成功。

不新增自动化测试文件或产品测试页面。按任务进行构建、静态检查和明确列出的本地/沙盒手动验收；沙盒验收需要用户自己的 Airwallex 沙盒凭据和已启用的 Webhook。

---

## 文件边界

| 文件/目录 | 职责 |
| --- | --- |
| `frontend/src/app/` | 前端应用装配、路由、全局错误与主题入口；主代理维护。 |
| `frontend/src/shared/api/client.ts` | Axios 基础实例、同源 API 前缀和统一错误处理；主代理维护。 |
| `frontend/src/shared/api/contracts.ts` | 付款上下文、模拟产品、模拟业务订单和状态 API 类型；主代理维护。 |
| `frontend/src/modules/mock-xingzhe/` | 本地模拟星辙的服务选择、下单、订单状态展示；子代理甲维护。 |
| `frontend/src/modules/checkout/` | 付款引导页、Airwallex.js 初始化及等待/返回状态；子代理乙维护。 |
| `frontend/src/shared/api/` | Axios 实例、统一错误处理与 API 契约类型；主代理先创建，子代理只消费。 |
| `backend/src/main/java/com/startrail/payment/` | Spring Boot 模块化单体；主代理维护支付、Airwallex、通知、模拟器模块。 |
| `backend/src/main/resources/db/migration/` | Flyway 版本化 schema。 |
| `compose.yaml` | 本地 PostgreSQL 18 服务，不含任何外部密钥。 |
| `.env.example` | 仅列环境变量名和非敏感说明；不放真实值。 |
| `README.md` | WSL 本地启动、沙盒配置和手动验收路径。 |
| `docs/architecture/README.md` | 入口链接及实现后的实际状态/恢复说明。 |

## Task 1: 建立前后端骨架和本地配置

**执行者：主代理**

**文件：**

- 新建 `pnpm-workspace.yaml`、`.node-version`、`frontend/package.json`、`frontend/vite.config.ts`、`frontend/tsconfig.json`、`frontend/components.json`。
- 新建 `frontend/src/main.tsx`、`frontend/src/app/router.tsx`、`frontend/src/styles/index.css`、`frontend/src/styles/tokens.css`。
- 新建 `frontend/src/shared/api/client.ts`、`frontend/src/shared/api/contracts.ts`。
- 新建 `backend/pom.xml`、`backend/mvnw`、`.mvn/wrapper/` 及 Spring Boot 启动入口。
- 新建 `backend/src/main/resources/application.yml`、`application-local.yml`、`application-production.yml`。
- 新建 `compose.yaml`、`.env.example`；修改现有 `.gitignore`、`README.md` 和技术栈文档中的已锁定版本。

- [ ] 按技术栈文档的推荐线创建 React/TypeScript/Vite 和 Spring Boot 4.1/JDK 21 工程；复核稳定补丁并由锁文件和 Maven 属性固定。
- [ ] 设置 pnpm workspace 只包含 `frontend`；仓库内不生成 `package-lock.json`。
- [ ] 安装已确定用途的前端依赖：React Router、Axios、TanStack Query、Tailwind CSS、shadcn/ui Base UI 基础配置、Lucide React 和 Airwallex `@airwallex/components-sdk`；不添加 Sass/SCSS。
- [ ] 在 Vite 中把 `/api` 和 `/mock-xingzhe` 代理到本地 Spring Boot，开发联调不依赖跨域放宽。
- [ ] 配置本地 PostgreSQL 18 Compose 服务和生产/本地分离配置；生产配置不得包含模拟器开关。
- [ ] 建立前端最小路由、Tailwind/shadcn 基础主题令牌和后端健康端点，不添加无业务占位页面。
- [ ] 运行 `pnpm install`、`pnpm --filter frontend build`、`./backend/mvnw -f backend/pom.xml -DskipTests package`，修正配置和编译错误。

**交付：** WSL 内可分别启动前端、后端和本地数据库；浏览器可打开前端骨架，后端健康端点返回正常。

## Task 2: 实现支付单模型、状态和幂等持久化

**执行者：主代理**

**文件：**

- 新建 `backend/src/main/java/com/startrail/payment/payment/interfaces/`。
- 新建 `backend/src/main/java/com/startrail/payment/payment/application/`。
- 新建 `backend/src/main/java/com/startrail/payment/payment/domain/`。
- 新建 `backend/src/main/java/com/startrail/payment/payment/infrastructure/`。
- 新建 `backend/src/main/resources/db/migration/V1__create_payment_tables.sql`。
- 修改 `backend/src/main/resources/application.yml`。

**接口：**

- `POST /api/v1/integration/payment-orders`。
- 输入：`CreatePaymentOrderRequest(requestId, businessOrderId, amount, currency)`。
- 输出：`CreatePaymentOrderResponse(paymentOrderId, businessOrderId, status, checkoutUrl, createdAt)`。
- 应用服务：`CreatePaymentOrderUseCase.execute(request)`。

- [ ] 建立内部 `payment_order` 表：内部 ID、唯一星辙业务订单号、唯一创建请求号、金额、币种、内部状态、Airwallex Payment Intent ID、时间戳。
- [ ] 用 `BigDecimal` 和固定小数精度解析金额；拒绝零/负数、超过两位小数、非 CNY/USD 币种及空订单号。
- [ ] 用数据库唯一约束保证 `requestId` 和 `businessOrderId` 幂等；同请求体重放返回原记录，不同请求体重放返回 HTTP 409。
- [ ] 创建 REST DTO、应用用例和领域状态转换；Controller 不承载支付业务判断。
- [ ] 按本地模拟契约验签：`X-Local-Request-Timestamp`、`X-Local-Request-Nonce`、`X-Local-Request-Signature`；签名为 HMAC-SHA256 十六进制摘要，输入按 `timestamp + "\\n" + nonce + "\\n" + 原始请求体` 拼接；拒绝时间差超过 5 分钟的请求，并用 10 分钟唯一随机数记录防重放。星辙结果回调使用方向独立的密钥和相同拼接格式。
- [ ] 运行后端打包命令和格式/编译检查；用本地 HTTP 请求核对正常创建、相同请求重放、冲突请求和输入拒绝响应。

**交付：** 只有数据库持久化成功、幂等约束生效后，接口才响应已创建支付单。

## Task 3: 接入 Airwallex 沙盒 Payment Intent 与付款上下文

**执行者：主代理**

**文件：**

- 新建 `backend/src/main/java/com/startrail/payment/integrations/airwallex/auth/`。
- 新建 `backend/src/main/java/com/startrail/payment/integrations/airwallex/payment/`。
- 新建 `backend/src/main/java/com/startrail/payment/integrations/airwallex/config/`。
- 新建 `backend/src/main/java/com/startrail/payment/checkout/`。
- 修改支付创建用例、`application-local.yml`、`.env.example`。

**接口：**

- Airwallex 创建请求映射：`amount`、`currency`、`merchant_order_id`、`request_id`。
- Airwallex 响应映射：`id` → `providerPaymentIntentId`；`client_secret` 仅作为受控付款上下文。
- 托管付款上下文：`CheckoutContext(intentId, clientSecret, currency, countryCode, environment)`，只返回给对应付款引导页。
- 付款引导上下文：`GET /api/v1/checkout/{opaqueToken}/context`。

- [ ] 使用服务端 Client ID/API Key 获取并缓存 Access Token；请求只到 sandbox API 根地址。
- [ ] 创建 Payment Intent 时使用稳定 `request_id` 和星辙 `businessOrderId`；网络结果不明时不得新建另一个请求号。
- [ ] 保存 Airwallex 原始状态与内部状态的映射；未知状态进入 `REQUIRES_REVIEW`。
- [ ] 生成随机不透明付款令牌，令牌只关联一个 Payment Intent 且有 15 分钟有效期；服务端核验令牌后才提供本笔 `CheckoutContext`。
- [ ] 将 API 凭据仅从环境变量读取；日志对 `client_secret`、访问令牌和签名做遮盖。
- [ ] 运行后端打包；配置缺失时确认付款请求失败关闭；在沙盒凭据可用后手动创建一笔付款并核对 Airwallex 返回 ID/状态。

**交付：** 创建支付 API 返回支付项目的 `checkoutUrl`，不会把 Airwallex `client_secret` 返回给模拟星辙。

## Task 4: 本地模拟星辙购买和业务订单界面

**执行者：子代理甲（界面）；主代理提供接口类型并审查**

**文件范围：**仅允许新增或修改 `frontend/src/modules/mock-xingzhe/**`。不得修改共享路由、主题、API 客户端、后端或其他模块。

**消费接口：**

- `GET /mock-xingzhe/api/v1/products` → `MockProduct(productCode, displayName, amount, currency, description)`。
- `POST /mock-xingzhe/api/v1/orders`，请求 `{ productCode }` → `MockBusinessOrder(businessOrderId, paymentOrderId, amount, currency, paymentStatus, checkoutUrl)`。
- `GET /mock-xingzhe/api/v1/orders/{businessOrderId}` → `MockBusinessOrderStatus(businessOrderId, businessStatus, paymentStatus, serviceStatus)`。

模拟服务仅使用两条明确标注虚构的示例目录记录：一个 `CNY` 示例项和一个 `USD` 示例项；创建订单只接收 `productCode`，价格由模拟服务端目录决定。模拟器后端创建业务订单后，使用本地集成凭据签名并通过 HTTP 调用 `POST /api/v1/integration/payment-orders`，不绕过接口直接调用支付用例。

- [ ] 实现本地模拟购买页，显示明确的模拟标识、人民币与美元示例服务、价格和“创建订单”主操作。
- [ ] 下单后显示业务订单号、金额币种、待付款状态和进入支付页面的操作；金额由模拟后端服务目录决定，用户不能在浏览器改金额。
- [ ] 实现订单状态页，区分等待付款、已付款并开通模拟服务、付款处理中、失败/取消和暂时无法确认。
- [ ] 使用 `frontend/src/shared/api/`；所有状态都有文本标签，不只依赖颜色；错误、加载、空状态和窄屏布局完整。
- [ ] 应用 `docs/engineering/frontend/design-principles.md`、`visual-style.md`；页面所有示例均注明非真实星辙数据。
- [ ] 运行前端构建；向主代理交付文件清单、所消费接口、未解决项和浏览器检查结果。

**交付：** 模拟星辙用户能从示例服务进入自己的订单状态页；组件不含真实业务商品或生产测试入口。

## Task 5: 付款引导页和 Airwallex 托管跳转界面

**执行者：子代理乙（界面）；主代理提供付款上下文接口并审查**

**文件范围：**仅允许新增或修改 `frontend/src/modules/checkout/**`。不得修改共享路由、主题、API 客户端、后端或模拟星辙模块。

**消费接口：**

- 从 `/checkout/:opaqueToken` 向支付服务请求单笔 `CheckoutContext`。
- 使用 Airwallex.js `init({ env, enabledElements: ['payments'] })` 和 `payments.redirectToCheckout({ intent_id, client_secret, currency, country_code })`。

- [ ] 实现金额/币种、订单关联号的付款确认摘要；金额只从服务端上下文读取，不可编辑。
- [ ] 加载中显示清晰进度；SDK 初始化失败显示可恢复提示和联系星辙业务平台的说明，不显示假成功。
- [ ] 按 sandbox 环境启动 Airwallex.js；生产切换只能来自后端部署配置，不能来自 URL 参数或用户选项。
- [ ] 处理 Airwallex 返回后的“正在核实/已收到结果”体验；不得用前端返回参数将支付状态设置为成功。
- [ ] 使用项目视觉令牌和 `shadcn/ui`；第三方付款信息输入仍由 Airwallex 托管收银台承担。
- [ ] 运行前端构建；在 Airwallex 沙盒页面打开时检查跳转、错误和窄屏表现，并向主代理交付结果。

**交付：** 用户从模拟星辙订单进入支付服务，再跳转到 Airwallex 托管付款页；付款资料不经过本项目前端表单。

## Task 6: Airwallex Webhook、可靠通知与模拟业务开通

**执行者：主代理**

**文件：**

- 新建 `backend/src/main/java/com/startrail/payment/integrations/airwallex/webhook/`。
- 新建 `backend/src/main/java/com/startrail/payment/notification/`。
- 新建 `backend/src/main/java/com/startrail/payment/simulator/`。
- 新建公共迁移 `V4`、`V102`—`V103`，以及 local 专属迁移 `V100`、`V101`；所有版本使用单调递增的全局序号。
- 修改支付状态模型、Airwallex 状态映射和本地配置。

**接口：**

- Airwallex Webhook：`POST /api/v1/webhooks/airwallex`。
- 模拟星辙结果接收：`POST /mock-xingzhe/api/v1/payment-events`。
- 模拟星辙用户 API：`GET /mock-xingzhe/api/v1/products`、`POST /mock-xingzhe/api/v1/orders`、`GET /mock-xingzhe/api/v1/orders/{businessOrderId}`。
- 通知载荷：`eventId`、`eventType`、`businessOrderId`、`paymentOrderId`、`status`、`amount`、`currency`、`occurredAt`。

- [x] 在解析 JSON 前，按 Airwallex 官方协议用原始请求体和签名头验签；拒绝无效签名且不产生业务副作用。
- [x] 对 Airwallex 事件 `id` 建唯一约束；先持久化事件，再快速返回 HTTP 200，后台处理并创建待发送 outbox。
- [x] 后台处理时读取 Payment Intent 当前状态，核对 ID/金额/币种/业务订单号；未知或冲突状态不触发业务成功。
- [x] 以退避策略重试模拟星辙通知；按 `eventId` 去重；只有已核实 `SUCCEEDED` 事件能使模拟业务订单已付款和服务已开通。
- [x] 模拟接口只在 local profile 装配；production profile 有模拟器禁用检查；模拟表结构只在 local migration 中建立。
- [x] Java 干净打包成功；数据库迁移已应用；健康和模拟商品接口返回 200；无效 Webhook 签名返回 401；有效事件重复投递只保存一个并进入 `PROCESSED`。
- [ ] 使用真实 Airwallex 沙盒凭据手动走通 Payment Intent、托管收银台、成功 Webhook 和模拟服务开通；凭据及 Webhook 配置待准备。

**交付：** 本地服务端事件链和可靠通知机制已实现；真实沙盒付款与开通仍待 Airwallex 沙盒凭据验证。

## Task 7: 本地启动说明与端到端手动验收

**执行者：主代理**

**文件：**

- 修改 `README.md`、`docs/operations/deployment.md`、`docs/architecture/README.md`、`docs/roadmap/development-roadmap.md`。
- 检查 `.env.example`、`.gitignore`、`compose.yaml`。

- [ ] 记录 WSL 内数据库启动、后端本地配置、前端启动和停止方式；所有命令按 AGENTS 使用 pnpm 与 Maven Wrapper。
- [ ] 记录 Airwallex 沙盒 Client ID/API Key/Webhook secret 的本地环境变量名称及 Webhook 隧道配置方式，不在文档或 Git 写入实际密钥。
- [ ] 手动验收 CNY、USD 两种示例订单；从购买、Payment Intent、Airwallex 托管付款、Webhook 核实、星辙业务开通核对订单关联、金额、币种和状态。
- [ ] 手动检查重复下单请求、重复 Webhook、通知失败恢复、付款未完成和用户直接返回状态页时的业务结果。
- [ ] 更新文档中哪些环节已通过构建、哪些通过真实沙盒联调、哪些仍待 Airwallex 账户/星辙资料确认；不得将沙盒结果表述为生产验收。

**交付：** 新开发者能按 README 在 WSL 启动并完成沙盒支付全链路；生产星辙协议仍清楚标记为待取得资料后适配。

## 并行执行顺序

1. 主代理完成任务 1，并提供锁定的前端路由约定、设计令牌、API 客户端和契约类型。
2. 主代理完成任务 2、3，形成可供 UI 使用的支付创建与付款上下文接口。
3. 子代理甲和子代理乙分别完成任务 4、5；二者只写各自模块目录，不互相编辑文件。
4. 主代理执行任务 6、7，接入模拟业务端点、验证服务端通知、集成两块界面并完成全链路复核。

主代理对每个子代理交付进行差异审查，检查其文件范围、产品边界、API 使用、视觉规范和运行结果；不让两个子代理同时修改同一文件。

## 本计划明确不包含

- 生产运营后台交易列表/详情的代码实现；已有 Figma 页面继续作为后续后台阶段的视觉依据。
- 员工账号、登录授权、退款后台操作和人工状态处理。
- 退款 API 实际调用、结算 API、自动对账和告警平台。
- Adyen、订阅支付、生产域名/服务器购买或正式账户切换。
