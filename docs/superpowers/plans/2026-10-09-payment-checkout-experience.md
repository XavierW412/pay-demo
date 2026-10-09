# 支付项目收银体验实施计划

> 历史计划：内嵌付款页目标已被用户确认的托管收银台范围取代。本计划仅记录既有实现，不按其新增自有收银功能。后续整理见[范围校正建议](../../roadmap/scope-alignment.md)。

**目标：** 让本地模拟星辙创建的订单进入本项目付款页面，在页面中完成 Airwallex 沙盒付款并看到可信的状态进度。

**设计：** [支付项目收银体验与本地沙盒链路设计](../specs/2026-10-09-payment-checkout-experience-design.md)。

**技术：** React、TypeScript、Airwallex Components SDK、Spring Boot、PostgreSQL。

## 工作项

- [x] 修正 `application-local.yml` 的 `.env` 搜索路径，并使 `CheckoutContextController` 在生产配置装配。核对密钥只在服务端加载，不打印值。
- [x] 在现有支付单状态机上增加短期令牌保护的状态查询和一次主动核实；复用 Airwallex Payment Intent 查询及订单字段校验，按状态变更写入通知 outbox。
- [x] Webhook 处理保持事件幂等，并避免主动核实与 Webhook 对同一状态重复通知。
- [x] 将 `CheckoutPage.tsx` 改成自有付款页面，嵌入 Airwallex Drop-in；展示不可编辑金额、订单号、异常与等待核实状态。修改本地星辙页面使订单创建后进入付款页，并提供返回本地订单状态的路径。
- [x] 同步产品说明、支付架构、README 和路线图；明确运营后台、退款、对账及真实星辙接入后续阶段的范围。
- [ ] 构建前后端，使用当前 `.env` 的沙盒凭据走到 Payment Intent/付款组件，记录实际验证到的边界；不以浏览器跳转声称支付成功。

## 实施约束

不在前端保存或打印 Airwallex 密钥、完整卡资料及完整 Webhook 正文。不为产品加入测试入口。保持本地模拟器与生产配置隔离；真实星辙协议未给出前不声称可上线。
