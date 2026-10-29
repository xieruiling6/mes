package com.gongqing.mes.modules.quality;
import org.apache.ibatis.annotations.*;
@Mapper public interface QualityAuditMapper {
 @Insert("INSERT INTO sys_operation_log(user_id,operation_code,target_type,target_id,result,detail) VALUES(#{actor},#{code},'QUALITY_INSPECTION',#{id},'SUCCESS',#{detail})")
 int insert(@Param("actor") Long actor,@Param("code") String code,@Param("id") Long id,@Param("detail") String detail);
}
