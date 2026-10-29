# 质量管理与设备管理设计

## 1. 范围与业务约定

本阶段提供可追溯的质量检验记录、不合格品处置、设备档案和维修记录。设备数据仅为人工维护的档案和状态，不接入或模拟传感器、PLC 等设备采集数据。

质量检验必须关联一张工单；可以关联一条报工记录。若关联报工，后端校验报工属于该工单且状态为 `VALID`。检验数量大于零，合格数和不合格数均非负，二者之和必须等于检验数量；不合格数大于零时必须填写不合格原因。所有质检记录的工单级累计检验数量不得超过有效报工合格数量；关联报工的子集累计检验数量也不得超过该报工的合格数。创建时锁定工单行并在同一事务内检查与插入，避免并发超检。质量记录创建后不提供覆盖式编辑或删除；处置状态通过单独的状态操作和操作日志追踪。

设备状态为 `IDLE`（闲置）、`IN_USE`（使用中）、`MAINTENANCE`（维修中）、`DISABLED`（停用），由有权限的用户维护。维修记录创建时将设备置为维修中；同一设备只能有一条未完成维修记录。完成维修需要处理说明并记录完成时间，设备恢复为闲置；维修记录保留创建、完成操作人和时间，不能删除。

## 2. 接口设计

接口均使用现有 `/api/v1` 前缀和统一响应结构。分页查询沿用 `PageQuery` / `PageResult`。所有写入操作从已认证主体解析操作人，忽略客户端传入的用户 ID。

### 2.1 质量管理

| 方法 | 路径 | 用途 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/v1/quality/records` | 分页查询质检历史；支持工单、报工、产品、处置状态、检验日期范围筛选 | `quality:read` |
| GET | `/api/v1/quality/records/{id}` | 查询质检记录详情及处置记录 | `quality:read` |
| POST | `/api/v1/quality/records` | 创建检验记录，提交工单、可选报工、数量、原因和备注 | `quality:create` |
| PATCH | `/api/v1/quality/records/{id}/disposition` | 更新不合格品处置；仅存在不合格数的记录可处置，记录处置状态、说明 | `quality:dispose` |
| GET | `/api/v1/quality/filter-options` | 返回可用于检验录入的工单选项 | `quality:read` 或 `quality:create` |
| GET | `/api/v1/quality/work-orders/{id}/reports` | 查询指定工单的有效报工供检验关联选择 | `quality:read` 或 `quality:create` |

处置状态：`PENDING`（待处理）、`ACCEPTED`（让步接收）、`REWORK`（返工）、`SCRAPPED`（报废）。不合格数为零的记录固定为 `NOT_REQUIRED`，不可变更处置状态。处置操作携带非空说明，后端拒绝重复提交相同终态变更；状态历史写入 `sys_operation_log`。

### 2.2 设备管理

| 方法 | 路径 | 用途 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/v1/equipment` | 分页查询设备；支持编号、名称、状态筛选 | `equipment:read` |
| GET | `/api/v1/equipment/{id}` | 查询设备档案详情 | `equipment:read` |
| POST | `/api/v1/equipment` | 新建设备档案 | `equipment:manage` |
| PUT | `/api/v1/equipment/{id}` | 编辑设备编号、名称、规格、位置和备注；并发更新使用 `updated_at` 版本检查 | `equipment:manage` |
| PATCH | `/api/v1/equipment/{id}/status` | 修改设备状态；维修中状态只能通过维修记录流程变更 | `equipment:manage` |
| GET | `/api/v1/equipment/{id}/maintenance-records` | 分页查询指定设备维修历史 | `equipment:read` |
| POST | `/api/v1/equipment/{id}/maintenance-records` | 创建维修记录并把设备置为维修中 | `equipment:maintain` |
| PATCH | `/api/v1/equipment/{id}/maintenance-records/{recordId}/complete` | 完成维修，记录处理说明并将设备置为闲置 | `equipment:maintain` |

设备编号唯一。设备和维修记录均不提供物理删除。维修时间、操作人由服务端记录；客户端不得指定维修状态、结束时间或操作人。

## 3. 数据库影响

新增版本迁移 `V6__quality_and_equipment.sql`，不修改既有业务表结构：

- `mes_quality_inspection`：质检编号唯一；关联工单必填，报工关联可空；检验、合格、不合格数量使用 `BIGINT`；保存不合格原因、处置状态/说明、检验人和时间。外键限制删除工单、报工和用户。索引支持按工单/报工/时间及状态查询，CHECK 约束保障数量非负、检验量大于零及合格加不合格等于检验量。
- `mes_equipment`：设备编号唯一；保存名称、规格、位置、状态、备注、创建/更新操作人及时间。状态使用 CHECK 约束；唯一编号与状态索引支持档案查询。
- `mes_equipment_maintenance`：维修编号唯一；关联设备必填；保存维修原因、处理说明、状态、开始/完成时间和操作人。外键限制删除设备和用户；索引支持按设备和维修状态查询。
- 在权限种子中新增质量查询/创建/处置、设备查询/管理/维修权限，并给管理员角色默认授权。其他角色按职责授予，避免扩大现有业务权限。

检验记录在写入时锁定工单行，读取有效报工合格数和已检数量并原子插入，数据库 CHECK 和外键提供第二层防护。处置和维修状态更新通过事务完成，相关操作写入 `sys_operation_log`。初始化 SQL 同步包含新增表和幂等权限种子，使全新部署与迁移部署一致。

## 4. 依赖关系

- 质检记录 -> 必需工单；可选有效报工；工单通过计划关联产品。报工归属关系及可检数量均由后端核验。
- 设备维修记录 -> 必需设备；设备当前维修状态与未完成维修记录在事务中同步。
- 设备档案不与产品或工单强制关联，避免当前没有设备使用/保养业务基础时引入虚构关系。
