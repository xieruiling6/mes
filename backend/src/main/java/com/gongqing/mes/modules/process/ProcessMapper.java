package com.gongqing.mes.modules.process;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProcessMapper {
    String COLUMNS = "id, process_code AS processCode, process_name AS processName, description, status, created_at AS createdAt, updated_at AS updatedAt";

    @Select("<script>SELECT " + COLUMNS + " FROM mes_process WHERE is_deleted=0 " +
            "<if test='keyword != null'>AND (process_code LIKE CONCAT('%',#{keyword},'%') OR process_name LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "<if test='status != null'>AND status=#{status} </if>ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<ProcessView> findPage(@Param("keyword") String keyword, @Param("status") String status,
                               @Param("limit") long limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM mes_process WHERE is_deleted=0 " +
            "<if test='keyword != null'>AND (process_code LIKE CONCAT('%',#{keyword},'%') OR process_name LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "<if test='status != null'>AND status=#{status} </if></script>")
    long count(@Param("keyword") String keyword, @Param("status") String status);

    @Select("SELECT " + COLUMNS + " FROM mes_process WHERE id=#{id} AND is_deleted=0")
    ProcessView findById(@Param("id") Long id);

    @Insert("INSERT INTO mes_process(process_code,process_name,description,status,is_deleted) " +
            "VALUES(#{processCode},#{processName},#{description},'ACTIVE',0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProcessRecord record);

    @Update("UPDATE mes_process SET process_code=#{processCode},process_name=#{processName},description=#{description} " +
            "WHERE id=#{id} AND is_deleted=0")
    int update(ProcessRecord record);

    @Update("UPDATE mes_process SET status=#{status} WHERE id=#{id} AND is_deleted=0")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Select("SELECT id FROM mes_process WHERE id=#{id} AND is_deleted=0")
    Long exists(@Param("id") Long id);

    class ProcessRecord {
        private Long id;
        private String processCode;
        private String processName;
        private String description;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProcessCode() { return processCode; }
        public void setProcessCode(String value) { processCode = value; }
        public String getProcessName() { return processName; }
        public void setProcessName(String value) { processName = value; }
        public String getDescription() { return description; }
        public void setDescription(String value) { description = value; }
    }

    record ProcessView(Long id, String processCode, String processName, String description,
                       String status, String createdAt, String updatedAt) { }
}
