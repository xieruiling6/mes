package com.gongqing.mes.modules.auth;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthAuditMapper {
    @Insert("INSERT INTO sys_operation_log(user_id,operation_code,target_type,target_id,result,detail) " +
            "VALUES(#{userId},#{operation},'USER',#{targetId},'SUCCESS',#{detail})")
    int insert(@Param("userId") Long userId, @Param("operation") String operation,
               @Param("targetId") Long targetId, @Param("detail") String detail);
}
