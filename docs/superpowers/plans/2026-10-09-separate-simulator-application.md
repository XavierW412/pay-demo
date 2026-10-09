# 支付后台与本地模拟星辙应用拆分计划

**状态：已确认并实施。**

**目标：** 把支付软件运营后台与开发阶段的模拟星辙拆成两个独立运行、独立构建的前端应用。生产构建只包含支付软件；模拟星辙只能在本地开发环境运行。

**设计依据：** [支付软件、模拟星辙、退款复核与资金总览边界设计](../specs/2026-10-09-separated-simulator-refund-finance-design.md)。

**当前代码依据：** 根 pnpm workspace 只有 `frontend`；`frontend/src/app/router.tsx` 同时注册 `/operations` 和开发态 `/mock-xingzhe`；模拟页面、API 客户端和共享 DTO 都在 `frontend/src`。后端模拟控制器和模拟服务已由 Spring `local` profile 限制。

## 设计选择与风险

- 保留现有 `frontend/` 作为支付软件应用，避免迁移整套运营后台和结账返回路由；新增根目录 `mock-xingzhe/` 作为 pnpm workspace 应用。
- 把模拟星辙页面、样式、模拟 API 客户端和只供模拟器使用的 DTO 搬入新应用。新应用可复用已安装依赖版本，但不依赖支付后台源码。
- 模拟应用只能通过开发代理访问本地后端；生产构建任务、部署说明和支付后台路由均不包含模拟页面。
- 后端仍以 `local` profile 保护模拟接口。前端拆分不是安全边界的替代品。
- 风险是搬迁会影响 Vite 路径别名、API base、静态资源路径和原有本地入口；实施时保留支付后台默认入口，并更新根脚本与 README。

## 涉及文件

- `pnpm-workspace.yaml`、根 `package.json`：注册模拟应用并提供明确的 `dev:mock-xingzhe`、`build:mock-xingzhe` 脚本。
- `mock-xingzhe/package.json`、`index.html`、`vite.config.ts`、`tsconfig*.json`、`src/main.tsx`：建立独立 React/Vite 应用和 `/mock-xingzhe/api` 本地代理。
- `frontend/src/app/router.tsx`、`frontend/src/modules/mock-xingzhe/**`、`frontend/src/shared/api/contracts.ts`：移除模拟页面、模拟路由及只供模拟器使用的类型；支付后台根路由指向运营后台。
- `mock-xingzhe/src/**`：承接原有模拟选购、订单页面、样式与 API；随后承接订单历史和付款结果控制计划中的页面。
- `README.md`、`docs/engineering/directory-structure.md`、`docs/engineering/ai-collaboration.md`、`docs/roadmap/development-roadmap.md`：记录独立启动/构建边界以及模拟数据标识。

## 实施步骤

1. 盘点模拟页面对 `frontend/src/shared` 的引用，将必要 DTO 与 API 封装迁至 `mock-xingzhe/src`，禁止新模拟应用反向导入支付后台模块。
2. 新建 Vite 应用和本地代理配置，沿用当前 React、TypeScript、Tailwind 与现有依赖主版本，不做无关升级。
3. 将模拟选购页、订单页与样式迁入，清除支付后台 Router 中的模拟路由和开发态模拟首页分支。
4. 更新 workspace、根脚本、运行说明和目录规范；部署说明明确生产只构建支付后台，后端生产 profile 不启用模拟接口。
5. 检查两个应用引用边界、路由和构建配置；不启动服务、不做浏览器验收，除非用户另外要求。

## 验收与验证

- `pnpm build:web` 构建支付后台，产物不含模拟页面与模拟入口。
- `pnpm build:mock-xingzhe` 独立构建模拟应用。
- `git diff --check` 通过；README 与目录文档能准确说明两套应用的本地启动和生产边界。
- 不新增或运行自动化测试；不启动服务、不调用 Airwallex 沙盒或生产 API。

## 依赖顺序

本计划可先实施。订单历史与退款模拟页面依赖新模拟应用目录；后端模拟 API 可并行按其专属计划设计，但实际实施按本计划完成后接入新 UI。
