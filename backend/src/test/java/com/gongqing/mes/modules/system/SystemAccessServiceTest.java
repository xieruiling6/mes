package com.gongqing.mes.modules.system;

import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.modules.auth.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SystemAccessServiceTest {
    private final UserMapper mapper = mock(UserMapper.class);
    private final SystemAccessService service = new SystemAccessService(mapper, mock(PasswordEncoder.class));

    @Test
    void refusesToDisableTheOnlyActiveAdministrator() {
        when(mapper.findStatusById(1L)).thenReturn("ACTIVE");
        when(mapper.existsRoleByIdByCode("ADMIN")).thenReturn(10L);
        when(mapper.findUserRoleIds(1L)).thenReturn(List.of(10L));
        when(mapper.countOtherActiveAdmins(1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.updateUser(1L, new SystemAccessService.UserRequest(
                "admin", "系统管理员", null, "DISABLED", List.of(10L))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("至少一名");
        verify(mapper, never()).updateUser(any());
    }

    @Test
    void refusesToRemoveAdministratorRoleFromTheOnlyActiveAdministrator() {
        when(mapper.findStatusById(1L)).thenReturn("ACTIVE");
        when(mapper.existsRoleByIdByCode("ADMIN")).thenReturn(10L);
        when(mapper.findUserRoleIds(1L)).thenReturn(List.of(10L));
        when(mapper.countOtherActiveAdmins(1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.replaceUserRoles(1L, List.of(3L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("至少一名");
        verify(mapper, never()).deleteUserRoles(any());
    }

    @Test
    void allowsDisablingAnAdministratorWhenAnotherActiveAdministratorExists() {
        when(mapper.findStatusById(1L)).thenReturn("ACTIVE");
        when(mapper.existsRoleByIdByCode("ADMIN")).thenReturn(10L);
        when(mapper.findUserRoleIds(1L)).thenReturn(List.of(10L));
        when(mapper.countOtherActiveAdmins(1L)).thenReturn(1L);

        service.updateUser(1L, new SystemAccessService.UserRequest(
                "admin", "系统管理员", null, "DISABLED", List.of(10L)));

        verify(mapper).updateUser(any());
        verify(mapper).deleteUserRoles(1L);
    }

    @Test
    void refusesToRemoveCriticalPermissionsFromAdminRole() {
        when(mapper.existsRoleById(10L)).thenReturn(10L);
        when(mapper.existsRoleByIdByCode("ADMIN")).thenReturn(10L);
        when(mapper.listPermissions()).thenReturn(List.of(
                new UserMapper.PermissionRow(1L, "system:user:manage", "管理用户", "system"),
                new UserMapper.PermissionRow(2L, "system:role:manage", "管理角色", "system"),
                new UserMapper.PermissionRow(3L, "product:read", "查看产品", "product")));

        assertThatThrownBy(() -> service.replaceRolePermissions(10L, List.of(3L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("必须保留");
        verify(mapper, never()).deleteRolePermissions(any());
    }

    @Test
    void refusesToDisableAdminRoleWhileEnabledUsersStillHaveIt() {
        when(mapper.existsRoleById(10L)).thenReturn(10L);
        when(mapper.existsRoleByIdByCode("ADMIN")).thenReturn(10L);
        when(mapper.countActiveUsersByRole(10L)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateRole(10L, new SystemAccessService.RoleRequest(
                "ADMIN", "系统管理员", null, "DISABLED", List.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ADMIN 角色");
        verify(mapper, never()).updateRole(any());
    }
}
