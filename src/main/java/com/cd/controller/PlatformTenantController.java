package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.LicenseGenerateDTO;
import com.cd.dto.LicenseDtoConverter;
import com.cd.dto.LicenseResponseDTO;
import com.cd.dto.TenantCreateDTO;
import com.cd.dto.TenantResponseDTO;
import com.cd.dto.TenantStatusUpdateDTO;
import com.cd.entity.LicenseEntity;
import com.cd.entity.TenantEntity;
import com.cd.service.LicenseService;
import com.cd.service.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/platform/tenant")
@RequiredArgsConstructor
@PreAuthorize("@perm.isSuperAdmin()")
public class PlatformTenantController {

    private final TenantService tenantService;
    private final LicenseService licenseService;

    @GetMapping("/list")
    public Result<PageResult<TenantResponseDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        PageResult<TenantEntity> result = tenantService.page(page, size, keyword, status);
        List<TenantResponseDTO> list = result.getList().stream()
                .map(this::toTenantResponse)
                .toList();
        return Result.success(new PageResult<>(result.getTotal(), list));
    }

    @GetMapping("/{id}")
    public Result<TenantResponseDTO> getById(@PathVariable @Min(value = 0, message = "id must be greater than or equal to 0") Long id) {
        return Result.success(toTenantResponse(tenantService.getById(id)));
    }

    @PostMapping
    public Result<TenantResponseDTO> create(@Valid @RequestBody TenantCreateDTO dto) {
        return Result.success(toTenantResponse(tenantService.create(dto)));
    }

    @PutMapping("/{id}/status")
    public Result<TenantResponseDTO> updateStatus(
            @PathVariable @Min(value = 0, message = "id must be greater than or equal to 0") Long id,
            @Valid @RequestBody TenantStatusUpdateDTO dto) {
        return Result.success(toTenantResponse(tenantService.updateStatus(id, dto.getStatus())));
    }

    @GetMapping("/{id}/licenses")
    public Result<List<LicenseResponseDTO>> licenses(
            @PathVariable @Min(value = 0, message = "id must be greater than or equal to 0") Long id) {
        List<LicenseResponseDTO> list = licenseService.listByTenantId(id).stream()
                .map(LicenseDtoConverter::toResponse)
                .toList();
        return Result.success(list);
    }

    @PostMapping("/{id}/licenses")
    public Result<LicenseResponseDTO> generateLicense(
            @PathVariable @Min(value = 0, message = "id must be greater than or equal to 0") Long id,
            @Valid @RequestBody LicenseGenerateDTO dto) {
        return Result.success(LicenseDtoConverter.toResponse(licenseService.generateForTenant(id, dto)));
    }

    private TenantResponseDTO toTenantResponse(TenantEntity entity) {
        TenantResponseDTO dto = new TenantResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setContact(entity.getContact());
        dto.setStatus(entity.getStatus());
        dto.setLicenseEdition(entity.getId() != null && entity.getId() == 0L ? "PLATFORM" : entity.getLicenseEdition());
        dto.setLicenseStatus(entity.getId() != null && entity.getId() == 0L ? "EFFECTIVE" : entity.getLicenseStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setAdminUserId(entity.getAdminUserId());
        dto.setAdminUserName(entity.getAdminUserName());
        return dto;
    }

}
