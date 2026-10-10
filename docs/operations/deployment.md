# 部署与资源准备说明

## 1. 资源归属与当前阶段

- 生产服务器、域名、数据库和网络资源由星辙购买并持有。
- 开发者自行购买的服务器或域名仅用于测试，不承载生产交易。
- 当前部署地域、云厂商、流量和预算未知，因此本文只记录通用顺序，不指定购买套餐或价格。
- 本地开发使用模拟星辙和 Airwallex 沙盒；生产使用星辙正式接口和其 Airwallex 生产账户。

## 2. 名词说明

- **服务器/运行环境**：运行支付后端、运营后台和相关存储的资源。
- **域名**：固定访问名称，通过 DNS 指向服务器或云平台入口。
- **公网入口地址**：互联网访问应用或 Webhook 的入口，可能由负载均衡器或云网关提供。
- **固定出口 IP**：本项目调用 Airwallex 时作为源地址的稳定 IP；仅在 API 凭证启用出口 IP 限制时需要。
- **HTTPS**：加密网络访问；Airwallex Webhook 使用可公开访问的 HTTPS 地址。

公网 Webhook 地址和固定出口 IP 用途不同，不要把二者当作同一项资源购买。

## 3. 通用准备顺序

1. 星辙确认部署地区、预算、预估访问量、资源负责人，以及是否必须使用现有云平台。
2. 确定应用运行方式、数据库和备份恢复要求，再由星辙开通生产云账号及资源。
3. 准备生产域名，配置 DNS 指向云平台公网入口。
4. 配置 HTTPS 证书，开放必要网站和 Webhook 流量，并限制运营后台访问范围。
5. 如 Airwallex API 凭证启用 IP 限制，再确认服务器是否有稳定出口 IP，并登记到允许列表。
6. 部署应用与数据库，配置生产地址、密钥、Webhook 地址和权限；密钥只进入服务端安全配置。
   支付项目还需要星辙提供已确认的付款、退款指令和结果通知接口。当前提议契约的生产配置是 `STARTRAIL_INBOUND_SECRET`、`STARTRAIL_REFUND_INBOUND_SECRET`、`STARTRAIL_OUTBOUND_SECRET` 和 `STARTRAIL_PAYMENT_NOTIFICATION_URL`；通知地址必须使用 HTTPS。见[星辙对接契约](../architecture/integration/xingzhe-payment-contract.md)。
7. 在 Airwallex 生产账户配置 Webhook 和所需事件，验证连通性、签名核验、状态查询、重试与异常记录。
8. 确认生产账户币种和付款方式、监控与告警、数据备份恢复及回退负责人，再按双方安排开放真实交易。

## 4. 按阶段准备

| 阶段 | 目标 | 典型资源 |
| --- | --- | --- |
| 本地开发 | 模拟星辙与支付项目联调 Airwallex 沙盒 | 开发机、沙盒凭证；无需购买生产服务器或域名 |
| 沙盒 Webhook 联调 | 验证公网事件送达及签名处理 | 临时 HTTPS 隧道或测试环境 |
| 生产准备 | 部署正式接口和账户配置 | 星辙购买的运行环境、数据库、域名、HTTPS、必要网络资源 |
| 正式上线 | 小范围验证后开放真实交易 | 生产账户、告警、备份和回退流程 |

## 5. 上线前清单

- [ ] 星辙生产订单和退款接口已确认并通过联调。
- [ ] Airwallex 生产账户、币种、付款方式和退款能力已确认。
- [ ] 生产密钥和沙盒密钥分开，密钥只在服务端配置。
- [ ] HTTPS 域名和 Airwallex Webhook 事件配置完成。
- [ ] 支付、通知、退款和结算状态可分开查询。
- [ ] 通知失败、状态未知和对账差异有告警与负责人。
- [ ] 数据备份、恢复演练、生产监控和故障回退已安排。
- [ ] 星辙负责生产资源、业务操作和开通服务；支付项目负责支付处理和技术留痕。

## 6. Airwallex Webhook 地址配置

### 沙盒本地联调

本地服务接收地址为 `POST /api/v1/webhooks/airwallex`。Airwallex 无法访问开发机的 `localhost`，因此本地联调需要 HTTPS 隧道。无需购买域名或服务器；项目 README 记录了启动数据库、后端和 Cloudflare Quick Tunnel 的命令。生成临时 HTTPS 地址后，将其与接收路径拼接，填入 Airwallex Sandbox 账户的 Webhook 通知链接，例如：

```text
https://<临时隧道域名>/api/v1/webhooks/airwallex
```

隧道停止后地址失效，新的隧道可能产生不同域名；此时需更新沙盒 Webhook 订阅。临时隧道只用于开发，不得放入生产配置。

### 生产环境

1. 由星辙提供固定生产域名，配置 DNS 和有效 HTTPS 证书，并通过负载均衡器/反向代理将下列路径转发到支付后端：

   ```text
   https://<星辙正式域名>/api/v1/webhooks/airwallex
   ```

2. 使用星辙的 Airwallex **生产账户**创建 Webhook 订阅。选择对应的账户级“收单”事件，并将事件范围限制在当前已部署版本能够处理的 Payment Intent 状态事件；本项目未完成退款事件处理前，不要把退款 Webhook 当作已支持的生产能力。
3. 锁定并记录 Webhook API 版本。创建订阅后，将该订阅对应的 Webhook 签名密钥写入生产密钥管理服务，并通过 `AIRWALLEX_WEBHOOK_SECRET` 注入支付后端。生产密钥不得进入代码仓库、镜像、前端或普通日志；沙盒和生产使用不同的订阅和密钥。
4. 确认公网 HTTPS 回调可达，边界网络策略按 Airwallex 当前官方文档维护 Webhook 来源 IP 允许列表（若防火墙要求），并验证原始请求验签、事件 ID 去重、快速返回 HTTP 200、乱序/重复事件处理、状态查询核实及失败告警。
5. 在真实交易开放前，确认生产 Payment Intent 创建、异步付款结果、星辙通知和故障负责人均完成双方联调。浏览器回跳不作为付款成功依据。

API 调用密钥与 Webhook 签名密钥用途不同：API Key/Client ID 用于后端调用 Airwallex API；Webhook secret 用于验证 Airwallex 发来的通知。支付服务的接收路径由应用固定，域名和 HTTPS 由生产部署提供。

具体云厂商购买、域名 DNS、证书签发和网络规则配置步骤，待星辙确定部署平台后再补充。

## 托管入口和自动恢复的部署补充

配置 `PAYMENT_CHECKOUT_PUBLIC_BASE_URL` 为支付项目公开前端的 HTTPS 根地址，配置 `STARTRAIL_BUSINESS_ORDER_URL_TEMPLATE` 为星辙真实业务订单页，包含唯一 `{businessOrderId}` 占位符。配置只在服务端管理，不能接收用户任意返回网址；生产禁止本地模拟地址。Webhook 地址仍是公开后端域名加 `/api/v1/webhooks/airwallex`，它与浏览器付款返回地址不同。

Flyway 公共迁移现已到 V123；保留历史迁移和数据，不为整理目录删除已应用迁移。新增迁移从下一个未使用编号顺延。服务运行后自动发现有服务商编号的未决订单；默认补查间隔 10 秒、订单创建 10 秒后纳入，每批最多 10 笔，失败最多退避 15 分钟。监控应覆盖补查失败、通知积压、Webhook 失败、待核实订单和数据库容量，生产部署前仍需安排告警、备份恢复及真实付款退款验收。该节奏仅用于支付状态恢复，不代表结算或财务对账每 10 秒执行。

本地只有订单模拟器；生产不装配模拟接口，需星辙真实接口及订单页就位。上线配置切换之外，还要确认正式账户支付方式、CNY/USD可用能力、Webhook可达和密钥匹配；不能将“改密钥后可构建”当作上线验收。

## 本地观测栈开发验证

`compose.observability.yaml` 是独立的受控开发验证配置，包含 Alloy、Prometheus、Loki、Tempo、Grafana；它不是生产高可用部署。运行前复制示例并为 Grafana 设置独立随机本地密码，不复用支付数据库、Airwallex 或星辙凭证：

```bash
cp .env.observability.example .env.observability
openssl rand -base64 32
```

将生成值仅写入本机未跟踪的 `.env.observability` 中 `OBS_GRAFANA_ADMIN_PASSWORD`，不要写入示例、仓库或聊天。随后在项目根目录独立启动或停止观测栈：

```bash
docker compose --env-file .env.observability -f compose.observability.yaml up -d
docker compose --env-file .env.observability -f compose.observability.yaml down
```

停止命令保留命名卷，不清除监控历史。不要使用带卷删除参数的停止命令；支付 PostgreSQL 的 `startrail-payment-data` 卷更不属于观测栈，绝不可当作清理监控的步骤。确需清理观测数据时，先由负责人确认数据保留和备份，再按卷名逐个审查；本指南不提供自动删除步骤。

Compose 宿主端口全部只绑定 `127.0.0.1`：Grafana 默认 `3300`，Prometheus 默认 `9091`，Alloy 的 OTLP/HTTP 接收端默认 `4318`，分别由 `OBS_GRAFANA_HOST_PORT`、`OBS_PROMETHEUS_HOST_PORT`、`OBS_OTLP_HTTP_HOST_PORT` 覆盖。Grafana 登录用户默认 `local-operator`，密码必须由 `.env.observability` 提供；Grafana 关闭匿名访问和自助注册。Loki、Tempo 的接收端及 Alloy 管理界面没有宿主端口映射；Alloy 的 OTLP/HTTP 接收端则映射到 `127.0.0.1:${OBS_OTLP_HTTP_HOST_PORT:-4318}`。Prometheus、Loki 和 Tempo 未设置独立公网认证层。

五个独立命名卷分别为 `startrail-observability-alloy-data`（Alloy 本地数据）、`startrail-observability-prometheus-data`（时序指标）、`startrail-observability-loki-data`（日志存储）、`startrail-observability-tempo-data`（追踪存储）、`startrail-observability-grafana-data`（Grafana 配置与状态）。所有容器只加入 `startrail-observability-internal` 专用内部网络；这套 Compose 未把支付后端加入该网络。示例保留期为 Prometheus 15 天、Loki 168 小时、Tempo 168 小时；这些不是已批准的生产留存政策，也没有备份或恢复保证。

本地 IDEA 后端可按 `.env.observability.example` 中的 `MANAGEMENT_*` 示例在项目根 `.env` 启用 OTLP 追踪，指向 `http://localhost:4318/v1/traces`。如果修改宿主 OTLP 端口，必须同步改后端的 endpoint；Compose 与 IDEA 不自动同步。追踪默认关闭且采样率为零；本地示例的全量采样不得直接用于生产。

应用配置明确关闭 OTLP 指标主动导出（`management.otlp.metrics.export.enabled=false`）；本地指标采用 Prometheus 从受控管理端点拉取，OTLP 端口仅用于追踪。当前 HTTP span 去除原始地址和查询参数，异常事件只带固定安全消息及受限类别；部署后仍需抽查实际导出数据，确认没有上游组件新增敏感属性。

Prometheus 的 `payment-service:8081` 是容器部署占位抓取目标。本地 IDEA 管理端口默认只绑定 `127.0.0.1:8081`，观测容器无法按该目标抓取；若日后容器化接入，应由受控内部网络、绑定地址和访问策略共同限定，不能公开管理端口。应用安全 JSON 日志目前只输出到 IDE 控制台，Alloy 没有日志采集管道，因此 Loki 不含应用日志；待批准安全日志文件通道后再接入。Grafana 追踪面板可按追踪编号查询 Tempo，但运营后台当前没有服务端提供的 Grafana 跳转地址合同，仅显示编号文本。

当前 Prometheus 告警规则只评估技术指标，不配置通知目的地。`stale_pending` 仅覆盖 `CREATING`、`PROCESSING`、`REQUIRES_REVIEW`，排除正常等待付款的 `AWAITING_PAYMENT`；它按支付单 `updated_at` 最近变化统计，不能解释为 Airwallex 付款超时或失败。阈值仅供开发观察，生产需按真实流量确定。

生产监控数据、监控账号和访问范围、保留期限、备份恢复、容量、告警接收人及渠道均由星辙持有并确认；接入第三方告警目标须另行批准。监控不可用时先核对支付数据库、通知 outbox、退款和对账的原始业务记录，再恢复采集与告警；不能通过监控事件直接改写支付结果。上线前仍需验证受控网络、采集、权限、保留与恢复，本地 Compose 静态配置不代表这些事项已验收。
