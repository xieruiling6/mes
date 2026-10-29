package com.gongqing.mes.modules.equipment;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper public interface EquipmentMapper {
 String COLUMNS="e.id,e.equipment_code AS equipmentCode,e.equipment_name AS equipmentName,e.specification,e.location,e.status,e.remark,e.created_by AS createdBy,cu.display_name AS creatorName,e.created_at AS createdAt,e.updated_by AS updatedBy,uu.display_name AS updaterName,e.updated_at AS updatedAt";
 String JOIN=" FROM mes_equipment e JOIN sys_user cu ON cu.id=e.created_by LEFT JOIN sys_user uu ON uu.id=e.updated_by ";
 @Select("SELECT id,status FROM mes_equipment WHERE id=#{id} FOR UPDATE") LockedEquipment lock(Long id);
 @Select("SELECT "+COLUMNS+JOIN+" WHERE e.id=#{id}") EquipmentView detail(Long id);
 @Select("<script>SELECT "+COLUMNS+JOIN+" WHERE 1=1 <if test='keyword!=null'>AND (e.equipment_code LIKE CONCAT('%',#{keyword},'%') OR e.equipment_name LIKE CONCAT('%',#{keyword},'%')) </if><if test='status!=null'>AND e.status=#{status} </if> ORDER BY e.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
 List<EquipmentView> page(@Param("keyword") String keyword,@Param("status") String status,@Param("limit") long limit,@Param("offset") long offset);
 @Select("<script>SELECT COUNT(*) "+JOIN+" WHERE 1=1 <if test='keyword!=null'>AND (e.equipment_code LIKE CONCAT('%',#{keyword},'%') OR e.equipment_name LIKE CONCAT('%',#{keyword},'%')) </if><if test='status!=null'>AND e.status=#{status} </if></script>")
 long count(@Param("keyword") String keyword,@Param("status") String status);
 @Insert("INSERT INTO mes_equipment(equipment_code,equipment_name,specification,location,status,remark,created_by) VALUES(#{equipmentCode},#{equipmentName},#{specification},#{location},'IDLE',#{remark},#{createdBy})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(Equipment row);
 @Update("UPDATE mes_equipment SET equipment_code=#{equipmentCode},equipment_name=#{equipmentName},specification=#{specification},location=#{location},remark=#{remark},updated_by=#{actor} WHERE id=#{id} AND updated_at=#{expected}") int update(@Param("id") Long id,@Param("equipmentCode") String code,@Param("equipmentName") String name,@Param("specification") String specification,@Param("location") String location,@Param("remark") String remark,@Param("actor") Long actor,@Param("expected") LocalDateTime expected);
 @Update("UPDATE mes_equipment SET status=#{status},updated_by=#{actor} WHERE id=#{id}") int status(@Param("id") Long id,@Param("status") String status,@Param("actor") Long actor);
 @Insert("INSERT INTO mes_equipment_maintenance(maintenance_no,equipment_id,maintenance_reason,status,started_by) VALUES(#{no},#{equipmentId},#{reason},'OPEN',#{actor})") int startMaintenance(@Param("no") String no,@Param("equipmentId") Long equipmentId,@Param("reason") String reason,@Param("actor") Long actor);
 @Select("SELECT id,status,maintenance_no AS maintenanceNo FROM mes_equipment_maintenance WHERE id=#{id} AND equipment_id=#{equipmentId} FOR UPDATE") MaintenanceRow lockMaintenance(@Param("id") Long id,@Param("equipmentId") Long equipmentId);
 @Select("SELECT id,status,maintenance_no AS maintenanceNo FROM mes_equipment_maintenance WHERE maintenance_no=#{no}") MaintenanceRow lockMaintenanceByNo(String no);
 @Update("UPDATE mes_equipment_maintenance SET status='COMPLETED',maintenance_note=#{note},completed_by=#{actor},completed_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status='OPEN'") int completeMaintenance(@Param("id") Long id,@Param("note") String note,@Param("actor") Long actor);
 String MAINTENANCE_COLS="m.id,m.maintenance_no AS maintenanceNo,m.equipment_id AS equipmentId,e.equipment_code AS equipmentCode,m.maintenance_reason AS maintenanceReason,m.maintenance_note AS maintenanceNote,m.status,m.started_by AS startedBy,s.display_name AS starterName,m.started_at AS startedAt,m.completed_by AS completedBy,c.display_name AS completerName,m.completed_at AS completedAt";
 @Select("SELECT "+MAINTENANCE_COLS+" FROM mes_equipment_maintenance m JOIN mes_equipment e ON e.id=m.equipment_id JOIN sys_user s ON s.id=m.started_by LEFT JOIN sys_user c ON c.id=m.completed_by WHERE m.equipment_id=#{id} ORDER BY m.started_at DESC,m.id DESC LIMIT #{limit} OFFSET #{offset}") List<MaintenanceView> maintenance(@Param("id") Long id,@Param("limit") long limit,@Param("offset") long offset);
 @Select("SELECT COUNT(*) FROM mes_equipment_maintenance WHERE equipment_id=#{id}") long maintenanceCount(Long id);
 @Select("SELECT id FROM mes_equipment WHERE id=#{id}") Long exists(Long id);
 @Update("UPDATE mes_equipment SET status='MAINTENANCE',updated_by=#{actor} WHERE id=#{id} AND status IN ('IDLE','IN_USE')") int setMaintenance(@Param("id") Long id,@Param("actor") Long actor);
 @Update("UPDATE mes_equipment SET status='IDLE',updated_by=#{actor} WHERE id=#{id} AND status='MAINTENANCE'") int setIdle(@Param("id") Long id,@Param("actor") Long actor);
 record LockedEquipment(Long id,String status){}
 record MaintenanceRow(Long id,String status,String maintenanceNo){}
 class Equipment {public Long id;public String equipmentCode,equipmentName,specification,location,status,remark;public Long createdBy;}
 record EquipmentView(Long id,String equipmentCode,String equipmentName,String specification,String location,String status,String remark,Long createdBy,String creatorName,LocalDateTime createdAt,Long updatedBy,String updaterName,LocalDateTime updatedAt){}
 record MaintenanceView(Long id,String maintenanceNo,Long equipmentId,String equipmentCode,String maintenanceReason,String maintenanceNote,String status,Long startedBy,String starterName,LocalDateTime startedAt,Long completedBy,String completerName,LocalDateTime completedAt){}
}
