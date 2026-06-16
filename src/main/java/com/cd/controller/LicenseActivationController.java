package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.LicenseActivateDTO;
import com.cd.dto.LicenseCurrentDTO;
import com.cd.dto.LicenseDtoConverter;
import com.cd.dto.LicenseResponseDTO;
import com.cd.service.LicenseService;
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

    @PermitAll
    @PostMapping("/activate")
    public Result<LicenseResponseDTO> activate(@Valid @RequestBody LicenseActivateDTO dto) {
        return Result.success(LicenseDtoConverter.toResponse(licenseService.activate(dto)));
    }

    @GetMapping("/current")
    public Result<LicenseCurrentDTO> current() {
        return Result.success(licenseService.current());
    }
}
