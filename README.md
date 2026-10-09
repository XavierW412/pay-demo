# 星辙独立支付项目

这是一个为星辙提供支付通道接入、支付结果通知、退款指令执行、运营留痕和对账支持的独立工具。星辙继续负责业务订单、退款审批和服务开通；本项目不接管这些业务。

## 当前状态

- 当前处于产品与工程准备阶段，尚未创建前端、后端或部署代码。
- 首期产品范围和责任边界见[产品说明](docs/product/product-spec.md)。
- 运营后台交易列表字段建议见[交易列表页面需求](docs/product/transaction-list.md)。
- 技术栈仍有推荐项待项目初始化时确认和锁定，见[技术栈清单](docs/engineering/technology-stack.md)。
- 按阶段推进的路线见[开发路线图](docs/roadmap/development-roadmap.md)。

## 从哪里开始

1. AI 协作和文档阅读规则：[AGENTS.md](AGENTS.md)
2. 产品做什么：[产品说明](docs/product/product-spec.md)
3. 技术选择与状态：[技术栈清单](docs/engineering/technology-stack.md)
4. 代码与目录约定：[代码风格](docs/engineering/code-style.md)、[工程目录](docs/engineering/directory-structure.md)
5. 前端实现与视觉：[React/TypeScript 规范](docs/engineering/frontend/react-typescript.md)、[视觉原则](docs/engineering/frontend/design-principles.md)、[视觉规范](docs/engineering/frontend/visual-style.md)
6. 按功能补充的架构设计：[架构文档索引](docs/architecture/README.md)
7. 开发顺序：[开发路线图](docs/roadmap/development-roadmap.md)

## 重要边界

- 本项目首期只接入星辙持有的 Airwallex 商户账户；正式接口和账户能力需以星辙与 Airwallex 实际资料为准。
- 开发联调用本地星辙模拟系统和 Airwallex 沙盒。交付给星辙的运营后台不提供测试付款、模拟交易或测试管理功能。
- 所有金额、币种、支付状态和外部接口字段都必须来自已确认的产品规则或正式契约，不从原型图推断。
- Figma 原型使用的虚构值须明确标为“原型示例数据（非真实交易）”，不得把示例字段变成产品需求。

## 开发环境

项目工具在 WSL Ubuntu 中运行。Node.js 通过 fnm 管理，前端包管理使用 pnpm；Java 版本通过 SDKMAN 管理。具体版本以技术栈清单和最终项目配置为准。

当前仓库还没有可运行程序，因此暂时没有启动命令。初始化代码后，在此补充安装、启动、构建和内部工程验证命令。
