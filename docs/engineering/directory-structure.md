# 工程目录结构规范

## 1. 文档职责

本文规定仓库现在有哪些文件、开始开发后如何扩展，以及前后端模块代码分别放在哪里。产品做什么见[产品说明](../product/product-spec.md)；具体技术选择见[技术栈清单](./technology-stack.md)；语言写法见[代码风格约定](./code-style.md)及各语言规范。

目录和文件名使用英文小写 kebab-case；文档正文使用中文。例外仅有仓库工具约定要求的根目录 `AGENTS.md`。

## 2. 当前仓库状态

当前仓库包含独立的 React/Vite 支付后台与本地模拟星辙前端、Spring Boot 后端和 PostgreSQL/Flyway 数据库。生产支付软件与开发模拟系统分开构建；后续模块状态以路线图为准。后续目录以实际模块边界为准，避免提前创建空目录和占位代码。

## 3. 初期形态

先保留与当前工作直接相关的文件：

```text
startrail-payment/
├── AGENTS.md
├── README.md
├── .editorconfig
├── .gitignore
└── docs/
    ├── product/
    │   ├── product-spec.md
    │   ├── transaction-list.md
    │   └── transaction-detail.md
    ├── architecture/
    │   └── README.md
    ├── superpowers/
    │   └── specs/                    # 跨模块架构设计与设计评审记录
    ├── engineering/
    │   ├── code-style.md
    │   ├── ai-collaboration.md
    │   ├── technology-stack.md
    │   ├── directory-structure.md
    │   ├── backend/
    │   │   └── java.md
    │   └── frontend/
    │       ├── react-typescript.md
    │       ├── design-principles.md
    │       ├── visual-references.md
    │       └── visual-style.md
    ├── operations/
    │   └── deployment.md
    └── roadmap/
        └── development-roadmap.md
```

## 4. 当前实现与后续目标形态

下方目录按当前实现和后续业务模块规划；技术栈及依赖版本以技术栈文档和锁文件为准。

```text
startrail-payment/
├── AGENTS.md                         # AI 协作入口、读取顺序和项目级约束
├── README.md                         # 人类开发者入口、本地启动和常用命令
├── .editorconfig                     # 编辑器基础格式
├── .gitignore
├── frontend/                         # React + TypeScript 运营后台
│   ├── package.json
│   ├── pnpm-lock.yaml
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── components.json               # shadcn/ui 基座、主题和组件源码路径
│   ├── public/
│   └── src/
│       ├── main.tsx
│       ├── app/                      # 路由、应用装配、全局布局和插件
│       ├── components/ui/            # shadcn/ui 源码组件，按实际使用逐步加入
│       ├── modules/                  # 按业务模块组织
│       │   ├── checkout/             # 托管跳转入口、短期上下文和历史返回适配
│       │   ├── operations/           # 登录、资金总览、交易、核查、员工及审计后台
│       │   ├── transactions/
│       │   ├── exceptions/
│       │   ├── refunds/
│       │   ├── reconciliation/
│       │   ├── team/
│       │   └── audit/
│       ├── shared/                   # 确认跨模块复用的组件和技术能力
│       │   └── components/           # 跨模块复用的业务组件
│       ├── assets/
│       └── styles/                   # Tailwind 主入口、CSS 变量和项目主题令牌
├── mock-xingzhe/                     # 仅本地开发使用的独立模拟星辙前端
│   ├── package.json
│   ├── vite.config.ts                # 独立端口 5175，只代理模拟 API
│   └── src/
│       ├── app/                      # 模拟器专属路由和应用装配
│       ├── modules/mock-xingzhe/     # 服务选购、订单、付款结果与退款历史模拟
│       ├── shared/api/               # 模拟器自己的 API 客户端和 DTO
│       └── styles/                   # 模拟器主题入口
├── backend/                          # Java/Spring Boot 模块化单体
│   ├── pom.xml                       # Maven 构建配置
│   ├── mvnw
│   ├── .mvn/wrapper/
│   └── src/
│       ├── main/
│       │   ├── java/<base-package>/
│       │   │   ├── order/
│       │   │   ├── payment/
│       │   │   ├── refund/
│       │   │   ├── notification/
│       │   │   ├── reconciliation/
│       │   │   ├── operations/
│       │   │   ├── identity/
│       │   │   ├── audit/
│       │   │   ├── integrations/
│       │   │   └── shared/            # 仅放稳定的跨模块基础能力
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-local.yml
│       │       ├── application-production.yml
│       │       ├── db/migration/          # 全环境数据库结构
│       │       └── db/migration-local/    # 仅本地模拟数据库结构
│       └── test/
├── deploy/                           # 只有实际采用后才建立部署脚本
├── docs/
│   ├── product/
│   │   └── product-spec.md
│   ├── architecture/                 # 随各业务模块设计逐步增加
│   │   ├── README.md
│   │   ├── payment/
│   │   ├── refund/
│   │   ├── notification/
│   │   └── reconciliation/
│   ├── engineering/
│   │   ├── code-style.md
│   │   ├── technology-stack.md
│   │   ├── directory-structure.md
│   │   ├── backend/
│   │   └── frontend/
│   │       ├── design-principles.md
│   │       ├── visual-references.md
│   │       └── visual-style.md
│   ├── integrations/                 # 有正式契约资料后建立
│   └── operations/
│       ├── deployment.md
│       └── reconciliation-runbook.md # 有实际运营流程后建立
├── .env.example                      # 仅变量名和非敏感示例
└── compose.yaml                      # 确认采用本地容器后再建立
```

支付后台 `frontend/` 与本地模拟器 `mock-xingzhe/` 是两个独立构建入口。模拟器不能被支付后台导入，生产部署只构建 `frontend/`；本地通过根脚本分别启动和构建。两个应用各自维护必要的主题样式与 API 类型，避免模拟业务代码进入支付软件模块。

目标树说明未来可能的完整形态，不代表所有模块要在第一天建好。尤其 `docs/architecture/` 和 `docs/integrations/` 随业务实现和真实外部契约增加。

## 5. 前端内部目录约定

业务模块内部按实际需要逐步增加子目录：

```text
modules/transactions/
├── api/              # 支付记录相关请求及响应类型
├── components/       # 本模块业务组件
├── hooks/            # 本模块查询、状态和交互逻辑
├── pages/            # 路由页面
└── types/             # 本模块业务类型
```

- 小模块可以直接放少量文件，不强制建立每个子目录。
- 组件或逻辑被多个模块真实复用后才移动到 `shared/`。
- 页面负责组合视图和交互；接口调用、复杂状态转换和展示组件分工清楚。
- 不设 `utils` 大杂烩；每个工具函数放在其业务模块或有清晰用途的共享子目录。
- `checkout` 已收缩为短期入口、官方 SDK 托管跳转与历史返回适配；核实服务位于 `payment/application`，后台补查位于 `payment/infrastructure`，运营和入口均复用支付核心。见[范围校正建议](../roadmap/scope-alignment.md)。

## 6. 后端模块内部约定

业务模块内部逐步形成如下分层；实际依赖使用的技术以技术栈清单为准：

```text
payment/
├── interfaces/        # HTTP Controller、传输 DTO
├── application/       # 用例编排、事务边界
├── domain/             # 领域规则、状态和值对象
└── infrastructure/   # 数据库和外部服务实现
```

- `order` 只保存星辙业务订单的必要关联，不接管星辙商品或服务。
- `payment`、`refund`、`notification`、`reconciliation` 分别管理支付、退款执行、结果通知和资金核对。
- `identity` 和 `audit` 负责员工身份权限及操作留痕。
- `integrations` 隔离星辙和 Airwallex 外部协议；业务规则不写进外部适配器。
- 模块不直接访问其他模块的 Repository 或内部表；通过明确的业务接口协作。

## 7. 增删目录的规则

1. 先问这个目录代表什么职责，以及它是否有真实文件和维护者。
2. 只为已有功能建立目录；架构目标树里的候选模块不生成空壳。
3. 小功能先留在所属模块；跨模块复用稳定后才抽取共享能力。
4. 不因将来可能需要第二个支付服务商就拆微服务或共享 SDK。
5. 文件或目录改名后，更新本文件、根目录 AI 阅读入口和相关链接。
6. 业务模块边界变化同步更新架构文档；依赖选型变化只更新技术栈清单。

- `checkout/config/TrustedCheckoutRedirectConfiguration` 负责可信公开入口与星辙返回模板；`payment_status_refresh_cursor` 由公共 V110 迁移创建，下一迁移序号 V111。
