package com.gongqing.mes.modules.system;

import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.common.exception.ErrorCode;
import com.gongqing.mes.modules.auth.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SystemAccessService {
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public SystemAccessService(UserMapper mapper, PasswordEncoder passwordEncoder) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<UserMapper.UserRow> users(PageQuery page, String keyword) {
        long offset = (page.getPage() - 1) * page.getSize();
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return new PageResult<>(mapper.listUsers(normalizedKeyword, page.getSize(), offset),
                page.getPage(), page.getSize(), mapper.countUsers(normalizedKeyword));
    }

    @Transactional
    public Long createUser(UserRequest request) {
        if (!StringUtils.hasText(request.password()) || request.password().length() < 12) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新用户密码至少 12 位");
        }
        var user = new UserMapper.ManagedUser();
        user.setUsername(request.username().trim());
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(request.status());
        try {
            mapper.insertUser(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "用户名已存在");
        }
        replaceUserRolesInternal(user.getId(), request.roleIds());
        return user.getId();
    }

    @Transactional
    public void updateUser(Long id, UserRequest request) {
        requireUser(id);
        ensureAdminRemains(id, request.status(), request.roleIds());
        var user = new UserMapper.ManagedUser();
        user.setId(id);
        user.setDisplayName(request.displayName().trim());
        user.setStatus(request.status());
        mapper.updateUser(user);
        if (StringUtils.hasText(request.password())) {
            mapper.updatePassword(id, passwordEncoder.encode(request.password()));
        }
        replaceUserRolesInternal(id, request.roleIds());
    }

    @Transactional
    public void replaceUserRoles(Long id, List<Long> roleIds) {
        requireUser(id);
        ensureAdminRemains(id, mapper.findStatusById(id), roleIds);
        replaceUserRolesInternal(id, roleIds);
    }

    private void replaceUserRolesInternal(Long id, List<Long> roleIds) {
        List<Long> distinct = roleIds.stream().distinct().toList();
        if (!distinct.isEmpty()) {
            long existing = distinct.stream().filter(roleId -> mapper.existsActiveRoleById(roleId) != null).count();
            if (existing != distinct.size()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "包含不存在的角色");
            }
        }
        mapper.deleteUserRoles(id);
        if (!distinct.isEmpty()) mapper.insertUserRoles(id, distinct);
    }

    /**
     * 停用用户或移除管理员角色前，确保系统仍保留至少一名可登录的管理员。
     */
    private void ensureAdminRemains(Long userId, String targetStatus, List<Long> roleIds) {
        if (!"ACTIVE".equals(mapper.findStatusById(userId))) return;
        Long adminRoleId = mapper.existsRoleByIdByCode("ADMIN");
        if (adminRoleId == null) return;
        if (!mapper.findUserRoleIds(userId).contains(adminRoleId)) return;
        boolean remainsActiveAdmin = "ACTIVE".equals(targetStatus)
                && roleIds.stream().anyMatch(adminRoleId::equals);
        if (remainsActiveAdmin) return;
        // 串行化管理员变更，避免两个并发请求各自看到“还有其他管理员”而同时停用最后两人。
        mapper.lockActiveAdminUserIds();
        if (mapper.countOtherActiveAdmins(userId) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "系统必须保留至少一名启用状态的管理员");
        }
    }

    public List<UserMapper.RoleRow> roles() { return mapper.listRoles(); }
    public List<UserMapper.PermissionRow> permissions() { return mapper.listPermissions(); }
    public List<Long> userRoleIds(Long id) { requireUser(id); return mapper.findUserRoleIds(id); }
    public List<Long> rolePermissionIds(Long id) { requireRole(id); return mapper.findRolePermissionIds(id); }

    @Transactional
    public Long createRole(RoleRequest request) {
        var role = new UserMapper.ManagedRole();
        role.setRoleCode(request.roleCode().trim().toUpperCase());
        role.setRoleName(request.roleName().trim());
        role.setDescription(request.description());
        role.setStatus(request.status());
        try {
            mapper.insertRole(role);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "角色编码已存在");
        }
        replaceRolePermissions(role.getId(), request.permissionIds());
        return role.getId();
    }

    @Transactional
    public void updateRole(Long id, RoleRequest request) {
        requireRole(id);
        Long adminRoleId = mapper.existsRoleByIdByCode("ADMIN");
        if (adminRoleId != null && adminRoleId.equals(id) && "DISABLED".equals(request.status())
                && mapper.countActiveUsersByRole(id) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "ADMIN 角色仍有启用用户，不能停用");
        }
        var role = new UserMapper.ManagedRole();
        role.setId(id);
        role.setRoleName(request.roleName().trim());
        role.setDescription(request.description());
        role.setStatus(request.status());
        mapper.updateRole(role);
        replaceRolePermissions(id, request.permissionIds());
    }

    @Transactional
    public void replaceRolePermissions(Long id, List<Long> permissionIds) {
        requireRole(id);
        List<Long> distinct = permissionIds.stream().distinct().toList();
        var permissionRows = mapper.listPermissions();
        List<Long> available = permissionRows.stream().map(UserMapper.PermissionRow::id).toList();
        if (!available.containsAll(distinct)) throw new BusinessException(ErrorCode.BAD_REQUEST, "包含不存在或已停用的权限");
        Long adminRoleId = mapper.existsRoleByIdByCode("ADMIN");
        if (adminRoleId != null && adminRoleId.equals(id)) {
            var codes = permissionRows.stream().filter(permission -> distinct.contains(permission.id()))
                    .map(UserMapper.PermissionRow::permissionCode).collect(java.util.stream.Collectors.toSet());
            if (!codes.contains("system:user:manage") || !codes.contains("system:role:manage")) {
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "ADMIN 角色必须保留用户和角色管理权限");
            }
        }
        mapper.deleteRolePermissions(id);
        if (!distinct.isEmpty()) mapper.insertRolePermissions(id, distinct);
    }

    private void requireUser(Long id) {
        if (mapper.existsById(id) == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    private void requireRole(Long id) {
        if (mapper.existsRoleById(id) == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    public record UserRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 64) String username,
                              @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String displayName,
                              @jakarta.validation.constraints.Size(max = 100) @jakarta.validation.constraints.Pattern(regexp = "^$|.{12,100}", message = "密码至少 12 位") String password,
                              @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "ACTIVE|DISABLED") String status,
                              @jakarta.validation.constraints.NotNull List<@jakarta.validation.constraints.Positive Long> roleIds) { }
    public record RoleRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,39}") String roleCode,
                              @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String roleName,
                              @jakarta.validation.constraints.Size(max = 255) String description,
                              @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "ACTIVE|DISABLED") String status,
                              @jakarta.validation.constraints.NotNull List<@jakarta.validation.constraints.Positive Long> permissionIds) { }
    public record UserSummary(UserMapper.UserRow user, List<Long> roleIds) { }
}
