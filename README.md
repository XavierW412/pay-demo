# 星辙独立支付服务

本项目是星辙平台与 Airwallex 之间的支付通道工具。星辙仍负责业务订单、服务开通和退款决策；本项目负责创建支付请求、核实支付结果、通知星辙并保留支付处理记录。

## 当前开发范围

- 本地模拟星辙购买流程，契约仅用于开发联调，不代表星辙真实接口。
- Airwallex 沙盒 Payment Intent 与托管收银台跳转。
- 服务端支付状态核实、Webhook 处理与模拟通知。
- 首期金额币种为 CNY、USD；支付方式由 Airwallex 商户账户实际启用能力决定。
- 本地运行依赖 PostgreSQL；所有 Airwallex 凭据只从本地环境变量读取。

业务边界和进度见[产品说明](docs/product/product-spec.md)、[架构索引](docs/architecture/README.md)和[开发路线图](docs/roadmap/development-roadmap.md)。

## 开发环境

请在 WSL Ubuntu 中运行项目。使用 `.node-version` 指定的 Node.js、pnpm 12.5.1、SDKMAN 管理的 Java 21，以及 Docker Compose。

复制环境变量模板并填入自己的 Airwallex 沙盒凭据：

```bash
cp .env.example .env
```

启动本地数据库：

```bash
pnpm db:up
```

在两个 WSL 终端分别运行后端和前端：

```bash
pnpm dev:backend
```

```bash
pnpm dev:web
```

前端地址为 `http://localhost:5173`，后端健康状态为 `http://localhost:8080/actuator/health`。前端开发服务器会代理 `/api` 和 `/mock-xingzhe` 到后端。

## 工程检查

```bash
pnpm build:web
pnpm lint:web
./backend/mvnw -f backend/pom.xml -DskipTests package
```

## 重要边界

- 沙盒进度不代表生产账户、生产星辙接口或生产付款方式已验收。
- 浏览器返回页面不能直接把支付标记为成功；必须由服务端核实 Airwallex 状态后通知星辙。
- 本地模拟器仅允许在 `local` 配置下启用；生产环境不得启用模拟 API。
- 真实星辙协议、商户账户地区、支付方式和正式环境配置仍待星辙及 Airwallex 确认。
