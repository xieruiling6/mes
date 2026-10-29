package com.gongqing.mes.modules.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("SELECT id, username, password_hash, display_name, status FROM sys_user WHERE username = #{username}")
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "username", javaType = String.class),
            @Arg(column = "password_hash", javaType = String.class),
            @Arg(column = "display_name", javaType = String.class),
            @Arg(column = "status", javaType = String.class)
    })
    UserAccount findByUsername(@Param("username") String username);

    @Select("SELECT DISTINCT p.permission_code FROM sys_user_role ur " +
            "JOIN sys_role r ON r.id = ur.role_id AND r.status = 'ACTIVE' " +
            "JOIN sys_role_permission rp ON rp.role_id = r.id " +
            "JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 'ACTIVE' " +
            "WHERE ur.user_id = #{userId}")
    List<String> findPermissionCodes(@Param("userId") Long userId);

    @Select("SELECT DISTINCT r.role_code FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 'ACTIVE'")
    List<String> findRoleCodes(@Param("userId") Long userId);

    @Update("UPDATE sys_user SET last_login_at = CURRENT_TIMESTAMP(3) WHERE id = #{userId}")
    int updateLastLogin(@Param("userId") Long userId);

    @Select("SELECT id, username, display_name, status, last_login_at AS lastLoginAt, created_at AS createdAt " +
            "FROM sys_user WHERE (#{keyword} IS NULL OR username LIKE CONCAT('%', #{keyword}, '%') " +
            "OR display_name LIKE CONCAT('%', #{keyword}, '%')) ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<UserRow> listUsers(@Param("keyword") String keyword, @Param("limit") long limit,
                            @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM sys_user WHERE (#{keyword} IS NULL OR username LIKE CONCAT('%', #{keyword}, '%') " +
            "OR display_name LIKE CONCAT('%', #{keyword}, '%'))")
    long countUsers(@Param("keyword") String keyword);

    @Insert("INSERT INTO sys_user(username,password_hash,display_name,status) " +
            "VALUES(#{username},#{passwordHash},#{displayName},#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(ManagedUser user);

    @Update("UPDATE sys_user SET display_name=#{displayName}, status=#{status} WHERE id=#{id}")
    int updateUser(ManagedUser user);

    @Update("UPDATE sys_user SET password_hash=#{passwordHash} WHERE id=#{id}")
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    @Select("SELECT id FROM sys_user WHERE id=#{id}")
    Long existsById(@Param("id") Long id);

    @Select("SELECT status FROM sys_user WHERE id=#{id}")
    String findStatusById(@Param("id") Long id);

    @Select("SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id " +
            "JOIN sys_role r ON r.id=ur.role_id AND r.role_code='ADMIN' AND r.status='ACTIVE' " +
            "WHERE u.status='ACTIVE' ORDER BY u.id FOR UPDATE")
    List<Long> lockActiveAdminUserIds();

    @Select("SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id " +
            "JOIN sys_role r ON r.id=ur.role_id AND r.role_code='ADMIN' AND r.status='ACTIVE' " +
            "WHERE u.status='ACTIVE' AND u.id<>#{userId}")
    long countOtherActiveAdmins(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id " +
            "WHERE ur.role_id=#{roleId} AND u.status='ACTIVE'")
    long countActiveUsersByRole(@Param("roleId") Long roleId);

    @Update("UPDATE sys_user SET display_name=#{displayName} WHERE id=#{id}")
    int updateDisplayName(@Param("id") Long id, @Param("displayName") String displayName);

    @Delete("DELETE FROM sys_user_role WHERE user_id=#{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Insert("<script>INSERT INTO sys_user_role(user_id, role_id) VALUES " +
            "<foreach collection='roleIds' item='roleId' separator=','>(#{userId},#{roleId})</foreach></script>")
    int insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    @Select("SELECT r.id,r.role_code AS roleCode,r.role_name AS roleName,r.description,r.status " +
            "FROM sys_role r ORDER BY r.id")
    List<RoleRow> listRoles();

    @Insert("INSERT INTO sys_role(role_code,role_name,description,status) " +
            "VALUES(#{roleCode},#{roleName},#{description},#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRole(ManagedRole role);

    @Update("UPDATE sys_role SET role_name=#{roleName},description=#{description},status=#{status} WHERE id=#{id}")
    int updateRole(ManagedRole role);

    @Delete("DELETE FROM sys_role_permission WHERE role_id=#{roleId}")
    int deleteRolePermissions(@Param("roleId") Long roleId);

    @Insert("<script>INSERT INTO sys_role_permission(role_id, permission_id) VALUES " +
            "<foreach collection='permissionIds' item='permissionId' separator=','>(#{roleId},#{permissionId})</foreach></script>")
    int insertRolePermissions(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

    @Select("SELECT id FROM sys_role WHERE id=#{id}")
    Long existsRoleById(@Param("id") Long id);

    @Select("SELECT id FROM sys_role WHERE id=#{id} AND status='ACTIVE'")
    Long existsActiveRoleById(@Param("id") Long id);

    @Select("SELECT id FROM sys_role WHERE role_code=#{roleCode} AND status='ACTIVE'")
    Long existsRoleByIdByCode(@Param("roleCode") String roleCode);

    @Select("SELECT id,permission_code AS permissionCode,permission_name AS permissionName,module_code AS moduleCode " +
            "FROM sys_permission WHERE status='ACTIVE' ORDER BY module_code,id")
    List<PermissionRow> listPermissions();

    @Select("SELECT role_id FROM sys_user_role WHERE user_id=#{userId} ORDER BY role_id")
    List<Long> findUserRoleIds(@Param("userId") Long userId);

    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id=#{roleId} ORDER BY permission_id")
    List<Long> findRolePermissionIds(@Param("roleId") Long roleId);

    record UserRow(Long id, String username, String displayName, String status, String lastLoginAt, String createdAt) { }
    record RoleRow(Long id, String roleCode, String roleName, String description, String status) { }
    record PermissionRow(Long id, String permissionCode, String permissionName, String moduleCode) { }
    class ManagedUser {
        private Long id;
        private String username;
        private String passwordHash;
        private String displayName;
        private String status;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    class ManagedRole {
        private Long id;
        private String roleCode;
        private String roleName;
        private String description;
        private String status;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getRoleCode() { return roleCode; }
        public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
