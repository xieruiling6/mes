# 生产计划模块设计

## 业务规则

- 仅 `CONFIRMED` 且没有关联计划的订单可排产；数据库 `UNIQUE(production_order_id)` 作为并发兜底。
- 一个订单最多一份计划，计划数量必须等于订单数量。所有数量由服务端读取订单并校验。
- 计划开始、完成日期必填；完成日期不得早于开始日期，也不得晚于订单交期（订单交期存在时）。
- 优先级使用非负 `SMALLINT`，范围为 0 至 32767，默认 0。
- 新建计划为 `DRAFT`。只有草稿计划可修改；更新、发布和取消均要求提交读取详情时返回的 `expectedUpdatedAt`。
- 发布草稿计划时将计划改为 `RELEASED`。工单模块随后从已发布计划创建唯一一张数量、日期与计划相同的 `PENDING` 工单，避免计划与工单两个管理入口重复创建。
- 草稿计划可取消；已发布计划仅在工单仍为 `PENDING` 且不存在有效报工时可取消，并在事务中取消待开工工单。已开工、完成或存在有效报工的计划拒绝取消。
- 计划完成、合格和报废数量实时汇总有效报工，不在计划表重复保存。后续报工模块负责将工单及订单状态推进到 `IN_PROGRESS`、`COMPLETED`；计划列表显示关联工单数量和当前有效报工汇总。计划详情同时返回关联工单编号、状态和数量（`workOrderId`、`workOrderNo`、`workOrderStatus`、`workOrderPlannedQty`），以及工单数、已完成工单数、合格量、报废量和完成量。
- 创建、编辑、发布和取消成功后写入 `sys_operation_log`。写操作由 Spring 事务保证业务数据和审计记录一致。

## 接口

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/api/v1/plans/available-orders` | `plan:read` 或 `plan:manage` | 查询已确认且未排产订单 |
| GET | `/api/v1/plans` | `plan:read` 或 `plan:manage` | 分页筛选计划 |
| GET | `/api/v1/plans/{id}` | `plan:read` 或 `plan:manage` | 计划及执行汇总详情 |
| POST | `/api/v1/plans` | `plan:manage` | 创建草稿计划 |
| PUT | `/api/v1/plans/{id}` | `plan:manage` | 更新草稿计划 |
| PATCH | `/api/v1/plans/{id}/release` | `plan:publish` | 发布计划 |
| PATCH | `/api/v1/plans/{id}/cancel` | `plan:cancel` | 取消未执行计划 |

列表筛选支持计划编号、订单编号、产品、状态、优先级、计划开始日期范围和计划完成日期范围。分页使用公共 `PageQuery`，统一响应使用 `ApiResponse` / `PageResult`。

创建请求字段：`productionOrderId`、`planQty`、`startDate`、`endDate`、`priority`、`remark`。更新请求不接受订单 ID，包含计划字段和 `expectedUpdatedAt`。发布、取消请求包含 `expectedUpdatedAt`。

## 状态流转

```text
DRAFT -> RELEASED -> IN_PROGRESS -> COMPLETED
   \-> CANCELLED
RELEASED -> CANCELLED  (仅待开工工单且无有效报工)
```

目前生产计划模块自身执行发布、取消；工单模块创建工单并推进计划状态，报工模块后续依据有效报工推进工单与订单状态。计划模块不提供将状态作为任意请求字段直接修改的接口。

## 数据库迁移

`docs/sql/V3__production_plan_priority.sql` 为已经部署旧版 `mes_production_plan` 表的升级脚本。新环境使用已包含 `priority` 的 `docs/sql/init.sql` 初始化，不应再执行 V3；已有环境执行 V3 前应确认该字段和约束尚未存在。
