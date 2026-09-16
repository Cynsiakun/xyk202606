package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.TenantCreateDTO;
import com.cd.dto.TenantOptionDTO;
import com.cd.entity.TenantEntity;

import java.util.List;

public interface TenantService {

    TenantEntity create(TenantCreateDTO dto);

    TenantEntity update(Long id, TenantEntity entity);

    void deleteById(Long id);

    TenantEntity getById(Long id);

    List<TenantEntity> listAll();

    List<TenantOptionDTO> options();

    PageResult<TenantEntity> page(int page, int size, String keyword, Integer status);

    TenantEntity updateStatus(Long id, Integer status);
}
