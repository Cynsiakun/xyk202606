package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.ClientMachineValidateDTO;
import com.cd.dto.ClientMachineValidateResponseDTO;
import com.cd.dto.TenantMachineCreateDTO;
import com.cd.dto.TenantMachineResponseDTO;
import com.cd.dto.TenantMachineUpdateDTO;

public interface TenantMachineService {

    PageResult<TenantMachineResponseDTO> list(int page, int size, String keyword);

    TenantMachineResponseDTO create(TenantMachineCreateDTO dto);

    TenantMachineResponseDTO update(Long id, TenantMachineUpdateDTO dto);

    void delete(Long id);

    ClientMachineValidateResponseDTO validate(ClientMachineValidateDTO dto);
}
