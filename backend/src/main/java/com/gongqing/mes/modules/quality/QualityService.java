package com.gongqing.mes.modules.quality;

import com.gongqing.mes.common.api.*;
import com.gongqing.mes.common.exception.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service public class QualityService {
 private final QualityMapper mapper; private final QualityAuditMapper audit;
 public QualityService(QualityMapper mapper,QualityAuditMapper audit){this.mapper=mapper;this.audit=audit;}
 @Transactional public QualityMapper.InspectionView create(CreateRequest req,Long actor){
  final long inspectedTotal;
  try { inspectedTotal=Math.addExact(req.goodQty(),req.unqualifiedQty()); }
  catch(ArithmeticException ex){ throw bad("检验数量超出允许范围"); }
  if(inspectedTotal!=req.inspectionQty()) throw bad("检验数量必须等于合格数与不合格数之和");
  if(req.unqualifiedQty()>0&&!StringUtils.hasText(req.unqualifiedReason())) throw bad("存在不合格品时必须填写不合格原因");
  var wo=mapper.lockWorkOrder(req.workOrderId()); if(wo==null) throw missing("生产工单不存在");
  if(!Set.of("IN_PROGRESS","COMPLETED").contains(wo.status())) throw conflict("工单当前状态不允许质检");
  long totalGood=mapper.reportedGood(req.workOrderId());
  long totalInspected=mapper.inspectedWorkOrder(req.workOrderId());
  if(req.inspectionQty()>totalGood-totalInspected) throw conflict("累计检验数量不能超过工单有效报工合格数量");
  if(req.reportId()!=null){
   var report=mapper.report(req.reportId());
   if(report==null||!report.workOrderId().equals(req.workOrderId())) throw bad("报工记录不属于所选工单");
   if(!"VALID".equals(report.reportStatus())) throw conflict("已作废的报工记录不能质检");
   if(mapper.inspectedReport(req.reportId())+req.inspectionQty()>report.goodQty()) throw conflict("累计检验数量不能超过该报工的合格数量");
  }
  var row=new QualityMapper.Inspection();
  row.inspectionNo="QI"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase();
  row.workOrderId=req.workOrderId();row.reportId=req.reportId();row.inspectionDate=req.inspectionDate();row.inspectionQty=req.inspectionQty();row.goodQty=req.goodQty();row.unqualifiedQty=req.unqualifiedQty();row.unqualifiedReason=StringUtils.hasText(req.unqualifiedReason())?req.unqualifiedReason().trim():null;row.dispositionStatus=req.unqualifiedQty()==0?"NOT_REQUIRED":"PENDING";row.inspectedBy=actor;row.remark=StringUtils.hasText(req.remark())?req.remark().trim():null;
  try{mapper.insert(row);}catch(DuplicateKeyException e){throw conflict("质检编号冲突，请重试");}
  audit.insert(actor,"QUALITY_INSPECTION_CREATE",row.id,"创建质检记录 "+row.inspectionNo);
  return mapper.detail(row.id);
 }
 public PageResult<QualityMapper.InspectionView> page(PageQuery page,Long workOrderId,Long reportId,Long productId,String status,LocalDate from,LocalDate to){
  if(from!=null&&to!=null&&to.isBefore(from))throw bad("结束日期不能早于开始日期");
  if(status!=null&&!Set.of("NOT_REQUIRED","PENDING","ACCEPTED","REWORK","SCRAPPED").contains(status))throw bad("处置状态不合法");
  long offset;try{offset=Math.multiplyExact(page.getPage()-1,page.getSize());}catch(ArithmeticException ex){throw bad("分页参数超出范围");}var rows=mapper.page(workOrderId,reportId,productId,status,from,to,page.getSize(),offset);
  return new PageResult<>(rows,page.getPage(),page.getSize(),mapper.count(workOrderId,reportId,productId,status,from,to));
 }
 public QualityMapper.InspectionView detail(Long id){var row=mapper.detail(id);if(row==null)throw missing("质检记录不存在");return row;}
 public Object options(){return Map.of("workOrders",mapper.workOrders());}
 public Object reports(Long workOrderId){return mapper.reports(workOrderId);}
 @Transactional public void dispose(Long id,DisposeRequest req,Long actor){var row=mapper.lockInspection(id);if(row==null)throw missing("质检记录不存在");if(row.unqualifiedQty()==0)throw conflict("该检验记录没有不合格品");if(!"PENDING".equals(row.dispositionStatus()))throw conflict("该记录已完成处置");if(mapper.dispose(id,req.status(),req.note().trim(),actor)!=1)throw conflict("处置状态已被其他请求修改");audit.insert(actor,"QUALITY_DISPOSITION",id,"处置 "+row.inspectionNo()+" 为 "+req.status()+"："+req.note().trim());}
 private BusinessException bad(String m){return new BusinessException(ErrorCode.BAD_REQUEST,m);} private BusinessException conflict(String m){return new BusinessException(ErrorCode.BUSINESS_CONFLICT,m);}private BusinessException missing(String m){return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,m);}
 public record CreateRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long workOrderId,@jakarta.validation.constraints.Positive Long reportId,@jakarta.validation.constraints.NotNull LocalDate inspectionDate,@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long inspectionQty,@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long goodQty,@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long unqualifiedQty,@jakarta.validation.constraints.Size(max=500) String unqualifiedReason,@jakarta.validation.constraints.Size(max=500) String remark){}
 public record DisposeRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Pattern(regexp="ACCEPTED|REWORK|SCRAPPED") String status,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=500) String note){}
}
