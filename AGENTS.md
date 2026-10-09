# 星辙独立支付项目 AI 阅读入口

## 1. 项目说明

这是星辙的独立支付接入工具项目。前端当前建议采用 React、TypeScript、Tailwind CSS 和 shadcn/ui；后端以 Java、Spring Boot 为主。技术方案状态以技术栈清单为准。项目在 Windows + WSL Ubuntu 环境中开发，沟通和文档正文使用简体中文；`docs/` 下目录名和文件名使用英文小写 kebab-case。

人类开发者从 [`README.md`](README.md) 进入；需求澄清、计划、Figma 页面生成和虚构数据标记遵循[AI 协作约定](docs/engineering/ai-collaboration.md)，开发顺序见[路线图](docs/roadmap/development-roadmap.md)。

## 2. 开始任务时的阅读顺序

每次开始产品或代码任务，按任务范围阅读相关文档：

1. 先读 [`docs/product/product-spec.md`](docs/product/product-spec.md)，确认产品边界和业务责任。
2. 若涉及交易列表或详情页，继续读 [`docs/product/transaction-list.md`](docs/product/transaction-list.md) 和 [`docs/product/transaction-detail.md`](docs/product/transaction-detail.md)；其中“建议”和“待确认”内容不得当作正式接口字段。
3. 再读 [`docs/engineering/technology-stack.md`](docs/engineering/technology-stack.md)，确认技术选型状态。**“建议”或“待确认”不等于已批准，不要自行改成确定选择。**
4. 读 [`docs/engineering/code-style.md`](docs/engineering/code-style.md) 和对应语言规范。
5. 读 [`docs/engineering/directory-structure.md`](docs/engineering/directory-structure.md)，按既有模块边界放置代码。
6. 改动已有业务模块时，查找 `docs/architecture/` 中对应设计；若没有设计，按本文件第 3 节处理。

对应语言规范：

- Java：[`docs/engineering/backend/java.md`](docs/engineering/backend/java.md)
- React、TypeScript：[`docs/engineering/frontend/react-typescript.md`](docs/engineering/frontend/react-typescript.md)
- Tailwind、主题令牌和视觉实现：[`docs/engineering/frontend/visual-style.md`](docs/engineering/frontend/visual-style.md)
- 产品视觉原则：[`docs/engineering/frontend/design-principles.md`](docs/engineering/frontend/design-principles.md)
- 视觉参考与评审方法：[`docs/engineering/frontend/visual-references.md`](docs/engineering/frontend/visual-references.md)
- 部署与线上资源：[`docs/operations/deployment.md`](docs/operations/deployment.md)
- AI 协作与 Figma 页面生成：[`docs/engineering/ai-collaboration.md`](docs/engineering/ai-collaboration.md)
- 开发路线图：[`docs/roadmap/development-roadmap.md`](docs/roadmap/development-roadmap.md)

## 3. 产品说明和架构设计的先后关系

- 产品说明先定义要做什么、谁负责什么、流程如何运作和哪些事项未确认。
- 架构设计不预先编造完整系统结构；随着业务功能开始实现，针对非简单功能先新增或更新对应的架构设计，再按设计实施。
- 架构设计记录模块职责、调用/数据流、状态变化、错误恢复、权限、安全、幂等和验证范围。具体格式见 [`docs/architecture/README.md`](docs/architecture/README.md)。
- 如果实现中发现产品说明有歧义，先指出业务问题并向用户询问；不能用技术实现替用户决定业务规则。

## 4. 长期工程约束

- 修改代码前先阅读现有实现和对应业务文档，理解调用链及状态变化。
- 保持实现简洁易读、职责清楚、模块边界明确；避免过度抽象、无必要依赖和无关重构。
- 大型技术决定先说明方案、取舍和风险；由用户确认后更新相应文档。
- 保持技术栈、目录结构、代码规范和实际配置同步；文档中的建议状态需要用户确认后才转为已选定。
- 支付项目只执行星辙已批准的退款指令，不参与业务审批；不手动把未核实交易改为支付成功。
- 产品后台不提供测试付款、测试交易或测试管理功能；开发联调使用本地星辙模拟器和 Airwallex 沙盒，不能暴露为生产操作入口。
- Airwallex 和星辙机密只放在服务端或安全配置中，不进入前端、Git、测试样例和日志。
- 不将模拟星辙系统配置到生产环境；沙盒和生产使用独立配置。

## 5. 本地开发约束

- 命令在 WSL Ubuntu 中运行；Node.js 使用 fnm，包管理使用 pnpm。
- 不输出或使用 npm 命令，不生成 `package-lock.json`。
- Java 版本由 SDKMAN 管理；具体项目版本以技术栈清单最终确认内容为准。
- 除非用户要求，不擅自安装全局工具、升级依赖、提交、推送或修改机器级配置。

## 6. 文档维护

- 业务范围变化更新 `docs/product/product-spec.md`。
- 实现一个新业务模块或重要技术流程时，在 `docs/architecture/` 增加相应设计文档。
- 技术选择变化更新 `docs/engineering/technology-stack.md`。
- 编码风格或个人偏好变化更新 `docs/engineering/code-style.md` 或对应语言规范。
- 目录变化更新 `docs/engineering/directory-structure.md` 和本入口中的链接。
- Figma 原型字段、待确认信息和示例数据按 [`docs/engineering/ai-collaboration.md`](docs/engineering/ai-collaboration.md) 标注；原型不是接口契约。
- 不提前创建没有内容的占位文档；英文文件名、中文正文。
