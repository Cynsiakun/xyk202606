package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.InstalledPatchCreateDTO;
import com.cd.dto.InstalledPatchResponseDTO;
import com.cd.dto.InstalledPatchUpdateDTO;

import java.util.List;

public interface InstalledPatchManagementService {

    InstalledPatchResponseDTO create(InstalledPatchCreateDTO dto);

    InstalledPatchResponseDTO update(Long id, InstalledPatchUpdateDTO dto);

    void deleteById(Long id);

    void deleteBatch(List<Long> ids);

    InstalledPatchResponseDTO getById(Long id);

    PageResult<InstalledPatchResponseDTO> list(int page, int size, String keyword, String installStatus);
}
