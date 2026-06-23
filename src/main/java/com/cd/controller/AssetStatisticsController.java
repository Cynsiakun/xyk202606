package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.AssetStatisticsOverviewDTO;
import com.cd.service.AssetStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/asset-statistics")
@RequiredArgsConstructor
@PreAuthorize("@accessPolicy.can('TENANT_ASSET_STATS_VIEW') or @accessPolicy.can('PLATFORM_ASSET_STATS_VIEW')")
public class AssetStatisticsController {

    private final AssetStatisticsService assetStatisticsService;

    @GetMapping("/overview")
    public Result<AssetStatisticsOverviewDTO> overview() {
        return Result.success(assetStatisticsService.overview());
    }
}
