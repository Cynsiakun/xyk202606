package com.cd.service;

import com.cd.dto.LicenseGenerateDTO;
import com.cd.dto.LicenseActivateDTO;
import com.cd.dto.LicenseCurrentDTO;
import com.cd.dto.LicensePlanResponseDTO;
import com.cd.entity.LicenseEntity;

import java.util.List;

public interface LicenseService {

    LicenseEntity create(LicenseEntity entity);

    LicenseEntity update(Long id, LicenseEntity entity);

    void deleteById(Long id);

    LicenseEntity getById(Long id);

    LicenseEntity getByLicenseKey(String licenseKey);

    List<LicenseEntity> listByTenantId(Long tenantId);

    LicenseEntity generateForTenant(Long tenantId, LicenseGenerateDTO dto);

    LicenseEntity activate(LicenseActivateDTO dto);

    LicenseEntity generateOffline(LicenseActivateDTO dto);

    LicenseCurrentDTO current();

    List<LicensePlanResponseDTO> plans();
}
