package com.gongqing.mes.modules.statistics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfSystemProperty(named = "mes.integration", matches = "true")
class StatisticsMapperDatabaseSmokeTest {
    @Autowired StatisticsMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void runsEveryStatisticsQueryAgainstConfiguredDatabase() {
        // 使用一个不与真实业务数据重叠的历史日期，避免测试依赖库内已有数据。
        LocalDate to = LocalDate.of(2001, 1, 1);
        LocalDate from = to.minusDays(29);
        String suffix = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sys_user(username,password_hash,display_name) VALUES(?,?,?)",
                "stats-test-" + suffix, "not-a-login-hash", "统计测试人员");
        long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, "stats-test-" + suffix);
        jdbc.update("INSERT INTO mes_product(product_code,product_name,unit,created_by) VALUES(?,?,?,?)",
                "ST-" + suffix.substring(0, 8), "统计测试产品", "件", userId);
        long productId = jdbc.queryForObject("SELECT id FROM mes_product WHERE product_code=?", Long.class, "ST-" + suffix.substring(0, 8));
        jdbc.update("INSERT INTO mes_production_order(order_no,product_id,order_qty,due_date,status,created_by) VALUES(?,?,?,?,?,?)",
                "SO-" + suffix, productId, 10, to, "IN_PROGRESS", userId);
        long orderId = jdbc.queryForObject("SELECT id FROM mes_production_order WHERE order_no=?", Long.class, "SO-" + suffix);
        jdbc.update("UPDATE mes_production_order SET created_at=? WHERE id=?", to.atStartOfDay(), orderId);
        jdbc.update("INSERT INTO mes_production_plan(plan_no,production_order_id,plan_qty,start_date,end_date,status,created_by) VALUES(?,?,?,?,?,?,?)",
                "PL-" + suffix, orderId, 10, to, to, "IN_PROGRESS", userId);
        long planId = jdbc.queryForObject("SELECT id FROM mes_production_plan WHERE plan_no=?", Long.class, "PL-" + suffix);
        jdbc.update("INSERT INTO mes_work_order(work_order_no,plan_id,sequence_no,planned_qty,start_date,end_date,status,created_by) VALUES(?,?,?,?,?,?,?,?)",
                "WO-" + suffix, planId, 1, 10, to, to, "IN_PROGRESS", userId);
        long workOrderId = jdbc.queryForObject("SELECT id FROM mes_work_order WHERE work_order_no=?", Long.class, "WO-" + suffix);
        jdbc.update("UPDATE mes_work_order SET created_at=? WHERE id=?", to.atStartOfDay(), workOrderId);
        jdbc.update("INSERT INTO mes_report(report_no,request_key,work_order_id,reporter_id,report_date,good_qty,scrap_qty,report_status) VALUES(?,?,?,?,?,?,?,?)",
                "RP-" + suffix, UUID.randomUUID().toString(), workOrderId, userId, to, 6, 1, "VALID");
        long reportId = jdbc.queryForObject("SELECT id FROM mes_report WHERE report_no=?", Long.class, "RP-" + suffix);
        jdbc.update("INSERT INTO mes_report(report_no,request_key,work_order_id,reporter_id,report_date,good_qty,scrap_qty,report_status,voided_by,voided_at,void_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                "RV-" + suffix, UUID.randomUUID().toString(), workOrderId, userId, to, 50, 50, "VOID", userId, java.time.LocalDateTime.now(), "统计验证作废样本");
        jdbc.update("INSERT INTO mes_quality_inspection(inspection_no,work_order_id,report_id,inspection_date,inspection_qty,good_qty,unqualified_qty,unqualified_reason,disposition_status,inspected_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                "QI-" + suffix, workOrderId, reportId, to, 4, 3, 1, "抽样不合格", "PENDING", userId);

        var orders = mapper.orderStats(from, to, null, null);
        assertThat(orders.total()).isEqualTo(1);
        assertThat(orders.inProgress()).isEqualTo(1);
        assertThat(mapper.workOrderStatuses(from, to, null, null)).anySatisfy(row -> {
            assertThat(row.status()).isEqualTo("IN_PROGRESS");
            assertThat(row.count()).isEqualTo(1);
        });
        assertThat(mapper.dailyProduction(from, to, null, null)).singleElement().satisfies(day -> {
            assertThat(day.productionQty()).isEqualTo(7);
            assertThat(day.goodQty()).isEqualTo(6);
            assertThat(day.unqualifiedQty()).isEqualTo(1);
        });
        assertThat(mapper.qualityStats(from, to, null, null).inspectionQty()).isEqualTo(4);
        assertThat(mapper.planExecution(from, to, null, null)).singleElement().satisfies(plan -> {
            assertThat(plan.plannedQty()).isEqualTo(10);
            assertThat(plan.reportedQty()).isEqualTo(7);
        });
        assertThat(mapper.productRanking(from, to, null, null, 10)).singleElement().satisfies(product ->
                assertThat(product.productionQty()).isEqualTo(7));
        assertThat(mapper.reportDetails(from, to, null, null, 500)).singleElement().satisfies(detail -> {
            assertThat(detail.reportNo()).isEqualTo("RP-" + suffix);
            assertThat(detail.productionQty()).isEqualTo(7);
        });
        assertThat(mapper.orderStats(from, to, productId, "IN_PROGRESS").total()).isEqualTo(1);
        assertThat(mapper.orderStats(from, to, productId, "COMPLETED").total()).isZero();
        assertThat(mapper.dailyProduction(from, to, productId, null)).singleElement()
                .satisfies(day -> assertThat(day.productionQty()).isEqualTo(7));
        assertThat(mapper.reportDetails(from, to, productId, null, 500)).singleElement()
                .satisfies(detail -> assertThat(detail.reportNo()).isEqualTo("RP-" + suffix));
    }
}
