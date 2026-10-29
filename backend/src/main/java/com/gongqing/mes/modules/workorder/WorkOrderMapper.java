package com.gongqing.mes.modules.workorder;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorkOrderMapper {
    String VIEW_COLUMNS = "wo.id, wo.work_order_no AS workOrderNo, wo.plan_id AS planId, pl.plan_no AS planNo, " +
            "pl.production_order_id AS productionOrderId, o.order_no AS orderNo, p.id AS productId, " +
            "p.product_code AS productCode, p.product_name AS productName, p.specification, p.unit, " +
            "wo.sequence_no AS sequenceNo, wo.planned_qty AS plannedQty, wo.start_date AS startDate, " +
            "wo.end_date AS endDate, wo.status, wo.remark, wo.created_by AS createdBy, " +
            "u.display_name AS createdByName, wo.created_at AS createdAt, wo.updated_by AS updatedBy, " +
            "wo.updated_at AS updatedAt, COALESCE(r.reportCount,0) AS reportCount, COALESCE(r.goodQty,0) AS goodQty, " +
            "COALESCE(r.scrapQty,0) AS scrapQty, COALESCE(r.completedQty,0) AS completedQty";
    String JOINS = " FROM mes_work_order wo JOIN mes_production_plan pl ON pl.id=wo.plan_id " +
            "JOIN mes_production_order o ON o.id=pl.production_order_id JOIN mes_product p ON p.id=o.product_id " +
            "JOIN sys_user u ON u.id=wo.created_by LEFT JOIN (SELECT work_order_id, COUNT(*) reportCount, " +
            "SUM(good_qty) goodQty, SUM(scrap_qty) scrapQty, SUM(good_qty+scrap_qty) completedQty " +
            "FROM mes_report WHERE report_status='VALID' GROUP BY work_order_id) r ON r.work_order_id=wo.id ";

    @Select("<script>SELECT " + VIEW_COLUMNS + JOINS + "WHERE 1=1 " +
            "<if test='workOrderNo != null'>AND wo.work_order_no LIKE CONCAT('%',#{workOrderNo},'%') </if>" +
            "<if test='productId != null'>AND p.id=#{productId} </if>" +
            "<if test='status != null'>AND wo.status=#{status} </if>" +
            "<if test='dateFrom != null'>AND wo.start_date &gt;=#{dateFrom} </if>" +
            "<if test='dateTo != null'>AND wo.start_date &lt;=#{dateTo} </if>" +
            "ORDER BY wo.created_at DESC, wo.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<WorkOrderView> findPage(@Param("workOrderNo") String workOrderNo, @Param("productId") Long productId,
            @Param("status") String status, @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo,
            @Param("limit") long limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM mes_work_order wo JOIN mes_production_plan pl ON pl.id=wo.plan_id " +
            "JOIN mes_production_order o ON o.id=pl.production_order_id WHERE 1=1 " +
            "<if test='workOrderNo != null'>AND wo.work_order_no LIKE CONCAT('%',#{workOrderNo},'%') </if>" +
            "<if test='productId != null'>AND o.product_id=#{productId} </if>" +
            "<if test='status != null'>AND wo.status=#{status} </if>" +
            "<if test='dateFrom != null'>AND wo.start_date &gt;=#{dateFrom} </if>" +
            "<if test='dateTo != null'>AND wo.start_date &lt;=#{dateTo} </if></script>")
    long countPage(@Param("workOrderNo") String workOrderNo, @Param("productId") Long productId,
            @Param("status") String status, @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);

    @Select("SELECT pl.id AS planId, pl.plan_no AS planNo, pl.production_order_id AS productionOrderId, " +
            "o.order_no AS orderNo, o.order_qty AS orderQty, o.status AS orderStatus, pl.plan_qty AS planQty, " +
            "pl.start_date AS startDate, pl.end_date AS endDate, pl.status AS planStatus, p.product_code AS productCode, " +
            "p.product_name AS productName, p.unit FROM mes_production_plan pl " +
            "JOIN mes_production_order o ON o.id=pl.production_order_id JOIN mes_product p ON p.id=o.product_id " +
            "WHERE pl.status='RELEASED' AND o.status='CONFIRMED' " +
            "AND NOT EXISTS(SELECT 1 FROM mes_work_order wo WHERE wo.plan_id=pl.id) " +
            "ORDER BY pl.priority DESC, pl.start_date, pl.id")
    List<AvailablePlan> findAvailablePlans();

    @Select("SELECT " + VIEW_COLUMNS + JOINS + "WHERE wo.id=#{id}")
    WorkOrderView findById(@Param("id") Long id);

    @Select("SELECT r.id, r.report_no AS reportNo, r.work_order_id AS workOrderId, r.report_date AS reportDate, " +
            "r.good_qty AS goodQty, r.scrap_qty AS scrapQty, r.report_status AS reportStatus, " +
            "r.remark, r.created_at AS createdAt, u.display_name AS reporterName, r.voided_at AS voidedAt, " +
            "r.void_reason AS voidReason FROM mes_report r JOIN sys_user u ON u.id=r.reporter_id " +
            "WHERE r.work_order_id=#{workOrderId} ORDER BY r.created_at DESC, r.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ReportView> findReports(@Param("workOrderId") Long workOrderId, @Param("limit") long limit,
            @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM mes_report WHERE work_order_id=#{workOrderId}")
    long countReports(@Param("workOrderId") Long workOrderId);

    @Select("SELECT production_order_id FROM mes_production_plan WHERE id=#{planId}")
    Long findOrderIdByPlan(@Param("planId") Long planId);

    @Select("SELECT id, order_qty AS orderQty, status FROM mes_production_order WHERE id=#{id} FOR UPDATE")
    LockedOrder lockOrder(@Param("id") Long id);

    @Select("SELECT id, production_order_id AS productionOrderId, plan_qty AS planQty, start_date AS startDate, " +
            "end_date AS endDate, status FROM mes_production_plan WHERE id=#{id} FOR UPDATE")
    LockedPlan lockPlan(@Param("id") Long id);

    @Select("SELECT id FROM mes_work_order WHERE plan_id=#{planId} ORDER BY sequence_no FOR UPDATE")
    List<Long> lockPlanWorkOrders(@Param("planId") Long planId);

    @Select("SELECT id, work_order_no AS workOrderNo, plan_id AS planId, planned_qty AS plannedQty, " +
            "status, updated_at AS updatedAt FROM mes_work_order WHERE id=#{id} FOR UPDATE")
    LockedWorkOrder lockWorkOrder(@Param("id") Long id);

    @Select("SELECT plan_id FROM mes_work_order WHERE id=#{id}")
    Long findPlanIdByWorkOrder(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM mes_work_order WHERE plan_id=#{planId}")
    long countByPlan(@Param("planId") Long planId);

    @Insert("INSERT INTO mes_work_order(work_order_no,plan_id,sequence_no,planned_qty,start_date,end_date,status,remark,created_by) " +
            "VALUES(#{workOrderNo},#{planId},1,#{plannedQty},#{startDate},#{endDate},'PENDING',#{remark},#{createdBy})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WorkOrderRecord record);

    @Update("UPDATE mes_work_order SET status=#{targetStatus},updated_by=#{actorId} " +
            "WHERE id=#{id} AND status=#{sourceStatus} AND updated_at=#{expectedUpdatedAt}")
    int transition(@Param("id") Long id, @Param("sourceStatus") String sourceStatus,
            @Param("targetStatus") String targetStatus, @Param("actorId") Long actorId,
            @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt);

    @Update("UPDATE mes_production_plan SET status='IN_PROGRESS',updated_by=#{actorId} WHERE id=#{id} AND status='RELEASED'")
    int markPlanInProgress(@Param("id") Long id, @Param("actorId") Long actorId);

    @Update("UPDATE mes_production_order SET status='IN_PROGRESS',updated_by=#{actorId} WHERE id=#{id} AND status='CONFIRMED'")
    int markOrderInProgress(@Param("id") Long id, @Param("actorId") Long actorId);

    @Update("UPDATE mes_production_plan SET status='COMPLETED',updated_by=#{actorId} WHERE id=#{id} AND status='IN_PROGRESS'")
    int markPlanCompleted(@Param("id") Long id, @Param("actorId") Long actorId);

    @Update("UPDATE mes_production_order SET status='COMPLETED',updated_by=#{actorId} WHERE id=#{id} AND status='IN_PROGRESS'")
    int markOrderCompleted(@Param("id") Long id, @Param("actorId") Long actorId);

    @Select("SELECT COALESCE(SUM(good_qty+scrap_qty),0) FROM mes_report WHERE work_order_id=#{id} AND report_status='VALID'")
    long sumValidReportedQty(@Param("id") Long workOrderId);

    class WorkOrderRecord {
        private Long id;
        private String workOrderNo;
        private Long planId;
        private Long plannedQty;
        private LocalDate startDate;
        private LocalDate endDate;
        private String remark;
        private Long createdBy;
        public Long getId() { return id; }
        public void setId(Long value) { id = value; }
        public String getWorkOrderNo() { return workOrderNo; }
        public void setWorkOrderNo(String value) { workOrderNo = value; }
        public Long getPlanId() { return planId; }
        public void setPlanId(Long value) { planId = value; }
        public Long getPlannedQty() { return plannedQty; }
        public void setPlannedQty(Long value) { plannedQty = value; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate value) { startDate = value; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate value) { endDate = value; }
        public String getRemark() { return remark; }
        public void setRemark(String value) { remark = value; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long value) { createdBy = value; }
    }

    record WorkOrderView(Long id, String workOrderNo, Long planId, String planNo, Long productionOrderId,
            String orderNo, Long productId, String productCode, String productName, String specification, String unit,
            Integer sequenceNo, Long plannedQty, LocalDate startDate, LocalDate endDate, String status, String remark,
            Long createdBy, String createdByName, LocalDateTime createdAt, Long updatedBy, LocalDateTime updatedAt,
            Long reportCount, Long goodQty, Long scrapQty, Long completedQty) { }
    record AvailablePlan(Long planId, String planNo, Long productionOrderId, String orderNo, Long orderQty,
            String orderStatus, Long planQty, LocalDate startDate, LocalDate endDate, String planStatus,
            String productCode, String productName, String unit) { }
    record ReportView(Long id, String reportNo, Long workOrderId, LocalDate reportDate, Long goodQty,
            Long scrapQty, String reportStatus, String remark, LocalDateTime createdAt, String reporterName,
            LocalDateTime voidedAt, String voidReason) { }
    record LockedOrder(Long id, Long orderQty, String status) { }
    record LockedPlan(Long id, Long productionOrderId, Long planQty, LocalDate startDate, LocalDate endDate, String status) { }
    record LockedWorkOrder(Long id, String workOrderNo, Long planId, Long plannedQty, String status, LocalDateTime updatedAt) { }
}
