# 支付系统监控与诊断能力实施计划

> **历史计划，已停止执行：** 用户已决定移除客户端事件收集、用户诊断面板及运营诊断事件入口。支付服务运行监控仍保留，当前边界见[支付服务运行监控](../../architecture/operations/payment-observability.md)。本文其余诊断事件任务不再执行。

**目标：** 让用户和员工能凭诊断编号定位支付前端、后端和支付链路问题，并在星辙控制的环境中查看告警与诊断记录。

**架构：** 支付服务生成请求/诊断关联编号，前端经受控接口提交脱敏事件，后端把支付专用日志、指标和追踪导出到可替换的观测收集层。支付项目数据库只保存可查询、可处理的技术事件摘要；生产观测数据留在星辙控制的自托管监控环境。监控故障不阻断支付，也没有权限修改支付或退款状态。

**技术栈：** Java 21、Spring Boot、PostgreSQL、React、TypeScript、pnpm、OpenTelemetry；监控部署建议 Grafana、Loki、Tempo、Prometheus 和 Grafana Alloy。观测组件版本及生产部署形态在星辙云平台确认后锁定。

**设计说明：** `docs/superpowers/specs/2026-10-10-observability-diagnostics-design.md`

## 全局约束

- 通讯、界面和文档正文使用简体中文；`docs/` 文件名使用英文小写 kebab-case。
- 命令在 WSL Ubuntu 运行；Node.js 相关命令使用 pnpm，不生成 `package-lock.json`。
- 不将 Airwallex/星辙密钥、认证头、Cookie、付款资料、完整请求体或 Webhook 原文写入诊断数据。
- 支付状态以 Airwallex 服务端核实为准；星辙通知状态和服务状态分别记录；诊断系统不可改写任何业务状态。
- 指标标签只使用有界字段；不以支付单号、业务订单号、用户或追踪编号作为指标标签。
- 监控系统不可用不能阻断支付、Webhook、退款或星辙通知；诊断数据缓冲必须有上限。
- 用户报告接口是匿名入口，必须有字段白名单、请求体限制和限流；不保留原始客户端 IP。
- 生产诊断数据及监控服务部署在星辙控制的环境中；外部告警渠道须由星辙批准，消息仅含诊断编号和受保护链接。
- 遵守当前协作约束：不新增或运行自动化测试，不启动本地服务，不提交或推送代码，不覆盖现有未提交变更。

## 文件职责图

| 文件/目录 | 责任 |
| --- | --- |
| `backend/src/main/java/com/startrail/payment/diagnostics/` | 诊断事件接收、业务服务、查询、处理状态和权限边界 |
| `backend/src/main/java/com/startrail/payment/shared/interfaces/ApiErrorResponse.java` | 在现有标准错误响应中暴露安全的请求/诊断编号 |
| `backend/src/main/java/com/startrail/payment/shared/interfaces/ApiExceptionHandler.java` | 统一生成错误响应并记录未处理异常，不向用户泄露堆栈 |
| `backend/src/main/resources/db/migration/V121__create_diagnostic_incidents.sql` | 持久化脱敏事件摘要和处理记录；编号接续当前未提交的 V120 迁移 |
| `backend/src/main/resources/db/migration/V122__add_diagnostic_browser_family.sql` | 为诊断事件增加受限浏览器类别字段 |
| `backend/src/main/resources/db/migration/V123__aggregate_duplicate_diagnostic_incidents.sql` | 保存事件组计数和最近接收时间；补充指纹窗口查询索引 |
| `frontend/src/shared/diagnostics/` | 前端异常捕获、字段过滤、错误提交和用户可复制的诊断编号 |
| `frontend/src/shared/api/client.ts` | 读取响应关联编号并在 API 错误中保留该编号 |
| `frontend/src/app/router.tsx`、`frontend/src/main.tsx` | 安装应用级错误边界和错误提示衔接 |
| `frontend/src/modules/operations/OperationsDiagnosticsPage.tsx` | 运营诊断列表、筛选、详情和处理状态 |
| `frontend/src/modules/operations/api.ts`、`OperationsLayout.tsx` | 诊断查询 API 与运营导航接入 |
| `backend/src/main/java/com/startrail/payment/operations/security/OperationsSecurityConfiguration.java` | 匿名上报与已认证诊断查询/处理的权限隔离 |
| `observability/`、`compose.observability.yaml` | 本地自托管收集器、指标/日志/追踪存储、仪表盘和告警规则 |
| `docs/architecture/operations/observability-diagnostics.md` | 实际模块、数据、安全、恢复和运维职责 |
| `docs/engineering/technology-stack.md`、`docs/operations/deployment.md`、`README.md` | 同步选型状态、启动方式、生产部署及敏感数据边界 |

## 重点审查项

- **匿名用户提交超长/畸形报告或高频重复上报：** 后端白名单校验、限流和大小限制生效；超过限制时返回明确拒绝，不能造成无界数据库增长。（任务 2 手工验证）
- **URL 带支付令牌、用户文本包含密钥或付款资料：** 前端先去除查询参数和片段，后端再次过滤/限制字段；运营详情和日志不显示认证数据。（任务 2、3 手工检查）
- **网络断开或观测收集器停机：** 前端保留可复制的诊断编号；支付 API、Webhook、退款和星辙通知照常处理，监控队列不无限堆积。（任务 1、4 手工验证）
- **重复/乱序 Webhook、重复通知或重复错误事件：** 追踪只记录处理链路，不驱动业务状态。自动错误按类别、严重度、模块、路由模板和应用版本计算指纹，在最近一次上报后的 15 分钟滑动窗口内聚合，保留发生次数和最近接收时间；用户主动提交的说明始终独立保存。事务级指纹锁避免并发创建重复记录。已完成事件不重新打开，新的上报创建新记录。
- **只读所有者尝试处理诊断事件：** 服务端拒绝并留痕；普通员工处理诊断不会获得退款确认、员工管理或支付状态修改权限。（任务 5 手工验证）

---

### 任务 1：请求编号、诊断编号与脱敏错误响应

**文件：**
- 新增：`backend/src/main/java/com/startrail/payment/diagnostics/interfaces/RequestCorrelationFilter.java`
- 新增：`backend/src/main/java/com/startrail/payment/diagnostics/application/DiagnosticIdGenerator.java`
- 修改：`backend/src/main/java/com/startrail/payment/shared/interfaces/ApiErrorResponse.java`
- 修改：`backend/src/main/java/com/startrail/payment/shared/interfaces/ApiExceptionHandler.java`
- 修改：`backend/src/main/resources/application.yml`
- 新增：`backend/src/main/resources/logback-spring.xml`

**接口：**
- 每个 HTTP 请求由服务端生成随机 `requestId`，经 `X-Request-Id` 响应头返回，并写入日志 MDC。
- 每次未处理错误生成随机 `diagnosticId`；错误 JSON 增加 `diagnosticId`，继续保留现有 `code`、`message`、`timestamp`。
- 已知业务拒绝保留原业务错误码，不记为未处理系统异常；服务端日志仍记录诊断编号和异常类别。
- 信任代理转发头只在可信代理配置中启用；默认忽略外部传入的同名请求编号。

- [ ] 盘点所有 `ApiExceptionHandler` 分支与错误 DTO 调用，保持现有字段和 HTTP 状态兼容，只新增 `diagnosticId`。
- [ ] 新增请求过滤器：生成 UUID 请求编号、写入 MDC、写入响应头，并在请求完成后清理 MDC。
- [ ] 新增随机诊断编号生成器，禁止从用户输入直接接受内部诊断编号作为授权凭证。
- [ ] 配置结构化日志字段：时间、级别、服务名、环境、请求编号、追踪编号、诊断编号、路由模板、HTTP 状态、异常类别。
- [ ] 配置日志过滤器，屏蔽认证头、Cookie、密钥、密码和请求/响应正文；错误响应不得包含堆栈。
- [ ] 用 `./backend/mvnw -f backend/pom.xml -DskipTests compile` 检查后端编译；不运行测试。

### 任务 2：后端诊断事件接收与事件摘要

**文件：**
- 新增：`backend/src/main/resources/db/migration/V121__create_diagnostic_incidents.sql`
- 新增：`backend/src/main/java/com/startrail/payment/diagnostics/application/DiagnosticIncidentService.java`
- 新增：`backend/src/main/java/com/startrail/payment/diagnostics/application/DiagnosticIncidentRepository.java`
- 新增：`backend/src/main/java/com/startrail/payment/diagnostics/infrastructure/persistence/JdbcDiagnosticIncidentRepository.java`
- 新增：`backend/src/main/java/com/startrail/payment/diagnostics/interfaces/ClientDiagnosticController.java`
- 新增：`backend/src/main/java/com/startrail/payment/diagnostics/interfaces/OperationsDiagnosticController.java`
- 新增：`backend/src/main/java/com/startrail/payment/diagnostics/interfaces/DiagnosticIncidentRequest.java`
- 新增：`backend/src/main/java/com/startrail/payment/diagnostics/interfaces/DiagnosticIncidentResponse.java`
- 修改：`backend/src/main/java/com/startrail/payment/operations/security/OperationsSecurityConfiguration.java`

**接口：**
- `POST /api/v1/diagnostics/client-events`：匿名接收受限前端事件，可包含可选用户说明；响应只返回随机 `diagnosticId` 和接收时间。
- `GET /api/v1/operations/diagnostics`：已认证员工分页查询事件。
- `GET /api/v1/operations/diagnostics/{diagnosticId}`：查询单条事件摘要、关联编号和处理历史。
- `POST /api/v1/operations/diagnostics/{diagnosticId}/acknowledge`、`/notes`、`/resolve`：处理技术事件，不触发支付/退款行为。
- 状态限定为 `OPEN`、`ACKNOWLEDGED`、`RESOLVED`；每次处理记录操作者、时间、动作和备注。

- [ ] 创建独立诊断事件表，字段包含随机诊断编号、事件指纹、发生时间、环境、版本、类别、严重级别、模块、请求/追踪编号、可空内部支付单 ID、脱敏摘要、状态和处理时间。
- [ ] 创建独立处理历史表；保留现有支付审计记录，不把诊断事件混进支付/退款状态表。
- [ ] 服务端执行字段白名单、错误摘要长度限制、用户说明长度限制、URL 参数拒收和请求体大小限制；不保存原始 IP、Cookie、请求正文或任意标签。
- [ ] 对匿名端点增加可配置的速率限制；按经可信网络边界处理后的地址计算短期散列桶，散列密钥轮换并不落原始地址；没有可信代理时不信任 `X-Forwarded-For`。
- [ ] 诊断查询和处理操作从 Spring Security 获取当前账号身份；所有者只读，员工可确认/备注/完成；不授权支付操作。
- [x] 迁移 V121 接续 V120，V122 增加浏览器类别，V123 增加聚合计数与最近接收时间；不改写已存在迁移。已完成 Java 21 编译和 SQL 静态检查，未启动数据库、未运行测试。

### 任务 3：前端错误收集、用户说明与编号显示

**文件：**
- 新增：`frontend/src/shared/diagnostics/client-diagnostics.ts`
- 新增：`frontend/src/shared/diagnostics/DiagnosticErrorBoundary.tsx`
- 新增：`frontend/src/shared/diagnostics/DiagnosticErrorPanel.tsx`
- 修改：`frontend/src/shared/api/client.ts`
- 修改：`frontend/src/app/router.tsx`
- 修改：`frontend/src/main.tsx`
- 修改：`frontend/vite.config.ts`
- 修改：`frontend/package.json`（仅当私有 Source Map 上传流程确需构建插件时）

**接口：**
- `client-diagnostics.ts` 暴露 `captureClientError(error, context)` 与 `submitDiagnosticReport(report)`；只传经过允许字段筛选的对象。
- API 错误类型保留 `diagnosticId` 和 `requestId`；错误面板提供复制编号、重试/返回入口及可选文字说明。
- 前端诊断上报只能调用任务 2 的匿名事件接口；不得在浏览器初始化监控后端密钥或服务端 DSN 凭证。

- [ ] 为应用根节点增加错误边界及 `window.onerror`/`unhandledrejection` 捕获；过滤预期的表单错误和业务 4xx。
- [ ] 为 Axios 响应错误读取 `X-Request-Id` 与错误正文 `diagnosticId`；网络不可达时生成本地随机编号并标记“尚未上报”。
- [ ] 上报仅含事件类别、页面路由模板、版本、浏览器大类、请求编号和截断后的错误摘要；主动报告自由文本前显示“不要填写银行卡、密码或密钥”等提示。
- [ ] URL 仅传路由模板，去掉查询参数和片段；禁止发送页面 DOM、截图、表单值、LocalStorage、SessionStorage 和 Cookie。
- [ ] 为生产构建生成私有 Source Map 并确保静态站点拒绝 `.map` 公网访问；构建产物按版本留存于受保护部署卷。若当前 Vite 输出不支持所需版本映射，先更新计划而不是公开 Source Map。
- [ ] 运行 `pnpm --filter frontend build`，人工检查主要错误页面状态；不运行测试。

### 任务 4：后端追踪和支付专用指标

**文件：**
- 新增：`backend/src/main/java/com/startrail/payment/diagnostics/infrastructure/PaymentTelemetry.java`
- 新增：`backend/src/main/java/com/startrail/payment/diagnostics/infrastructure/TelemetryConfiguration.java`
- 修改：`backend/src/main/java/com/startrail/payment/payment/application/PaymentOrderCreationService.java`
- 修改：`backend/src/main/java/com/startrail/payment/payment/application/PaymentStatusVerificationService.java`
- 修改：`backend/src/main/java/com/startrail/payment/integrations/airwallex/webhook/AirwallexWebhookEventService.java`
- 修改：`backend/src/main/java/com/startrail/payment/notification/XingzheOutboxDispatcher.java`
- 修改：`backend/src/main/java/com/startrail/payment/refund/application/RefundExecutionService.java`
- 修改：`backend/src/main/java/com/startrail/payment/refund/application/RefundStatusReconciliationJob.java`
- 修改：`backend/src/main/java/com/startrail/payment/reconciliation/infrastructure/PaymentReconciliationScheduler.java`
- 修改：`backend/pom.xml`（接入 Prometheus registry）、`backend/src/main/resources/application.yml`、`backend/src/main/resources/application-local.yml`

**接口：**
- `PaymentTelemetry` 提供低基数计数器与耗时记录方法，例如 `recordPaymentIntentCreation(result)`、`recordPaymentVerification(result)`、`recordWebhookProcessing(result)`、`recordOutboxDelivery(result)`、`recordRefundVerification(result)`。
- Spring Boot Actuator/Micrometer 暴露低基数指标；OpenTelemetry Java Agent 提供自动追踪，Micrometer Observation 为关键业务步骤增加自定义 Span；只添加低敏属性和内部关联编号，不添加订单/用户 ID 作为指标标签。
- OTLP 导出地址和采样比例通过环境配置控制；未配置收集器时以无导出模式运行，不让业务代码依赖监控平台可用性。

- [ ] 复核 OpenTelemetry Java Agent 与 Java 21、Spring Boot 4.1.1 的兼容性并固定 Agent 版本；使用 Spring Boot 已有 Actuator/Micrometer 接入应用观测，不升级 Spring Boot 或其他依赖。
- [ ] 增加与 Spring Boot 依赖管理兼容的 `micrometer-registry-prometheus`，只在内部网络暴露 `/actuator/prometheus`，不扩大公网 Actuator 暴露范围。
- [ ] 为支付创建、Webhook 接收/处理、主动状态核查、星辙通知投递、退款执行/核查和对账调度添加少量命名稳定的 Span 与错误结果计数。
- [ ] 显式启用 OpenTelemetry Java Agent 对 Micrometer Observation 的桥接，并人工确认一次支付链路能在本地追踪存储中按 trace ID 查看完整步骤。
- [ ] 将请求编号、诊断编号通过 MDC/追踪上下文关联；跨 Airwallex/星辙边界不要求外部系统继续传播内部 Span，使用受控事件编号关联。
- [ ] 从现有交易与 outbox 表生成低基数的待核实数量、通知积压数、退款未知数和对账差异数；指标采集查询须分页/聚合，不能把全表扫描放在每次请求中。
- [ ] 逐项核查指标属性没有支付单号、业务订单号、Webhook 原文或用户数据；关闭观测导出时支付流程仍按原有逻辑运行。
- [ ] 用 Java 编译检查依赖、配置和方法签名；不运行测试。

### 任务 5：星辙环境内监控栈、仪表盘和告警规则

**文件：**
- 新增：`compose.observability.yaml`
- 新增：`observability/alloy/config.alloy`
- 新增：`observability/prometheus/prometheus.yml`
- 新增：`observability/prometheus/alerts/payment-rules.yml`
- 新增：`observability/grafana/provisioning/datasources/datasources.yml`
- 新增：`observability/grafana/dashboards/payment-overview.json`
- 新增：`observability/grafana/dashboards/payment-trace.json`
- 新增：`.env.observability.example`

**接口：**
- 服务应用只向配置的本地 OTLP 收集端发送观测数据；Grafana、Prometheus、Loki 和 Tempo 仅能由受控运维网络访问。
- 仪表盘展示 API 可靠性、支付创建/核查、Webhook、通知积压、退款核查、数据库与调度健康；不显示全量个人/支付资料。
- 告警包括 5xx/延迟、Webhook 验签或处理失败、待核实支付过久、通知积压、退款结果未知和对账差异；具体阈值走配置，不硬编码成支付规则。

监控栈建议使用 Alloy 收集和转发观测数据，Prometheus 保存指标，Loki 保存日志，Tempo 保存追踪，Grafana 提供仪表盘和告警；这些组件可在星辙环境内自托管。Grafana 官方资料说明 Alloy 可处理 OpenTelemetry/Prometheus 数据并连接自托管数据后端；Tempo 支持以 Alloy 或 OpenTelemetry Collector 接收追踪。[Alloy 组件说明](https://grafana.com/docs/alloy/latest/introduction/how-alloy-works/)、[Tempo 收集器说明](https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/set-up-collector/)、[告警通知配置](https://grafana.com/docs/grafana/latest/alerting/fundamentals/notifications/)。具体镜像版本和资源需求在实施及星辙部署条件确认时锁定。

- [ ] 建立独立的观测 Compose 文件和持久卷，服务命名、端口与当前 `compose.yaml` 的 PostgreSQL/Postgres 5433、支付应用 8080 不冲突。
- [ ] 选用 Alloy OTLP 接收器转发日志/追踪/指标到 Loki、Tempo、Prometheus；所有镜像版本使用固定标签，首次实施前复核官方部署说明和许可证。
- [ ] 将 Grafana 绑定内部网络并配置独立运维凭证；生产禁止匿名访问，后台链接不包含管理员密码或数据库凭证。
- [ ] 为支付指标建立概览和关联跳转；告警消息只包含规则名、严重级别、诊断编号和受保护链接，不带订单号、用户资料或完整异常文本。
- [ ] 本地示例配置不含真实密钥；`.env.observability.example` 仅列非敏感变量名和本地默认值。
- [ ] 只做 Compose 配置静态检查和文档核对；除非用户另行要求，不启动新增监控容器。

### 任务 6：运营后台技术诊断工作台

**文件：**
- 新增：`frontend/src/modules/operations/OperationsDiagnosticsPage.tsx`
- 修改：`frontend/src/modules/operations/api.ts`
- 修改：`frontend/src/modules/operations/OperationsLayout.tsx`
- 修改：`frontend/src/app/router.tsx`
- 新增：`backend/src/main/java/com/startrail/payment/operations/application/OperationsDiagnosticsQueryService.java`
- 新增：`backend/src/main/java/com/startrail/payment/operations/application/OperationsDiagnosticsActionService.java`
- 修改：`backend/src/main/java/com/startrail/payment/operations/application/OperatorAuditService.java`

**接口：**
- 前端提供事件列表、诊断编号搜索、严重度/模块/状态/时间筛选、事件详情、请求编号、可空内部支付单跳转、备注、确认和完成动作。
- `STAFF_OPERATOR`、`STAFF_ADMIN` 和 `TEMP_TESTER` 可查询、确认、备注及完成技术诊断；`OWNER_READONLY` 仅查看。临时测试员仍不能管理员工；所有者不能处理；后端逐请求校验角色。
- 详情中的追踪链接采用服务端配置的 Grafana 基址与 trace ID 构造；浏览器不接收 Grafana 管理凭证，缺少监控部署时展示“追踪存储未配置”。

- [ ] 在运营导航新增“技术诊断”，保持与业务“对账与异常”分开，避免把技术告警误认为资金差异。
- [ ] 页面实现前按项目规范调用 `impeccable` 与 `ui-styling` 技能，沿用现有运营后台设计系统并完整考虑加载、空、错误、无权限和敏感文本隐藏状态。
- [ ] 实现分页检索、事件详情和处理历史；所有文本转义，堆栈和自由文本按角色限制且默认折叠/脱敏。
- [ ] 操作确认/备注/完成使用现有审计服务记录人员、对象、动作和结果；拒绝所有者处理请求。
- [ ] 可从技术事件跳转到关联内部支付单；跳转只是查看，不执行支付刷新、退款或通知重试。
- [ ] 用 Java 编译和 `pnpm --filter frontend build` 检查；人工按员工、临时测试员、只读所有者角色核对可见/可操作状态，不运行测试。

### 任务 7：部署文档、隐私和恢复说明

**文件：**
- 新增：`docs/architecture/operations/observability-diagnostics.md`
- 修改：`docs/architecture/README.md`
- 修改：`docs/product/product-spec.md`
- 修改：`docs/engineering/directory-structure.md`
- 修改：`docs/engineering/technology-stack.md`
- 修改：`docs/operations/deployment.md`
- 修改：`README.md`

- [ ] 记录 request ID/diagnostic ID、前端事件、追踪、指标、告警、角色权限、数据保留和监控故障恢复语义。
- [ ] 写明本地观测栈的独立启动/停止方式、端口、数据卷与清理边界；禁止用户误删支付数据库卷。
- [ ] 写明生产必须由星辙控制监控数据、监控账号、保留期限、备份和告警接收渠道；第三方告警目标须单独批准。
- [ ] 更新技术栈状态：OpenTelemetry 标准已选；Grafana OSS 组件仅为推荐的自托管部署方案，最终镜像版本及生产形态需按环境复核。
- [ ] 按产品说明检查所有文档未把监控事件写成支付/退款状态事实，也未将本地模拟星辙放入生产部署。

### 任务 8：最终静态构建与安全复核

**文件：** 本轮变更的全部代码、配置和文档。

- [ ] 选择 Java 21，运行 `./backend/mvnw -f backend/pom.xml -DskipTests compile`。
- [ ] 运行 `pnpm --filter frontend build`。
- [ ] 用 YAML/Compose 静态解析器检查观测配置，不启动服务，不连接 Airwallex，不访问真实数据。
- [ ] 搜索新增文件中的 `Authorization`、`Cookie`、`secret`、`password`、`client_secret` 和 `api_key` 等字段，逐个确认没有日志正文泄漏路径；配置变量名可保留，真实值不可出现。
- [ ] 手工检查匿名入口限流、超长事件、带敏感 URL 查询参数、监控停机和只读账号越权场景；验证付款状态、退款状态和星辙通知不被监控逻辑改写。
- [ ] 执行 `git diff --check` 并审阅精确 diff；保留所有既有未提交工作，不提交、不推送、不启动应用或数据库。

## 自审结论

- 产品设计中的前端异常、用户说明、后端关联日志、支付链路追踪、指标告警、运营诊断入口、审计、权限和恢复要求均由任务 1 至任务 8 覆盖。
- V121 迁移紧接 V120 临时账号迁移；实现时发现 V122 已用于浏览器类别字段，重复事件聚合使用 V123。后续迁移从 V124 顺延，不改已应用迁移。
- 业务 API 只新增诊断关联字段，不把监控 API 纳入支付/退款状态变更服务。
- 监控系统供应商数据留在星辙环境，但实际云平台、存储规模、告警联系人和保留期限仍需在部署阶段确认；不影响本地先实现可替换采集接口与诊断查询。
- 浏览器错误初期采用项目自有匿名诊断接口，不依赖仍属实验状态的浏览器 OpenTelemetry SDK；后端采集按 OpenTelemetry 标准导出。
- 计划没有安排自动化测试或启动服务；静态编译、构建和受控手工验收用于降低上线风险。
