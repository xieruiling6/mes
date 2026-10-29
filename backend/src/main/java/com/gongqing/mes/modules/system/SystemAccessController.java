package com.gongqing.mes.modules.system;

import com.gongqing.mes.common.api.ApiResponse;
import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system")
public class SystemAccessController {
    private final SystemAccessService service;
    private final ApiResponseFactory responses;

    public SystemAccessController(SystemAccessService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('system:user:manage')")
    public ApiResponse<PageResult<com.gongqing.mes.modules.auth.UserMapper.UserRow>> users(
            @Valid @ModelAttribute PageQuery page, @RequestParam(required = false) String keyword) {
        return responses.success(service.users(page, keyword));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('system:user:manage')")
    public ApiResponse<Long> createUser(@Valid @RequestBody SystemAccessService.UserRequest request) {
        return responses.success(service.createUser(request));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('system:user:manage')")
    public ApiResponse<Void> updateUser(@PathVariable @Positive Long id,
                                        @Valid @RequestBody SystemAccessService.UserRequest request) {
        service.updateUser(id, request);
        return responses.success(null);
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:manage')")
    public ApiResponse<Void> replaceUserRoles(@PathVariable @Positive Long id,
                                              @Valid @RequestBody RoleIds body) {
        service.replaceUserRoles(id, body.roleIds());
        return responses.success(null);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:manage') or hasAuthority('system:user:manage')")
    public ApiResponse<List<com.gongqing.mes.modules.auth.UserMapper.RoleRow>> roles() {
        return responses.success(service.roles());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<Long> createRole(@Valid @RequestBody SystemAccessService.RoleRequest request) {
        return responses.success(service.createRole(request));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<Void> updateRole(@PathVariable @Positive Long id,
                                        @Valid @RequestBody SystemAccessService.RoleRequest request) {
        service.updateRole(id, request);
        return responses.success(null);
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<Void> replaceRolePermissions(@PathVariable @Positive Long id,
                                                    @Valid @RequestBody PermissionIds body) {
        service.replaceRolePermissions(id, body.permissionIds());
        return responses.success(null);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('system:permission:read') or hasAuthority('system:role:manage')")
    public ApiResponse<List<com.gongqing.mes.modules.auth.UserMapper.PermissionRow>> permissions() {
        return responses.success(service.permissions());
    }

    @GetMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:manage')")
    public ApiResponse<List<Long>> userRoleIds(@PathVariable @Positive Long id) {
        return responses.success(service.userRoleIds(id));
    }

    @GetMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public ApiResponse<List<Long>> rolePermissionIds(@PathVariable @Positive Long id) {
        return responses.success(service.rolePermissionIds(id));
    }

    public record RoleIds(@NotNull List<@Positive Long> roleIds) { }
    public record PermissionIds(@NotNull List<@Positive Long> permissionIds) { }
}
