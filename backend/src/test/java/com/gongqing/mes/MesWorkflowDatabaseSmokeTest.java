package com.gongqing.mes;

import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.modules.equipment.EquipmentService;
import com.gongqing.mes.modules.order.ProductionOrderMapper;
import com.gongqing.mes.modules.order.ProductionOrderService;
import com.gongqing.mes.modules.plan.ProductionPlanService;
import com.gongqing.mes.modules.product.ProductService;
import com.gongqing.mes.modules.quality.QualityService;
import com.gongqing.mes.modules.report.ReportService;
import com.gongqing.mes.modules.statistics.StatisticsService;
import com.gongqing.mes.modules.workorder.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 MySQL 下的纵向闭环验证：产品 -> 订单 -> 计划 -> 工单 -> 报工/作废 -> 质检 -> 完工 -> 设备维修。
 * 默认跳过；使用 -Dmes.integration=true 且提供 DB_* 环境变量运行，事务结束自动回滚。
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "mes.integration", matches = "true")
class MesWorkflowDatabaseSmokeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductService products;
    @Autowired ProductionOrderService orders;
    @Autowired ProductionPlanService plans;
    @Autowired WorkOrderService workOrders;
    @Autowired ReportService reports;
    @Autowired QualityService quality;
    @Autowired EquipmentService equipment;
    @Autowired StatisticsService statistics;

    @Test
    @Transactional
    void completesTheWholeProductionWorkflowAgainstRealMySql() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String username = "flow-" + suffix;
        jdbc.update("INSERT INTO sys_user(username,password_hash,display_name,status) VALUES(?,?,?,'ACTIVE')",
                username, "not-a-login-hash", "闭环测试员");
        Long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        LocalDate today = LocalDate.now();

        Long productId = products.create(new ProductService.ProductRequest(
                "FP-" + suffix, "闭环测试产品", "T-1", "台"), userId);
        Long orderId = orders.create(new ProductionOrderService.OrderRequest(
                "FO-" + suffix, productId, 10L, today.plusDays(30), "闭环测试"), userId);
        orders.confirm(orderId, userId);

        Long planId = plans.create(new ProductionPlanService.CreatePlanRequest(
                orderId, 10L, today, today.plusDays(10), 3, "闭环计划"), userId);
        var plan = plans.detail(planId);
        plans.release(planId, plan.updatedAt(), userId);

        Long workOrderId = workOrders.createFromPlan(planId,
                new WorkOrderService.CreateFromPlanRequest("闭环工单"), userId);
        var workOrder = workOrders.detail(workOrderId);
        workOrders.dispatch(workOrderId, new WorkOrderService.StatusRequest(workOrder.updatedAt()), userId);
        workOrder = workOrders.detail(workOrderId);
        workOrders.start(workOrderId, new WorkOrderService.StatusRequest(workOrder.updatedAt()), userId);

        var first = reports.submit(new ReportService.SubmitReportRequest(UUID.randomUUID().toString(),
                workOrderId, today, 4L, 3L, 1L, "首批"), userId);
        var second = reports.submit(new ReportService.SubmitReportRequest(UUID.randomUUID().toString(),
                workOrderId, today, 6L, 6L, 0L, "待更正"), userId);
        assertThatThrownBy(() -> reports.submit(new ReportService.SubmitReportRequest(UUID.randomUUID().toString(),
                workOrderId, today, 1L, 1L, 0L, null), userId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("超过工单计划");

        reports.voidReport(second.id(), new ReportService.VoidReportRequest("数量录错，重新报工"), userId);
        var corrected = reports.submit(new ReportService.SubmitReportRequest(UUID.randomUUID().toString(),
                workOrderId, today, 6L, 6L, 0L, "更正后"), userId);
        assertThat(corrected.id()).isNotEqualTo(second.id());

        var inspection = quality.create(new QualityService.CreateRequest(workOrderId, corrected.id(), today,
                5L, 4L, 1L, "外观划伤", "闭环质检"), userId);
        quality.dispose(inspection.id(), new QualityService.DisposeRequest("REWORK", "返工处理"), userId);

        workOrder = workOrders.detail(workOrderId);
        workOrders.complete(workOrderId, new WorkOrderService.StatusRequest(workOrder.updatedAt()), userId);

        ProductionOrderMapper.OrderExecutionView execution = orders.execution(orderId);
        assertThat(execution.planId()).isEqualTo(planId);
        assertThat(execution.workOrderId()).isEqualTo(workOrderId);
        assertThat(execution.reportCount()).isEqualTo(2);
        assertThat(execution.goodQty()).isEqualTo(9);
        assertThat(execution.scrapQty()).isEqualTo(1);
        assertThat(execution.completedQty()).isEqualTo(10);
        assertThat(orders.detail(orderId).status()).isEqualTo("COMPLETED");
        assertThat(plans.detail(planId).status()).isEqualTo("COMPLETED");
        assertThat(workOrders.detail(workOrderId).status()).isEqualTo("COMPLETED");
        assertThat(quality.detail(inspection.id()).dispositionStatus()).isEqualTo("REWORK");

        Long equipmentId = equipment.create(new EquipmentService.EquipmentRequest(
                "FE-" + suffix, "闭环测试设备", "M1", "A 线", null, null), userId);
        assertThat(equipment.detail(equipmentId).status()).isEqualTo("IDLE");
        Long maintenanceId = equipment.startMaintenance(equipmentId,
                new EquipmentService.StartMaintenanceRequest("异响"), userId);
        assertThat(equipment.detail(equipmentId).status()).isEqualTo("MAINTENANCE");
        equipment.completeMaintenance(equipmentId, maintenanceId,
                new EquipmentService.CompleteMaintenanceRequest("更换轴承"), userId);
        assertThat(equipment.detail(equipmentId).status()).isEqualTo("IDLE");
        assertThat(equipment.maintenance(equipmentId, new com.gongqing.mes.common.api.PageQuery())
                .records()).singleElement().satisfies(item -> assertThat(item.status()).isEqualTo("COMPLETED"));

        assertThat(statistics.dashboard(today, today)).isNotNull();
        assertThat(statistics.reportDetails(today, today))
                .anySatisfy(detail -> assertThat(detail.reportNo()).isEqualTo(corrected.reportNo()));
        assertThat(first.id()).isNotEqualTo(corrected.id());
    }
}
