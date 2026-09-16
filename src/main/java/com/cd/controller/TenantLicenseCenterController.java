package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.ActivationCodeCreateDTO;
import com.cd.dto.ActivationCodeResponseDTO;
import com.cd.dto.LicenseCurrentDTO;
import com.cd.dto.TenantLicenseOverviewDTO;
import com.cd.service.ActivationCodeService;
import com.cd.service.LicenseService;
import com.cd.service.TenantMachineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/license-center")
@RequiredArgsConstructor
@PreAuthorize("@perm.has('tenant-machine:view')")
public class TenantLicenseCenterController {

    private final LicenseService licenseService;
    private final TenantMachineService tenantMachineService;
    private final ActivationCodeService activationCodeService;

    @GetMapping("/overview")
    public Result<TenantLicenseOverviewDTO> overview() {
        LicenseCurrentDTO current = licenseService.current();
        long activatedCount = tenantMachineService.countActivatedByTenant();
        int hostLimit = current.getHostLimit() == null ? 0 : current.getHostLimit();
        Integer remaining = hostLimit > 0 ? Math.max(0, hostLimit - (int) activatedCount) : null;

        TenantLicenseOverviewDTO dto = new TenantLicenseOverviewDTO();
        dto.setCurrentLicense(current);
        dto.setActivatedHostCount(activatedCount);
        dto.setRemainingActivatableCount(remaining);
        dto.setActivatedHosts(tenantMachineService.activatedHosts());
        dto.setActivationCodes(activationCodeService.listByTenantId(current.getTenantId()));
        return Result.success(dto);
    }

    @PostMapping("/activation-codes")
    @PreAuthorize("@perm.has('tenant-machine:create')")
    public Result<List<ActivationCodeResponseDTO>> createActivationCodes(@Valid @RequestBody ActivationCodeCreateDTO dto) {
        LicenseCurrentDTO current = licenseService.current();
        return Result.success(activationCodeService.createBatch(current.getTenantId(), dto));
    }
}
