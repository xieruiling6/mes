package com.gongqing.mes.modules.order;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductionOrderMapper {
    String COLUMNS = "o.id, o.order_no AS orderNo, o.product_id AS productId, p.product_code AS productCode, " +
            "p.product_name AS productName, p.specification, p.unit, o.order_qty AS orderQty, o.due_date AS dueDate, " +
            "o.status, o.remark, o.created_by AS createdBy, u.display_name AS createdByName, " +
            "o.created_at AS createdAt, o.updated_by AS updatedBy, o.updated_at AS updatedAt, " +
            "EXISTS(SELECT 1 FROM mes_production_plan pl WHERE pl.production_order_id=o.id) AS hasPlan";

    @Select("<script>SELECT " + COLUMNS + " FROM mes_production_order o " +
            "JOIN mes_product p ON p.id=o.product_id JOIN sys_user u ON u.id=o.created_by " +
            "WHERE 1=1 " +
            "<if test='orderNo != null'>AND o.order_no LIKE CONCAT('%',#{orderNo},'%') </if>" +
            "<if test='productId != null'>AND o.product_id=#{productId} </if>" +
            "<if test='status != null'>AND o.status=#{status} </if>" +
            "<if test='dueDateFrom != null'>AND o.due_date &gt;=#{dueDateFrom} </if>" +
            "<if test='dueDateTo != null'>AND o.due_date &lt;=#{dueDateTo} </if>" +
            "ORDER BY o.created_at DESC, o.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<OrderView> findPage(@Param("orderNo") String orderNo, @Param("productId") Long productId,
                             @Param("status") String status, @Param("dueDateFrom") LocalDate dueDateFrom,
                             @Param("dueDateTo") LocalDate dueDateTo, @Param("limit") long limit,
                             @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM mes_production_order o WHERE 1=1 " +
            "<if test='orderNo != null'>AND o.order_no LIKE CONCAT('%',#{orderNo},'%') </if>" +
            "<if test='productId != null'>AND o.product_id=#{productId} </if>" +
            "<if test='status != null'>AND o.status=#{status} </if>" +
            "<if test='dueDateFrom != null'>AND o.due_date &gt;=#{dueDateFrom} </if>" +
            "<if test='dueDateTo != null'>AND o.due_date &lt;=#{dueDateTo} </if></script>")
    long count(@Param("orderNo") String orderNo, @Param("productId") Long productId,
               @Param("status") String status, @Param("dueDateFrom") LocalDate dueDateFrom,
               @Param("dueDateTo") LocalDate dueDateTo);

    @Select("SELECT " + COLUMNS + " FROM mes_production_order o " +
            "JOIN mes_product p ON p.id=o.product_id JOIN sys_user u ON u.id=o.created_by WHERE o.id=#{id}")
    OrderView findById(@Param("id") Long id);

    @Select("SELECT o.id AS orderId, pl.id AS planId, pl.plan_no AS planNo, pl.status AS planStatus, " +
            "pl.plan_qty AS planQty, pl.start_date AS startDate, pl.end_date AS endDate, wo.id AS workOrderId, " +
            "wo.work_order_no AS workOrderNo, wo.status AS workOrderStatus, wo.planned_qty AS plannedQty, " +
            "COALESCE(r.reportCount,0) AS reportCount, COALESCE(r.goodQty,0) AS goodQty, " +
            "COALESCE(r.scrapQty,0) AS scrapQty, COALESCE(r.completedQty,0) AS completedQty " +
            "FROM mes_production_order o " +
            "LEFT JOIN mes_production_plan pl ON pl.production_order_id=o.id " +
            "LEFT JOIN mes_work_order wo ON wo.plan_id=pl.id " +
            "LEFT JOIN (SELECT work_order_id, COUNT(*) reportCount, SUM(good_qty) goodQty, " +
            "SUM(scrap_qty) scrapQty, SUM(good_qty+scrap_qty) completedQty FROM mes_report " +
            "WHERE report_status='VALID' GROUP BY work_order_id) r ON r.work_order_id=wo.id " +
            "WHERE o.id=#{id}")
    OrderExecutionView findExecution(@Param("id") Long id);

    @Select("SELECT id, product_id AS productId, due_date AS dueDate, status, updated_at AS updatedAt FROM mes_production_order WHERE id=#{id} FOR UPDATE")
    LockedOrder findForUpdate(@Param("id") Long id);

    @Select("SELECT id FROM mes_product WHERE id=#{id} AND status='ACTIVE' AND is_deleted=0 FOR UPDATE")
    Long findActiveProduct(@Param("id") Long id);

    @Insert("INSERT INTO mes_production_order(order_no,product_id,order_qty,due_date,status,remark,created_by) " +
            "VALUES(#{orderNo},#{productId},#{orderQty},#{dueDate},'DRAFT',#{remark},#{createdBy})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderRecord order);

    @Update("UPDATE mes_production_order SET order_no=#{orderNo},product_id=#{productId},order_qty=#{orderQty}," +
            "due_date=#{dueDate},remark=#{remark},updated_by=#{updatedBy} " +
            "WHERE id=#{id} AND updated_at=#{expectedUpdatedAt} AND status IN ('DRAFT','CONFIRMED') " +
            "AND NOT EXISTS (SELECT 1 FROM mes_production_plan pl WHERE pl.production_order_id=#{id})")
    int updateEditable(OrderRecord order);

    @Update("UPDATE mes_production_order SET status=#{status},updated_by=#{updatedBy} " +
            "WHERE id=#{id} AND updated_at=#{expectedUpdatedAt}")
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("updatedBy") Long updatedBy,
                     @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt);

    @Select("SELECT COUNT(*) FROM mes_production_plan WHERE production_order_id=#{id} " +
            "AND status IN ('RELEASED','IN_PROGRESS','COMPLETED')")
    long countExecutionPlans(@Param("id") Long id);

    @Update("UPDATE mes_work_order wo JOIN mes_production_plan pl ON pl.id=wo.plan_id " +
            "SET wo.status='CANCELLED', wo.updated_by=#{actorId} WHERE pl.production_order_id=#{id} " +
            "AND pl.status='DRAFT' AND wo.status='PENDING'")
    int cancelPendingWorkOrders(@Param("id") Long id, @Param("actorId") Long actorId);

    @Update("UPDATE mes_production_plan SET status='CANCELLED', updated_by=#{actorId} " +
            "WHERE production_order_id=#{id} AND status='DRAFT'")
    int cancelDraftPlans(@Param("id") Long id, @Param("actorId") Long actorId);

    @Select("SELECT COUNT(*) FROM mes_report r JOIN mes_work_order w ON w.id=r.work_order_id " +
            "JOIN mes_production_plan p ON p.id=w.plan_id " +
            "WHERE p.production_order_id=#{id} AND r.report_status='VALID'")
    long countValidReports(@Param("id") Long id);

    class OrderRecord {
        private Long id;
        private String orderNo;
        private Long productId;
        private Long orderQty;
        private LocalDate dueDate;
        private String remark;
        private Long createdBy;
        private Long updatedBy;
        private LocalDateTime expectedUpdatedAt;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String value) { orderNo = value; }
        public Long getProductId() { return productId; }
        public void setProductId(Long value) { productId = value; }
        public Long getOrderQty() { return orderQty; }
        public void setOrderQty(Long value) { orderQty = value; }
        public LocalDate getDueDate() { return dueDate; }
        public void setDueDate(LocalDate value) { dueDate = value; }
        public String getRemark() { return remark; }
        public void setRemark(String value) { remark = value; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long value) { createdBy = value; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long value) { updatedBy = value; }
        public LocalDateTime getExpectedUpdatedAt() { return expectedUpdatedAt; }
        public void setExpectedUpdatedAt(LocalDateTime value) { expectedUpdatedAt = value; }
    }

    record OrderView(Long id, String orderNo, Long productId, String productCode, String productName,
                     String specification, String unit, Long orderQty, LocalDate dueDate, String status,
                     String remark, Long createdBy, String createdByName, LocalDateTime createdAt,
                     Long updatedBy, LocalDateTime updatedAt, Boolean hasPlan) { }
    record LockedOrder(Long id, Long productId, LocalDate dueDate, String status, LocalDateTime updatedAt) { }
    record OrderExecutionView(Long orderId, Long planId, String planNo, String planStatus, Long planQty,
                              LocalDate startDate, LocalDate endDate, Long workOrderId, String workOrderNo,
                              String workOrderStatus, Long plannedQty, Long reportCount, Long goodQty,
                              Long scrapQty, Long completedQty) { }
}
