package com.gongqing.mes.modules.quality;

import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface QualityMapper {
    @Select("SELECT id,status FROM mes_work_order WHERE id=#{id} FOR UPDATE")
    WorkOrder lockWorkOrder(Long id);
    @Select("SELECT work_order_id AS workOrderId,report_status AS reportStatus,good_qty AS goodQty FROM mes_report WHERE id=#{id}")
    Report report(Long id);
    @Select("SELECT COALESCE(SUM(good_qty),0) FROM mes_report WHERE work_order_id=#{id} AND report_status='VALID'")
    long reportedGood(Long id);
    @Select("SELECT COALESCE(SUM(inspection_qty),0) FROM mes_quality_inspection WHERE work_order_id=#{id}")
    long inspectedWorkOrder(Long id);
    @Select("SELECT COALESCE(SUM(inspection_qty),0) FROM mes_quality_inspection WHERE report_id=#{id}")
    long inspectedReport(Long id);
    @Insert("INSERT INTO mes_quality_inspection(inspection_no,work_order_id,report_id,inspection_date,inspection_qty,good_qty,unqualified_qty,unqualified_reason,disposition_status,inspected_by,remark) VALUES(#{inspectionNo},#{workOrderId},#{reportId},#{inspectionDate},#{inspectionQty},#{goodQty},#{unqualifiedQty},#{unqualifiedReason},#{dispositionStatus},#{inspectedBy},#{remark})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Inspection row);
    String COLUMNS = "q.id,q.inspection_no AS inspectionNo,q.work_order_id AS workOrderId,wo.work_order_no AS workOrderNo,q.report_id AS reportId,r.report_no AS reportNo,p.id AS productId,p.product_code AS productCode,p.product_name AS productName,q.inspection_date AS inspectionDate,q.inspection_qty AS inspectionQty,q.good_qty AS goodQty,q.unqualified_qty AS unqualifiedQty,q.unqualified_reason AS unqualifiedReason,q.disposition_status AS dispositionStatus,q.disposition_note AS dispositionNote,q.inspected_by AS inspectedBy,iu.display_name AS inspectorName,q.disposed_by AS disposedBy,du.display_name AS disposerName,q.disposed_at AS disposedAt,q.remark,q.created_at AS createdAt";
    String JOIN = " FROM mes_quality_inspection q JOIN mes_work_order wo ON wo.id=q.work_order_id JOIN mes_production_plan pl ON pl.id=wo.plan_id JOIN mes_production_order o ON o.id=pl.production_order_id JOIN mes_product p ON p.id=o.product_id LEFT JOIN mes_report r ON r.id=q.report_id JOIN sys_user iu ON iu.id=q.inspected_by LEFT JOIN sys_user du ON du.id=q.disposed_by ";
    @Select("SELECT "+COLUMNS+JOIN+" WHERE q.id=#{id}")
    InspectionView detail(Long id);
    @Select("<script>SELECT "+COLUMNS+JOIN+" WHERE 1=1 <if test='workOrderId!=null'>AND q.work_order_id=#{workOrderId} </if><if test='reportId!=null'>AND q.report_id=#{reportId} </if><if test='productId!=null'>AND p.id=#{productId} </if><if test='status!=null'>AND q.disposition_status=#{status} </if><if test='from!=null'>AND q.inspection_date&gt;=#{from} </if><if test='to!=null'>AND q.inspection_date&lt;=#{to} </if> ORDER BY q.inspection_date DESC,q.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<InspectionView> page(@Param("workOrderId") Long workOrderId,@Param("reportId") Long reportId,@Param("productId") Long productId,@Param("status") String status,@Param("from") LocalDate from,@Param("to") LocalDate to,@Param("limit") long limit,@Param("offset") long offset);
    @Select("<script>SELECT COUNT(*) "+JOIN+" WHERE 1=1 <if test='workOrderId!=null'>AND q.work_order_id=#{workOrderId} </if><if test='reportId!=null'>AND q.report_id=#{reportId} </if><if test='productId!=null'>AND p.id=#{productId} </if><if test='status!=null'>AND q.disposition_status=#{status} </if><if test='from!=null'>AND q.inspection_date&gt;=#{from} </if><if test='to!=null'>AND q.inspection_date&lt;=#{to} </if></script>")
    long count(@Param("workOrderId") Long workOrderId,@Param("reportId") Long reportId,@Param("productId") Long productId,@Param("status") String status,@Param("from") LocalDate from,@Param("to") LocalDate to);
    @Select("SELECT id,unqualified_qty AS unqualifiedQty,disposition_status AS dispositionStatus,inspection_no AS inspectionNo FROM mes_quality_inspection WHERE id=#{id} FOR UPDATE")
    DispositionRow lockInspection(Long id);
    @Update("UPDATE mes_quality_inspection SET disposition_status=#{status},disposition_note=#{note},disposed_by=#{actor},disposed_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND disposition_status='PENDING'")
    int dispose(@Param("id") Long id,@Param("status") String status,@Param("note") String note,@Param("actor") Long actor);
    @Select("SELECT id,work_order_no AS workOrderNo FROM mes_work_order WHERE status IN ('IN_PROGRESS','COMPLETED') ORDER BY id DESC LIMIT 500")
    List<WorkOrderOption> workOrders();
    @Select("SELECT id,report_no AS reportNo,good_qty AS goodQty FROM mes_report WHERE work_order_id=#{id} AND report_status='VALID' ORDER BY id DESC")
    List<ReportOption> reports(Long id);
    record WorkOrder(Long id,String status){}
    record Report(Long workOrderId,String reportStatus,Long goodQty){}
    record DispositionRow(Long id,Long unqualifiedQty,String dispositionStatus,String inspectionNo){}
    record WorkOrderOption(Long id,String workOrderNo){}
    record ReportOption(Long id,String reportNo,Long goodQty){}
    class Inspection { public Long id; public String inspectionNo; public Long workOrderId; public Long reportId; public LocalDate inspectionDate; public Long inspectionQty; public Long goodQty; public Long unqualifiedQty; public String unqualifiedReason; public String dispositionStatus; public Long inspectedBy; public String remark; }
    record InspectionView(Long id,String inspectionNo,Long workOrderId,String workOrderNo,Long reportId,String reportNo,Long productId,String productCode,String productName,LocalDate inspectionDate,Long inspectionQty,Long goodQty,Long unqualifiedQty,String unqualifiedReason,String dispositionStatus,String dispositionNote,Long inspectedBy,String inspectorName,Long disposedBy,String disposerName,java.time.LocalDateTime disposedAt,String remark,java.time.LocalDateTime createdAt){}
}
