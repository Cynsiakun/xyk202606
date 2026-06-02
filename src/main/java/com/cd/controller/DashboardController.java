package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.DashboardStatisticsDTO;
import com.cd.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/statistics")
    public Result<DashboardStatisticsDTO> statistics() {
        return Result.success(dashboardService.statistics());
    }
}
