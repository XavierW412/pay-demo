# 支付运营资金总览与结算数据计划

**状态：已确认；支付/退款事实总览已实现，Airwallex 结算报告接入待后续完成。**

**目标：** 支付后台提供按时间和币种查看支付成功、退款和异常的总览；Airwallex 实际结算入账及手续费只能来自服务商报告/结算数据，未接入时清楚显示“待接入”，不以成功支付减退款推算到账。

**设计依据：** [支付软件、模拟星辙、退款复核与资金总览边界设计](../specs/2026-10-09-separated-simulator-refund-finance-design.md)。

## 数据口径

- CNY、USD 分开汇总，不做无来源汇率换算。
- “支付成功金额”只统计已核实成功的支付单，并注明不代表已结算。
- “退款中金额”按退款状态单列；“退款已结算金额”和“手续费”仅在对应 Airwallex 结算/交易报告证据到达后展示。
- “实际结算入账”默认指进入 Airwallex 商户余额/结算目的地的服务商结算记录；不等同于转入银行账户。
- 本地模拟交易与 Airwallex 沙盒/生产数据分区。生产经营总览排除 `LOCAL_SIMULATOR`；沙盒也有明确环境标签。
- 结算报告尚未接入或不可用时，展示数据源状态及已知支付/退款事实，不显示估算净到账金额。

## 涉及文件

- `backend/src/main/java/com/startrail/payment/operations/application/`：新增只读总览查询服务，按币种/日期范围聚合支付、退款、待复核和差异。
- `backend/src/main/java/com/startrail/payment/operations/interfaces/`：新增总览 API DTO 与只读接口；验证时间范围和币种过滤。
- `backend/src/main/java/com/startrail/payment/operations/security/OperationsSecurityConfiguration.java`：总览仅登录账号可读，不增加写操作。
- `backend/src/main/resources/db/migration/V116+`：如需持久化报告，新增结算批次/行项目/导入游标/报告来源记录；V113 为本地订单历史、V114 为公共退款复核、V115 为本地退款历史；保留 Airwallex 原始业务 ID 和币种，安全保存不含敏感凭据的必要对账字段。
- `backend/src/main/java/com/startrail/payment/` 的 Airwallex finance adapter：只依据官方 API/账户实际权限确定接入方式；提供商报告暂不可用时以显式未连接状态响应，不伪造或静默降级到公式估算。
- `frontend/src/modules/operations/`：增加默认总览页、币种分栏卡片、支付/退款/异常趋势和结算数据源状态；运营后台首页进入总览。
- `docs/architecture/operations/`、产品说明、Airwallex 接入、README 和路线图。

## 实施步骤

1. 先实现只基于现有支付单、退款单、核查差异的事实汇总 API，明确环境、币种、时间区间与统计状态。
2. UI 分别展示支付成功、退款申请/处理中、待核实异常；不能把 Airwallex Payment Intent `SUCCEEDED` 标成已结算。
3. 查看 Airwallex 官方结算与报表 API 的当前字段及权限要求，核对沙盒账户是否有可用报告；不基于前端手工数据或推测字段接入。
4. 若官方接口与账户权限允许，新增批次/报告同步、稳定游标、去重、报告日期和来源 ID；导入数据只追加或幂等更新，不覆盖支付事实。
5. 将结算批次、费用、退款抵扣与已结算支付逐笔匹配；未匹配项进入异常清单供员工查看并留备注。
6. 若沙盒账户不提供报告，先保留真实数据源连接状态及清楚的“待接入”，同时交付已有支付/退款事实总览；不得用支付额减退款额伪造到账。
7. 更新运营首页、财务口径、Airwallex 接入和部署说明。

## 验收与验证

- 不同币种分开展示；过滤日期不会混入本地模拟数据。
- 支付成功与结算到账在页面上语义不同；手续费/到账未接入时不显示猜测数值。
- 结算报告重复导入可去重；匹配不到的记录保留异常线索，不篡改支付/退款状态。
- 若沙盒报告权限/数据不可用，交付状态明确显示“待接入”，并记录外部依赖。
- `pnpm build:web`、Java 21 下 `./backend/mvnw -f backend/pom.xml -DskipTests package` 与 `git diff --check`。
- 不新增或运行自动化测试；不启动服务、不调用真实财务 API 或导入真实账户数据。

## 风险与边界

- 报告字段、账本入账时间、手续费与结算币种受 Airwallex 账户区域和权限影响，实施前必须按官方文档和沙盒实际响应核实。
- “转入银行账户”需额外的余额出账/银行流水数据源；不在当前“Airwallex 结算入账”指标中推断。
- 任何“已结算”状态必须能追溯到 provider settlement/report identifier 与报告时间；缺失则只展示未匹配或待接入。
