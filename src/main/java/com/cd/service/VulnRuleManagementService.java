package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.VulnRuleCreateDTO;
import com.cd.dto.VulnRuleResponseDTO;
import com.cd.dto.VulnRuleUpdateDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface VulnRuleManagementService {

    VulnRuleResponseDTO create(VulnRuleCreateDTO dto);

    VulnRuleResponseDTO update(Long id, VulnRuleUpdateDTO dto);

    void deleteById(Long id);

    void deleteBatch(List<Long> ids);

    VulnRuleResponseDTO getById(Long id);

    PageResult<VulnRuleResponseDTO> list(int page,
                                         int size,
                                         String ruleCode,
                                         String cveId,
                                         String productName,
                                         String severity,
                                         Integer enabled);

    CsvImportResultDTO importCsv(MultipartFile file);
}
