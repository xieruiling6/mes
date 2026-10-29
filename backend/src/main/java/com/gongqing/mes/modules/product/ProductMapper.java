package com.gongqing.mes.modules.product;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductMapper {
    String COLUMNS = "id, product_code AS productCode, product_name AS productName, specification, unit, " +
            "status, remark, created_by AS createdBy, created_at AS createdAt, updated_by AS updatedBy, updated_at AS updatedAt";

    @Select("<script>SELECT " + COLUMNS + " FROM mes_product WHERE is_deleted=0 " +
            "<if test='code != null'>AND product_code LIKE CONCAT('%',#{code},'%') </if>" +
            "<if test='name != null'>AND product_name LIKE CONCAT('%',#{name},'%') </if>" +
            "<if test='status != null'>AND status=#{status} </if>" +
            "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<ProductView> findPage(@Param("code") String code, @Param("name") String name,
                               @Param("status") String status, @Param("limit") long limit,
                               @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM mes_product WHERE is_deleted=0 " +
            "<if test='code != null'>AND product_code LIKE CONCAT('%',#{code},'%') </if>" +
            "<if test='name != null'>AND product_name LIKE CONCAT('%',#{name},'%') </if>" +
            "<if test='status != null'>AND status=#{status} </if></script>")
    long count(@Param("code") String code, @Param("name") String name, @Param("status") String status);

    @Select("SELECT " + COLUMNS + " FROM mes_product WHERE id=#{id} AND is_deleted=0")
    ProductView findById(@Param("id") Long id);

    @Insert("INSERT INTO mes_product(product_code,product_name,specification,unit,status,is_deleted,created_by) " +
            "VALUES(#{productCode},#{productName},#{specification},#{unit},'ACTIVE',0,#{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductRecord record);

    @Update("UPDATE mes_product SET product_code=#{productCode},product_name=#{productName}," +
            "specification=#{specification},unit=#{unit},updated_by=#{updatedBy} WHERE id=#{id} AND is_deleted=0")
    int update(ProductRecord record);

    @Update("UPDATE mes_product SET status=#{status},updated_by=#{updatedBy} WHERE id=#{id} AND is_deleted=0")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("updatedBy") Long updatedBy);

    @Select("SELECT id FROM mes_product WHERE id=#{id} AND is_deleted=0")
    Long exists(@Param("id") Long id);

    class ProductRecord {
        private Long id;
        private String productCode;
        private String productName;
        private String specification;
        private String unit;
        private Long createdBy;
        private Long updatedBy;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProductCode() { return productCode; }
        public void setProductCode(String value) { productCode = value; }
        public String getProductName() { return productName; }
        public void setProductName(String value) { productName = value; }
        public String getSpecification() { return specification; }
        public void setSpecification(String value) { specification = value; }
        public String getUnit() { return unit; }
        public void setUnit(String value) { unit = value; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long value) { createdBy = value; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long value) { updatedBy = value; }
    }

    record ProductView(Long id, String productCode, String productName, String specification, String unit,
                       String status, String remark, Long createdBy, String createdAt, Long updatedBy, String updatedAt) { }
}
