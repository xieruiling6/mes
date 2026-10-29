# 工擎 MES 认证与基础权限设计

## 认证方案

- 使用 Spring Security 服务端 Session，浏览器只持有 HttpOnly `JSESSIONID` Cookie；不向 JavaScript 暴露会话 ID。
- 使用 `CookieCsrfTokenRepository` 提供 `XSRF-TOKEN`，前端 Axios 在写请求中回传 `X-XSRF-TOKEN`。生产环境通过 HTTPS 并启用 Secure Cookie。
- 用户名及 BCrypt 密码摘要从 `sys_user` 加载。密码使用 BCrypt cost 12；接口、日志和用户列表均不返回密码摘要。
- 登录成功轮换 Session ID，退出使 Session 失效。禁用用户不能通过认证；登录错误统一为 401 和相同提示，不暴露账号状态。
- CSRF 初始化及登录端点公开；其余 `/api/v1/**` 默认需要登录。HTTP 401 表示无会话，403 表示权限不足。

## RBAC

认证时读取有效角色和权限关系，仅把状态为 `ACTIVE` 的角色、权限转换为 Spring Security Authorities。业务端点显式使用 `@PreAuthorize("hasAuthority('...')")` 做服务端校验；前端按当前用户权限控制显示只作为交互辅助。

系统管理端点使用 `system:user:manage`、`system:role:manage`、`system:permission:read`。用户角色及角色权限替换在事务中删除旧关系并写入经服务端核验的新关系。角色/权限停用后不再授予新的登录权限。为避免系统失去可管理入口，服务端禁止停用最后一名启用状态的管理员、禁止移除其 `ADMIN` 角色，也禁止停用仍有启用用户的 `ADMIN` 角色；`ADMIN` 角色必须保留 `system:user:manage` 和 `system:role:manage` 权限。

用户可自行修改显示名称和密码。修改密码必须校验当前密码，新密码至少 12 位且不能与当前密码相同；密码只保存 BCrypt 摘要，操作写入 `sys_operation_log`。

## API

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/api/v1/auth/csrf` | 公开，建立 CSRF Cookie |
| POST | `/api/v1/auth/login` | 公开，需有效 CSRF Token |
| GET | `/api/v1/auth/me` | 已登录 |
| PUT | `/api/v1/auth/profile` | 已登录，需 CSRF Token；修改本人显示名称 |
| POST | `/api/v1/auth/change-password` | 已登录，需 CSRF Token；校验当前密码后修改本人密码 |
| POST | `/api/v1/auth/logout` | 已登录，需 CSRF Token |
| GET/POST | `/api/v1/system/users` | `system:user:manage` |
| PUT | `/api/v1/system/users/{id}` | `system:user:manage` |
| GET/POST | `/api/v1/system/roles` | `system:role:manage` |
| PUT | `/api/v1/system/roles/{id}` | `system:role:manage` |
| GET | `/api/v1/system/permissions` | `system:permission:read` 或 `system:role:manage` |

角色关联和角色权限关联可通过对应的 `/roles`、`/permissions` 子资源整体替换。用户创建必须设置至少 12 位密码；编辑密码可留空以维持原值。用户列表通过 `/users/{id}/roles` 读取角色关联，角色编辑通过 `/roles/{id}/permissions` 读取权限关联。

## 首个管理员

SQL 初始化不包含通用密码。首次空库运行时可注入 `BOOTSTRAP_ADMIN_USERNAME` 和 `BOOTSTRAP_ADMIN_PASSWORD` 创建管理员。初始化器确认用户数为零后才插入用户、BCrypt 摘要及 `ADMIN` 角色关联；出现任意既有用户时跳过。凭据应由部署环境注入，不写入仓库、命令参数或日志。

## 验证边界

MockMvc 验证认证拦截、403 权限拒绝和 CSRF；服务测试验证禁用用户状态、权限加载、最后管理员保护、个人资料与密码修改；BCrypt 测试验证哈希不可逆使用方式。`MesWorkflowDatabaseSmokeTest` 在 `-Dmes.integration=true` 时使用真实 MySQL 验证完整业务闭环并在事务中回滚。
