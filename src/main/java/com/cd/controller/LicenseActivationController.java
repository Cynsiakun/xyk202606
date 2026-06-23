package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.ClientMachineValidateDTO;
import com.cd.dto.ClientMachineValidateResponseDTO;
import com.cd.dto.LicenseActivateDTO;
import com.cd.dto.LicenseCurrentDTO;
import com.cd.dto.LicenseDtoConverter;
import com.cd.dto.LicenseOfflineResponseDTO;
import com.cd.dto.LicensePlanResponseDTO;
import com.cd.service.LicenseService;
import com.cd.service.TenantMachineService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/license")
@RequiredArgsConstructor
public class LicenseActivationController {

    private final LicenseService licenseService;
    private final TenantMachineService tenantMachineService;

    @PermitAll
    @PostMapping("/activate")
    public Result<LicenseOfflineResponseDTO> activate(@Valid @RequestBody LicenseActivateDTO dto) {
        return Result.success(LicenseDtoConverter.toOfflineResponse(licenseService.activate(dto)));
    }

    @PermitAll
    @PostMapping("/check")
    public Result<ClientMachineValidateResponseDTO> check(@Valid @RequestBody ClientMachineValidateDTO dto) {
        return Result.success(tenantMachineService.validate(dto));
    }

    @GetMapping("/current")
    public Result<LicenseCurrentDTO> current() {
        return Result.success(licenseService.current());
    }

    @GetMapping("/plans")
    public Result<java.util.List<LicensePlanResponseDTO>> plans() {
        return Result.success(licenseService.plans());
    }
}
