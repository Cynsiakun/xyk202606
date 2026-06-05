package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.exception.UnauthorizedException;
import com.cd.common.security.SecurityUtils;
import com.cd.dto.MenuItemDTO;
import com.cd.dto.SysPermissionCreateDTO;
import com.cd.dto.SysPermissionResponseDTO;
import com.cd.dto.SysPermissionUpdateDTO;
import com.cd.dto.SysRoleCreateDTO;
import com.cd.dto.SysRoleResponseDTO;
import com.cd.dto.SysRoleUpdateDTO;
import com.cd.entity.SysMenuEntity;
import com.cd.entity.SysPermissionEntity;
import com.cd.entity.SysRoleEntity;
import com.cd.common.config.CacheConfig;
import com.cd.common.security.PermissionChecker;
import com.cd.mapper.SysMenuMapper;
import com.cd.mapper.SysPermissionMapper;
import com.cd.mapper.SysRoleMapper;
import com.cd.mapper.SysRolePermissionMapper;
import com.cd.mapper.SysUserRoleMapper;
import com.cd.mapper.UserMapper;
import com.cd.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RbacServiceImpl implements RbacService {

    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final UserMapper userMapper;
    private final PermissionChecker permissionChecker;

    @Override
    public PageResult<SysRoleResponseDTO> rolePage(int page, int size, String keyword) {
        int offset = (page - 1) * size;
        long total = sysRoleMapper.countAll(keyword);
        List<SysRoleResponseDTO> list = sysRoleMapper.selectPage(offset, size, keyword)
                .stream()
                .map(this::toRoleResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    @Override
    public SysRoleResponseDTO createRole(SysRoleCreateDTO dto) {
        validateRoleCodeUnique(null, dto.getRoleCode());
        SysRoleEntity entity = new SysRoleEntity();
        entity.setRoleCode(dto.getRoleCode());
        entity.setRoleName(dto.getRoleName());
        entity.setStatus(dto.getStatus());
        sysRoleMapper.insert(entity);
        return toRoleResponse(ensureRoleExists(entity.getId()));
    }

    @Override
    @CacheEvict(value = CacheConfig.USER_AUTH_CACHE, allEntries = true)
    public SysRoleResponseDTO updateRole(Long id, SysRoleUpdateDTO dto) {
        SysRoleEntity entity = ensureRoleExists(id);
        validateRoleCodeUnique(id, dto.getRoleCode());
        entity.setRoleCode(dto.getRoleCode());
        entity.setRoleName(dto.getRoleName());
        entity.setStatus(dto.getStatus());
        sysRoleMapper.updateById(entity);
        return toRoleResponse(ensureRoleExists(id));
    }

    @Override
    @CacheEvict(value = CacheConfig.USER_AUTH_CACHE, allEntries = true)
    public void deleteRole(Long id) {
        ensureRoleExists(id);
        if (sysUserRoleMapper.countByRoleId(id) > 0) {
            throw new IllegalArgumentException("该角色已分配给用户，无法删除");
        }
        sysRolePermissionMapper.deleteByRoleId(id);
        sysRoleMapper.deleteById(id);
    }

    @Override
    public SysRoleResponseDTO getRoleById(Long id) {
        return toRoleResponse(ensureRoleExists(id));
    }

    @Override
    @CacheEvict(value = CacheConfig.USER_AUTH_CACHE, allEntries = true)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        ensureRoleExists(roleId);
        validatePermissionIds(permissionIds);
        sysRolePermissionMapper.deleteByRoleId(roleId);
        if (!permissionIds.isEmpty()) {
            sysRolePermissionMapper.insertBatch(roleId, permissionIds);
        }
    }

    @Override
    public PageResult<SysPermissionResponseDTO> permissionPage(int page, int size, String keyword) {
        int offset = (page - 1) * size;
        long total = sysPermissionMapper.countAll(keyword);
        List<SysPermissionResponseDTO> list = sysPermissionMapper.selectPage(offset, size, keyword)
                .stream()
                .map(this::toPermissionResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    @Override
    public SysPermissionResponseDTO createPermission(SysPermissionCreateDTO dto) {
        validatePermissionCodeUnique(null, dto.getPermissionCode());
        SysPermissionEntity entity = new SysPermissionEntity();
        entity.setPermissionCode(dto.getPermissionCode());
        entity.setPermissionName(dto.getPermissionName());
        entity.setPermissionType(dto.getPermissionType());
        entity.setPath(emptyToNull(dto.getPath()));
        entity.setStatus(dto.getStatus());
        sysPermissionMapper.insert(entity);
        return toPermissionResponse(ensurePermissionExists(entity.getId()));
    }

    @Override
    @CacheEvict(value = CacheConfig.USER_AUTH_CACHE, allEntries = true)
    public SysPermissionResponseDTO updatePermission(Long id, SysPermissionUpdateDTO dto) {
        SysPermissionEntity entity = ensurePermissionExists(id);
        validatePermissionCodeUnique(id, dto.getPermissionCode());
        entity.setPermissionCode(dto.getPermissionCode());
        entity.setPermissionName(dto.getPermissionName());
        entity.setPermissionType(dto.getPermissionType());
        entity.setPath(emptyToNull(dto.getPath()));
        entity.setStatus(dto.getStatus());
        sysPermissionMapper.updateById(entity);
        return toPermissionResponse(ensurePermissionExists(id));
    }

    @Override
    @CacheEvict(value = CacheConfig.USER_AUTH_CACHE, allEntries = true)
    public void deletePermission(Long id) {
        ensurePermissionExists(id);
        if (sysRolePermissionMapper.countByPermissionId(id) > 0) {
            throw new IllegalArgumentException("该权限已分配给角色，无法删除");
        }
        sysPermissionMapper.deleteById(id);
    }

    @Override
    public SysPermissionResponseDTO getPermissionById(Long id) {
        return toPermissionResponse(ensurePermissionExists(id));
    }

    @Override
    @CacheEvict(value = CacheConfig.USER_AUTH_CACHE, key = "#userId")
    public void assignRolesToUser(Long userId, List<Long> roleIds) {
        if (userMapper.selectById(userId) == null) {
            throw new ResourceNotFoundException("用户不存在 id=" + userId);
        }
        validateRoleIds(roleIds);
        sysUserRoleMapper.deleteByUserId(userId);
        if (!roleIds.isEmpty()) {
            sysUserRoleMapper.insertBatch(userId, roleIds);
        }
    }

    @Override
    public List<Long> getRoleIdsByUserId(Long userId) {
        return sysUserRoleMapper.selectRoleIdsByUserId(userId);
    }

    @Override
    public List<MenuItemDTO> currentUserMenus() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("未登录或登录状态已失效");
        }

        List<SysMenuEntity> allMenus = sysMenuMapper.selectAllEnabled();

        // 1. 扁平实体转 DTO（携带权限码）并构建 id→DTO 映射
        Map<Long, MenuItemDTO> dtoMap = new LinkedHashMap<>();
        for (SysMenuEntity entity : allMenus) {
            dtoMap.put(entity.getId(), toMenuItem(entity));
        }

        // 2. 构建父子关系树
        List<MenuItemDTO> roots = new ArrayList<>();
        for (SysMenuEntity entity : allMenus) {
            MenuItemDTO dto = dtoMap.get(entity.getId());
            if (entity.getParentId() == null || !dtoMap.containsKey(entity.getParentId())) {
                roots.add(dto);
            } else {
                dtoMap.get(entity.getParentId()).getChildren().add(dto);
            }
        }

        // 3. 递归过滤：无权限的叶子移除，父节点若无剩余子节点也移除
        return filterMenuTreeByPermission(roots);
    }

    /**
     * 递归按权限过滤菜单树：保留有权限的节点，以及尚有可见子节点的父节点。
     */
    private List<MenuItemDTO> filterMenuTreeByPermission(List<MenuItemDTO> nodes) {
        List<MenuItemDTO> result = new ArrayList<>();
        for (MenuItemDTO node : nodes) {
            List<MenuItemDTO> filteredChildren = filterMenuTreeByPermission(node.getChildren());
            node.setChildren(filteredChildren);

            boolean hasAccess = node.getPermissionCode() == null
                    || permissionChecker.has(node.getPermissionCode());
            boolean hasVisibleChildren = !filteredChildren.isEmpty();

            if (hasAccess || hasVisibleChildren) {
                result.add(node);
            }
        }
        return result;
    }

    @Override
    public List<String> currentUserPermissionCodes() {
        if (SecurityUtils.getCurrentUserId() == null) {
            throw new UnauthorizedException("未登录或登录状态已失效");
        }
        return permissionChecker.currentPermissionCodes();
    }

    @Override
    public List<SysRoleResponseDTO> allRoles() {
        return sysRoleMapper.selectAll().stream().map(this::toRoleResponse).toList();
    }

    @Override
    public List<SysPermissionResponseDTO> allPermissions() {
        return sysPermissionMapper.selectAll().stream().map(this::toPermissionResponse).toList();
    }

    private MenuItemDTO toMenuItem(SysMenuEntity entity) {
        MenuItemDTO dto = new MenuItemDTO();
        dto.setTitle(entity.getMenuName());
        dto.setPage(entity.getMenuPath());
        dto.setIcon(entity.getMenuIcon());
        if (entity.getPermissionId() != null) {
            SysPermissionEntity permission = sysPermissionMapper.selectById(entity.getPermissionId());
            dto.setPermissionCode(permission == null ? null : permission.getPermissionCode());
        }
        return dto;
    }

    private void validateRoleCodeUnique(Long id, String roleCode) {
        SysRoleEntity entity = sysRoleMapper.selectByRoleCode(roleCode);
        if (entity != null && !entity.getId().equals(id)) {
            throw new IllegalArgumentException("角色编码已存在");
        }
    }

    private void validatePermissionCodeUnique(Long id, String permissionCode) {
        SysPermissionEntity entity = sysPermissionMapper.selectByPermissionCode(permissionCode);
        if (entity != null && !entity.getId().equals(id)) {
            throw new IllegalArgumentException("权限编码已存在");
        }
    }

    private void validateRoleIds(List<Long> roleIds) {
        for (Long roleId : roleIds) {
            ensureRoleExists(roleId);
        }
    }

    private void validatePermissionIds(List<Long> permissionIds) {
        for (Long permissionId : permissionIds) {
            ensurePermissionExists(permissionId);
        }
    }

    private SysRoleEntity ensureRoleExists(Long id) {
        SysRoleEntity entity = sysRoleMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("角色不存在 id=" + id);
        }
        return entity;
    }

    private SysPermissionEntity ensurePermissionExists(Long id) {
        SysPermissionEntity entity = sysPermissionMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("权限不存在 id=" + id);
        }
        return entity;
    }

    private SysRoleResponseDTO toRoleResponse(SysRoleEntity entity) {
        SysRoleResponseDTO dto = new SysRoleResponseDTO();
        dto.setId(entity.getId());
        dto.setRoleCode(entity.getRoleCode());
        dto.setRoleName(entity.getRoleName());
        dto.setStatus(entity.getStatus());
        dto.setCreateAt(entity.getCreateAt());
        dto.setPermissionIds(sysRolePermissionMapper.selectPermissionIdsByRoleId(entity.getId()));
        return dto;
    }

    private SysPermissionResponseDTO toPermissionResponse(SysPermissionEntity entity) {
        SysPermissionResponseDTO dto = new SysPermissionResponseDTO();
        dto.setId(entity.getId());
        dto.setPermissionCode(entity.getPermissionCode());
        dto.setPermissionName(entity.getPermissionName());
        dto.setPermissionType(entity.getPermissionType());
        dto.setPath(entity.getPath());
        dto.setStatus(entity.getStatus());
        dto.setCreateAt(entity.getCreateAt());
        return dto;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
