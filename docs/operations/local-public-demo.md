# 本地开发版公网演示

## 1. 用途与限制

本文说明如何用 Cloudflare Quick Tunnel 临时分享本机运行的开发版，供甲方查看页面和体验本地模拟流程。隧道不部署应用：数据库、支付后台、模拟星辙和后端仍运行在 WSL 本机。

Quick Tunnel 是临时测试通道，生成的域名随机，停止后失效，不提供可用性保证。访问者持有链接即可访问对应页面；只分享给预期体验者。开发模拟器可创建虚构订单，应使用 Airwallex 沙盒凭据，不要放入真实交易、生产数据或生产密钥。Quick Tunnel 的用途与限制见 [Cloudflare 官方说明](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/)。

本项目的支付后台和模拟星辙是两个独立的前端服务，因此需要分别建立隧道。支付后台和模拟器的前端代理会把 API 请求转发给本机后端，不需要把整个后端直接暴露到公网。若还要接收 Airwallex 沙盒 Webhook，按本文件第 6 节额外开放 Webhook 路径。

## 2. 前置条件

- 在 WSL Ubuntu 中运行项目，Docker PostgreSQL、后端、支付后台和模拟星辙均能在本机访问。
- 已安装 `cloudflared`。可用 `cloudflared --version` 检查。
- 本地开发使用 `local` 配置和 Airwallex 沙盒，不使用 `production` 配置。

本地地址：

| 服务 | 本地地址 |
| --- | --- |
| 支付后台 | `http://localhost:5173` |
| 模拟星辙 | `http://localhost:5175/mock-xingzhe` |
| 后端健康检查 | `http://localhost:8080/actuator/health` |
| PostgreSQL | `localhost:5433` |

## 3. 启动本地服务

每条命令在独立的 WSL 终端中运行，保持终端打开。

启动本地数据库：

```bash
pnpm db:up
```

启动支付后台前端：

```bash
__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS=.trycloudflare.com pnpm dev:web
```

启动模拟星辙前端：

```bash
__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS=.trycloudflare.com pnpm dev:mock-xingzhe
```

Vite 默认会拒绝未知 Host。这个环境变量只把 `trycloudflare.com` 域名后缀加入允许列表；不要改成允许任意 Host。

先启动前端，再建立隧道，以便获取两个公网域名。后端可在取得域名后按第 5 节启动。

## 4. 建立两个公网入口

在另外两个 WSL 终端中分别运行：

```bash
cloudflared tunnel --url http://localhost:5173
```

```bash
cloudflared tunnel --url http://localhost:5175
```

每条命令会显示一个随机的 `https://……trycloudflare.com` 地址。分别记为：

- `<后台公网地址>`：指向本机 5173 端口。
- `<模拟器公网地址>`：指向本机 5175 端口。

例如，后台链接打开支付后台首页；模拟器链接的体验入口是 `<模拟器公网地址>/mock-xingzhe`。不要互换两条地址。

## 5. 配置支付返回地址并启动后端

支付完成后，浏览器需要回到公网可访问的页面。本机默认配置使用 `localhost`，不能供甲方的浏览器使用。因此每次 Quick Tunnel 生成新域名后，都要用这两个域名启动后端。

在后端终端切换到 Java 21，然后执行以下命令。把占位地址替换成第 4 节得到的实际地址，保留 URL 中的 `{businessOrderId}`：

```bash
sdk use java 21.0.12+1.1-tem
PAYMENT_CHECKOUT_PUBLIC_BASE_URL='https://<后台公网地址>' \
STARTRAIL_BUSINESS_ORDER_URL_TEMPLATE='https://<模拟器公网地址>/mock-xingzhe/order/{businessOrderId}' \
pnpm dev:backend
```

如果后端已在运行，先在它的终端按 `Ctrl+C` 停止，再用上述命令启动。命令只覆盖当前后端进程的两个公开回跳地址，不需要把临时域名写入 `.env`。后端仍使用本地 `local` 配置和本机数据库。

首次启动的运营账号来自根目录 `.env` 中的 `OPERATOR_OWNER_USERNAME`/`OPERATOR_OWNER_PASSWORD`（只读）和 `OPERATOR_STAFF_USERNAME`/`OPERATOR_STAFF_PASSWORD`。请通过安全方式把演示账号交给甲方，不要把密码写进此文档或提交到仓库。

## 6. 可选：接收 Airwallex 沙盒 Webhook

只在需要验证 Airwallex 沙盒异步通知时执行。再开一个终端运行：

```bash
cloudflared tunnel --url http://localhost:8080
```

将终端输出的地址与以下路径拼接，在 Airwallex **Sandbox** Webhook 订阅中填写：

```text
https://<后端隧道地址>/api/v1/webhooks/airwallex
```

按项目现有说明，把该订阅的签名密钥写入本机 `.env` 的 `AIRWALLEX_WEBHOOK_SECRET`，然后重启本地后端。不要把签名密钥放进前端、文档或聊天。后端隧道会把本机后端更多接口暴露到公网；只在联调期间运行，联调结束后停止。

## 7. 检查访问

从本机或甲方网络打开：

- `https://<后台公网地址>/`
- `https://<后台公网地址>/operations`
- `https://<模拟器公网地址>/mock-xingzhe`

也可从 WSL 检查隧道是否能到达页面和模拟器 API：

```bash
curl -I 'https://<后台公网地址>/'
curl -I 'https://<模拟器公网地址>/mock-xingzhe'
curl -i 'https://<模拟器公网地址>/mock-xingzhe/api/v1/products'
curl -f 'http://localhost:8080/actuator/health'
```

页面和模拟器接口应返回成功状态；健康检查应返回 `200`。只有页面可打开不代表支付已完成端到端验收；沙盒 Webhook、付款核实和结果通知需要按对应流程另行验证。

## 8. 访问慢时的判断

- Quick Tunnel 的公网流量仍会经过 Cloudflare。关闭本机代理或 VPN 可能改变本机到 Cloudflare 的线路，但不会绕过 Cloudflare，也不能保证更快。
- 本次隧道日志显示 Cloudflare 接入位置为 LAX（洛杉矶）。甲方在中国大陆访问时，跨境线路可能增加延迟；不同网络运营商和代理出口表现也可能不同。
- Vite 开发服务器会动态提供开发模块，适合本机热更新，不是高效的公网演示服务。本地 CPU、上行带宽和隧道线路都会影响速度。
- 如果页面只是偶尔慢，可分别对比关闭/开启本机代理后的访问结果。如果持续慢或需要稳定体验，建议把构建后的演示版部署到靠近甲方的云环境；换成固定 Cloudflare Tunnel 域名本身不等于换了应用所在地区。

## 9. 关闭公网演示

在每个运行 `cloudflared tunnel` 的终端按 `Ctrl+C`，停止后台、模拟器以及可选的 Webhook 隧道。隧道关闭后，对应公网域名不再转发到本机。

若后端仍以临时公网地址启动，停止后端并在本地重新执行：

```bash
pnpm dev:backend
```

这会恢复 `local` 配置中的本机返回地址。是否停止数据库、前端和模拟器由后续本地开发需要决定；关闭隧道本身不会要求删除数据库或清理本地数据。

## 10. 常见问题

### 页面显示 Vite 的 Host 拒绝提示

确认支付后台和模拟星辙是通过带有 `__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS=.trycloudflare.com` 的命令启动。改完环境变量后需要重启对应前端，再检查隧道地址。

### 付款后跳到甲方电脑的 localhost

后端没有使用第 5 节中的两个公网回跳地址，或创建的支付单早于本次配置。确认后端启动命令、终端进程和两条域名，再用新模拟订单重新体验。

### 换网络后链接无法访问

Quick Tunnel 没有可用性保证，也可能受访问者网络策略影响。先确认本机前端和后端仍在运行，再让甲方更换网络测试；需要长期稳定访问时使用独立演示环境。
