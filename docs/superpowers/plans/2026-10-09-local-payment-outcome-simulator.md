# 本地模拟支付结果实施计划

> 本计划由当前主会话逐项实施；不派发给其他代理。不得新增或运行自动化测试，按用户授权后的开发范围完成功能和静态检查。

**目标：** 在本地模拟星辙中提供独立于 Airwallex 的模拟付款来源，以成功或失败结果联调支付状态、通知和服务开通。

**架构：** 支付单明确记录来源 `AIRWALLEX` 或 `LOCAL_SIMULATOR`。模拟来源只由 `local` profile 创建和推进；成功/失败仍经过支付服务状态事务和 outbox 通知。模拟支付不带 Airwallex Payment Intent，不进入 Airwallex 查询、退款或结算对账。

**技术栈：** Java 21、Spring Boot、PostgreSQL/Flyway、React、TypeScript、Tailwind CSS。

**设计：** [本地模拟支付结果设计](../specs/2026-10-09-local-payment-outcome-simulator-design.md)

## 全局约束

- Airwallex 沙盒托管付款是现有默认路径，保持行为不变。
- 模拟星辙只在 `local` profile 装配；生产环境不暴露模拟入口。
- 不实现付款人银行卡输入、绑卡或资料处理。
- 不把模拟结果表示为 Airwallex webhook、Payment Intent、退款或已结算资金。
- 模拟结果必须经过同一支付状态持久化和通知 outbox，不由模拟星辙直接伪造服务开通。
- 不新增或运行自动化测试；本计划中的验收用例是实现边界，运行验证需另有明确请求。
- 不提交、不清理或重置现有工作区改动。

## 重点审查风险

1. 同一业务订单不能同时创建 Airwallex 和本地模拟支付单。
2. 模拟来源不得被定时补查、运营主动查询、退款或对账服务当成真实 Airwallex 支付。
3. 生产 profile 必须缺少模拟控制器和模拟服务装配。
4. 模拟结果不能被重复提交或覆盖终态；成功通知只能使模拟星辙开通一次。
5. 交易列表与详情必须显式显示本地模拟来源，不能让员工误认成实际收款。

## 文件职责与修改清单

- `backend/src/main/java/com/startrail/payment/payment/domain/PaymentProvider.java`：付款来源枚举。
- `backend/src/main/java/com/startrail/payment/payment/domain/PaymentOrder.java`、`PaymentOrderMapper.java`、`PaymentOrderEntity.java`、`JpaPaymentOrderRepository.java`、`SpringDataPaymentOrderRepository.java`：持久化和映射付款来源。
- `backend/src/main/java/com/startrail/payment/payment/application/PaymentOrderCreationTransaction.java`：保留现有 Airwallex 建单规则，增加仅供本地模拟器调用的本地来源建单方法，并校验幂等请求的来源一致。
- `backend/src/main/java/com/startrail/payment/simulator/application/LocalPaymentOutcomeSimulator.java`：本地模拟支付单创建、成功/失败推进和 outbox 入队；类与入口均限 `local` profile。
- `backend/src/main/java/com/startrail/payment/payment/application/PaymentStatusApplicationService.java` 与 `PaymentOrderProviderUpdateTransaction.java`：增加来源受限的模拟状态事务；不把模拟状态构造为 Airwallex 响应。
- `backend/src/main/java/com/startrail/payment/simulator/application/MockBusinessOrder.java`、`MockBusinessOrderService.java`、`MockBusinessOrderRepository.java`、`JdbcMockBusinessOrderRepository.java`、`MockBusinessOrderResponse.java`、`MockBusinessOrderStatusResponse.java`、`CreateMockBusinessOrderRequest.java`、`MockXingzheController.java`：保存联调模式，按模式选择沙盒或模拟支付，提供本地模拟结果入口和状态标识。
- `backend/src/main/java/com/startrail/payment/operations/application/OperationsTransactionQueryService.java`、对应交易响应 DTO 与 `OperationsPaymentActionService.java`：展示来源并拒绝对模拟支付调用 Airwallex 查询。
- `backend/src/main/java/com/startrail/payment/refund/application/RefundExecutionService.java`、`MockRefundInstructionService.java`：对模拟来源退款直接拒绝，不调用 Airwallex。
- `backend/src/main/resources/db/migration/V111__add_payment_provider.sql`：增加来源字段并将存量记录归为 `AIRWALLEX`。
- `backend/src/main/resources/db/migration-local/V112__add_mock_payment_mode.sql`：只给本地模拟业务订单增加联调模式；本地加载公共迁移后使用 V112，避免同一 Flyway 配置中版本重复。
- `frontend/src/shared/api/contracts.ts`、`frontend/src/modules/mock-xingzhe/api.ts`、`MockXingzhePage.tsx`、`MockXingzheOrderPage.tsx`：提供沙盒/模拟模式选择、模拟成功/失败操作和来源提示。
- `frontend/src/modules/operations/api.ts`、`OperationsTransactionListPage.tsx`、`OperationsTransactionDetailPage.tsx`：呈现来源并隐藏/禁用仅适用于真实 Airwallex 支付的操作。
- `docs/architecture/payment/payment-flow.md`、`docs/product/product-spec.md`、`README.md`、`docs/roadmap/development-roadmap.md`：实现后同步实际边界和状态。

如果代码检查发现某个列出的类名或 DTO 职责与当前分支不同，先沿实际调用链定位等价文件，再只调整对应条目，不重构无关模块。

## 实施进度

- [x] 记录支付来源并新增数据库迁移。
- [x] 实现本地支付单和一次性成功/失败推进。
- [x] 接入本地模拟星辙接口与同一通知链路。
- [x] 更新购买页、订单页和运营来源标识。
- [x] 对 Airwallex 查询、退款和后台补查做来源隔离。
- [x] 同步产品、架构、README 和路线图文档。
- [x] 完成后端与前端构建检查及最终代码审阅（未运行自动化测试）。

## 实施步骤

### 第 1 步：记录来源并迁移数据库

- 新增 `PaymentProvider`，值为 `AIRWALLEX` 和 `LOCAL_SIMULATOR`。
- 在 `PaymentOrder` 和 JPA 实体、映射、仓储查询中增加付款来源；现有生产/沙盒接口建单固定来源为 `AIRWALLEX`，不能由外部请求体选择。
- 新增公共迁移 V111：`payment_provider` 非空，存量行默认 `AIRWALLEX`，数据库约束只接受两种来源。
- 新增本地迁移 V112：给模拟业务订单保存联调模式，历史模拟订单默认 `AIRWALLEX_SANDBOX`。
- 现有相同 `requestId` 重试只在业务字段和付款来源一致时复用；来源不一致返回冲突。

### 第 2 步：实现本地来源支付单和结果推进

- 建立仅 `local` profile 的 `LocalPaymentOutcomeSimulator`，创建模拟支付单时使用服务端已有的业务订单、金额和币种，不读取浏览器金额。
- 模拟支付单直接进入 `AWAITING_PAYMENT`，`providerPaymentIntentId` 保持空值，来源为 `LOCAL_SIMULATOR`。
- 提供一个服务端模拟结果方法，入参仅为支付单标识和 `SUCCEEDED`/`FAILED`；通过锁行重新读取支付单，只允许 `LOCAL_SIMULATOR` 且当前状态为 `AWAITING_PAYMENT`。
- 在单一事务中更新状态并用新唯一事件号写入现有 `PaymentStatusOutboxWriter`；重复操作、金额/币种/业务单不匹配和终态覆盖均拒绝。
- 现有 Airwallex 状态核实方法只处理 `AIRWALLEX`，来源不匹配时拒绝，不调用模拟结果逻辑。

### 第 3 步：接入本地模拟星辙 API 与业务状态

- 创建本地模拟业务订单时接受明确的 `AIRWALLEX_SANDBOX` 或 `LOCAL_SIMULATOR` 联调模式；缺省值保持 `AIRWALLEX_SANDBOX`，避免破坏现有流程。
- `MockBusinessOrderService` 按持久化模式走现有 Airwallex 创建客户端，或调用本地模拟支付用例；不允许支付发起后切换模式。
- 增加仅 `local` profile 的 API，使本地页面可对该业务单提交成功或失败结果；请求体只包含结果枚举，不接收金额或货币覆盖值。
- 结果通知仍经过现有 outbox、双向签名和 `MockPaymentEventService`；只允许成功状态激活示例服务。
- 模拟来源不能批准/执行退款；服务端在产生外部副作用之前拒绝指令。

### 第 4 步：更新模拟购买页和订单页

- 在模拟服务购买流程展示两种开发联调路径，默认沿用 Airwallex 沙盒托管付款。
- 选择本地模拟结果时不跳转托管页，订单页展示醒目的“本地模拟”标识和成功/失败操作。
- 操作完成后刷新相同业务单状态；成功显示由模拟星辙开通的服务，失败显示未开通；不能直接在前端更新支付状态。
- Airwallex 沙盒模式保持原有跳转与返回体验，不显示本地模拟操作。
- 本地模拟支付订单不提供退款操作；退款沙盒演示继续使用真实 Airwallex 沙盒付款单。

### 第 5 步：运营页面和其他支付操作按来源隔离

- 运营交易列表和详情展示“Airwallex”或“本地模拟”来源。
- 模拟来源不提供 Airwallex 状态刷新；服务端 `OperationsPaymentActionService` 同样拒绝调用 Airwallex。
- 补查只处理有 Airwallex Payment Intent 的 Airwallex 来源记录；本地模拟单不进入提供方查询和结算核对。
- 退款服务在读取服务商信息前检查来源；模拟单退款失败且不发起远端请求。
- 保留现有支付状态通知、操作权限和审计要求，不新增正式后台测试入口。

### 第 6 步：同步文档并进行代码审阅

- 在支付链路架构中记录来源字段、模拟流程、状态、通知和隔离约束。
- 在 README 区分当前 Airwallex 沙盒流程与本地模拟流程，并明确本地模拟不能证明 Airwallex 实际收款。
- 更新路线图为“本地模拟联调已实现”，实际沙盒付款、Webhook、退款验收仍分别记录。
- 按重点审查风险逐条阅读变更，检查模拟来源不会触发 Airwallex 退款、补查或结算操作。
- 不新增或运行自动化测试；除非用户另行要求，不启动/停止服务或调用真实沙盒资金接口。

## 完成条件

- 本地页面可以创建沙盒模式或模拟模式订单；沙盒模式保持当前 Airwallex 托管跳转。
- 模拟模式可通过显式成功/失败操作完成支付状态、outbox 通知和模拟星辙业务状态联动。
- 数据和运营页面能够分辨模拟来源；模拟支付没有 Airwallex Payment Intent 编号。
- 模拟来源退款、Airwallex 核实和提供方补查均被服务端拒绝或排除。
- 非 `local` profile 不含模拟入口；生产支付接口永远创建 `AIRWALLEX` 来源。
- 文档和实现一致，且没有把本地模拟结果描述成 Airwallex 沙盒支付验收。
