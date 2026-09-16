package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.ClientMachineValidateDTO;
import com.cd.dto.ClientMachineValidateResponseDTO;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.TenantMachineCreateDTO;
import com.cd.dto.TenantMachineResponseDTO;
import com.cd.dto.TenantMachineUpdateDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TenantMachineService {

    PageResult<TenantMachineResponseDTO> list(int page, int size, String keyword);

    TenantMachineResponseDTO create(TenantMachineCreateDTO dto);

    TenantMachineResponseDTO update(Long id, TenantMachineUpdateDTO dto);

    void delete(Long id);

    CsvImportResultDTO importCsv(MultipartFile file);

    List<TenantMachineResponseDTO> activatedHosts();

    long countActivatedByTenant();

    ClientMachineValidateResponseDTO validate(ClientMachineValidateDTO dto);

    void requireMachineQuotaForActivation(Long tenantId);

    void syncAuthorizedHostTenantForActivation(Long tenantId, String macAddress, String hostName);
}
