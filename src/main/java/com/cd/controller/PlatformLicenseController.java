package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.LicenseActivateDTO;
import com.cd.dto.LicenseDtoConverter;
import com.cd.dto.LicenseOfflineResponseDTO;
import com.cd.service.LicenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/platform/license")
@RequiredArgsConstructor
@PreAuthorize("@perm.isSuperAdmin()")
public class PlatformLicenseController {

    private final LicenseService licenseService;

    @PostMapping("/offline")
    public Result<LicenseOfflineResponseDTO> generateOffline(@Valid @RequestBody LicenseActivateDTO dto) {
        return Result.success(LicenseDtoConverter.toOfflineResponse(licenseService.generateOffline(dto)));
    }
}
