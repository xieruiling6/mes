package com.gongqing.mes.modules.plan;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductionPlanMapper {
    String VIEW_COLUMNS = "pl.id, pl.plan_no AS planNo, pl.production_order_id AS productionOrderId, " +
            "o.order_no AS orderNo, o.order_qty AS orderQty, o.due_date AS dueDate, p.id AS productId, " +
            "p.product_code AS productCode, p.product_name AS productName, p.specification, p.unit, " +
            "pl.plan_qty AS planQty, pl.start_date AS startDate, pl.end_date AS endDate, pl.priority, pl.status, " +
            "pl.remark, pl.created_by AS createdBy, u.display_name AS createdByName, pl.created_at AS createdAt, " +
            "pl.updated_by AS updatedBy, pl.updated_at AS updatedAt, pl.released_by AS releasedBy, " +
            "pl.released_at AS releasedAt, COALESCE(w.workOrderCount,0) AS workOrderCount, " +
            "COALESCE(w.completedWorkOrderCount,0) AS completedWorkOrderCount, " +
            "w.workOrderId AS workOrderId, w.workOrderNo AS workOrderNo, w.workOrderStatus AS workOrderStatus, " +
            "COALESCE(w.workOrderPlannedQty,0) AS workOrderPlannedQty, " +
            "COALESCE(r.goodQty,0) AS goodQty, COALESCE(r.scrapQty,0) AS scrapQty, " +
            "COALESCE(r.completedQty,0) AS completedQty";
    String JOINS = " FROM mes_production_plan pl JOIN mes_production_order o ON o.id=pl.production_order_id " +
            "JOIN mes_product p ON p.id=o.product_id JOIN sys_user u ON u.id=pl.created_by " +
            "LEFT JOIN (SELECT plan_id, COUNT(*) workOrderCount, " +
            "SUM(status='COMPLETED') completedWorkOrderCount, MAX(id) workOrderId, MAX(work_order_no) workOrderNo, " +
            "MAX(status) workOrderStatus, MAX(planned_qty) workOrderPlannedQty FROM mes_work_order GROUP BY plan_id) w ON w.plan_id=pl.id " +
            "LEFT JOIN (SELECT wo.plan_id, SUM(r.good_qty) goodQty, SUM(r.scrap_qty) scrapQty, " +
            "SUM(r.good_qty+r.scrap_qty) completedQty FROM mes_report r JOIN mes_work_order wo ON wo.id=r.work_order_id " +
            "WHERE r.report_status='VALID' GROUP BY wo.plan_id) r ON r.plan_id=pl.id ";

    @Select("<script>SELECT " + VIEW_COLUMNS + JOINS + "WHERE 1=1 " +
            "<if test='planNo != null'>AND pl.plan_no LIKE CONCAT('%',#{planNo},'%') </if>" +
            "<if test='orderNo != null'>AND o.order_no LIKE CONCAT('%',#{orderNo},'%') </if>" +
            "<if test='productId != null'>AND o.product_id=#{productId} </if>" +
            "<if test='status != null'>AND pl.status=#{status} </if>" +
            "<if test='priority != null'>AND pl.priority=#{priority} </if>" +
            "<if test='startDateFrom != null'>AND pl.start_date &gt;=#{startDateFrom} </if>" +
            "<if test='startDateTo != null'>AND pl.start_date &lt;=#{startDateTo} </if>" +
            "<if test='endDateFrom != null'>AND pl.end_date &gt;=#{endDateFrom} </if>" +
            "<if test='endDateTo != null'>AND pl.end_date &lt;=#{endDateTo} </if>" +
            "ORDER BY pl.priority DESC, pl.start_date ASC, pl.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<PlanView> findPage(@Param("planNo") String planNo, @Param("orderNo") String orderNo,
            @Param("productId") Long productId, @Param("status") String status, @Param("priority") Integer priority,
            @Param("startDateFrom") LocalDate startDateFrom, @Param("startDateTo") LocalDate startDateTo,
            @Param("endDateFrom") LocalDate endDateFrom, @Param("endDateTo") LocalDate endDateTo,
            @Param("limit") long limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM mes_production_plan pl JOIN mes_production_order o ON o.id=pl.production_order_id WHERE 1=1 " +
            "<if test='planNo != null'>AND pl.plan_no LIKE CONCAT('%',#{planNo},'%') </if>" +
            "<if test='orderNo != null'>AND o.order_no LIKE CONCAT('%',#{orderNo},'%') </if>" +
            "<if test='productId != null'>AND o.product_id=#{productId} </if>" +
            "<if test='status != null'>AND pl.status=#{status} </if>" +
            "<if test='priority != null'>AND pl.priority=#{priority} </if>" +
            "<if test='startDateFrom != null'>AND pl.start_date &gt;=#{startDateFrom} </if>" +
            "<if test='startDateTo != null'>AND pl.start_date &lt;=#{startDateTo} </if>" +
            "<if test='endDateFrom != null'>AND pl.end_date &gt;=#{endDateFrom} </if>" +
            "<if test='endDateTo != null'>AND pl.end_date &lt;=#{endDateTo} </if></script>")
    long countPage(@Param("planNo") String planNo, @Param("orderNo") String orderNo,
            @Param("productId") Long productId, @Param("status") String status, @Param("priority") Integer priority,
            @Param("startDateFrom") LocalDate startDateFrom, @Param("startDateTo") LocalDate startDateTo,
            @Param("endDateFrom") LocalDate endDateFrom, @Param("endDateTo") LocalDate endDateTo);

    @Select("SELECT o.id AS orderId, o.order_no AS orderNo, o.product_id AS productId, p.product_code AS productCode, " +
            "p.product_name AS productName, p.specification, p.unit, o.order_qty AS orderQty, o.due_date AS dueDate, " +
            "o.status, o.created_at AS createdAt FROM mes_production_order o JOIN mes_product p ON p.id=o.product_id " +
            "WHERE o.status='CONFIRMED' AND NOT EXISTS(SELECT 1 FROM mes_production_plan pl WHERE pl.production_order_id=o.id) " +
            "ORDER BY o.due_date IS NULL, o.due_date, o.id")
    List<AvailableOrder> findAvailableOrders();

    @Select("SELECT " + VIEW_COLUMNS + JOINS + "WHERE pl.id=#{id}")
    PlanView findById(@Param("id") Long id);

    @Select("SELECT id, order_no AS orderNo, order_qty AS orderQty, due_date AS dueDate, status FROM mes_production_order WHERE id=#{id} FOR UPDATE")
    LockedOrder lockOrder(@Param("id") Long id);

    @Select("SELECT production_order_id FROM mes_production_plan WHERE id=#{id}")
    Long findOrderIdByPlan(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM mes_production_plan WHERE production_order_id=#{orderId}")
    long countPlansForOrder(@Param("orderId") Long orderId);

    @Select("SELECT id, production_order_id AS productionOrderId, status, updated_at AS updatedAt FROM mes_production_plan WHERE id=#{id} FOR UPDATE")
    LockedPlan lockPlan(@Param("id") Long id);

    @Insert("INSERT INTO mes_production_plan(plan_no,production_order_id,plan_qty,start_date,end_date,priority,status,remark,created_by) " +
            "VALUES(#{planNo},#{productionOrderId},#{planQty},#{startDate},#{endDate},#{priority},'DRAFT',#{remark},#{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PlanRecord record);

    @Update("UPDATE mes_production_plan SET plan_qty=#{planQty},start_date=#{startDate},end_date=#{endDate},priority=#{priority},remark=#{remark},updated_by=#{updatedBy} " +
            "WHERE id=#{id} AND status='DRAFT' AND updated_at=#{expectedUpdatedAt}")
    int updateDraft(PlanRecord record);

    @Update("UPDATE mes_production_plan SET status='RELEASED',released_by=#{actorId},released_at=CURRENT_TIMESTAMP(3),updated_by=#{actorId} " +
            "WHERE id=#{id} AND status='DRAFT' AND updated_at=#{expectedUpdatedAt}")
    int release(@Param("id") Long id, @Param("actorId") Long actorId, @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt);

    @Update("UPDATE mes_production_plan SET status='CANCELLED',updated_by=#{actorId} " +
            "WHERE id=#{id} AND status IN ('DRAFT','RELEASED') AND updated_at=#{expectedUpdatedAt}")
    int cancel(@Param("id") Long id, @Param("actorId") Long actorId, @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt);

    @Select("SELECT COUNT(*) FROM mes_work_order WHERE plan_id=#{planId} AND status NOT IN ('PENDING','CANCELLED')")
    long countStartedWorkOrders(@Param("planId") Long planId);

    @Select("SELECT COUNT(*) FROM mes_report r JOIN mes_work_order w ON w.id=r.work_order_id WHERE w.plan_id=#{planId} AND r.report_status='VALID'")
    long countValidReports(@Param("planId") Long planId);

    @Update("UPDATE mes_work_order SET status='CANCELLED',updated_by=#{actorId} WHERE plan_id=#{planId} AND status='PENDING'")
    int cancelPendingWorkOrders(@Param("planId") Long planId, @Param("actorId") Long actorId);

    class PlanRecord {
        private Long id;
        private String planNo;
        private Long productionOrderId;
        private Long planQty;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer priority;
        private String remark;
        private Long createdBy;
        private Long updatedBy;
        private LocalDateTime expectedUpdatedAt;
        public Long getId() { return id; }
        public void setId(Long value) { id = value; }
        public String getPlanNo() { return planNo; }
        public void setPlanNo(String value) { planNo = value; }
        public Long getProductionOrderId() { return productionOrderId; }
        public void setProductionOrderId(Long value) { productionOrderId = value; }
        public Long getPlanQty() { return planQty; }
        public void setPlanQty(Long value) { planQty = value; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate value) { startDate = value; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate value) { endDate = value; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer value) { priority = value; }
        public String getRemark() { return remark; }
        public void setRemark(String value) { remark = value; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long value) { createdBy = value; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long value) { updatedBy = value; }
        public LocalDateTime getExpectedUpdatedAt() { return expectedUpdatedAt; }
        public void setExpectedUpdatedAt(LocalDateTime value) { expectedUpdatedAt = value; }
    }

    record PlanView(Long id, String planNo, Long productionOrderId, String orderNo, Long orderQty, LocalDate dueDate,
            Long productId, String productCode, String productName, String specification, String unit, Long planQty,
            LocalDate startDate, LocalDate endDate, Integer priority, String status, String remark, Long createdBy,
            String createdByName, LocalDateTime createdAt, Long updatedBy, LocalDateTime updatedAt, Long releasedBy,
            LocalDateTime releasedAt, Long workOrderCount, Long completedWorkOrderCount, Long workOrderId,
            String workOrderNo, String workOrderStatus, Long workOrderPlannedQty, Long goodQty, Long scrapQty,
            Long completedQty) { }
    record AvailableOrder(Long orderId, String orderNo, Long productId, String productCode, String productName,
            String specification, String unit, Long orderQty, LocalDate dueDate, String status, LocalDateTime createdAt) { }
    record LockedOrder(Long id, String orderNo, Long orderQty, LocalDate dueDate, String status) { }
    record LockedPlan(Long id, Long productionOrderId, String status, LocalDateTime updatedAt) { }
}
