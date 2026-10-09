# 星辙国家/地区传递与 Webhook 操作文档实施计划

> **供执行者使用：** 按任务逐项完成。此计划不包含新增或运行自动化测试；完成后进行代码审阅和项目构建检查。

**目标：** 本地模拟星辙将示例订单的国家/地区代码随金额和币种传入支付项目，支付项目保存该订单快照并按需传给 Airwallex 托管收银台，同时为本地沙盒 Webhook 和生产 Webhook 补齐可照做的操作说明。

**架构：** 使用可选字段 `shopperCountryCode` 表示付款用户国家/地区；本地商品样例明确配置 CNY→CN、USD→US，这只是模拟数据。正式星辙未提供该字段时保持为空，Checkout 初始化时省略 Airwallex `country_code`，不从币种推断，也不再读取固定国家/地区环境变量。

**技术栈：** Java 21、Spring Boot、PostgreSQL、Flyway、React、TypeScript、pnpm；沙盒本地事件转发使用已安装的 `cloudflared` Quick Tunnel。

**设计稿：** `docs/superpowers/specs/2026-10-09-xingzhe-shopper-country-forwarding-design.md`

**执行状态：** 已实施；前端和 Java 构建检查均完成，自动化测试未添加或运行。

## 执行约束

- 在当前工作目录实施；用户此前允许当前目录或隔离工作区，本计划选择当前目录。
- 保留现有 `compose.yaml` 修改；不重置、覆盖或提交用户已有改动。
- 不新增或运行自动化测试；可以运行前端构建和 Java 跳过测试的打包检查。
- 不启动本地服务、不创建 Cloudflare 隧道、不创建 Airwallex Webhook，也不要求用户提供或发送密钥。
- 旧数据库迁移保持不变；公共新迁移使用下一个全局版本 `V104`。
- 文档正文使用中文，`docs/` 路径继续使用英文 kebab-case。

## 文件与职责

| 文件 | 职责 |
| --- | --- |
| `README.md` | 本地 Airwallex 沙盒 Webhook：启动数据库/后端、建立 HTTPS 隧道、拼出通知链接、保存 Webhook secret 并重启后端。 |
| `docs/operations/deployment.md` | 生产域名、HTTPS、公网通知链接、正式账户事件订阅、密钥部署、验收及环境隔离步骤。 |
| `backend/src/main/java/com/startrail/payment/simulator/domain/MockProduct.java` | 为本地商品样例增加显式国家/地区字段。 |
| `backend/src/main/java/com/startrail/payment/simulator/application/MockProductCatalog.java`、`MockBusinessOrderService.java`、`backend/src/main/java/com/startrail/payment/simulator/infrastructure/LocalMockPaymentOrderClient.java` | 配置 CNY 示例 CN、USD 示例 US，并由模拟星辙随订单把该值传给支付集成请求。 |
| `backend/src/main/java/com/startrail/payment/simulator/interfaces/MockProductResponse.java`、`frontend/src/shared/api/contracts.ts` | 将模拟商品国家/地区字段通过产品目录 API 提供给模拟星辙前端。 |
| `backend/src/main/java/com/startrail/payment/payment/interfaces/CreatePaymentOrderRequest.java`、`backend/src/main/java/com/startrail/payment/payment/application/CreatePaymentOrderCommand.java`、`IntegrationPaymentOrderController.java` | 扩展星辙到支付项目的本地签名接口契约，并传递可选字段。 |
| `backend/src/main/java/com/startrail/payment/payment/application/PaymentOrderCreationTransaction.java`、`backend/src/main/java/com/startrail/payment/payment/domain/PaymentOrder.java` | 规范化国家/地区值、验证格式、持久化领域快照并将其纳入幂等请求比较。 |
| `backend/src/main/java/com/startrail/payment/payment/infrastructure/persistence/PaymentOrderEntity.java`、相关持久化映射与 repository、`backend/src/main/resources/db/migration/V104__add_shopper_country_code_to_payment_orders.sql` | 通过可空字段保存订单国家/地区代码，旧支付单保持 null。 |
| `backend/src/main/java/com/startrail/payment/checkout/application/CheckoutContextQueryService.java`、`backend/src/main/java/com/startrail/payment/checkout/interfaces/CheckoutContextResponse.java`、`frontend/src/shared/api/contracts.ts`、`frontend/src/modules/checkout/CheckoutPage.tsx` | 从支付单读取可选国家/地区；有值时传给 Airwallex.js，缺省时省略参数。 |
| `backend/src/main/java/com/startrail/payment/integrations/airwallex/config/AirwallexProperties.java`、`backend/src/main/resources/application-local.yml`、`application-production.yml`、`.env.example` | 移除固定 `AIRWALLEX_CHECKOUT_COUNTRY_CODE` 配置。 |
| `docs/architecture/payment/payment-flow.md`、`docs/superpowers/specs/2026-10-09-local-xingzhe-airwallex-payment-design.md`、国家/地区设计稿 | 记录最终字段、可选规则、本地样例和 Airwallex checkout 映射，保持实现和契约文档一致。 |

## 任务 1：补充本地与生产 Webhook 指南

**改动：** 更新 `README.md` 与 `docs/operations/deployment.md`。

- README 写明本地启动数据库和后端的现有 pnpm 脚本；用 `cloudflared tunnel --url http://localhost:8080` 获取临时 HTTPS 域名；通知链接格式为 `https://<临时域名>/api/v1/webhooks/airwallex`。
- 说明健康检查地址、隧道进程需要在沙盒联调期间持续运行、关闭后临时域名失效；Webhook secret 写入根目录 `.env` 的 `AIRWALLEX_WEBHOOK_SECRET` 后重启后端。不得写入任何实际密钥。
- 部署文档写清生产通知链接使用星辙正式域名和 HTTPS，路由仍为 `/api/v1/webhooks/airwallex`；在星辙生产 Airwallex 账户创建订阅，选择生产账户级“收单”事件，按生产环境独立密钥注入 Webhook secret，并完成公网连通、签名核验、重试、事件去重和告警负责人确认。
- 生产资源归星辙持有；固定出口 IP 只有在 Airwallex API 凭据启用 IP 限制时才配置，不把出口 IP 与 Webhook 入站地址混为一谈。

**完成标准：** 新手可依照 README 得到本地 Webhook 完整链接；生产运维人员可按部署文档识别正式域名、账户、secret 和验收责任。

## 任务 2：扩展本地模拟星辙商品和订单请求

**改动：** `MockProduct` 和对应响应增加 `shopperCountryCode`；商品目录显式配置 CNY 示例 `CN`、USD 示例 `US`；`LocalMockPaymentOrderClient` 将商品国家/地区值放入创建支付单请求。前端产品类型可读取该字段，但不需要增加可编辑国家/地区控件。

**完成标准：** 用户选中任一示例商品时，模拟星辙使用目录中该商品的国家/地区值，随业务订单快照发送；不由币种转换逻辑生成国家代码。

## 任务 3：支付服务接收并持久化可选国家/地区代码

**改动：** 扩展创建支付单 DTO、命令、映射和 `PaymentOrder`；缺省/null 接受为空，有值时 trim 后要求两个大写字母；添加 V104 可空数据库列；幂等重放比较业务订单、金额、币种和国家/地区代码。

**完成标准：** 同一请求标识和完全相同请求返回原支付单；同一请求标识更换国家/地区值时冲突；既有行可通过 V104 保持 null。

## 任务 4：按支付单上下文初始化 Airwallex 收银台

**改动：** Checkout Context 返回可选 `countryCode`；后端从支付单读取，不再读取固定环境变量；React 收银台仅在该值存在时向 `redirectToCheckout` 传 `country_code`。移除 Java 配置属性、环境模板和示例中的 `AIRWALLEX_CHECKOUT_COUNTRY_CODE`。

**完成标准：** CNY 本地示例传 `CN`、USD 示例传 `US`；不含国家/地区的支付单不会发送 `country_code`，且不阻断用户打开托管收银台。

## 任务 5：同步产品、接口与架构文档

**改动：** 更新支付架构链路、原本地模拟契约示例及国家/地区设计稿状态；README 和部署文档链接放入合适的项目入口。所有文档明确生产字段名须按真实星辙契约适配，CNY→CN、USD→US 仅是本地示例。

**完成标准：** 文档不再要求固定国家环境变量，不把币种等同国家/地区，且与最终 DTO、数据库字段及前端可选行为一致。

## 完成后的核对

- 检查变更列表，确认未修改原有迁移且 `compose.yaml` 用户改动仍保留。
- 运行 `pnpm build:web` 与 `./backend/mvnw -f backend/pom.xml -DskipTests package`；不运行自动化测试。
- 核对 README 的本地 URL 由 Quick Tunnel 公网域名拼接后正好落到现有 Webhook 控制器路径；核对生产说明未把本地模拟器或沙盒密钥放入生产流程。
- 不提交 Git，不启动服务或隧道；沙盒实际支付验收留待用户明确启动并配置其本地沙盒凭据后进行。
