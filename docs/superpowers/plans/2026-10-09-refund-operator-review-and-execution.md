# 星辙退款申请、支付侧复核与执行计划

**状态：已确认并完成首版实现；Airwallex 沙盒退款验收待进行。**

**目标：** 模拟星辙业务人员先申请并批准退款，再向支付软件发送稳定指令；支付软件校验支付和退款金额，有异常则拦截；校验无误后等待支付侧管理员确认并发起 Airwallex 退款；服务商确认结果后再通知星辙并留在原订单历史。

**设计依据：** [支付软件、模拟星辙、退款复核与资金总览边界设计](../specs/2026-10-09-separated-simulator-refund-finance-design.md)。

## 当前实现与关键调整

- 当前 `RefundExecutionService.execute()` 收到星辙指令后会验证 Airwallex 原支付、预留金额并立即调用 `airwallex.create()`。本计划把入站和执行拆开。
- `refund_order` 目前允许 `SUBMITTING/RECEIVED/ACCEPTED/SUCCEEDED/FAILED/NEEDS_REVIEW`；需新增“待管理员确认”和可审计的支付侧人工动作记录。
- 模拟星辙当前只有直接发送已批准退款指令的接口。改为先记录模拟用户申请、模拟业务审批，再发送稳定指令；该流程仅 `local` profile 可见。
- 管理员动作是确认发起已由星辙批准的退款，不是业务批准，也不能手工把退款标为成功。

## 权限与状态约束

- `STAFF_ADMIN` 可复核并确认发起退款；`STAFF_OPERATOR` 可查阅并追加处理备注；`OWNER_READONLY` 只读。
- 退款入站请求按 `refundRequestId` 幂等。相同编号内容不同则冲突拒绝。
- 校验支付单来源为 Airwallex、支付成功、业务单/支付单/金额/币种一致、服务商原支付已核实、累计未终结退款不超已捕获金额。
- 核查差异、服务商核实失败或超时：不调用 Airwallex，进入待核实/差异状态并保留可用证据。未知结果不可重新创建退款。
- 管理员确认时再次锁定退款与支付单并重新核对；确认操作、操作人、时间、核对摘要和服务商请求 ID 写入审计。
- Airwallex 退款状态只能由已验证 webhook 或服务端主动查询更新；状态通知进入现有 outbox。星辙事件去重后追加原订单历史。

## 涉及文件

- `backend/src/main/java/com/startrail/payment/refund/application/RefundExecutionService.java`、`RefundStateService.java`、`RefundRecord.java`：分离接收/校验/预留和确认执行用例。
- `backend/src/main/java/com/startrail/payment/refund/infrastructure/RefundJdbcRepository.java` 与公共 `V114+` 数据库迁移：新增待复核状态、确认人/时间或独立审计动作表，补齐唯一约束、锁定和查询；本地订单历史先使用 V113。
- `backend/src/main/java/com/startrail/payment/refund/interfaces/IntegrationRefundController.java`、`CreateRefundRequest.java`、运营退款 DTO：星辙入站继续只表示已批准退款指令；运营端增加只允许管理员的确认执行接口。
- `backend/src/main/java/com/startrail/payment/operations/interfaces/OperationsRefundController.java`、`OperationsSecurityConfiguration.java`、服务层和审计服务：列表/详情/差异、备注、管理员确认；授权在服务端强制执行。
- `backend/src/main/java/com/startrail/payment/simulator/application/MockRefundInstructionService.java`、`MockXingzheController.java`、本地迁移 V115：模拟申请与业务批准历史，并向支付软件发送签名指令。
- `frontend/src/modules/operations/**`：退款复核列表/详情、差异说明、确认弹窗与操作结果；owner 隐藏写入口但 API 仍严格拒绝。
- 新模拟应用订单详情：展示退款申请、模拟星辙批准、支付侧待复核/处理和服务商最终状态时间线。
- `docs/architecture/refund/refund-execution.md`、产品说明、角色权限、README 和路线图。

## 实施步骤

1. 从接口到 webhook/查询核实梳理退款状态，确认可重用现有 provider request ID、并发金额预留、通知 outbox 与审计服务。
2. 新增状态及审计迁移；保留旧退款记录兼容，未知旧状态仍需显式核实。
3. 调整入站退款用例：验签后按稳定指令幂等，核实原支付和累计退款金额，预留金额并写入待复核或差异状态；这一步绝不调用 Airwallex。
4. 新增 `STAFF_ADMIN` 专属确认执行用例：事务内重新锁定和核实并登记操作；事务外用稳定 provider request ID 发起 Airwallex；未知响应进入查询恢复流程。
5. 服务端授权限定确认接口；同一写请求记录审计，操作员仅可备注，所有者拒绝写操作。
6. 模拟星辙订单页增加退款申请/业务批准/指令发送/事件历史；不能将管理员的支付侧确认混成业务批准。
7. 更新运营后台和模拟历史；服务商最终状态通知回传模拟星辙，所有事件按 event ID 去重。
8. 更新架构、产品、权限、README 和路线图，并审查所有 Airwallex 副作用只能从确认执行用例产生。

## 验收与验证

- 星辙未批准的退款不能进入支付服务；已批准但差异未解决不会调用 Airwallex。
- 只有 `STAFF_ADMIN` 能确认发起；其他角色的直接 API 请求均被服务端拒绝。
- 双击、重放同一指令或确认请求不会重复扣减可退余额或创建新退款；金额/币种/关联号不匹配会留下差异记录。
- Airwallex 超时显示待核实；只有 webhook/服务端查询可推进为受理、成功或失败；管理员不能手工置成功。
- 模拟星辙原订单历史可见申请、业务批准、指令、支付侧处理和最终退款事件。
- `pnpm build:web`、`pnpm build:mock-xingzhe`、Java 21 下 `./backend/mvnw -f backend/pom.xml -DskipTests package` 与 `git diff --check`。
- 不新增或运行自动化测试；不启动服务、不调用真实或沙盒退款接口，除非用户后续明确要求沙盒验收。

## 实施风险

- 退款创建的数据库预留和远端调用无法放进同一原子事务；必须依赖稳定请求 ID、保存未知态、主动核查和人工差异处理。
- 已存在的 `NEEDS_REVIEW` 可能来自远端结果未知，不应被错误解释为“等待管理员首次确认”；状态拆分需通过迁移和清晰操作语义处理。
- Airwallex 退款受理不等于退款已结算，界面和通知必须保留状态差异。
