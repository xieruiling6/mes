package com.gongqing.mes.modules.equipment;
import com.gongqing.mes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EquipmentServiceTest {
 private final EquipmentMapper mapper=mock(EquipmentMapper.class);
 private final EquipmentAuditMapper audit=mock(EquipmentAuditMapper.class);
 private final EquipmentService service=new EquipmentService(mapper,audit);
 @Test void rejectsManualMaintenanceStateAndEditingDuringRepair(){
  when(mapper.lock(1L)).thenReturn(new EquipmentMapper.LockedEquipment(1L,"IDLE"));
  assertThatThrownBy(()->service.changeStatus(1L,new EquipmentService.StatusRequest("MAINTENANCE"),4L)).isInstanceOf(BusinessException.class);
  when(mapper.lock(1L)).thenReturn(new EquipmentMapper.LockedEquipment(1L,"MAINTENANCE"));
  var req=new EquipmentService.EquipmentRequest("EQ-1","Lathe",null,null,null,java.time.LocalDateTime.now());
  assertThatThrownBy(()->service.update(1L,req,4L)).isInstanceOf(BusinessException.class).hasMessageContaining("维修中的设备");
  verify(mapper,never()).status(any(),anyString(),any());
  verify(mapper,never()).update(any(),anyString(),anyString(),any(),any(),any(),any(),any());
 }
 @Test void createUsesServerActorAndInitialIdleStatus(){
  when(mapper.insert(any())).thenAnswer(invocation -> {
   EquipmentMapper.Equipment row=invocation.getArgument(0);
   row.id=51L;
   org.assertj.core.api.Assertions.assertThat(row.createdBy).isEqualTo(4L);
   return 1;
  });
  Long id=service.create(new EquipmentService.EquipmentRequest(" EQ-9 "," 车床 ","C6140"," 一车间 ",null,null),4L);
  org.assertj.core.api.Assertions.assertThat(id).isEqualTo(51L);
  verify(mapper).insert(argThat(row->"EQ-9".equals(row.equipmentCode)&&"车床".equals(row.equipmentName)));
  verify(audit).insert(4L,"EQUIPMENT_CREATE",51L,"新增设备 EQ-9");
 }
 @Test void repairStartAndCompletionAreAuditedAndKeepEquipmentStatusInSync(){
  when(mapper.lock(8L)).thenReturn(new EquipmentMapper.LockedEquipment(8L,"IN_USE"),new EquipmentMapper.LockedEquipment(8L,"MAINTENANCE"));
  when(mapper.startMaintenance(anyString(),eq(8L),eq("油压异常"),eq(4L))).thenReturn(1);
  when(mapper.lockMaintenanceByNo(anyString())).thenReturn(new EquipmentMapper.MaintenanceRow(41L,"OPEN","MT-41"));
  when(mapper.setMaintenance(8L,4L)).thenReturn(1);
  Long maintenanceId=service.startMaintenance(8L,new EquipmentService.StartMaintenanceRequest(" 油压异常 "),4L);
  assertThat(maintenanceId).isEqualTo(41L);
  when(mapper.lockMaintenance(41L,8L)).thenReturn(new EquipmentMapper.MaintenanceRow(41L,"OPEN","MT-41"));
  when(mapper.completeMaintenance(41L,"更换密封圈",4L)).thenReturn(1);
  when(mapper.setIdle(8L,4L)).thenReturn(1);
  service.completeMaintenance(8L,41L,new EquipmentService.CompleteMaintenanceRequest(" 更换密封圈 "),4L);
  verify(audit).insert(eq(4L),eq("EQUIPMENT_MAINTENANCE_START"),eq(8L),org.mockito.ArgumentMatchers.startsWith("开始维修 MT"));
  verify(audit).insert(4L,"EQUIPMENT_MAINTENANCE_COMPLETE",8L,"完成维修 MT-41：更换密封圈");
 }
}
