package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.MenuItemDTO;
import com.cd.dto.RolePermissionAssignDTO;
import com.cd.dto.SysPermissionCreateDTO;
import com.cd.dto.SysPermissionResponseDTO;
import com.cd.dto.SysPermissionUpdateDTO;
import com.cd.dto.SysRoleCreateDTO;
import com.cd.dto.SysRoleResponseDTO;
import com.cd.dto.SysRoleUpdateDTO;
import com.cd.dto.UserRoleAssignDTO;
import com.cd.service.RbacService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/rbac")
@RequiredArgsConstructor
public class RbacController {

    private final RbacService rbacService;

    @PreAuthorize("hasAuthority('role:view')")
    @GetMapping("/role/list")
    public Result<PageResult<SysRoleResponseDTO>> roleList(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.success(rbacService.rolePage(page, size, keyword));
    }

    @PreAuthorize("hasAuthority('role:view')")
    @GetMapping("/role/all")
    public Result<List<SysRoleResponseDTO>> allRoles() {
        return Result.success(rbacService.allRoles());
    }

    @PreAuthorize("hasAuthority('role:create')")
    @PostMapping("/role")
    public Result<SysRoleResponseDTO> createRole(@Valid @RequestBody SysRoleCreateDTO dto) {
        return Result.success(rbacService.createRole(dto));
    }

    @PreAuthorize("hasAuthority('role:update')")
    @PutMapping("/role/{id}")
    public Result<SysRoleResponseDTO> updateRole(
            @PathVariable @Min(value = 1, message = "id must be greater than 0") Long id,
            @Valid @RequestBody SysRoleUpdateDTO dto) {
        return Result.success(rbacService.updateRole(id, dto));
    }

    @PreAuthorize("hasAuthority('role:delete')")
    @DeleteMapping("/role/{id}")
    public Result<Void> deleteRole(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        rbacService.deleteRole(id);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('role:permission:assign')")
    @PostMapping("/role/{id}/permissions")
    public Result<Void> assignPermissions(
            @PathVariable @Min(value = 1, message = "id must be greater than 0") Long id,
            @Valid @RequestBody RolePermissionAssignDTO dto) {
        rbacService.assignPermissions(id, dto.getPermissionIds());
        return Result.success();
    }

    @PreAuthorize("hasAuthority('permission:view')")
    @GetMapping("/permission/list")
    public Result<PageResult<SysPermissionResponseDTO>> permissionList(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.success(rbacService.permissionPage(page, size, keyword));
    }

    @PreAuthorize("hasAuthority('permission:view')")
    @GetMapping("/permission/all")
    public Result<List<SysPermissionResponseDTO>> allPermissions() {
        return Result.success(rbacService.allPermissions());
    }

    @PreAuthorize("hasAuthority('permission:create')")
    @PostMapping("/permission")
    public Result<SysPermissionResponseDTO> createPermission(@Valid @RequestBody SysPermissionCreateDTO dto) {
        return Result.success(rbacService.createPermission(dto));
    }

    @PreAuthorize("hasAuthority('permission:update')")
    @PutMapping("/permission/{id}")
    public Result<SysPermissionResponseDTO> updatePermission(
            @PathVariable @Min(value = 1, message = "id must be greater than 0") Long id,
            @Valid @RequestBody SysPermissionUpdateDTO dto) {
        return Result.success(rbacService.updatePermission(id, dto));
    }

    @PreAuthorize("hasAuthority('permission:delete')")
    @DeleteMapping("/permission/{id}")
    public Result<Void> deletePermission(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        rbacService.deletePermission(id);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('user:role:assign')")
    @PostMapping("/user/{userId}/roles")
    public Result<Void> assignUserRoles(
            @PathVariable @Min(value = 1, message = "userId must be greater than 0") Long userId,
            @Valid @RequestBody UserRoleAssignDTO dto) {
        rbacService.assignRolesToUser(userId, dto.getRoleIds());
        return Result.success();
    }

    @PreAuthorize("hasAuthority('user:role:assign')")
    @GetMapping("/user/{userId}/roles")
    public Result<List<Long>> userRoleIds(
            @PathVariable @Min(value = 1, message = "userId must be greater than 0") Long userId) {
        return Result.success(rbacService.getRoleIdsByUserId(userId));
    }

    @GetMapping("/menu/current")
    public Result<List<MenuItemDTO>> currentMenus() {
        return Result.success(rbacService.currentUserMenus());
    }
}
