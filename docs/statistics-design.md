# 生产统计与报表设计

## 统计口径

所有日期区间均为闭区间 `[dateFrom, dateTo]`，必填且 `dateTo >= dateFrom`，最多覆盖 366 个日历日。首页默认近 30 日。接口用数据库日期列过滤，不在前端计算业务数字。看板与统计支持可选 `productId` 和 `status`（订单当前状态）筛选；筛选同时作用于所有卡片、图表和明细，非法产品或订单状态返回 HTTP 400。

| 指标 | 来源与口径 | 日期维度 |
| --- | --- | --- |
| 生产订单数量及状态 | `mes_production_order` 按订单当前状态计数；只统计创建时间落在范围内的订单。状态是当前状态，不是期间历史快照。 | `created_at` |
| 工单状态分布 | `mes_work_order` 按当前状态计数；只统计创建时间落在范围内的工单。 | `created_at` |
| 每日生产、合格与不合格数量 | 仅累加 `mes_report.report_status='VALID'`。生产量=`good_qty+scrap_qty`，合格量=`good_qty`，不合格量=`scrap_qty`。作废记录保留但不计入。无记录日期不返回，由图表保留真实有数据的日期，不补造零值。 | `report_date` |
| 质检量、质检合格与不合格 | 独立累加 `mes_quality_inspection`，不与报工记录相加或关联后再聚合。质检合格率=`good_qty/inspection_qty`；无检验时前端显示“—”。 | `inspection_date` |
| 计划执行情况 | 按计划当前状态统计计划份数、计划量及关联工单有效报工量。单个计划最多一张工单；报工先按工单聚合，再关联计划，避免报工扩行重复计划量。取消计划保留在统计内。 | `start_date` |
| 产品生产数量排行 | 有效报工按工单→计划→订单→产品的单向关系归属产品；按报工日期范围汇总，最多返回前 10 个产品。没有报工的产品不显示。 | `report_date` |
| 报工明细 | 每条有效报工一行，显示日期、报工号、工单、订单、产品、人员及数量；最多 500 条，超出时不保证包含完整区间，后续可增加分页。 | `report_date` |

## 接口清单

所有接口都要求登录并由后端检查 `statistics:read`。

| 方法与路径 | 用途 | 参数 / 返回 |
| --- | --- | --- |
| `GET /api/v1/statistics/dashboard` | 首页卡片与统计图表数据 | 必填 `dateFrom`、`dateTo`（ISO 日期）；返回订单汇总、工单状态、每日产量、质检汇总、计划执行和产品排行 |
| `GET /api/v1/statistics/reports` | 有效报工明细表 | 必填 `dateFrom`、`dateTo`；最多 500 行 |

日期范围错误或筛选参数非法返回 HTTP 400；没有业务数据时返回零汇总和空数组。接口沿用 `{code,message,data,traceId}`。前端首页和统计页都提供产品筛选，统计页额外提供订单状态筛选。

## 查询与数据安全

每个指标按自身事实表独立聚合。订单和工单统计不连接报工；计划量和报工量先各自按所属关系聚合；报工趋势与产品排行只使用报工事实，质检统计只使用质检事实。这样避免多个一对多关系联查造成的重复计数。

`docs/sql/init.sql` 新增 `idx_mes_report_status_date(report_status, report_date)` 和 `idx_mes_quality_inspection_date(inspection_date)`，用于日期范围扫描。已有数据库先应用 `docs/sql/V7__statistics_date_indexes.sql`。日期列上的筛选不包裹 `DATE()` 函数，便于索引生效。

## 验证说明

单元测试验证日期范围、空列表、汇总数据传递和明细数据传递；Web 层测试验证无 `statistics:read` 权限被拒绝。`StatisticsMapperDatabaseSmokeTest` 在 `mes.integration=true` 时会于事务中插入临时用户、产品、订单、计划、工单、有效/作废报工和质检数据，校验所有实际 SQL 后回滚。

本次本机数据库实测样本得到：订单 1、工单 1、计划量 10、有效报工产量 7（合格 6、不合格 1）、质检量 4；额外的 100 件作废报工未进入趋势、计划执行、排行和明细。回滚后确认测试用户及业务记录均为 0。未设置数据库连接时，数据库 smoke test 会跳过。
