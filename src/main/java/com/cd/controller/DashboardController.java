package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.DashboardStatisticsDTO;
import com.cd.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @PreAuthorize("@perm.has('dashboard:view')")
    @GetMapping("/statistics")
    public Result<DashboardStatisticsDTO> statistics() {
        return Result.success(dashboardService.statistics());
    }
}
