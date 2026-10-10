# 星辙独立支付服务

本项目是星辙平台与 Airwallex 之间的支付通道工具。星辙仍负责业务订单、服务开通和退款决策；本项目负责创建支付请求、核实支付结果、通知星辙并保留支付处理记录。

## 当前开发范围

- 本地模拟星辙购买流程，契约仅用于开发联调，不代表星辙真实接口。
- 本地结果模拟路径可跳过付款人页面，验证支付状态、结果通知和模拟服务开通；只在 `local` 配置下可用。
- Airwallex 沙盒 Payment Intent 与官方托管收银台跳转；本项目只提供短期安全入口。
- 服务端支付状态核实、Webhook 处理、自动补查与可靠通知。
- 运营后台：资金总览、交易查询、支付状态核实、失败通知重试、处理备注、员工邀请与操作审计。项目所有者账号只读。
- 星辙签名退款指令的接收、金额核对和预留；只有支付侧员工管理员确认后才向 Airwallex 提交。退款状态会继续核实并通知星辙。
- 逐笔比对本地支付单与 Airwallex 当前状态，保存差异快照和通知送达情况。该功能不等同于资金结算对账。
- 资金总览按 CNY、USD 分开显示已核实支付成功、退款和异常；实际结算金额及手续费等待 Airwallex 报告接入，不按公式估算。
- 首期金额币种为 CNY、USD；支付方式由 Airwallex 商户账户实际启用能力决定。
- 本地运行依赖 PostgreSQL；所有 Airwallex 凭据只从本地环境变量读取。

业务边界和进度见[产品说明](docs/product/product-spec.md)、[架构索引](docs/architecture/README.md)和[开发路线图](docs/roadmap/development-roadmap.md)。

## 同事阅读顺序

先读本文件了解项目范围和本地启动方式；再读[产品说明](docs/product/product-spec.md)理解星辙、支付项目与 Airwallex 的职责边界；随后按需阅读[支付链路架构](docs/architecture/payment/payment-flow.md)、[星辙接口约定](docs/architecture/integration/xingzhe-payment-contract.md)和[开发路线图](docs/roadmap/development-roadmap.md)。本地模拟接口不代表星辙正式接口，沙盒验证也不代表生产验收。

## 开发环境

请在 WSL Ubuntu 中运行项目。使用 `.node-version` 指定的 Node.js、pnpm 12.5.1、SDKMAN 管理的 Java 21，以及 Docker Compose。

复制环境变量模板。首次填入 Airwallex 沙盒 `CLIENT_ID` 和 `API_KEY`；Webhook 签名密钥在 Airwallex 创建订阅后取得，配置方法见下文：

```bash
cp .env.example .env
```

启动本地数据库：

```bash
pnpm db:up
```

如需启动数据库，在 WSL 终端执行：

```bash
pnpm dev:backend
```

```bash
pnpm dev:web
```

在另一个终端启动仅供本地开发联调的模拟星辙：

```bash
pnpm dev:mock-xingzhe
```

后端脚本和 IDEA 从 `backend` 目录启动时，`local` 配置会读取项目根目录 `.env`；IDE 需选择 Java 21、激活 `local` 配置。当前终端若仍使用 Java 8，可先在该终端执行 `sdk use java 21.0.12+1.1-tem`，或选用本机已安装的 Java 21 版本。

支付后台地址为 `http://localhost:5173`，本地模拟星辙地址为 `http://localhost:5175/mock-xingzhe`，后端健康状态为 `http://localhost:8080/actuator/health`。支付后台只代理 `/api`；模拟器单独代理 `/mock-xingzhe/api`。生产只构建和部署支付后台，不部署模拟器。

运营后台入口为 `http://localhost:5173/operations`。首次启动时，后端会从根目录 `.env` 的 `OPERATOR_OWNER_USERNAME`、`OPERATOR_OWNER_PASSWORD` 创建只读所有者账号，并从 `OPERATOR_STAFF_USERNAME`、`OPERATOR_STAFF_PASSWORD` 创建首位星辙员工管理员账号。请在本机查看自己的 `.env` 获取登录信息，不要将密码发到聊天或提交到仓库。初始账号创建后，修改 `.env` 不会覆盖数据库中已有密码。员工管理员可以在后台生成一次性邀请链接，由自己交付给星辙员工。

本地购买页可选两条联调路径：

- **Airwallex 沙盒收银台：** 模拟星辙创建业务订单 → 支付项目创建 Airwallex Payment Intent → 跳转 Airwallex 托管收银台 → 服务端核实并通知模拟星辙 → 模拟星辙开通示例服务。
- **本地模拟付款结果：** 模拟星辙创建业务订单 → 支付项目创建来源标记为 `LOCAL_SIMULATOR` 的支付单 → 在订单页选择成功、资金不足、处理中或等待超时 → 经过同一状态保存与通知 outbox → 成功时模拟星辙开通示例服务。处理中或超时后可补录模拟最终结果。

第二条路径不会请求 Airwallex，不代表真实收款，也不支持退款、Airwallex 状态查询或资金对账；它只验证支付服务内部状态和星辙通知链路。模拟超时会标为待核实，不自动视为失败。模拟星辙提供独立的订单列表、详情和支付事件历史。付款方式选择、卡片和账单资料填写仍由 Airwallex 托管页面负责；本项目不提供自有收银台。订单金额、币种与国家由模拟星辙服务端提供，国家不按币种推断。创建收款失败时保留原业务单，以同一请求编号继续付款；付款处理中等待结果，不重新创建业务单。

付款成功并收到星辙通知后，可在本地模拟订单页提交退款申请，再模拟星辙业务方批准或驳回。只有模拟业务方批准后才会发送独立签名指令；支付项目核对金额、原支付和重复请求后，将退款标为“待员工管理员确认”或“已拦截”。支付后台的 `STAFF_ADMIN` 员工管理员确认后才调用 Airwallex；`STAFF_OPERATOR` 和所有者只读账号不能执行。Airwallex 状态随后核实并回写模拟星辙退款历史。退款要求沙盒权限且原付款已成功。

支付后台默认进入资金总览，可按日期查看 CNY、USD 的支付成功金额、退款处理金额和核查异常。支付成功与退款执行状态不等于实际结算入账；Airwallex 结算报告尚未接入时，结算金额和手续费显示“待接入”。

2026-10-09 已按上述范围替换内嵌付款组件，保留交易、退款、核查和审计模块，并加入仅本地可用的结果模拟路径。退款需要员工管理员复核执行；运营首页增加只读资金总览。业务职责与完整步骤见[产品说明](docs/product/product-spec.md)，实现与恢复规则见[支付架构](docs/architecture/payment/payment-flow.md)。

Airwallex Pay 是一种钱包付款方式，选择后会进入付款人的 Airwallex Pay 登录页；这不是商户管理后台，也不代表需要使用你的商户沙盒账号付款。

项目不开发银行卡输入、绑卡或银行卡资料处理。需要验证 Airwallex 实际流程时使用沙盒托管页；只验证本项目状态与通知链路时可选本地模拟付款。需求依据及已被取代的早期想法见[产品说明](docs/product/product-spec.md#需求依据与付款方式约束2026-10-09-校正)。

### 托管收银台显示的付款方式

付款页由 Airwallex 托管；支付项目只负责安全跳转、核实结果和通知星辙。托管页按商户已启用的方式、订单币种和付款人地区决定显示哪些选项，用户在该页面完成付款。付款用户不选择收款商户账户。

如果选择 Airwallex Pay，跳到登录页是该钱包方式本身的流程，需要付款人登录其可用的 Airwallex Pay 账户；这不是商户后台登录。用户选择其他方式时，按 Airwallex 托管页相应流程完成付款。

2026-10-09 较早一次查询 `GET /api/v1/pa/config/payment_method_types` 时，所有单次付款方式、人民币/中国和美元/美国三组查询均仅返回已启用的 `airwallex_pay`。这是历史能力快照，账户后续配置已经变化，不能据此断言当前仍只有该方式。联调前应重新查询并核对订单币种和付款人地区；查询到方式已启用，也不代表它支持每笔目标订单或已经完成付款验收。

首轮联调优先核对“付款结果→服务端核实→通知星辙”。当前通过 Airwallex 沙盒托管页完成；如果不想操作付款人页面，可再实现隔离的本地结果模拟。无需为了验证这条业务链路开通或实现银行卡功能。

### Airwallex 沙盒 Webhook 联调

Airwallex 需要从公网访问本地后端。开发联调不需要购买域名或服务器，可使用 Cloudflare 临时 HTTPS 隧道。保持数据库和后端运行，在新的 WSL 终端执行：

```bash
cloudflared tunnel --url http://localhost:8080
```

本机需要安装 `cloudflared`；临时 Quick Tunnel 不需要购买域名或注册 Cloudflare 账户，具体限制见 [Cloudflare 官方说明](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)。

终端会输出临时的 `https://……trycloudflare.com` 地址。在 Airwallex 沙盒的 Webhook 订阅中，将“通知链接”填写为该地址加上后端接收路径：

```text
https://……trycloudflare.com/api/v1/webhooks/airwallex
```

可先访问 `https://……trycloudflare.com/actuator/health` 确认隧道能到达后端。创建 Webhook 后，将 Airwallex 为该订阅提供的签名密钥填入根目录 `.env` 的 `AIRWALLEX_WEBHOOK_SECRET`，然后重启后端，再发起沙盒付款。该密钥和 `AIRWALLEX_API_KEY` 不同，均不得提交到 Git 或发送到聊天中。

临时隧道地址会在隧道停止后失效；测试期间需保持隧道终端运行。临时公网链接可访问本地服务，只在沙盒联调期间开启，结束后停止隧道和本地服务。Quick Tunnel 仅用于开发测试，不用于生产。

生产环境域名、HTTPS、正式 Webhook 配置和密钥部署步骤见[部署与资源准备说明](docs/operations/deployment.md)。

若要把本机运行的支付后台和模拟星辙临时分享给甲方，按[本地开发版公网演示](docs/operations/local-public-demo.md)分别开放两个前端入口并配置临时返回地址；该方式仅用于沙盒体验。

## 星辙正式对接准备

本地模拟器使用的[星辙付款与结果通知接口约定](docs/architecture/integration/xingzhe-payment-contract.md)已经由支付项目实现，供星辙讨论和联调；它尚未获得星辙正式确认。生产环境需要星辙提供实际通知地址、独立的付款/退款入站签名密钥、出站签名密钥和业务订单接口资料。当前代码的生产配置要求 `STARTRAIL_INBOUND_SECRET`、`STARTRAIL_REFUND_INBOUND_SECRET`、`STARTRAIL_OUTBOUND_SECRET` 与 `STARTRAIL_PAYMENT_NOTIFICATION_URL`，以及 `PAYMENT_CHECKOUT_PUBLIC_BASE_URL`、`STARTRAIL_BUSINESS_ORDER_URL_TEMPLATE`，并拒绝不合规的通知、付款入口和业务返回配置。正式环境不读取根目录开发 `.env`，也不会启用模拟星辙接口。

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
- 退款复核执行、资金总览和支付状态核查已有代码，但尚未完成沙盒退款及真实业务验收；Airwallex 结算报表和星辙真实业务账本尚未接入，页面不会宣称已完成资金结算对账。

## 付款入口与恢复配置

本地 `.env.example` 提供 `PAYMENT_CHECKOUT_PUBLIC_BASE_URL=http://localhost:5173` 与 `STARTRAIL_BUSINESS_ORDER_URL_TEMPLATE=http://localhost:5175/mock-xingzhe/order/{businessOrderId}`；后端本地默认值也使用模拟星辙的 5175 端口。Airwallex.js 付款成功后先回支付项目的结果页，由服务端主动核实并通知星辙，再回到对应的模拟星辙订单页。若已有 `.env` 覆盖业务返回地址，需同步改为 5175。Payment Intent 创建时会固定星辙兜底返回地址；修改配置并重启后端后，新创建的支付单使用新地址，旧支付单仍按创建时的地址返回。

生产时分别配置支付项目公开 HTTPS 地址和星辙真实订单页 HTTPS 模板，不能使用本地模拟页面。付款入口默认有效 15 分钟；过期后由星辙用原请求编号重新取得入口，不应重新建业务订单。支付是否成功、通知是否送达、服务是否开通分别记录。

已有外部收款编号的未决支付单会由服务端补查：创建至少 10 秒后纳入，每轮最多 10 笔，默认每 10 秒执行一次；服务商仍返回未决状态时也按 10 秒后再查。可通过 `PAYMENT_STATUS_REFRESH_POLL_DELAY_MS` 调整调度周期；网络错误等失败逐笔退避至最多 15 分钟。补查不依赖用户停留页面，也不把超时当成付款失败；没有外部编号的创建请求继续使用原编号重试。Webhook 是主要的主动通知通道，需保持本地 HTTPS 隧道运行，并在 Airwallex 沙盒订阅受支持的 Payment Intent 付款事件；Webhook 不可达时，结果页核实和后台补查负责恢复。此调整只涉及支付状态更新，不改变财务/结算核查周期。

当前仍需补齐生产星辙协议、星辙确认的商户付款能力和实际付款/退款验收；银行卡未被指定为必选。构建通过不等同于资金链路完整验收。
