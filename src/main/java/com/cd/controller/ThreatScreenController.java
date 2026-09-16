package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.ThreatScreenOverviewDTO;
import com.cd.service.ThreatScreenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/threat-screen")
@RequiredArgsConstructor
public class ThreatScreenController {

    private final ThreatScreenService threatScreenService;

    @PreAuthorize("@perm.has('dashboard:view')")
    @GetMapping("/overview")
    public Result<ThreatScreenOverviewDTO> overview() {
        return Result.success(threatScreenService.overview());
    }
}
