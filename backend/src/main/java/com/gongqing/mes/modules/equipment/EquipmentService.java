package com.gongqing.mes.modules.equipment;

import com.gongqing.mes.common.api.*;
import com.gongqing.mes.common.exception.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Service public class EquipmentService {
 private final EquipmentMapper mapper;private final EquipmentAuditMapper audit;
 public EquipmentService(EquipmentMapper mapper,EquipmentAuditMapper audit){this.mapper=mapper;this.audit=audit;}
 public PageResult<EquipmentMapper.EquipmentView> page(PageQuery page,String keyword,String status){
  if(status!=null&&!Set.of("IDLE","IN_USE","MAINTENANCE","DISABLED").contains(status))throw bad("设备状态不合法");
  long offset;try{offset=Math.multiplyExact(page.getPage()-1,page.getSize());}catch(ArithmeticException e){throw bad("分页参数超出范围");}
  String key=StringUtils.hasText(keyword)?keyword.trim():null;return new PageResult<>(mapper.page(key,status,page.getSize(),offset),page.getPage(),page.getSize(),mapper.count(key,status));
 }
 public EquipmentMapper.EquipmentView detail(Long id){var row=mapper.detail(id);if(row==null)throw missing("设备不存在");return row;}
 @Transactional public Long create(EquipmentRequest req,Long actor){var e=normalize(req);e.createdBy=actor;try{mapper.insert(e);}catch(DuplicateKeyException ex){throw new BusinessException(ErrorCode.DATA_CONFLICT,"设备编号已存在");}audit.insert(actor,"EQUIPMENT_CREATE",e.id,"新增设备 "+e.equipmentCode);return e.id;}
 @Transactional public void update(Long id,EquipmentRequest req,Long actor){var locked=mapper.lock(id);if(locked==null)throw missing("设备不存在");if("MAINTENANCE".equals(locked.status()))throw conflict("维修中的设备不能编辑档案");if(req.expectedUpdatedAt()==null)throw bad("缺少设备版本时间，请刷新后重试");var e=normalize(req);try{if(mapper.update(id,e.equipmentCode,e.equipmentName,e.specification,e.location,e.remark,actor,req.expectedUpdatedAt())!=1)throw conflict("设备档案已被修改，请刷新后重试");}catch(DuplicateKeyException ex){throw new BusinessException(ErrorCode.DATA_CONFLICT,"设备编号已存在");}audit.insert(actor,"EQUIPMENT_UPDATE",id,"更新设备档案 "+e.equipmentCode);}
 @Transactional public void changeStatus(Long id,StatusRequest req,Long actor){var e=mapper.lock(id);if(e==null)throw missing("设备不存在");if("MAINTENANCE".equals(e.status())||"MAINTENANCE".equals(req.status()))throw conflict("维修中状态只能通过维修流程变更");if(mapper.status(id,req.status(),actor)!=1)throw conflict("设备状态更新失败");audit.insert(actor,"EQUIPMENT_STATUS",id,"设备状态由 "+e.status()+" 变更为 "+req.status());}
 @Transactional public Long startMaintenance(Long id,StartMaintenanceRequest req,Long actor){var e=mapper.lock(id);if(e==null)throw missing("设备不存在");if(!Set.of("IDLE","IN_USE").contains(e.status()))throw conflict("当前设备状态不能开始维修");String no="MT"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase();try{mapper.startMaintenance(no,id,req.reason().trim(),actor);}catch(DuplicateKeyException ex){throw conflict("该设备已有未完成维修记录");}if(mapper.setMaintenance(id,actor)!=1)throw conflict("设备状态已变化，无法开始维修");audit.insert(actor,"EQUIPMENT_MAINTENANCE_START",id,"开始维修 "+no+"："+req.reason().trim());return mapper.lockMaintenanceByNo(no).id();}
 @Transactional public void completeMaintenance(Long equipmentId,Long recordId,CompleteMaintenanceRequest req,Long actor){var e=mapper.lock(equipmentId);if(e==null)throw missing("设备不存在");var m=mapper.lockMaintenance(recordId,equipmentId);if(m==null)throw missing("维修记录不存在");if(!"OPEN".equals(m.status()))throw conflict("该维修记录已完成");if(!"MAINTENANCE".equals(e.status()))throw conflict("设备状态与维修记录不一致");if(mapper.completeMaintenance(recordId,req.note().trim(),actor)!=1||mapper.setIdle(equipmentId,actor)!=1)throw conflict("维修完成状态更新失败");audit.insert(actor,"EQUIPMENT_MAINTENANCE_COMPLETE",equipmentId,"完成维修 "+m.maintenanceNo()+"："+req.note().trim());}
 public PageResult<EquipmentMapper.MaintenanceView> maintenance(Long id,PageQuery page){if(mapper.exists(id)==null)throw missing("设备不存在");long offset;try{offset=Math.multiplyExact(page.getPage()-1,page.getSize());}catch(ArithmeticException e){throw bad("分页参数超出范围");}return new PageResult<>(mapper.maintenance(id,page.getSize(),offset),page.getPage(),page.getSize(),mapper.maintenanceCount(id));}
 private EquipmentMapper.Equipment normalize(EquipmentRequest r){var e=new EquipmentMapper.Equipment();e.equipmentCode=r.equipmentCode().trim();e.equipmentName=r.equipmentName().trim();e.specification=clean(r.specification());e.location=clean(r.location());e.remark=clean(r.remark());return e;}
 private String clean(String s){return StringUtils.hasText(s)?s.trim():null;}
 private BusinessException bad(String m){return new BusinessException(ErrorCode.BAD_REQUEST,m);}private BusinessException conflict(String m){return new BusinessException(ErrorCode.BUSINESS_CONFLICT,m);}private BusinessException missing(String m){return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,m);}
 public record EquipmentRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=40) String equipmentCode,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=120) String equipmentName,@jakarta.validation.constraints.Size(max=120) String specification,@jakarta.validation.constraints.Size(max=120) String location,@jakarta.validation.constraints.Size(max=500) String remark,LocalDateTime expectedUpdatedAt){}
 public record StatusRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Pattern(regexp="IDLE|IN_USE|DISABLED") String status){}
 public record StartMaintenanceRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=500) String reason){}
 public record CompleteMaintenanceRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=1000) String note){}
}
