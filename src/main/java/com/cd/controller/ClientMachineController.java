package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.ClientMachineValidateDTO;
import com.cd.dto.ClientMachineValidateResponseDTO;
import com.cd.service.TenantMachineService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/client/machine")
@RequiredArgsConstructor
public class ClientMachineController {

    private final TenantMachineService tenantMachineService;

    @PermitAll
    @PostMapping("/validate")
    public Result<ClientMachineValidateResponseDTO> validate(@Valid @RequestBody ClientMachineValidateDTO dto) {
        return Result.success(tenantMachineService.validate(dto));
    }
}
