package com.gongqing.mes.modules.quality;

import com.gongqing.mes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QualityServiceTest {
 private final QualityMapper mapper=mock(QualityMapper.class);
 private final QualityAuditMapper audit=mock(QualityAuditMapper.class);
 private final QualityService service=new QualityService(mapper,audit);
 private QualityService.CreateRequest request(Long reportId,long qty,long good,long bad,String reason){return new QualityService.CreateRequest(9L,reportId,LocalDate.parse("2026-10-22"),qty,good,bad,reason,null);}

 @Test void rejectsQuantityMismatchAndMissingDefectReason(){
  assertThatThrownBy(()->service.create(request(null,10,8,1,"reason"),7L)).isInstanceOf(BusinessException.class).hasMessageContaining("之和");
  assertThatThrownBy(()->service.create(request(null,10,8,2," "),7L)).isInstanceOf(BusinessException.class).hasMessageContaining("不合格原因");
  verifyNoInteractions(mapper,audit);
 }

 @Test void locksWorkOrderAndRejectsInspectionAboveValidReportedGoodQty(){
  when(mapper.lockWorkOrder(9L)).thenReturn(new QualityMapper.WorkOrder(9L,"IN_PROGRESS"));
  when(mapper.inspectedWorkOrder(9L)).thenReturn(4L);
  when(mapper.reportedGood(9L)).thenReturn(10L);
  assertThatThrownBy(()->service.create(request(null,7,6,1,"defect"),7L))
      .isInstanceOf(BusinessException.class).hasMessageContaining("不能超过");
  verify(mapper).lockWorkOrder(9L);verify(mapper,never()).insert(any());
  verifyNoInteractions(audit);
 }

 @Test void rejectsWrongOrVoidedReportBeforeWriting(){
  when(mapper.lockWorkOrder(9L)).thenReturn(new QualityMapper.WorkOrder(9L,"IN_PROGRESS"));
  when(mapper.reportedGood(9L)).thenReturn(20L);
  when(mapper.inspectedWorkOrder(9L)).thenReturn(0L);
  when(mapper.report(20L)).thenReturn(new QualityMapper.Report(8L,"VALID",10L));
  assertThatThrownBy(()->service.create(request(20L,5,4,1,"defect"),7L)).isInstanceOf(BusinessException.class).hasMessageContaining("不属于");
  when(mapper.report(20L)).thenReturn(new QualityMapper.Report(9L,"VOID",10L));
  assertThatThrownBy(()->service.create(request(20L,5,4,1,"defect"),7L)).isInstanceOf(BusinessException.class).hasMessageContaining("作废");
  verify(mapper,never()).insert(any());
 }

 @Test void createsRecordAndRequiresPendingDefectForDisposition(){
  when(mapper.lockWorkOrder(9L)).thenReturn(new QualityMapper.WorkOrder(9L,"COMPLETED"));
  when(mapper.reportedGood(9L)).thenReturn(20L);
  when(mapper.inspectedWorkOrder(9L)).thenReturn(0L);
  when(mapper.report(20L)).thenReturn(new QualityMapper.Report(9L,"VALID",20L));
  when(mapper.inspectedReport(20L)).thenReturn(0L);
  when(mapper.insert(any())).thenAnswer(inv->{QualityMapper.Inspection i=inv.getArgument(0);i.id=12L;return 1;});
  var view=mock(QualityMapper.InspectionView.class);when(mapper.detail(12L)).thenReturn(view);
  assertThat(service.create(request(20L,5,4,1,"crack"),7L)).isSameAs(view);
  verify(audit).insert(eq(7L),eq("QUALITY_INSPECTION_CREATE"),eq(12L),org.mockito.ArgumentMatchers.contains("QI"));
  when(mapper.lockInspection(12L)).thenReturn(new QualityMapper.DispositionRow(12L,1L,"PENDING","QI-12"));
  when(mapper.dispose(12L,"REWORK","repair",7L)).thenReturn(1);
  service.dispose(12L,new QualityService.DisposeRequest("REWORK"," repair "),7L);
  verify(audit).insert(7L,"QUALITY_DISPOSITION",12L,"处置 QI-12 为 REWORK：repair");
 }
}
