package com.gongqing.mes.modules.plan;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlanAuditMapper {
    @Insert("INSERT INTO sys_operation_log(user_id,operation_code,target_type,target_id,result,detail) " +
            "VALUES(#{userId},#{operationCode},'PRODUCTION_PLAN',#{targetId},'SUCCESS',#{detail})")
    int insert(@Param("userId") Long userId, @Param("operationCode") String operationCode,
               @Param("targetId") Long targetId, @Param("detail") String detail);
}
