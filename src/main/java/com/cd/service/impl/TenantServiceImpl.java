package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.entity.TenantEntity;
import com.cd.mapper.TenantMapper;
import com.cd.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private static final long PLATFORM_TENANT_ID = 0L;

    private final TenantMapper tenantMapper;

    @Override
    public TenantEntity create(TenantEntity entity) {
        validateStatus(entity.getStatus());
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        entity.setName(trim(entity.getName()));
        entity.setContact(trim(entity.getContact()));
        tenantMapper.insert(entity);
        return tenantMapper.selectById(entity.getId());
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
    public PageResult<TenantEntity> page(int page, int size, String keyword, Integer status) {
        validateStatus(status);
        int offset = (page - 1) * size;
        String normalizedKeyword = trim(keyword);
        long total = tenantMapper.countAll(normalizedKeyword, status);
        List<TenantEntity> list = tenantMapper.selectPage(offset, size, normalizedKeyword, status);
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
}
