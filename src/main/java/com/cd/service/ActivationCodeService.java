package com.cd.service;

import com.cd.dto.ActivationCodeCreateDTO;
import com.cd.dto.ActivationCodeResponseDTO;
import com.cd.dto.LicenseActivateDTO;
import com.cd.dto.LicenseOfflineResponseDTO;

import java.util.List;

public interface ActivationCodeService {

    ActivationCodeResponseDTO create(Long tenantId, ActivationCodeCreateDTO dto);

    List<ActivationCodeResponseDTO> createBatch(Long tenantId, ActivationCodeCreateDTO dto);

    List<ActivationCodeResponseDTO> listByTenantId(Long tenantId);

    LicenseOfflineResponseDTO activate(LicenseActivateDTO dto);
}
