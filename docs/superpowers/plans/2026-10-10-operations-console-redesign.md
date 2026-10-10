# 星辙支付运营后台重做实施计划

> **执行要求：**实施时按任务顺序推进；前后端接口以本文和设计文档定义为准，页面不能绕过服务端权限。
> **执行技能：**并行实施时使用 `superpowers:subagent-driven-development`；主代理独立实施时使用 `superpowers:executing-plans`。

**目标：**把支付运营后台改造成待办优先、状态可追溯、操作有权限边界的运营工作区，覆盖交易、异常、退款核实、审计和员工管理。

**架构：**继续使用现有 React + TypeScript + TanStack Query 前端和 Spring Boot 模块化单体。后端新增分页查询与员工生命周期接口，复用支付核实、退款状态服务、通知 outbox 和现有账号/审计表；前端按工作台、交易、异常、审计、员工五个任务入口重组。

**依据文档：**
- `docs/superpowers/specs/2026-10-10-operations-console-redesign-design.md`
- `docs/architecture/integration/payment-interface-contracts.md`
- `docs/architecture/operations/operations-console.md`
- `docs/architecture/refund/refund-execution.md`

## 全局约束

- 生产后台只展示支付服务真实记录，不提供模拟支付、测试交易或手工修改支付/退款成功状态。
- 星辙负责业务订单、退款审批和服务开通；支付项目只执行已批准指令并核实结果。
- `OWNER_READONLY` 只能读取；`STAFF_OPERATOR` 可处理获准的核查、通知重试和备注；`STAFF_ADMIN` 才能确认退款及管理员工。
- `STAFF_ADMIN` 只能管理 `STAFF_OPERATOR`，不能停用自己，必须保护至少一名启用的 `STAFF_ADMIN`。
- 支付、退款、星辙通知和结算分开记录；Airwallex 结算报告未接入时，到账金额和手续费保持未接入，不做估算。
- 星辙生产 API、身份源、回执和正式字段未确认；只实现已存在的本地模拟协议和明确的适配层。
- 使用现有依赖和模块边界；没有必要不增加依赖，不重构模拟星辙、付款跳转页或支付核心。
- 代码和文档使用中文说明；目录和文件名使用英文 kebab-case。Node 项目只用 `pnpm`。
- 不提交 Git、不推送远端；保留开始前已有的工作区改动。
- 不新增或运行自动化测试；本计划收尾只执行前端构建和后端编译构建。

## 重点审查的边界

1. Airwallex Webhook 重复、乱序、查询超时：页面显示核实状态和时间，不能把收到事件本身显示为支付成功。
2. 退款结果未知或服务商列表未找到记录：只执行查询，不创建第二笔退款；未知金额继续占用可退款额度。
3. 星辙通知 HTTP 成功但业务订单尚未更新：分别显示“通知已送达”和“星辙业务状态待确认”。
4. 结算报表未连接、空列表和查询失败：分别呈现“未接入”“暂无记录”“读取失败”，不使用同一空白/零值状态。
5. 账号停用与当前登录会话：服务端拒绝停用账号的后续请求，且不能让最后一名启用管理员被停用。

## 文件职责地图

### 后端现有文件

- `backend/src/main/java/com/startrail/payment/operations/application/OperationsTransactionQueryService.java`：交易详情、列表查询和分页模型。
- `backend/src/main/java/com/startrail/payment/operations/application/OperationsFinanceOverviewQueryService.java`：现有币种资金摘要，保持现有来源口径。
- `backend/src/main/java/com/startrail/payment/operations/application/OperationsRefundQueryService.java`：退款详情查询模型。
- `backend/src/main/java/com/startrail/payment/operations/application/OperationsPaymentActionService.java`：支付核实、通知重试和备注动作。
- `backend/src/main/java/com/startrail/payment/operations/application/OperatorAuditService.java`：写入后台操作审计。
- `backend/src/main/java/com/startrail/payment/operations/application/OperatorInvitationService.java`：创建邀请与员工注册。
- `backend/src/main/java/com/startrail/payment/operations/interfaces/OperationsTransactionController.java`、`OperationsRefundController.java`、`OperationsPaymentActionController.java`、`OperatorInvitationController.java`：后台 HTTP 边界。
- `backend/src/main/java/com/startrail/payment/operations/security/OperationsSecurityConfiguration.java`、`OperatorUserDetailsService.java`：会话、角色和停用账号访问。
- 新建 `backend/src/main/java/com/startrail/payment/operations/security/OperatorAccountStatusFilter.java`：每次已登录后台请求检查账号是否仍启用，停用后注销当前会话。
- `backend/src/main/java/com/startrail/payment/refund/application/RefundStateService.java`、`RefundStatusReconciliationJob.java`、`backend/src/main/java/com/startrail/payment/refund/infrastructure/AirwallexRefundGateway.java`：复用服务商退款查询与状态核实；人工查询不得调用退款创建接口。
- `backend/src/main/resources/db/migration/`：新增一份向前兼容的员工邀请撤销字段迁移。

### 后端新增文件

- `operations/application/OperationsWorkbenchQueryService.java`：待办计数、可追溯的近期 outbox/退款/员工操作动态和现有资金摘要的组合读取；不伪造支付历史。
- `operations/application/OperationsExceptionQueryService.java`：按待确认退款、退款异常、支付差异、通知失败分页读取真实记录。
- `operations/application/OperationsAuditQueryService.java`：带筛选和分页的只读审计查询。
- `operations/application/OperationsTeamService.java`：员工/邀请查询、邀请撤销、普通员工启停及管理员保护规则。
- `operations/application/OperationsRefundStatusQueryService.java`：按既有退款编号或既有服务商 request id 查询并核实退款。
- 对应的 `interfaces/OperationsWorkbenchController.java`、`OperationsExceptionController.java`、`OperationsTeamController.java`；退款查询可扩展既有 `OperationsRefundController.java`。

### 前端文件

- `frontend/src/modules/operations/OperationsLayout.tsx`：主导航、角色入口、页面框架。
- `frontend/src/modules/operations/OperationsFinanceOverviewPage.tsx`：继续作为资金摘要内容来源，可在独立财务路由保留。
- 新建 `frontend/src/modules/operations/OperationsWorkbenchPage.tsx`：后台首页，待办优先并在旁边显示资金摘要。
- `OperationsTransactionListPage.tsx`、`OperationsTransactionDetailPage.tsx`：交易搜索、筛选、状态时间线和上下文操作。
- `OperationsReconciliationPage.tsx`：改为支付、退款、通知三个异常队列入口。
- `OperationsAuditPage.tsx`：筛选、分页和关联交易跳转。
- `OperationsTeamPage.tsx`：员工目录和待处理邀请。
- `frontend/src/modules/operations/api.ts`：接口调用与传输模型；若类型继续增长，可拆出同目录 `contracts.ts`。
- `frontend/src/app/router.tsx`：工作台默认路由及兼容旧入口。
- `frontend/src/styles/tokens.css`、`frontend/src/styles/index.css`：保持现有主题，只按设计统一后台语义色、表格/状态样式和窄屏布局。

## 任务 1：后台工作台与异常查询接口

**文件：**
- 新建上文列出的 `OperationsWorkbenchQueryService.java` 和 `OperationsWorkbenchController.java`。
- 新建 `OperationsExceptionQueryService.java` 和 `OperationsExceptionController.java`。
- 修改 `OperationsTransactionQueryService.java`、`OperationsTransactionController.java`，扩充服务端搜索/筛选与分页。
- 对照 `backend/src/main/resources/db/migration/V1__create_payment_orders.sql`、退款、核查和 outbox 的实际表结构编写参数化 SQL；只有查询性能确有需要时增加索引迁移。

**接口契约：**
- `GET /api/v1/operations/workbench?from=YYYY-MM-DD&to=YYYY-MM-DD` 返回 `generatedAt`、四类待办数量及各队列前五条真实记录和近期状态活动。页面独立读取 `GET /api/v1/operations/finance/overview`，避免待办接口和资金摘要互相遮蔽故障。
- `GET /api/v1/operations/exceptions?type=REFUND_CONFIRMATION|REFUND_REVIEW|PAYMENT_DIFFERENCE|NOTIFICATION_FAILURE&page=0&size=20` 返回 `items[]`、`page`、`size`、`total`。每项至少有 `type`、`paymentOrderId`、`businessOrderId`、`amount`、`currency`、`status`、`createdAt`、`updatedAt`；支付核查异常额外带 `issueCodes` 和 `checkedAt`，通知异常带 `eventId`、`deliveryStatus`、`attemptCount`。
- 交易列表 `GET /api/v1/operations/transactions` 增加 `currency`、`provider`、`refundStatus`、`notificationStatus`、`from`、`to`、`page`、`size`；`query` 在业务订单号、内部支付单 UUID、Payment Intent ID 与可联表命中的退款 ID 内查找。
- 任何过滤值均由后端白名单校验，日期范围沿用不超过 93 天和 `Asia/Shanghai` 日期口径；不得把用户输入拼接为 SQL。

**步骤：**

1. 从实际 SQL 表确认待办定义：待管理员确认取退款 `PENDING_OPERATOR_CONFIRMATION`；退款异常取 `BLOCKED`/`NEEDS_REVIEW`；支付差异取每笔最近一次差异/查询失败；通知失败取最新未送达且可重试的 outbox 事件。近期动态只从 outbox、退款更新时间和操作审计中读取可追溯记录。
2. 建立分页结果类型和过滤器校验，再实现异常列表查询；每个队列只返回对应真实记录及精确关联编号。
3. 建立工作台只读查询，调用/复用现有 `OperationsFinanceOverviewQueryService` 的统计口径，避免复制或重新解释财务 SQL。
4. 扩展交易列表参数和总数查询；列表与总数必须共用相同筛选条件，按创建时间和 UUID 稳定排序。
5. 实现控制器和当前会话访问控制；所有者只读，员工查询可用。接口失败使用统一 `ApiErrorResponse`。
6. 手工核对 SQL 和 DTO 字段与 `payment-interface-contracts.md` 中的标识语义一致；模拟来源在任何统计中明确排除。

## 任务 2：安全的即时退款核实

**文件：**
- 新建 `OperationsRefundStatusQueryService.java`。
- 修改 `OperationsRefundController.java`、`OperationsRefundQueryService.java`、`OperatorAuditService.java`。
- 复用 `RefundStateService.java` 和 `AirwallexRefundGateway.java`；仅在确有复用需要时提取 `RefundStatusReconciliationJob.java` 中的查询协调逻辑。

**接口契约：**
- `POST /api/v1/operations/refunds/{refundId}/refresh` 要求有效运营会话、CSRF，允许 `STAFF_OPERATOR` 与 `STAFF_ADMIN`，只读所有者返回 403。
- 成功响应返回完整现有退款摘要：`refundId`、`refundRequestId`、`paymentOrderId`、`businessOrderId`、`providerRefundId`、本地 `status`、原始 `providerStatus`、`amount`、`currency`、`lastCheckedAt`、`notificationStatus`、核实 `outcome`。
- 已有 `providerRefundId` 时调用 `GET /pa/refunds/{id}`；没有编号时按原 Payment Intent + 已持久化 `providerRequestId` 调用退款列表查询。该操作路径不得调用 `POST /pa/refunds/create`。

**步骤：**

1. 将人工核实入口限制为 Airwallex 退款；本地模拟交易不允许进入此服务。
2. 对服务商返回逐项核对退款 ID、原 Payment Intent、原服务商 request id、金额和币种；任何不一致保留现状并返回“结果待核实”。
3. 复用现有状态服务写入映射状态与 outbox；已有终态不能回退，查询失败不能擦除现存退款编号或状态。
4. 将 HTTP 超时与 Airwallex 未找到记录分别返回可理解的 outcome；两种情况下均不创建新退款，后台保留额度。
5. 写审计动作 `REFRESH_REFUND_STATUS`，仅记录安全摘要，不记录支付凭证、完整服务商响应或敏感付款信息。
6. 在详情和异常队列中展示“查询中、已核实、未找到、服务商暂不可用、数据不匹配”五种结果；未找到不等同退款失败。

## 任务 3：操作记录筛选与分页

**文件：**
- 新建 `OperationsAuditQueryService.java`。
- 修改 `OperationsTransactionController.java`，让现有 `/api/v1/operations/audit` 路径调用专用审计查询服务，保持路径兼容。
- 修改 `OperatorAuditService.java` 只用于新的审计写入动作，不把查询逻辑混入审计写入职责。

**接口契约：**
- `GET /api/v1/operations/audit?from&to&actor&action&outcome&businessOrderId&paymentOrderId&page&size` 返回服务端分页。
- 日志详情至少包含 `id`、`actor`、`paymentOrderId`、关联业务订单号（可空）、`action`、`outcome`、`detail`、`createdAt`。
- 邀请、撤销邀请、账号停用/恢复事件允许没有支付单关联。

**步骤：**

1. 用参数化条件实现可组合筛选，限制 `size` 不超过 100，稳定按 `created_at DESC, id DESC` 排序。
2. 保持历史审计只读；不提供修改/删除接口。
3. 为员工账号动作和退款核实动作添加一致的审计动作枚举及最小安全描述。
4. 保留旧 `limit` 调用兼容一轮迁移，前端切换完成后再决定是否弃用，不在本次删除旧行为。

## 任务 4：员工目录、邀请撤销与停用会话

**文件：**
- 新建 `OperationsTeamService.java`、`OperationsTeamController.java`。
- 修改 `OperatorInvitationService.java` 或将已有逻辑委托给新服务；修改 `OperationsSecurityConfiguration.java` 与 `OperatorUserDetailsService.java`。
- 新增 `V117__add_operator_invitation_revocation.sql`，只增加 `operator_invitation.revoked_at`（及确需追踪的撤销人字段）；`operator_account.active` 已存在，不重复建列。另新增 V118 单独追踪退款最近核实时间。
- `OperatorAuditService.java` 写入邀请撤销、员工停用/恢复记录。

**接口契约：**
- `GET /api/v1/operations/team/accounts?page&size` 返回账号 ID、用户名、角色、启用状态、创建时间；没有最近登录数据字段时不得伪造。
- `GET /api/v1/operations/team/invitations?page&size` 返回创建时间、过期时间、使用/撤销状态和邀请人；列表永不返回原始邀请令牌。
- `DELETE /api/v1/operations/team/invitations/{invitationId}` 撤销仍未使用且未过期的邀请，已使用/已撤销/已过期返回明确冲突结果。
- `PATCH /api/v1/operations/team/accounts/{accountId}/active` 请求 `{ "active": false }` 或 `true`。只允许 `STAFF_ADMIN` 操作 `STAFF_OPERATOR`；禁止自我操作和停用最后一名启用管理员。

**步骤：**

1. 用数据库事务和行锁读取账号目标、操作者和启用管理员数量；应用安全默认后再写入 active 状态。
2. `OperatorAccountStatusFilter` 对每个已认证的 `/api/v1/operations/**` 请求按用户名查询 `operator_account.active`；账号不存在或已停用时清除 `SecurityContext`、注销当前 HTTP session 并返回 401。登录、注册和 CSRF 获取入口保持匿名请求行为不变。
3. 撤销邀请只更新邀请生命周期状态，不泄露 token hash 或 token；邀请创建响应仍仅显示一次原始令牌。
4. 每个写动作在事务内产生审计；失败操作保留失败审计且不留下半完成状态。
5. 对 `STAFF_OPERATOR`、`OWNER_READONLY` 的邀请/账号管理请求由服务端拒绝；不能只在前端隐藏按钮。

## 任务 5：统一导航和待办工作台

**文件：**
- 新建 `OperationsWorkbenchPage.tsx`。
- 修改 `OperationsLayout.tsx`、`router.tsx`、`api.ts`。
- 仅必要时新增同模块小型展示组件；不引入新的 UI 库。

**步骤：**

1. 主导航按“运营工作台、交易、对账与异常、操作记录、员工与权限”排列；员工入口只对 `STAFF_ADMIN` 显示。
2. 默认 `/operations` 显示四类待办队列，按优先级展示真实计数、订单、币种金额、状态、等待/更新时间和详情入口。
3. 同页邻近区域展示现有 `CNY`/`USD` 资金摘要，不合并币种；实际结算和手续费继续显示“待接入”。
4. 待办计数点击后进入异常页对应 `type` 筛选；从异常项目进入详情后保留来源筛选返回路径。
5. 处理首次加载、刷新中、无待办、部分接口失败、完全失败和只读角色状态；局部失败不能让其他已加载摘要消失。
6. `/operations/finance` 保留为旧财务页入口或重定向到工作台中的资金区域，不破坏书签。

## 任务 6：交易列表与详情操作重组

**文件：**
- 修改 `OperationsTransactionListPage.tsx`、`OperationsTransactionDetailPage.tsx`、`api.ts`、`format.ts`、`router.tsx`。
- 后端依赖任务 1 的交易查询 DTO 和任务 2 的退款即时核实接口。

**步骤：**

1. 列表筛选支持业务订单号、支付单号、Payment Intent/Refund 编号、币种、来源、支付状态、退款状态、通知状态和时间范围；条件同步到 URL 查询参数。
2. 服务端分页的页码、每页数量、筛选条件进入 React Query key；返回详情后使用 Router state/URL 保留列表条件。
3. 列表展示支付、退款、星辙通知三个独立状态；只在数据来源明确时显示服务商编号。
4. 详情顶部集中显示订单与支付核心编号、金额币种、支付状态、来源；复制操作提供成功/失败反馈。
5. 时间线按事实来源展示业务订单接入、本项目支付、Airwallex 核实、通知投递、退款指令与审计事件；排序用发生时间并显示时区。
6. 退款操作遵循角色和状态矩阵；`NEEDS_REVIEW` 只给“立即核实”，不出现再次提交退款；管理员退款确认对话框列出星辙批准指令、原支付、金额、币种和累计额度。
7. 高风险操作的网络超时显示“结果待核实”；再次操作前重新读取服务端状态，不自动重复提交。

## 任务 7：异常队列与操作记录页面

**文件：**
- 修改 `OperationsReconciliationPage.tsx`、`OperationsAuditPage.tsx`、`api.ts`、`format.ts`。
- 使用任务 1、任务 2、任务 3 已明确的响应模型。

**步骤：**

1. 异常页提供支付核查、退款和通知三个标签/队列，并允许从 URL 读取队列类型、状态、币种和页码。
2. 支付差异显示本项目与 Airwallex 核查结果；显著说明这不是星辙业务账本对账或结算报告对账。
3. 退款待确认行显示管理员操作入口；退款待核实行仅显示核查、备注、历史入口；不增加批量金融操作。
4. 通知失败行显示 event id、失败次数、最近尝试和关联交易；安全重试只针对服务端认定为 `RETRY` 的 outbox 项。
5. 审计页支持时间、操作人、动作、结果和订单筛选，服务端分页；无支付单关联的员工动作仍可见。
6. 空、加载、失败、无权限状态各自有准确文案，不能把接口失败显示为“无异常”。

## 任务 8：员工管理交互和跨页面一致性

**文件：**
- 修改 `OperationsTeamPage.tsx`、`OperationsLayout.tsx`、`api.ts`、`router.tsx`。
- 复用 `ConfirmActionDialog.tsx`，仅在明确需要时调整其文案/加载态。

**步骤：**

1. 员工页分为“员工账号”和“待处理邀请”；表格显示服务端真实账号/邀请状态。
2. 邀请操作保留现有一次性令牌只显示一次、管理员自行复制交付的约束。
3. 撤销未使用邀请、停用/恢复普通员工均先展示账号身份和影响说明，确认后调用写接口；按钮加载期间禁止重复提交。
4. `OWNER_READONLY` 和 `STAFF_OPERATOR` 只能查看可见员工信息，不能调用管理写接口；普通员工不能管理员工。
5. 停用成功后更新列表、清理相关缓存；被停用账号的下一个后台请求必须得到未授权/禁止响应。
6. 维护可访问性：键盘操作、焦点反馈、清楚的错误描述、窄屏列表呈现。

## 任务 9：文档回写与构建收尾

**文件：**
- 更新 `docs/architecture/operations/operations-console.md`、`docs/architecture/README.md`、`docs/engineering/directory-structure.md`、`docs/superpowers/specs/2026-10-10-operations-console-redesign-design.md`。
- 如 DTO 与本设计偏离，先更新 `docs/architecture/integration/payment-interface-contracts.md`，不能只为配合页面而悄悄改变外部契约。

**步骤：**

1. 把实际接口路径、筛选字段、角色权限、账号停用语义和已验证的恢复行为回写架构文档。
2. 核对产品文档、导航名称、状态展示与 DTO 字段一致；生产星辙待确认项保持明确。
3. 完成后运行前端 `pnpm build` 和后端项目构建，检查类型/编译问题；不新增或运行自动化测试。
4. 汇报哪些流程完成、构建结果、尚未接入的真实星辙/结算能力；不声称已完成沙盒或生产验收。

## 实施依赖顺序

`任务 1 → 任务 2/3/4 → 任务 5 → 任务 6/7/8 → 任务 9`

任务 2、3、4 可在任务 1 的 DTO 边界确定后分开实现；前端工作台和列表依赖后端接口先定型。主执行者负责统一状态和字段口径，并审阅所有合并结果。此前已获用户授权可为该后台任务委派子代理；如采用并行实现，退款/账号权限等高风险后端任务必须先由主执行者确认接口边界，再分发独立文件范围。
