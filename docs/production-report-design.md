# 生产报工模块设计

## 1. 接口

所有接口使用 `/api/v1/reports` 前缀、统一响应 `{ code, message, data, traceId }` 和会话认证。

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/available-work-orders` | `report:create` | 查询生产中且尚有可报数量的工单 |
| GET | `/filter-options` | 报工读取/创建权限 | 获取当前可见范围内的产品和报工人筛选项 |
| POST | `/` | `report:create` | 提交报工 |
| GET | `/mine` | `report:read:own` 或 `report:create` | 查询本人报工；用户 ID 由服务端取得 |
| GET | `/` | `report:read` | 按日期、产品、工单、报工人和状态分页查询 |
| GET | `/work-orders/{id}/summary` | 报工读取/创建权限 | 查看工单计划数、累计生产数、合格数和不合格数 |
| PATCH | `/{id}/void` | `report:void` | 填写原因作废报工，保留原数量与提交者 |

提交字段：`requestKey`（UUID）、`workOrderId`、`reportDate`、`productionQty`、`goodQty`、`unqualifiedQty`、`remark`。`reporterId` 不接受客户端提供；`productionQty` 不单独落库，保存的合格数和不合格数相加必须等于其值。

日期筛选使用 `dateFrom`/`dateTo`；产品、工单和人员筛选分别使用 `productId`、`workOrderId` 和 `reporterId`。本人查询忽略客户端人员条件，始终按当前会话用户过滤。

## 2. 业务规则和追溯

- 数量是整数 `BIGINT`：生产数大于零，合格数与不合格数均非负且不能同时为零，合计必须精确相等。
- 仅 `IN_PROGRESS` 状态的工单允许报工；可选工单列表同时排除已达到计划数量的工单。
- 当前数据库没有工单人员指派关系，首版的操作范围由 `report:create` 权限与工单状态限定。若需要按班组/人员进一步限制，需增加工单指派或生产班组关系表。
- 每条提交有唯一 `request_key`。相同用户、工单、日期、数量和备注的网络重试返回原记录；相同键对应不同内容返回冲突。并发重试造成唯一键冲突时，服务端读取已经提交的记录后按相同规则返回。
- 工单数量控制使用事务：事务以 `READ_COMMITTED` 隔离级别锁定工单行（`SELECT ... FOR UPDATE`），汇总有效报工数量，检查“当前有效量 + 本次数量 <= 工单计划量”，再写报工和审计日志。所有对同一工单的报工按工单行锁串行执行；锁释放和数据提交/回滚同时发生。
- 工单完成仍要求有效报工数量等于计划数量。完成检查也使用 `READ_COMMITTED`，确保取到锁后汇总时读取最新已提交的报工。
- 不提供数量编辑和物理删除。`report:void` 用户可在工单仍生产中时按原因作废记录，原数量、人员和日期保持不变；更正数量时另提交一条新的报工。已完成/取消工单不允许作废，以避免隐式回滚计划和订单状态。
- 有效生产数量按 `SUM(good_qty + scrap_qty)` 统计；有效合格与不合格数分别聚合。所有进度聚合排除 `VOID` 记录。

## 3. 数据与一致性

沿用 `mes_report`：`request_key` 全局唯一，`reporter_id` 来自认证会话，`work_order_id` 外键追溯工单，作废字段追溯更正。事务内同时写 `sys_operation_log`。查询汇总不回写冗余累计数量，降低并发更新点，工单行锁保证上限校验与插入互斥。

不需要数据库迁移。该实现依赖 MySQL/InnoDB 的行锁和 `READ_COMMITTED` 事务语义；不能只用 Mockito 测试替代部署目标数据库上的并发验证。
