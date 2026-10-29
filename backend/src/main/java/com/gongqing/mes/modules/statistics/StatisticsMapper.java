package com.gongqing.mes.modules.statistics;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StatisticsMapper {
    @Select("SELECT COUNT(*) total, COALESCE(SUM(o.status='DRAFT'),0) draft, " +
            "COALESCE(SUM(o.status='CONFIRMED'),0) confirmed, COALESCE(SUM(o.status='IN_PROGRESS'),0) inProgress, " +
            "COALESCE(SUM(o.status='COMPLETED'),0) completed, COALESCE(SUM(o.status='CANCELLED'),0) cancelled " +
            "FROM mes_production_order o WHERE o.created_at >= #{from} AND o.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY) " +
            "AND (#{productId} IS NULL OR o.product_id=#{productId}) " +
            "AND (#{orderStatus} IS NULL OR o.status=#{orderStatus})")
    OrderStats orderStats(@Param("from") LocalDate from, @Param("to") LocalDate to,
                          @Param("productId") Long productId, @Param("orderStatus") String orderStatus);

    @Select("SELECT wo.status,COUNT(*) count FROM mes_work_order wo " +
            "JOIN mes_production_plan pl ON pl.id=wo.plan_id JOIN mes_production_order o ON o.id=pl.production_order_id " +
            "WHERE wo.created_at >= #{from} AND wo.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY) " +
            "AND (#{productId} IS NULL OR o.product_id=#{productId}) " +
            "AND (#{orderStatus} IS NULL OR o.status=#{orderStatus}) " +
            "GROUP BY wo.status ORDER BY wo.status")
    List<StatusCount> workOrderStatuses(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                        @Param("productId") Long productId, @Param("orderStatus") String orderStatus);

    @Select("SELECT r.report_date reportDate,COALESCE(SUM(r.good_qty+r.scrap_qty),0) productionQty, " +
            "COALESCE(SUM(r.good_qty),0) goodQty,COALESCE(SUM(r.scrap_qty),0) unqualifiedQty " +
            "FROM mes_report r JOIN mes_work_order wo ON wo.id=r.work_order_id " +
            "JOIN mes_production_plan pl ON pl.id=wo.plan_id JOIN mes_production_order o ON o.id=pl.production_order_id " +
            "WHERE r.report_status='VALID' AND r.report_date BETWEEN #{from} AND #{to} " +
            "AND (#{productId} IS NULL OR o.product_id=#{productId}) " +
            "AND (#{orderStatus} IS NULL OR o.status=#{orderStatus}) " +
            "GROUP BY r.report_date ORDER BY r.report_date")
    List<DailyProduction> dailyProduction(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                          @Param("productId") Long productId, @Param("orderStatus") String orderStatus);

    @Select("SELECT COALESCE(SUM(q.inspection_qty),0) inspectionQty,COALESCE(SUM(q.good_qty),0) goodQty, " +
            "COALESCE(SUM(q.unqualified_qty),0) unqualifiedQty FROM mes_quality_inspection q " +
            "JOIN mes_work_order wo ON wo.id=q.work_order_id " +
            "JOIN mes_production_plan pl ON pl.id=wo.plan_id JOIN mes_production_order o ON o.id=pl.production_order_id " +
            "WHERE q.inspection_date BETWEEN #{from} AND #{to} " +
            "AND (#{productId} IS NULL OR o.product_id=#{productId}) " +
            "AND (#{orderStatus} IS NULL OR o.status=#{orderStatus})")
    QualityStats qualityStats(@Param("from") LocalDate from, @Param("to") LocalDate to,
                              @Param("productId") Long productId, @Param("orderStatus") String orderStatus);

    @Select("SELECT pl.status,COUNT(*) planCount,COALESCE(SUM(pl.plan_qty),0) plannedQty, " +
            "COALESCE(SUM(COALESCE(r.reportedQty,0)),0) reportedQty " +
            "FROM mes_production_plan pl JOIN mes_production_order o ON o.id=pl.production_order_id " +
            "LEFT JOIN mes_work_order wo ON wo.plan_id=pl.id " +
            "LEFT JOIN (SELECT work_order_id,SUM(good_qty+scrap_qty) reportedQty FROM mes_report " +
            "WHERE report_status='VALID' GROUP BY work_order_id) r ON r.work_order_id=wo.id " +
            "WHERE pl.start_date BETWEEN #{from} AND #{to} " +
            "AND (#{productId} IS NULL OR o.product_id=#{productId}) " +
            "AND (#{orderStatus} IS NULL OR o.status=#{orderStatus}) " +
            "GROUP BY pl.status ORDER BY pl.status")
    List<PlanExecution> planExecution(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                      @Param("productId") Long productId, @Param("orderStatus") String orderStatus);

    @Select("SELECT p.id productId,p.product_code productCode,p.product_name productName,p.unit, " +
            "SUM(r.good_qty+r.scrap_qty) productionQty,SUM(r.good_qty) goodQty,SUM(r.scrap_qty) unqualifiedQty " +
            "FROM mes_report r JOIN mes_work_order wo ON wo.id=r.work_order_id " +
            "JOIN mes_production_plan pl ON pl.id=wo.plan_id " +
            "JOIN mes_production_order o ON o.id=pl.production_order_id " +
            "JOIN mes_product p ON p.id=o.product_id WHERE r.report_status='VALID' " +
            "AND r.report_date BETWEEN #{from} AND #{to} " +
            "AND (#{productId} IS NULL OR o.product_id=#{productId}) " +
            "AND (#{orderStatus} IS NULL OR o.status=#{orderStatus}) " +
            "GROUP BY p.id,p.product_code,p.product_name,p.unit ORDER BY productionQty DESC,p.product_code LIMIT #{limit}")
    List<ProductRanking> productRanking(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                        @Param("productId") Long productId, @Param("orderStatus") String orderStatus,
                                        @Param("limit") int limit);

    @Select("SELECT r.report_date reportDate,r.report_no reportNo,wo.work_order_no workOrderNo, " +
            "o.order_no orderNo,p.product_code productCode,p.product_name productName, " +
            "r.good_qty goodQty,r.scrap_qty unqualifiedQty,r.good_qty+r.scrap_qty productionQty, " +
            "u.display_name reporterName FROM mes_report r JOIN mes_work_order wo ON wo.id=r.work_order_id " +
            "JOIN mes_production_plan pl ON pl.id=wo.plan_id JOIN mes_production_order o ON o.id=pl.production_order_id " +
            "JOIN mes_product p ON p.id=o.product_id JOIN sys_user u ON u.id=r.reporter_id " +
            "WHERE r.report_status='VALID' AND r.report_date BETWEEN #{from} AND #{to} " +
            "AND (#{productId} IS NULL OR o.product_id=#{productId}) " +
            "AND (#{orderStatus} IS NULL OR o.status=#{orderStatus}) " +
            "ORDER BY r.report_date DESC,r.id DESC LIMIT #{limit}")
    List<ReportDetail> reportDetails(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                     @Param("productId") Long productId, @Param("orderStatus") String orderStatus,
                                     @Param("limit") int limit);

    record OrderStats(Long total, Long draft, Long confirmed, Long inProgress, Long completed, Long cancelled) { }
    record StatusCount(String status, Long count) { }
    record DailyProduction(LocalDate reportDate, Long productionQty, Long goodQty, Long unqualifiedQty) { }
    record QualityStats(Long inspectionQty, Long goodQty, Long unqualifiedQty) { }
    record PlanExecution(String status, Long planCount, Long plannedQty, Long reportedQty) { }
    record ProductRanking(Long productId, String productCode, String productName, String unit,
                          Long productionQty, Long goodQty, Long unqualifiedQty) { }
    record ReportDetail(LocalDate reportDate, String reportNo, String workOrderNo, String orderNo,
                        String productCode, String productName, Long goodQty, Long unqualifiedQty,
                        Long productionQty, String reporterName) { }
}
