package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.TenantMachineCreateDTO;
import com.cd.dto.TenantMachineResponseDTO;
import com.cd.dto.TenantMachineUpdateDTO;
import com.cd.service.TenantMachineService;
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
@RequestMapping("/api/tenant-machines")
@RequiredArgsConstructor
public class TenantMachineController {

    private final TenantMachineService tenantMachineService;

    @PreAuthorize("@perm.has('tenant-machine:view') and @licenseGuard.hasFeature('HOST')")
    @GetMapping("/list")
    public Result<PageResult<TenantMachineResponseDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.success(tenantMachineService.list(page, size, keyword));
    }

    @PreAuthorize("@perm.has('tenant-machine:create') and @licenseGuard.hasFeature('HOST')")
    @PostMapping
    public Result<TenantMachineResponseDTO> create(@Valid @RequestBody TenantMachineCreateDTO dto) {
        return Result.success(tenantMachineService.create(dto));
    }

    @PreAuthorize("@perm.has('tenant-machine:update') and @licenseGuard.hasFeature('HOST')")
    @PutMapping("/{id}")
    public Result<TenantMachineResponseDTO> update(
            @PathVariable @Min(value = 1, message = "id must be greater than 0") Long id,
            @Valid @RequestBody TenantMachineUpdateDTO dto) {
        return Result.success(tenantMachineService.update(id, dto));
    }

    @PreAuthorize("@perm.has('tenant-machine:delete') and @licenseGuard.hasFeature('HOST')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        tenantMachineService.delete(id);
        return Result.success();
    }

    @PreAuthorize("@perm.has('tenant-machine:create') and @licenseGuard.hasFeature('HOST')")
    @PostMapping("/import")
    public Result<CsvImportResultDTO> importCsv(@RequestParam("file") MultipartFile file) {
        return Result.success(tenantMachineService.importCsv(file));
    }
}
