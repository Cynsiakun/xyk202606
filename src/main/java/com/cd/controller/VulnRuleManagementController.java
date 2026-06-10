package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.VulnRuleBatchDeleteDTO;
import com.cd.dto.VulnRuleCreateDTO;
import com.cd.dto.VulnRuleResponseDTO;
import com.cd.dto.VulnRuleUpdateDTO;
import com.cd.service.VulnRuleManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping
@RequiredArgsConstructor
public class VulnRuleManagementController {

    private final VulnRuleManagementService vulnRuleManagementService;

    @PreAuthorize("@perm.has('vuln-rule:create')")
    @PostMapping("/api/vuln-rule")
    public Result<VulnRuleResponseDTO> create(@Valid @RequestBody VulnRuleCreateDTO dto) {
        return Result.success(vulnRuleManagementService.create(dto));
    }

    @PreAuthorize("@perm.has('vuln-rule:update')")
    @PutMapping("/api/vuln-rule/{id}")
    public Result<VulnRuleResponseDTO> update(
            @PathVariable @Min(value = 1, message = "id必须大于0") Long id,
            @Valid @RequestBody VulnRuleUpdateDTO dto) {
        return Result.success(vulnRuleManagementService.update(id, dto));
    }

    @PreAuthorize("@perm.has('vuln-rule:delete')")
    @DeleteMapping("/api/vuln-rule/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        vulnRuleManagementService.deleteById(id);
        return Result.success();
    }

    @PreAuthorize("@perm.has('vuln-rule:delete')")
    @PostMapping("/api/vuln-rule/batch-delete")
    public Result<Void> deleteBatch(@Valid @RequestBody VulnRuleBatchDeleteDTO dto) {
        vulnRuleManagementService.deleteBatch(dto.getIds());
        return Result.success();
    }

    @PreAuthorize("@perm.has('vuln-rule:view')")
    @GetMapping("/api/vuln-rule/{id}")
    public Result<VulnRuleResponseDTO> getById(
            @PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        return Result.success(vulnRuleManagementService.getById(id));
    }

    @PreAuthorize("@perm.has('vuln-rule:view')")
    @GetMapping("/api/vuln-rule/list")
    public Result<PageResult<VulnRuleResponseDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page必须大于0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size必须大于0") Integer size,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String cveId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Integer enabled) {
        return Result.success(vulnRuleManagementService.list(page, size, ruleCode, cveId, productName, severity, enabled));
    }

    @PreAuthorize("@perm.has('vuln-rule:create')")
    @PostMapping("/vulnRule/import")
    public Result<CsvImportResultDTO> importCsv(@RequestParam("file") MultipartFile file) {
        return Result.success(vulnRuleManagementService.importCsv(file));
    }
}
