package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.MenuItemDTO;
import com.cd.dto.SysPermissionCreateDTO;
import com.cd.dto.SysPermissionResponseDTO;
import com.cd.dto.SysPermissionUpdateDTO;
import com.cd.dto.SysRoleCreateDTO;
import com.cd.dto.SysRoleResponseDTO;
import com.cd.dto.SysRoleUpdateDTO;

import java.util.List;

public interface RbacService {

    PageResult<SysRoleResponseDTO> rolePage(int page, int size, String keyword);

    SysRoleResponseDTO createRole(SysRoleCreateDTO dto);

    SysRoleResponseDTO updateRole(Long id, SysRoleUpdateDTO dto);

    void deleteRole(Long id);

    SysRoleResponseDTO getRoleById(Long id);

    void assignPermissions(Long roleId, List<Long> permissionIds);

    PageResult<SysPermissionResponseDTO> permissionPage(int page, int size, String keyword);

    SysPermissionResponseDTO createPermission(SysPermissionCreateDTO dto);

    SysPermissionResponseDTO updatePermission(Long id, SysPermissionUpdateDTO dto);

    void deletePermission(Long id);

    SysPermissionResponseDTO getPermissionById(Long id);

    void assignRolesToUser(Long userId, List<Long> roleIds);

    List<Long> getRoleIdsByUserId(Long userId);

    List<MenuItemDTO> currentUserMenus();

    List<String> currentUserPermissionCodes();

    List<SysRoleResponseDTO> allRoles();

    List<SysPermissionResponseDTO> allPermissions();
}
