package com.gongqing.mes.modules.report;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReportMapper {
    String COLUMNS = "r.id, r.report_no AS reportNo, r.work_order_id AS workOrderId, " +
            "wo.work_order_no AS workOrderNo, pl.plan_no AS planNo, o.order_no AS orderNo, p.id AS productId, " +
            "p.product_code AS productCode, p.product_name AS productName, p.unit, r.reporter_id AS reporterId, " +
            "u.display_name AS reporterName, r.report_date AS reportDate, r.good_qty AS goodQty, " +
            "r.scrap_qty AS unqualifiedQty, r.good_qty+r.scrap_qty AS productionQty, r.report_status AS reportStatus, " +
            "r.remark, r.created_at AS createdAt, r.voided_by AS voidedBy, vu.display_name AS voidedByName, " +
            "r.voided_at AS voidedAt, r.void_reason AS voidReason";
    String JOINS = " FROM mes_report r JOIN mes_work_order wo ON wo.id=r.work_order_id " +
            "JOIN mes_production_plan pl ON pl.id=wo.plan_id " +
            "JOIN mes_production_order o ON o.id=pl.production_order_id " +
            "JOIN mes_product p ON p.id=o.product_id JOIN sys_user u ON u.id=r.reporter_id " +
            "LEFT JOIN sys_user vu ON vu.id=r.voided_by ";

    @Select("SELECT wo.id AS workOrderId, wo.work_order_no AS workOrderNo, pl.plan_no AS planNo, " +
            "o.order_no AS orderNo, p.id AS productId, p.product_code AS productCode, p.product_name AS productName, " +
            "p.unit, wo.planned_qty AS plannedQty, COALESCE(SUM(CASE WHEN r.report_status='VALID' " +
            "THEN r.good_qty+r.scrap_qty ELSE 0 END),0) AS reportedQty " +
            "FROM mes_work_order wo JOIN mes_production_plan pl ON pl.id=wo.plan_id " +
            "JOIN mes_production_order o ON o.id=pl.production_order_id JOIN mes_product p ON p.id=o.product_id " +
            "LEFT JOIN mes_report r ON r.work_order_id=wo.id WHERE wo.status='IN_PROGRESS' " +
            "GROUP BY wo.id,wo.work_order_no,pl.plan_no,o.order_no,p.id,p.product_code,p.product_name,p.unit,wo.planned_qty " +
            "HAVING COALESCE(SUM(CASE WHEN r.report_status='VALID' THEN r.good_qty+r.scrap_qty ELSE 0 END),0)<wo.planned_qty " +
            "ORDER BY wo.updated_at,wo.id")
    List<AvailableWorkOrder> findAvailableWorkOrders();

    @Select("<script>SELECT DISTINCT p.id,p.product_code AS productCode,p.product_name AS productName FROM mes_report r " +
            "JOIN mes_work_order wo ON wo.id=r.work_order_id JOIN mes_production_plan pl ON pl.id=wo.plan_id " +
            "JOIN mes_production_order o ON o.id=pl.production_order_id JOIN mes_product p ON p.id=o.product_id " +
            "<if test='reporterId != null'>WHERE r.reporter_id=#{reporterId} </if>ORDER BY p.product_code</script>")
    List<ProductOption> findProductOptions(@Param("reporterId") Long reporterId);

    @Select("SELECT DISTINCT u.id AS reporterId,u.display_name AS reporterName FROM mes_report r " +
            "JOIN sys_user u ON u.id=r.reporter_id ORDER BY u.display_name,u.id")
    List<ReporterOption> findReporterOptions();

    @Select("SELECT id, work_order_no AS workOrderNo, planned_qty AS plannedQty, status " +
            "FROM mes_work_order WHERE id=#{id} FOR UPDATE")
    LockedWorkOrder lockWorkOrder(@Param("id") Long id);

    @Select("SELECT COALESCE(SUM(good_qty+scrap_qty),0) FROM mes_report " +
            "WHERE work_order_id=#{workOrderId} AND report_status='VALID'")
    long sumValidQty(@Param("workOrderId") Long workOrderId);

    @Select("SELECT " + COLUMNS + JOINS + "WHERE r.request_key=#{requestKey}")
    ReportView findByRequestKey(@Param("requestKey") String requestKey);

    @Select("SELECT id,report_no AS reportNo,work_order_id AS workOrderId,report_status AS reportStatus " +
            "FROM mes_report WHERE id=#{id} FOR UPDATE")
    LockedReport lockReport(@Param("id") Long id);

    @Insert("INSERT INTO mes_report(report_no,request_key,work_order_id,reporter_id,report_date,good_qty,scrap_qty,report_status,remark) " +
            "VALUES(#{reportNo},#{requestKey},#{workOrderId},#{reporterId},#{reportDate},#{goodQty},#{unqualifiedQty},'VALID',#{remark})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReportRecord record);

    @Update("UPDATE mes_report SET report_status='VOID',voided_by=#{actorId},voided_at=CURRENT_TIMESTAMP(3),void_reason=#{reason} " +
            "WHERE id=#{id} AND report_status='VALID'")
    int voidReport(@Param("id") Long id, @Param("actorId") Long actorId, @Param("reason") String reason);

    @Select("<script>SELECT " + COLUMNS + JOINS + "WHERE 1=1 " +
            "<if test='workOrderId != null'>AND r.work_order_id=#{workOrderId} </if>" +
            "<if test='productId != null'>AND p.id=#{productId} </if>" +
            "<if test='reporterId != null'>AND r.reporter_id=#{reporterId} </if>" +
            "<if test='dateFrom != null'>AND r.report_date &gt;=#{dateFrom} </if>" +
            "<if test='dateTo != null'>AND r.report_date &lt;=#{dateTo} </if>" +
            "<if test='reportStatus != null'>AND r.report_status=#{reportStatus} </if>" +
            "ORDER BY r.created_at DESC,r.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<ReportView> findPage(@Param("workOrderId") Long workOrderId, @Param("productId") Long productId,
            @Param("reporterId") Long reporterId, @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo, @Param("reportStatus") String reportStatus,
            @Param("limit") long limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM mes_report r JOIN mes_work_order wo ON wo.id=r.work_order_id " +
            "JOIN mes_production_plan pl ON pl.id=wo.plan_id JOIN mes_production_order o ON o.id=pl.production_order_id " +
            "WHERE 1=1 <if test='workOrderId != null'>AND r.work_order_id=#{workOrderId} </if>" +
            "<if test='productId != null'>AND o.product_id=#{productId} </if>" +
            "<if test='reporterId != null'>AND r.reporter_id=#{reporterId} </if>" +
            "<if test='dateFrom != null'>AND r.report_date &gt;=#{dateFrom} </if>" +
            "<if test='dateTo != null'>AND r.report_date &lt;=#{dateTo} </if>" +
            "<if test='reportStatus != null'>AND r.report_status=#{reportStatus} </if></script>")
    long countPage(@Param("workOrderId") Long workOrderId, @Param("productId") Long productId,
            @Param("reporterId") Long reporterId, @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo, @Param("reportStatus") String reportStatus);

    @Select("SELECT wo.id AS workOrderId,wo.work_order_no AS workOrderNo,wo.planned_qty AS plannedQty," +
            "COALESCE(SUM(CASE WHEN r.report_status='VALID' THEN r.good_qty+r.scrap_qty ELSE 0 END),0) AS productionQty," +
            "COALESCE(SUM(CASE WHEN r.report_status='VALID' THEN r.good_qty ELSE 0 END),0) AS goodQty," +
            "COALESCE(SUM(CASE WHEN r.report_status='VALID' THEN r.scrap_qty ELSE 0 END),0) AS unqualifiedQty " +
            "FROM mes_work_order wo LEFT JOIN mes_report r ON r.work_order_id=wo.id WHERE wo.id=#{workOrderId} " +
            "GROUP BY wo.id,wo.work_order_no,wo.planned_qty")
    WorkOrderSummary summarizeWorkOrder(@Param("workOrderId") Long workOrderId);

    class ReportRecord {
        private Long id;
        private String reportNo;
        private String requestKey;
        private Long workOrderId;
        private Long reporterId;
        private LocalDate reportDate;
        private Long goodQty;
        private Long unqualifiedQty;
        private String remark;
        public Long getId() { return id; }
        public void setId(Long value) { id = value; }
        public String getReportNo() { return reportNo; }
        public void setReportNo(String value) { reportNo = value; }
        public String getRequestKey() { return requestKey; }
        public void setRequestKey(String value) { requestKey = value; }
        public Long getWorkOrderId() { return workOrderId; }
        public void setWorkOrderId(Long value) { workOrderId = value; }
        public Long getReporterId() { return reporterId; }
        public void setReporterId(Long value) { reporterId = value; }
        public LocalDate getReportDate() { return reportDate; }
        public void setReportDate(LocalDate value) { reportDate = value; }
        public Long getGoodQty() { return goodQty; }
        public void setGoodQty(Long value) { goodQty = value; }
        public Long getUnqualifiedQty() { return unqualifiedQty; }
        public void setUnqualifiedQty(Long value) { unqualifiedQty = value; }
        public String getRemark() { return remark; }
        public void setRemark(String value) { remark = value; }
    }

    record AvailableWorkOrder(Long workOrderId, String workOrderNo, String planNo, String orderNo, Long productId,
            String productCode, String productName, String unit, Long plannedQty, Long reportedQty) { }
    record ProductOption(Long id, String productCode, String productName) { }
    record ReporterOption(Long reporterId, String reporterName) { }
    record LockedWorkOrder(Long id, String workOrderNo, Long plannedQty, String status) { }
    record LockedReport(Long id, String reportNo, Long workOrderId, String reportStatus) { }
    record ReportView(Long id, String reportNo, Long workOrderId, String workOrderNo, String planNo,
            String orderNo, Long productId, String productCode, String productName, String unit, Long reporterId,
            String reporterName, LocalDate reportDate, Long goodQty, Long unqualifiedQty, Long productionQty,
            String reportStatus, String remark, LocalDateTime createdAt, Long voidedBy, String voidedByName,
            LocalDateTime voidedAt, String voidReason) { }
    record WorkOrderSummary(Long workOrderId, String workOrderNo, Long plannedQty, Long productionQty,
            Long goodQty, Long unqualifiedQty) { }
}
