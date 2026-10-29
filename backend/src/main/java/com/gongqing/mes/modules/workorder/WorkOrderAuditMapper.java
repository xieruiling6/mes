package com.gongqing.mes.modules.workorder;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkOrderAuditMapper {
    @Insert("INSERT INTO sys_operation_log(user_id,operation_code,target_type,target_id,result,detail) " +
            "VALUES(#{userId},#{operationCode},'PRODUCTION_WORK_ORDER',#{targetId},'SUCCESS',#{detail})")
    int insert(@Param("userId") Long userId, @Param("operationCode") String operationCode,
            @Param("targetId") Long targetId, @Param("detail") String detail);
}
