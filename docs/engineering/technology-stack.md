# 技术栈清单与选型状态

## 1. 文档目的与状态说明

本文从开发机到生产运行环境，完整列出本项目需要评估和记录的技术领域、候选方案、版本方向、职责、引入条件和暂缓项。它是技术选型的唯一索引，不替代各语言的开发规范和前端视觉规范。

- **已确认**：用户明确提出或项目范围已经确定。
- **建议采用**：基于项目形态给出可执行的默认方案，实施前可审阅。
- **待甲方/部署环境确认**：需要星辙接口、云平台或生产条件才能定案。
- **暂不引入**：首期没有明确需要，未来满足触发条件时再评估。

版本以本文件核对日期为参考。实际创建项目时选择仍受支持的稳定版本，精确到补丁版本后写进锁文件或构建文件；不追逐预览版，不随意升级主版本。

## 2. 首期工程基线

| 范畴 | 推荐基线 | 状态 | 说明 |
| --- | --- | --- | --- |
| 开发系统 | Windows + WSL Ubuntu，运行工具和项目均在 WSL 内 | 已确认 | 不用 Windows 原生 Node、Java、Python 或 Git |
| Java 运行时 | JDK 21 | 建议采用 | 新项目基线；具体发行版由 SDKMAN 管理，沙盒与生产统一大版本 |
| 前端运行时 | Node.js 当前受支持的 LTS 主版本 | 建议采用 | 通过 fnm 管理；项目写入 `.node-version` 或等效版本文件 |
| 前端包管理 | pnpm | 已确认 | 只使用 pnpm，提交 `pnpm-lock.yaml`，不生成 npm 锁文件 |
| 前端框架 | React 19.3 + TypeScript | 建议采用 | 与附件中 shadcn/ui 方案直接匹配；初始化时复核稳定版本 |
| 前端构建 | Vite | 建议采用 | 适合 Java REST API 配套的单页运营后台；无需为运营后台引入服务端渲染 |
| 前端样式系统 | Tailwind CSS + CSS 自定义属性 + 项目设计令牌 | 已确认 | Tailwind 是唯一页面主样式体系；不引入 Sass/SCSS 作为平行样式系统 |
| 前端组件方案 | shadcn/ui + Base UI | 建议采用 | shadcn CLI 将所选组件源码加入项目；Base UI 是当前新项目默认基座，初始化时核验 |
| 前端图标 | Lucide React | 建议采用 | 统一图标风格；避免用 Emoji 作为操作图标 |
| 后端框架 | Spring Boot 4.1.x | 建议采用 | 以 2026-10-09 核对的官方稳定版本线为基线；创建项目时复核最新受支持补丁版本 |
| 后端构建 | Maven Wrapper，Maven 3.9.x | 建议采用 | 每位开发者和 CI 使用项目固定的 Maven 版本 |
| 服务形态 | 一个 Spring Boot 模块化单体 | 已确认 | 首期不拆微服务，按支付业务模块隔离内部职责 |
| 关系数据库 | PostgreSQL 18.x | 建议采用 | 适合交易、退款、审计及对账记录；需确认星辙生产云服务区域支持情况 |
| 数据访问 | Spring Data JPA + Hibernate | 建议采用 | 聚合与常规持久化优先；复杂对账查询可用明确 SQL，不为抽象而抽象 |
| 数据库迁移 | Flyway | 建议采用 | 所有结构变化通过有版本的迁移脚本，不在生产手工改表 |
| 用户认证授权 | Spring Security + 服务端会话 Cookie | 建议采用 | HttpOnly、Secure、SameSite 等属性按部署域名配置；角色授权由后端执行 |
| 服务端 API | Spring MVC REST/JSON | 建议采用 | 星辙协议、错误码和签名方式等候其正式接口资料 |
| 服务商 API | 服务端 HTTP 客户端访问 Airwallex API | 建议采用 | 评估官方 SDK 后再决定；任何密钥只在服务端保存 |
| 前端 API 客户端 | Axios 封装 | 建议采用 | 统一基础地址、认证凭证、错误转换、超时和请求标识 |
| 图标 | Lucide React 图标集 | 建议采用 | 全站保持同一线性风格；使用语义清楚的矢量图标，不用 Emoji 充当界面图标 |
| 图表 | Apache ECharts | 条件引入 | 只有首期确实需要趋势或分布图时加入；对账先保证表格可核查 |
| 接口契约 | OpenAPI 3 | 建议采用 | 星辙接口明确后记录契约；需要时生成前端类型，不手工维护多份相同定义 |
| 后端质量工具 | JUnit 5、Mockito、Spring Boot Test、Testcontainers | 建议采用 | 覆盖状态、幂等、事务和数据库行为；集成测试按需要启动容器 |
| 前端质量工具 | ESLint、Prettier、TypeScript、Vitest、React Testing Library | 建议采用 | 统一格式、类型检查和组件/业务逻辑验证；仅用于开发阶段 |
| 浏览器验收 | Playwright | 建议采用 | 覆盖关键支付状态展示、权限和后台操作的端到端流程 |
| 服务运行 | Linux 云环境，HTTPS | 待部署环境确认 | 生产服务器、域名和网络资源由星辙持有 |
| 容器 | Docker；本地可用 Docker Compose | 建议采用 | 生产是否采用容器由星辙运维平台决定，不强制 Kubernetes |
| 可观测性 | Spring Boot Actuator + 结构化日志 | 建议采用 | 健康检查、错误和关键业务关联号；生产指标平台待星辙确认 |
| CI/CD | Git 托管平台流水线 | 待确认 | GitHub、GitLab 或星辙指定平台未确定前，不写死供应商 |

## 3. 完整技术分层清单

### 3.1 前端基础层

| 能力 | 选型 | 状态与边界 |
| --- | --- | --- |
| 单页应用框架 | React 19.3 | 建议采用；初始化时复核并锁定稳定版本，不与 Vue 并行 |
| 语言 | TypeScript | 已确认；启用严格模式；API 边界避免 `any` |
| 组件格式 | React 函数组件 + TSX | 建议采用；页面、业务组件和基础 UI 组件分层 |
| 构建和开发服务器 | Vite + React 插件 | 建议采用；兼容稳定版本在初始化时锁定 |
| 页面路由 | React Router | 建议采用；路由级懒加载、登录保护和权限元信息 |
| 全局客户端状态 | React Context 管理登录身份和稳定全局设置 | 建议基线；避免过早引入 Redux 等全局状态框架 |
| 服务端请求 | Axios + 模块 API 封装 | 建议采用；统一凭证、取消、超时、错误码和请求追踪 |
| 服务端缓存 | TanStack Query for React | 建议采用；管理服务端列表/详情缓存和刷新，界面临时状态留在页面/表单 |
| 表单 | shadcn/ui 字段组件 + 后端校验 | 建议采用；复杂表单确有需求后再评估 React Hook Form |
| 客户端数据校验 | 原生类型和轻量规则优先 | 暂不指定 Zod 等库；服务端永远是金额、币种和权限校验的权威 |
| 日期、数字与币种展示 | `Intl` API + 项目内格式化函数 | 建议采用；金额展示必须带币种，内部传输按 API 契约执行 |
| 国际化 | react-i18next | 条件引入；仅在运营后台实际需要中英文界面时加入。付款页语言由 Airwallex 能力和星辙需求决定 |
| 图标 | Lucide React | 建议采用；只在语义无法表达时使用文字按钮，不混搭多套图标 |
| 图表 | ECharts | 条件引入；没有明确运营问题需要图表时不安装 |
| CSS 样式系统 | Tailwind CSS 4 + CSS 自定义属性 | 建议采用；Tailwind 是页面主样式体系，不再使用 SCSS |
| UI 组件 | shadcn/ui + Base UI | 建议采用；组件源码由项目维护，设计原则与主题令牌以本项目为准 |

### 3.2 前端代码质量与工程验证（仅开发阶段）

本节工具用于开发者检查代码、联调和上线前验证，不会形成供星辙员工或最终用户使用的测试页面、测试支付入口或测试数据管理功能。产品范围排除详见[产品说明](../product/product-spec.md)。

| 能力 | 选型 | 约束 |
| --- | --- | --- |
| 代码检查 | ESLint + typescript-eslint + React Hooks 规则 | 统一 TypeScript、TSX 和 React Hooks 规则 |
| 格式化 | Prettier | 与 ESLint 分工；格式规则由配置文件固定 |
| 类型检查 | TypeScript `tsc --noEmit` | 构建前执行，不能只依赖 Vite 转译 |
| 单元测试 | Vitest | 适合 Vite 项目；覆盖纯业务逻辑、状态转换和工具函数 |
| React 组件测试 | React Testing Library | 检查用户可观察行为，不检查组件内部实现细节 |
| 端到端测试 | Playwright | 用于登录权限、查询异常、重试通知等关键用户链路 |
| 依赖锁定 | pnpm 锁文件 | 所有开发机和 CI 使用同一锁文件解析依赖 |

### 3.3 后端语言、框架和构建

| 能力 | 选型 | 状态与边界 |
| --- | --- | --- |
| JDK | Java 21 | 建议锁定；不使用 Java 8 作为新项目基线。上线云平台必须提供相同或兼容运行时 |
| 框架 | Spring Boot 4.1.x | 建议采用；至少使用仍有开源支持的稳定版本线，并在启动项目时重新核验 |
| Web 框架 | Spring MVC | 建议采用常规同步 HTTP API；首期不因“高并发”假设选择 WebFlux |
| 构建工具 | Maven Wrapper 3.9.x | 建议采用；仓库固定 Wrapper，CI 不依赖全局 Maven 版本 |
| 分层方式 | Controller/接口层 → Application/用例层 → Domain/领域层 → Infrastructure/基础设施层 | 已确认方向；模块内部按职责分层，见 Java 开发规范 |
| Java 代码格式 | Spotless + Google Java Format | 建议采用；自动格式化与 `check` 作为提交/CI 检查 |
| 静态规则 | Checkstyle 或 SpotBugs 二选一逐步引入 | 暂缓一次性堆叠；先启用必要错误检查，再依据缺陷类型扩充 |
| DTO | Java `record` 优先用于不可变请求/响应对象 | 建议采用；领域实体仍按其生命周期和不变量建模 |
| 代码生成 | 不默认使用 Lombok | 建议采用显式构造与现代 Java 语法；只有重复代码明显且团队理解其行为时再评估 |

Java 21 是本项目推荐基线，不代表生产服务器已确认支持。Spring Boot 官方文档中，4.1.1 至少需要 Java 17，Java 21 满足要求；3.5 系列的最后一个开源支持版本已在 2026 年 6 月发布，因此新项目优先评估受支持的 4.x 版本线。

### 3.4 后端业务和数据访问

| 能力 | 选型 | 状态与边界 |
| --- | --- | --- |
| HTTP API | Spring MVC REST/JSON | 以星辙接口协议为准，不擅自固定外部字段 |
| 参数校验 | Jakarta Bean Validation | 请求结构校验；金额、币种、订单归属和退款累计额还需业务校验 |
| 认证授权 | Spring Security | 服务端验证身份和角色；所有者账号只读权限必须在后端限制 |
| 会话方式 | HttpOnly 安全 Cookie 会话 | 前后端同站部署时的优先方案；域名、跨站和 CSRF 策略待部署方式确认 |
| 持久化 | Spring Data JPA + Hibernate | 首期推荐；复杂列表/对账聚合可使用清晰、可审查的 SQL |
| 数据库 | PostgreSQL 18.x | 首期推荐；必须核验星辙选择的云服务是否提供该主版本 |
| 数据库迁移 | Flyway | 结构修改提交迁移脚本；生产禁止仅靠启动时自动创建表结构 |
| API 文档 | OpenAPI 3；Spring 侧实现库待版本兼容核验 | 对外契约以星辙正式文档为准；接口文档不得泄漏密钥和真实交易数据 |
| 定时任务 | Spring Scheduling + 数据库任务状态 | 首期用于可恢复查询与通知重试；任务需记录次数、下次时间和最终结果 |
| 幂等 | 数据库唯一约束 + 应用层幂等键 | 支付创建、Webhook、退款指令和通知都要处理重复到达 |
| 事务 | Spring 事务管理 + 数据库约束 | 资金状态、关联记录和操作审计需明确事务一致性边界 |
| 服务商集成 | Spring `RestClient` 或经核实的官方 SDK | 先核验 Airwallex 官方 Java SDK 对当前 API 的支持；避免封装未经证实的能力 |
| 错误处理 | 全局异常映射 + 稳定内部错误码 | 外部服务错误与内部错误分开记录；不向浏览器暴露敏感细节 |

### 3.5 集成、安全和敏感信息

| 能力 | 选型 | 状态与边界 |
| --- | --- | --- |
| 星辙集成 | 独立 `StarPath` 接口适配层 | 正式认证方式、签名和重试协议待星辙资料 |
| Airwallex 集成 | 独立 `Airwallex` API/Webhook 适配层 | 首期只接 Airwallex；沙盒与生产凭证、地址完全分开 |
| Webhook | 公开 HTTPS 接收端、签名核验、事件幂等和查询核实 | 以 Airwallex 当前官方文档与账户能力为准 |
| 机密管理 | 环境变量或云厂商密钥管理服务 | 开发密钥放本地忽略文件；生产密钥仅通过星辙批准的安全配置注入 |
| 传输安全 | HTTPS/TLS | 付款通知和后台登录必须使用 HTTPS；不把密钥放入浏览器 |
| 审计 | 自有审计表记录操作者、对象、动作、时间和结果 | 重要运营操作不可只依赖普通应用日志 |
| 隐私 | 最小化收集和脱敏日志 | 不记录完整卡号、认证头、密钥或不必要个人信息 |
| 限流/防护 | 先由应用与云平台基础防护提供 | API 网关、WAF、专门限流组件待威胁模型和部署方案确认 |

### 3.6 本地开发、交付和生产运行

| 能力 | 选型 | 状态与边界 |
| --- | --- | --- |
| 本地数据库 | Docker Compose 启动 PostgreSQL | 等数据库主版本确认后建立本地一致配置 |
| 本地星辙替身 | `tools/mock-starpath/` 内的模拟服务或脚本 | 只用于本地联调，不进入生产构建 |
| 服务容器 | Dockerfile | 建议提供可重复构建的运行镜像；生产是否使用由星辙决定 |
| 健康检查 | Spring Boot Actuator | 区分存活和就绪；敏感 Actuator 端点不得公开 |
| 日志 | SLF4J + Logback，关键字段结构化 | 订单关联号、支付号、事件类型和结果；屏蔽敏感字段 |
| 指标/追踪 | OpenTelemetry 或云厂商原生监控 | 生产平台确认后选一套；首期不先部署完整监控栈 |
| 持续集成 | Git 托管平台流水线 | 至少运行格式、静态检查、测试、前端构建和后端打包；平台待确认 |
| 发布配置 | 环境变量/受控密钥服务 | 本地、沙盒、生产隔离；生产值不能提交到代码仓库 |
| 数据备份 | 云数据库备份及恢复演练 | 星辙选择云平台后确定保留周期、恢复目标和负责人 |
| 域名、HTTPS、固定出口 IP | 星辙提供生产资源 | 具体准备步骤见[部署说明](../operations/deployment.md) |

## 4. UI 组件和样式系统

你说明 Vue 是目前较熟悉的技术，但并非必须沿用；结合你接受 React、并希望使用 shadcn 组件体系的选择，当前建议采用 **React + TypeScript + Vite + Tailwind CSS 4 + shadcn/ui + Base UI + Lucide React**。这与 shadcn/ui 方案直接匹配，也有官方 Vite + React + TypeScript 初始化路径。[shadcn/ui 官方 Vite 指南](https://ui.shadcn.com/docs/installation/vite)

shadcn/ui 会把所选组件源码加入项目，供团队直接维护。2026 年 7 月起，shadcn/ui 将 Base UI 设为新项目默认基座，Radix 仍受支持；本项目先按 Base UI 建立基线，初始化时以 CLI 实际配置和稳定版本复核。[组件基座公告](https://ui.shadcn.com/docs/changelog/2026-07-base-ui-default)

| 层次 | 技术 | 项目约定 |
| --- | --- | --- |
| 页面框架 | React 19.3 + TypeScript | 函数组件、TSX、严格类型检查；与 Vue 不并行 |
| 构建和路由 | Vite + React Router | 单页运营后台；无需 SSR，页面按模块路由组织 |
| 页面样式 | Tailwind CSS 4 | 唯一页面主样式体系；使用主题令牌，不堆砌任意值 |
| 主题令牌 | CSS 自定义属性 + Tailwind 主题映射 | 原始、语义、组件三层；业务界面使用语义令牌 |
| 交互原语 | Base UI（shadcn/ui 当前默认） | 用于弹窗、菜单和复合控件；通过已加入项目的组件源码维护 |
| 可维护组件源码 | shadcn/ui CLI 添加到项目的 React 组件 | 纳入代码评审和规范；只添加实际使用的组件并维护项目主题 |
| 图标 | Lucide React | 统一线条风格和尺寸；图标按钮必须有可访问名称 |
| 服务端状态 | TanStack Query for React | 管理查询缓存和刷新；服务端列表不复制进全局客户端状态 |
| 数据表格 | 项目业务表格组件；复杂排序筛选确有需求后再评估 TanStack Table | 不因“后台常用”提前增加多层抽象 |

不引入 Vue、shadcn-vue、Reka UI、Element Plus、Dart Sass 或 SCSS 作为并行实现。视觉目标由项目设计原则、令牌和浏览器评审流程约束，不由默认组件外观决定。详见[产品界面设计原则](./frontend/design-principles.md)和[视觉参考](./frontend/visual-references.md)。

动效保持轻量；只有出现无法用 CSS 过渡表达的复杂时间轴需求时再评估 GSAP，后台不默认引入 Three.js。

## 5. 明确暂缓的技术

下列技术不属于首期默认依赖，只有出现明确需求、容量指标或甲方平台要求时再评估：

- 微服务、服务网格、Kubernetes。
- Kafka、RabbitMQ、独立消息中间件。
- Redis 分布式缓存或分布式锁。
- GraphQL、服务端渲染框架或第二套前端框架。
- Vue/shadcn-vue、Reka UI、Element Plus、Sass/SCSS 或另一套完整 UI 组件库作为平行主体系。
- 大型状态管理或查询缓存框架，在没有缓存失效问题前不引入。
- 多租户平台、订阅计费、第二家支付服务商和自建统一收银台。
- 为图表、国际化或后台自动化而先行安装的可选依赖。

## 6. 版本和升级管理

1. JDK 固定为 Java 21；开发机通过 SDKMAN 切换，CI 和服务器也固定兼容的大版本。
2. Spring Boot 固定在仍有开源支持的 4.1.x 线，补丁升级按安全公告和兼容验证执行。
3. PostgreSQL 固定 18.x；如果星辙云平台暂不支持，再评估仍受支持的上一主版本并记录原因。
4. 前端 React、TypeScript、Vite、Tailwind CSS 和 shadcn/ui CLI 固定兼容的稳定版本；具体补丁号由 pnpm 锁文件固定。
5. Java 依赖由 Spring Boot 依赖管理优先控制；只在确认兼容后覆盖版本。
6. 大版本升级必须先查看官方升级指南、兼容性与安全说明，再安排单独变更，不与支付功能改动捆绑。
7. 每次修改核心版本，更新本文件的核对日期、版本号、影响和回退办法。

## 7. 开始编码前需要落定的配置

- JDK 21 发行版和 CI/生产安装方式。
- 初始化时仍受支持的 Spring Boot 4.1.x 补丁版本、Maven Wrapper 精确版本。
- PostgreSQL 18 在星辙目标地区云服务的可用性、备份和恢复配置。
- 初始化项目时锁定 React、Vite、TypeScript、Tailwind CSS、shadcn CLI、Base UI、Lucide React 的兼容稳定版本，并确定主题令牌和代表页面评审基线。
- ESLint、Prettier、TypeScript、React Testing Library 及开发阶段验证工具的兼容版本集合。
- 星辙正式 API、签名方式、通知重试和 API 客户端认证模式。
- 生产 CI/CD、密钥管理、日志、监控、域名、HTTPS 和网络访问方案。

以上是少数必须和运行环境或甲方协议共同确定的项；其他推荐方案可以作为首期设计默认值，并在工程初始化时一次性锁定版本。

## 8. 官方依据

- [React 官方版本说明](https://react.dev/versions)
- [shadcn/ui Vite 初始化指南](https://ui.shadcn.com/docs/installation/vite)
- [shadcn/ui Base UI 默认基座说明](https://ui.shadcn.com/docs/changelog/2026-07-base-ui-default)
- [shadcn/ui 组件配置](https://ui.shadcn.com/docs/components-json)
- [Tailwind CSS 工具类样式模型](https://tailwindcss.com/docs/styling-with-utility-classes)
- [Spring Boot 4.1 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot 3.5 最后一个开源支持版本公告](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)
- [PostgreSQL 版本支持政策](https://www.postgresql.org/support/versioning/)

资料核对日期：2026-10-09。版本支持和项目维护状态会变化，项目初始化前要再核验一次。
