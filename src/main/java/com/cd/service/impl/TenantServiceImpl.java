package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.security.Md5PasswordEncoder;
import com.cd.dto.TenantCreateDTO;
import com.cd.dto.TenantOptionDTO;
import com.cd.entity.TenantEntity;
import com.cd.entity.SysRoleEntity;
import com.cd.entity.UserEntity;
import com.cd.mapper.SysRoleMapper;
import com.cd.mapper.SysRolePermissionMapper;
import com.cd.mapper.SysUserRoleMapper;
import com.cd.mapper.TenantMapper;
import com.cd.mapper.UserMapper;
import com.cd.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private static final long PLATFORM_TENANT_ID = 0L;
    private static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final Md5PasswordEncoder md5PasswordEncoder;

    @Override
    @Transactional
    public TenantEntity create(TenantCreateDTO dto) {
        TenantEntity entity = new TenantEntity();
        entity.setName(dto.getName());
        entity.setContact(dto.getContact());
        entity.setStatus(dto.getStatus());
        validateStatus(entity.getStatus());
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        entity.setName(trim(entity.getName()));
        entity.setContact(trim(entity.getContact()));
        tenantMapper.insert(entity);
        TenantEntity created = tenantMapper.selectById(entity.getId());
        UserEntity admin = createInitialAdmin(created.getId(), dto);
        created.setAdminUserId(admin.getId());
        created.setAdminUserName(admin.getUserName());
        return created;
    }

    @Override
    public TenantEntity update(Long id, TenantEntity entity) {
        ensureExists(id);
        validateStatus(entity.getStatus());
        entity.setId(id);
        entity.setName(trim(entity.getName()));
        entity.setContact(trim(entity.getContact()));
        tenantMapper.updateById(entity);
        return tenantMapper.selectById(id);
    }

    @Override
    public void deleteById(Long id) {
        ensureExists(id);
        tenantMapper.deleteById(id);
    }

    @Override
    public TenantEntity getById(Long id) {
        return ensureExists(id);
    }

    @Override
    public List<TenantEntity> listAll() {
        return tenantMapper.selectAll();
    }

    @Override
    public List<TenantOptionDTO> options() {
        return tenantMapper.selectOptions(1).stream()
                .map(entity -> {
                    TenantOptionDTO dto = new TenantOptionDTO();
                    dto.setId(entity.getId());
                    dto.setName(entity.getName());
                    return dto;
                })
                .toList();
    }

    @Override
    public PageResult<TenantEntity> page(int page, int size, String keyword, Integer status) {
        validateStatus(status);
        int offset = (page - 1) * size;
        String normalizedKeyword = trim(keyword);
        long total = tenantMapper.countAll(normalizedKeyword, status);
        List<TenantEntity> list = tenantMapper.selectPageWithLicense(offset, size, normalizedKeyword, status);
        return new PageResult<>(total, list);
    }

    @Override
    public TenantEntity updateStatus(Long id, Integer status) {
        TenantEntity entity = ensureExists(id);
        validateStatus(status);
        if (PLATFORM_TENANT_ID == id && status != null && status == 0) {
            throw new IllegalArgumentException("Platform tenant cannot be disabled");
        }
        tenantMapper.updateStatusById(entity.getId(), status);
        return tenantMapper.selectById(entity.getId());
    }

    private TenantEntity ensureExists(Long id) {
        TenantEntity entity = tenantMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Tenant not found: id=" + id);
        }
        return entity;
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private UserEntity createInitialAdmin(Long tenantId, TenantCreateDTO dto) {
        String adminUserName = requireText(dto.getAdminUserName(), "adminUserName");
        if (userMapper.selectByUserNameAndTenant(adminUserName, tenantId) != null) {
            throw new IllegalArgumentException("adminUserName already exists in tenant");
        }
        String adminPhone = trim(dto.getAdminPhone());
        if (adminPhone != null && userMapper.selectByUserPhoneAndTenant(adminPhone, tenantId) != null) {
            throw new IllegalArgumentException("adminPhone already exists in tenant");
        }
        String adminEmail = trim(dto.getAdminEmail());
        if (adminEmail != null && userMapper.selectByUserEmailAndTenant(adminEmail, tenantId) != null) {
            throw new IllegalArgumentException("adminEmail already exists in tenant");
        }

        UserEntity admin = new UserEntity();
        admin.setTenantId(tenantId);
        admin.setUserName(adminUserName);
        admin.setUserPwd(md5PasswordEncoder.encode(requireText(dto.getAdminPassword(), "adminPassword")));
        admin.setUserPhone(adminPhone);
        admin.setUserEmail(adminEmail);
        admin.setStatus(1);
        userMapper.insert(admin);

        SysRoleEntity role = ensureTenantAdminRole();
        sysUserRoleMapper.insertIgnore(admin.getId(), role.getId());
        return admin;
    }

    private SysRoleEntity ensureTenantAdminRole() {
        SysRoleEntity role = sysRoleMapper.selectByRoleCode(TENANT_ADMIN_ROLE);
        if (role != null) {
            sysRolePermissionMapper.insertMissingFromRole(role.getId(), "SECURITY_ADMIN");
            sysRolePermissionMapper.insertMissing(role.getId(), "user:role:assign");
            sysRolePermissionMapper.insertMissing(role.getId(), "host:asset:view");
            sysRolePermissionMapper.insertMissing(role.getId(), "asset-stats:view");
            sysRolePermissionMapper.insertMissing(role.getId(), "host:probe");
            return role;
        }
        SysRoleEntity entity = new SysRoleEntity();
        entity.setRoleCode(TENANT_ADMIN_ROLE);
        entity.setRoleName("Tenant Admin");
        entity.setStatus(1);
        sysRoleMapper.insert(entity);
        SysRoleEntity created = sysRoleMapper.selectByRoleCode(TENANT_ADMIN_ROLE);
        sysRolePermissionMapper.insertMissingFromRole(created.getId(), "SECURITY_ADMIN");
        sysRolePermissionMapper.insertMissing(created.getId(), "user:role:assign");
        sysRolePermissionMapper.insertMissing(created.getId(), "host:asset:view");
        sysRolePermissionMapper.insertMissing(created.getId(), "asset-stats:view");
        sysRolePermissionMapper.insertMissing(created.getId(), "host:probe");
        return created;
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
