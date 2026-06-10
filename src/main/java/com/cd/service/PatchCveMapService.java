package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.PatchCveMapCreateDTO;
import com.cd.dto.PatchCveMapResponseDTO;
import com.cd.dto.PatchCveMapUpdateDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PatchCveMapService {

    PatchCveMapResponseDTO create(PatchCveMapCreateDTO dto);

    PatchCveMapResponseDTO update(Long id, PatchCveMapUpdateDTO dto);

    void deleteById(Long id);

    void deleteBatch(List<Long> ids);

    PatchCveMapResponseDTO getById(Long id);

    PageResult<PatchCveMapResponseDTO> list(int page, int size, String keyword, String severity, Integer kevFlag);

    CsvImportResultDTO importCsv(MultipartFile file);
}
