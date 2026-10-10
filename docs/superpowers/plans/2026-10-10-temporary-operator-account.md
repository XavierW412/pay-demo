# 甲方临时测试账号实施计划

> **供执行者使用：** 按任务顺序实现本计划。账号登录、会话和角色授权均属于安全边界，不能只实现前端显示。

**目标：** 让员工管理员在本地 Airwallex 沙盒中生成 6 小时有效的临时运营账号，甲方可使用生成的一次性用户名和密码测试运营操作。

**架构：** 复用 `operator_account`、BCrypt、Spring Security 会话和操作审计；新增 `TEMP_TESTER` 角色与 `expires_at`，在登录和每个已认证运营请求中校验到期。临时账号可执行退款管理员的运营动作，但员工维护和账号生成仍仅限 `STAFF_ADMIN`；生成接口同时校验 `local` 配置和 Airwallex 沙盒地址。

**技术栈：** Java 21、Spring Boot、Spring Security、JdbcTemplate、Flyway、React、TypeScript、TanStack Query、Tailwind CSS、shadcn/ui。

**设计文档：** [2026-10-10-temporary-operator-account-design.md](../specs/2026-10-10-temporary-operator-account-design.md)

## 全局约束

- 临时账号有效期固定为 6 小时，从生成时间开始计算。
- 临时账号只在 `local` 配置且 Airwallex `environment=sandbox`、`baseUrl=https://api.sandbox.airwallex.com/api/v1` 时可生成。
- 初始密码至少 128 位随机熵，只返回一次，只存 BCrypt 摘要，不写日志或审计详情。
- `TEMP_TESTER` 可执行退款确认等运营动作，但不能创建邀请、生成临时账号、修改员工状态。
- 过期和提前停用均在服务端使当前运营会话失效；永久账号不受影响。
- 不增加依赖，不加入测试付款或跳过星辙退款批准的入口。
- WSL Ubuntu 开发；Node 操作用 `pnpm`。未经用户明确要求不提交、不推送、不启动服务、不改机器配置。
- 按当前项目指令，不新增或运行测试；使用后端编译、前端构建、差异检查和代码路径审阅作为本次静态验证。

## 文件结构与责任

- 修改 `backend/src/main/resources/db/migration/V120__add_temporary_operator_accounts.sql`：添加可空过期时间并允许 `TEMP_TESTER` 角色。
- 修改 `backend/src/main/java/com/startrail/payment/operations/security/OperatorUserDetailsService.java`：登录时加载过期时间并拒绝过期账号。
- 修改 `backend/src/main/java/com/startrail/payment/operations/security/OperatorAccountStatusFilter.java`：每个已认证运营请求检查启用状态和过期时间，过期后清理会话。
- 修改 `backend/src/main/java/com/startrail/payment/operations/security/OperationsSecurityConfiguration.java`：将退款确认、退款核实、交易操作授权给 `TEMP_TESTER`；邀请、停用和临时账号生成仍限管理员。
- 新增 `backend/src/main/java/com/startrail/payment/operations/application/TemporaryOperatorAccountService.java`：生成随机凭证、写入账号及审计、校验本地沙盒配置。
- 修改 `backend/src/main/java/com/startrail/payment/operations/interfaces/OperationsTeamController.java`：新增生成临时账号的受保护接口。
- 修改 `backend/src/main/java/com/startrail/payment/operations/application/OperationsTeamService.java`：返回过期时间和临时账号状态；管理员可提前停用 `TEMP_TESTER`。
- 修改 `frontend/src/modules/operations/api.ts`：扩展角色和账号数据类型，添加临时账号生成 API。
- 修改 `frontend/src/modules/operations/format.ts`：添加“临时测试员”角色名称与临时账号状态文案。
- 修改 `frontend/src/modules/operations/OperationsTeamPage.tsx`：生成凭证一次性展示、复制、到期提示和临时账号提前停用。
- 修改 `docs/architecture/operations/operations-console.md`：记录接口、角色、过期和一次性凭证安全规则。
- 修改 `README.md`：增加本地生成流程和沙盒退款提示的入口说明。

## 代码评审重点

1. **过期边界：** `expires_at <= now()` 时，拒绝密码登录并拒绝已有会话的每个运营请求；数据库时间统一使用 `TIMESTAMPTZ`。
2. **授权绕过：** 直接调用退款确认、员工邀请、账号停用和临时账号生成接口时，权限必须由服务端校验。
3. **凭证泄漏：** 初始密码不能写入数据库、日志、审计正文、错误消息或前端持久存储；凭证不得通过列表或后续查询再次获取。
4. **环境误配：** `local` profile 但 Airwallex URL 指向生产、或其他 profile 指向沙盒时，生成操作都应拒绝。
5. **永久账号兼容：** `expires_at IS NULL` 的既有员工、管理员与所有者登录和权限维持现状。

---

### 任务 1：数据库与登录过期校验

**文件：**
- 新增：`backend/src/main/resources/db/migration/V120__add_temporary_operator_accounts.sql`
- 修改：`backend/src/main/java/com/startrail/payment/operations/security/OperatorUserDetailsService.java`
- 修改：`backend/src/main/java/com/startrail/payment/operations/security/OperatorAccountStatusFilter.java`

**接口：**
- 数据库新增 `operator_account.expires_at TIMESTAMPTZ NULL`；永久账号保持 `NULL`。
- 角色约束新增 `TEMP_TESTER`。
- 登录服务继续返回现有 `UserDetails`，但账号启用条件变为 `active = true AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)`。
- 状态过滤器查询当前账号的 `active`、`expires_at`；缺失、停用或到期时清理会话并返回现有统一 `401` 格式。

- [x] 新建 Flyway V120 迁移：增加 `expires_at`，删除并重建角色检查约束以加入 `TEMP_TESTER`；保留已有行和密码摘要。
- [x] 修改登录查询，让数据库通过 `expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP` 计算账号是否未过期，并据此设置 `UserDetails` 的启用状态；永久账号始终使用现有登录行为。
- [x] 修改每请求状态查询，同时返回 `active` 和 `expires_at > CURRENT_TIMESTAMP`；到期时清除 `SecurityContext`、使现有会话失效并返回 `OPERATOR_ACCOUNT_EXPIRED`，停用账号继续返回现有停用错误。
- [x] 审阅数据迁移兼容性，确认现有所有者、管理员、员工记录的 `expires_at` 均为 `NULL`，且登录路径不读取明文密码以外的新字段。
- [x] 运行后端编译：`source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java 21.0.12+1.1-tem >/dev/null && ./backend/mvnw -q -f backend/pom.xml -DskipTests compile`。

### 任务 2：创建临时账号和服务端权限

**文件：**
- 新增：`backend/src/main/java/com/startrail/payment/operations/application/TemporaryOperatorAccountService.java`
- 修改：`backend/src/main/java/com/startrail/payment/operations/interfaces/OperationsTeamController.java`
- 修改：`backend/src/main/java/com/startrail/payment/operations/security/OperationsSecurityConfiguration.java`

**接口：**
- `POST /api/v1/operations/team/temporary-accounts`，仅 `ROLE_STAFF_ADMIN`。
- 成功响应字段：`accountId`、`username`、`initialPassword`、`role`、`expiresAt`。
- 请求体为空；有效期、权限和密码均由服务端固定生成。
- 使用 `SecureRandom` 产生用户名随机部分和 128 位以上密码熵；使用现有 `PasswordEncoder` 保存 BCrypt 摘要。
- 仅当前 Spring `local` profile 且 Airwallex `environment` 为 `sandbox`、`baseUrl` 与本地沙盒配置完全一致时创建。

- [x] 新建服务并注入 `JdbcTemplate`、`PasswordEncoder`、`OperatorAuditService`、`Environment` 与 `AirwallexProperties`。
- [x] 生成符合现有用户名校验规则的唯一小写用户名；密码使用安全随机字节后编码为可复制字符，数据库只写密码摘要。
- [x] 在数据库事务内插入 `TEMP_TESTER`、`active=true`、`created_at=now()`、`expires_at=now()+6 hours` 的账号行，并记录 `CREATE_TEMP_OPERATOR` 审计；审计只包含账号 ID、用户名、角色和到期时间。
- [x] 对用户名唯一冲突做有限次数重新生成；所有失败响应和日志都不得包含密码。
- [x] 新增控制器端点并返回凭证一次性响应；后续查询员工账号列表不返回 `initialPassword`。
- [x] 在 Spring Security 中显式允许 `TEMP_TESTER` 调用退款确认、退款即时核实和交易操作接口；邀请、邀请撤销、账号状态修改及临时账号生成仍只允许 `STAFF_ADMIN`。
- [x] 对 profile 或 Airwallex 环境校验失败返回 `403` 和不含凭证的业务错误，不创建账号、不留下成功审计。
- [x] 运行后端编译命令：`source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java 21.0.12+1.1-tem >/dev/null && ./backend/mvnw -q -f backend/pom.xml -DskipTests compile`。

### 任务 3：账号列表和提前停用

**文件：**
- 修改：`backend/src/main/java/com/startrail/payment/operations/application/OperationsTeamService.java`
- 修改：`backend/src/main/java/com/startrail/payment/operations/interfaces/OperationsTeamController.java`

**接口：**
- `AccountRow` 增加 `Instant expiresAt`；永久账号返回 `null`。
- `PATCH /api/v1/operations/team/accounts/{accountId}/active` 仍由管理员调用，目标允许 `STAFF_OPERATOR` 或 `TEMP_TESTER`，拒绝管理员和所有者。

- [x] 更新账号列表查询和响应映射，读取 `expires_at` 并按 `active`、`expiresAt` 显示停用、过期或有效状态。
- [x] 修改账号停用目标校验：管理员可以停用普通员工和临时测试员；不能通过此接口停用管理员/所有者或修改当前操作者。
- [x] 为提前停用临时测试账号记录专属动作名称 `DISABLE_TEMP_OPERATOR`，并保证不向审计详情写入临时密码。
- [x] 保持账号列表读取权限与当前运营后台行为一致；临时账号不能对员工列表执行写操作。
- [x] 运行后端编译命令：`source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java 21.0.12+1.1-tem >/dev/null && ./backend/mvnw -q -f backend/pom.xml -DskipTests compile`。

### 任务 4：后台生成凭证与到期展示

**文件：**
- 修改：`frontend/src/modules/operations/api.ts`
- 修改：`frontend/src/modules/operations/format.ts`
- 修改：`frontend/src/modules/operations/OperationsTeamPage.tsx`

**接口：**
- `OperatorRole` 增加 `TEMP_TESTER`。
- `OperatorAccount` 增加 `expiresAt: string | null`。
- 新增 `createTemporaryOperatorAccount(): Promise<TemporaryOperatorCredentials>`，一次性解析创建响应。

- [x] 更新前端角色和账号类型，补充“临时测试员”角色名称及有效/已过期/已停用显示。
- [x] 在“员工与权限”页新增“生成临时测试账号”按钮；仅 `STAFF_ADMIN` 可见，正在请求时禁用按钮。
- [x] 成功后在当前组件状态中展示用户名、初始密码、到期时间和沙盒退款提示；不写 `localStorage`、`sessionStorage` 或 URL。
- [x] 提供复制完整凭证的交互；关闭凭证展示或离开页面时清空前端持有的明文密码。
- [x] 在账号列表显示临时账号标签、有效期/到期状态及管理员提前停用操作；临时测试员页面不显示员工管理入口。
- [x] 对本地配置拒绝、服务端错误和网络错误显示可理解提示；不要把响应正文或密码打印到浏览器控制台。
- [x] 运行前端构建：`pnpm --filter frontend build`。

### 任务 5：文档同步与完成核查

**文件：**
- 修改：`docs/architecture/operations/operations-console.md`
- 修改：`README.md`

- [x] 在运营后台架构文档记录新角色、生成 API、到期校验、一次展示密码、沙盒限制和退款执行风险。
- [x] 在 README 本地开发说明中链接临时账号使用规则，明确生成前需运行本地沙盒配置，不要把凭证提交到 Git 或写入截图/日志。
- [x] 对照设计文档逐项审阅：6 小时固定期限、过期会话失效、员工管理排除、退款仍遵守星辙批准与支付核对、生产禁止生成、永久账户兼容。
- [x] 检查变更格式：`git diff --check`。
- [x] 确认构建已完成；不启动服务、不运行测试、不提交或推送。将启动与沙盒操作留给用户自行执行。

## 执行顺序与依赖

任务 1 先完成账号到期基础；任务 2 依赖迁移并新增生成与授权；任务 3 扩展账号目录和提前停用；任务 4 依赖接口与响应契约；任务 5 最后同步文档和核查。

## 本计划之外

- 不生成真实生产账号、不连接生产 Airwallex。
- 不新增自动交付凭证的邮件、短信或聊天功能。
- 不修改现有永久员工和所有者账号口令、角色或数据。
- 不新增模拟付款或绕过退款核验的功能。
