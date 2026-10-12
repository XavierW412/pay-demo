# 浏览器状态服务器推送实施计划

> **执行方式：** 当前会话直接实施；保留现有未提交修改，不运行测试套件。

**目标：** 支付/退款状态更新后，已打开的运营后台与本地模拟星辙页面能在无定时轮询的情况下及时显示最新记录。

**架构：** 后端通过 SSE 发送不含业务详情的变更提示；浏览器收到提示后使当前活动查询失效，再调用现有受权 API。Airwallex Webhook、退款状态机、签名星辙通知 outbox 保持原职责。

**技术栈：** Java 21、Spring MVC `SseEmitter`、Spring Security、React、TypeScript、TanStack Query。

**设计：** [退款 Webhook、星辙状态通知与浏览器推送设计](../specs/2026-10-12-refund-webhook-design.md)；[浏览器实时更新架构](../../architecture/operations/realtime-browser-updates.md)。

## 全局约束

- Airwallex 与星辙服务器状态通知仍通过持久化 Webhook/outbox 处理，不改成浏览器直连。
- SSE 消息只提示数据变化，不承载金额、退款状态、令牌或签名。
- 运营事件流必须通过现有员工会话认证；模拟星辙事件流只在 `local` profile 提供。
- 状态变更提示在数据库事务提交后发送；重连后客户端重新读取当前页面数据。
- 支付状态补查、退款状态补查、支付定时核查和页面定时刷新保持关闭。
- 不新增运行依赖；不新增或运行自动化测试。

## 评审重点

- 未登录用户不能连接运营事件流；`OWNER_READONLY` 与其他已登录角色可监听，但事件不泄露交易内容。
- 一个模拟订单的浏览器不能订阅另一个订单的通知主题。
- 事务回滚时不能向浏览器发出数据变化提示。
- SSE 断线重连后，即使漏掉期间事件，也会重新读取最新记录。
- 客户端收到重复事件只会重新读取，不会重复执行退款等业务副作用。

## 文件结构与职责

- `backend/src/main/java/com/startrail/payment/shared/application/StateChangePublisher.java`、`backend/src/main/java/com/startrail/payment/shared/infrastructure/ServerEventHub.java`：定义状态变更通知端口，并管理进程内 SSE 连接、主题广播、事务提交后发送和断线清理。
- `backend/src/main/java/com/startrail/payment/operations/interfaces/OperationsEventController.java`：员工认证后的运营事件流。
- `backend/src/main/java/com/startrail/payment/simulator/interfaces/MockXingzheEventController.java`：仅本地、按业务订单隔离的模拟器事件流。
- `backend/src/main/java/com/startrail/payment/payment/application/PaymentStatusApplicationService.java`、`backend/src/main/java/com/startrail/payment/refund/application/RefundExecutionService.java`、`backend/src/main/java/com/startrail/payment/refund/application/RefundStateService.java`：支付/退款记录提交后通知运营频道。
- `backend/src/main/java/com/startrail/payment/simulator/application/MockPaymentEventService.java`：模拟星辙服务端提交支付/退款通知记录后通知对应订单频道。
- `frontend/src/modules/operations/OperationsLayout.tsx`：打开员工事件流，事件到达或重连后失效活动查询。
- `mock-xingzhe/src/modules/mock-xingzhe/MockXingzheOrderPage.tsx`：订阅当前业务订单主题并失效对应订单、历史与退款查询。
- `docs/architecture/operations/realtime-browser-updates.md`、`docs/architecture/README.md`、支付/退款架构文档：记录职责、安全、重连和无轮询边界。

## 实施步骤

### 任务一：建立安全的服务端事件流

- [x] 增加线程安全的 `ServerEventHub`，以 `ConcurrentHashMap` 和并发集合管理连接；连接完成/超时/报错时移除；状态事务提交后发送名为 `state-changed`、数据为固定字符串 `refresh` 的事件。
- [x] 增加 `GET /api/v1/operations/events`，订阅全局 `operations` 主题。由现有 `/api/v1/operations/**` 认证规则保护，不加入公开白名单。
- [x] 增加仅 `local` profile 的 `GET /mock-xingzhe/api/v1/orders/{businessOrderId}/events`；先确认模拟订单存在，再订阅 `mock-order:{businessOrderId}` 主题。

### 任务二：从已提交状态更新触发事件

- [x] `PaymentStatusApplicationService` 在支付状态更新或本地模拟状态提交后通知 `operations`。
- [x] `RefundExecutionService.receive` 在新退款指令成功登记后通知 `operations`；`RefundStateService` 在退款状态或处理阶段改变后通知 `operations`。
- [x] `MockPaymentEventService` 在模拟星辙收到付款/退款通知后通知对应订单频道；发送延后到数据库事务提交后，避免回滚时误报。

### 任务三：浏览器订阅并重新读取

- [x] 运营后台 `OperationsLayout` 使用 `EventSource('/api/v1/operations/events')`；收到 `state-changed` 或连接恢复时，使所有活动的非会话查询失效；组件卸载时关闭连接。
- [x] 模拟星辙订单详情订阅 `/mock-xingzhe/api/v1/orders/{businessOrderId}/events`；收到事件或连接恢复时，只失效当前订单、支付历史、退款申请和退款事件查询；切换订单或卸载时关闭旧连接。
- [x] 保留显式手动刷新；页面说明服务器通知后自动读取，不添加页面定时刷新。

### 任务四：同步架构文档并静态核对

- [x] 更新运营后台、支付和退款架构说明，明确 Webhook、outbox、SSE 分别处理什么，以及本地单实例和未来多实例的边界。
- [x] 执行 `git diff --check`；搜索前后端页面定时刷新和服务商状态补查，确认相关补查开关关闭；不运行测试或构建。
- [ ] 手工验收留给用户：重启 IDEA 后，浏览器开着时发送退款指令并等待 Airwallex Webhook，确认运营页和模拟星辙页无需手动刷新；暂时断开 SSE 后恢复，确认重连会读到最新数据。

## 完成标准

- 退款指令、Airwallex 状态推进、星辙服务器通知均不依赖交易状态轮询。
- 两个浏览器页面由 SSE 触发查询更新；SSE 重连后能恢复到服务端最新记录。
- 运营事件流受认证保护，模拟器事件按订单隔离并仅在本地提供。
- 本地仍保留手动刷新入口；断线时状态留在服务端，不丢失可靠通知记录。
