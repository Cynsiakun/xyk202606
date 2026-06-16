package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.TenantOptionDTO;
import com.cd.service.TenantService;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantPublicController {

    private final TenantService tenantService;

    @PermitAll
    @GetMapping("/options")
    public Result<List<TenantOptionDTO>> options() {
        return Result.success(tenantService.options());
    }
}
