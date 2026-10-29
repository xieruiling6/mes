# 工擎 MES 验收测试报告

| 项目 | 内容 |
| --- | --- |
| 测试日期 | 2026-10-28 |
| 测试范围 | 需求文档第 12 章验收标准、核心业务闭环、系统管理、统计筛选、异常与并发 |
| 测试环境 | Java 17.0.20.1、Spring Boot 3.5.6、Node.js 26.9.0、Element Plus 2.11、MySQL 8 兼容实例、Chrome Headless |
| 测试数据库 | 由 `docs/sql/init.sql` 全新建库；测试结束后删除，未污染业务库 |
| 测试工具 | Maven/JUnit 5、Spring Boot 数据库集成测试、Python `requests` 接口验收脚本、Playwright 浏览器验收 |

## 1. 结论

本轮验收共执行：

- 接口验收用例 **46 项，全部通过**。
- 后端自动化测试 **73 项，0 失败、0 错误**；默认运行跳过 3 项数据库集成测试，使用 `-Dmes.integration=true` 单独运行时 **3 项全部通过**。
- 前端生产构建通过。
- 浏览器 UI 验收覆盖 11 个页面、系统管理、报工、质检、设备维修、操作员权限和移动端，无页面异常。
- 干净环境建库、bootstrap 管理员、后端启动、前端构建和演示闭环可复现。

**验收结论：通过。**

## 2. 验收标准逐项结果

| 验收标准 | 结果 | 主要验证内容 |
| --- | --- | --- |
| 1. 登录与权限 | 通过 | 未登录 401、缺 CSRF 403、错误密码统一 401；操作员访问产品/订单/统计/系统管理返回 403；退出后会话失效 |
| 2. 产品管理 | 通过 | 创建、编辑、启用/停用、编码唯一；停用产品不能创建订单；删除接口返回 405 |
| 3. 生产订单 | 通过 | 创建、编辑、确认、取消；数量/交期/产品校验；订单号唯一；乐观锁；取消级联取消草稿计划；取消后不能重新排产 |
| 4. 计划与工单唯一性 | 通过 | 计划数量必须等于订单量；日期合法；一个订单一份计划；一份计划一张工单；重复创建/非法状态被拒绝 |
| 5. 报工数量 | 通过 | 只允许生产中工单；合格数+报废数=生产数；负数/零值/超报拒绝；并发报工只成功一笔 |
| 6. 数量与状态汇总 | 通过 | 报工后工单/计划/订单合格、报废、完成数量一致；完成后三张单据状态同步；作废后不计入进度 |
| 7. 报工作废 | 通过 | 操作员无权限；管理员作废必须填原因；作废记录保留；重复作废和终态作废拒绝 |
| 8. 终态与删除限制 | 通过 | 已完成/已取消工单不能报工；工单、产品不提供物理删除接口；计划取消后工单进入终态 |
| 9. 看板与统计筛选 | 通过 | 统计页支持日期、产品、订单状态筛选；首页支持产品筛选；日报与报工明细产量合计一致；作废数据不参与统计 |
| 10. 干净环境部署 | 通过 | `init.sql` 全新建库；bootstrap 管理员；后端启动；前端构建；完整业务闭环演示 |

## 3. 完整业务链路验证

在全新数据库中实际执行并通过：

```text
产品
  -> 生产订单（草稿/确认/取消）
  -> 生产计划（草稿/编辑/发布/取消）
  -> 生产工单（创建/下发/开工/暂停/恢复/完成）
  -> 生产报工（提交/幂等/超报拦截/作废/重新报工）
  -> 质检（创建/检验数量限制/不合格处置）
  -> 设备（档案/状态/维修开始/维修完成）
  -> 订单/计划/工单完成状态同步
  -> 生产统计（日期/产品/状态筛选、明细核对）
```

另外验证了并发报工和并发建计划：两个并发请求均只有一个成功，另一个返回 409，未出现超报或重复计划。

## 4. 本轮发现并修复的缺陷

| 编号 | 严重级别 | 问题 | 修复 |
| --- | --- | --- | --- |
| BUG-01 | 中 | 未知接口返回 500，而不是 404 | `GlobalExceptionHandler` 增加 `NoResourceFoundException` 处理 |
| BUG-02 | 中 | 对不支持的 HTTP 方法（如 DELETE 产品）返回 500 | 增加 `HttpRequestMethodNotSupportedException` 处理，返回 405 |
| BUG-03 | 中 | 统计接口只支持日期，无法按产品或订单状态筛选，不满足验收标准第 9 条 | 统计 SQL、Service、Controller 和前端统计页/首页全部增加 `productId`、`status` 筛选 |
| BUG-04 | 低 | Element Plus 未配置中文 locale，确认框显示 OK/Cancel，分页显示 10/page | 在 `main.js` 配置 `zh-cn` locale |
| BUG-05 | 低 | 设备编号重复返回 `BUSINESS_CONFLICT`，与其他基础资料错误码不一致 | 统一为 `DATA_CONFLICT` |
| BUG-06 | 测试缺陷 | 统计数据库 smoke test 依赖库内无同日数据，在已有业务库上失败 | 使用隔离历史日期并修正测试数据创建时间 |

## 5. 其他验证结果

### 5.1 审计日志

验收库共产生 **55 条** `sys_operation_log`，覆盖：

`ORDER_CREATE`、`ORDER_UPDATE`、`ORDER_CONFIRMED`、`ORDER_CANCELLED`、`PLAN_CREATE`、`PLAN_UPDATE`、`PLAN_RELEASE`、`PLAN_CANCEL`、`WORK_ORDER_CREATE`、`WORK_ORDER_DISPATCH`、`WORK_ORDER_START`、`WORK_ORDER_PAUSE`、`WORK_ORDER_RESUME`、`WORK_ORDER_COMPLETE`、`REPORT_CREATE`、`REPORT_VOID`、`QUALITY_INSPECTION_CREATE`、`QUALITY_DISPOSITION`、`EQUIPMENT_CREATE`、`EQUIPMENT_UPDATE`、`EQUIPMENT_MAINTENANCE_START`、`EQUIPMENT_MAINTENANCE_COMPLETE`、`ACCOUNT_PROFILE_UPDATE`、`ACCOUNT_PASSWORD_CHANGE`。

### 5.2 密码存储

验收库中全部用户密码摘要以 `$2a$` BCrypt 前缀存储，接口和用户列表未返回密码摘要。

### 5.3 异常与边界

- 分页页码 0、每页超过 100 条返回 400。
- 非法路径 ID 返回 400，不存在资源返回 404。
- 非法状态、过期版本、重复编码、重复决策返回 409。
- 日期范围倒置、超过 366 天返回 400。
- SQL 注入关键字按普通文本查询，不扩大结果集。
- 移动端页面无横向溢出，导航可横向滚动。

## 6. 复现命令

```bash
# 后端全部自动化测试
cd backend && mvn test

# 数据库集成测试（先配置 DB_URL/DB_USERNAME/DB_PASSWORD）
cd backend
mvn -Dmes.integration=true -Dtest='*DatabaseSmokeTest' test

# 前端生产构建
cd frontend && npm run build

# 干净数据库初始化
mysql -h 127.0.0.1 -u <user> -p < docs/sql/init.sql
```

接口验收和浏览器验收使用独立脚本/浏览器自动化执行，测试数据库在验收完成后删除。

## 7. 遗留说明

- 数据库仍然采用版本化 SQL 手工迁移，未引入 Flyway；部署时需按 README 执行初始化或增量脚本。
- 生产部署编排、HTTPS 证书和外部监控不在本次功能验收范围内。
- 统计报工明细按设计最多返回 500 条；超出部分需要后续分页或导出能力。
