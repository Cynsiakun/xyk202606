package com.cd.mapper;

import com.cd.dto.DashboardOverviewDTO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DashboardMapper {

    DashboardOverviewDTO.BaselineStatusDTO selectBaselineStatusSummary(@Param("tenantId") Long tenantId);

    List<DashboardOverviewDTO.DistributionItemDTO> selectAssetCategoryDistribution(@Param("tenantId") Long tenantId);

    List<DashboardOverviewDTO.HostNodeDTO> selectHostRiskNodes(@Param("tenantId") Long tenantId);

    LocalDateTime selectLatestActivityTime(@Param("tenantId") Long tenantId);

    List<DashboardOverviewDTO.TrendPointDTO> selectBaselineRiskTrend(@Param("tenantId") Long tenantId,
                                                                     @Param("startDate") String startDate);

    List<DashboardOverviewDTO.TrendPointDTO> selectVulnerabilityTrend(@Param("tenantId") Long tenantId,
                                                                      @Param("startDate") String startDate);

    List<DashboardOverviewDTO.TrendPointDTO> selectPatchRiskTrend(@Param("tenantId") Long tenantId,
                                                                  @Param("startDate") String startDate);

    List<DashboardOverviewDTO.TrendPointDTO> selectAlertTrend(@Param("tenantId") Long tenantId,
                                                              @Param("startDate") String startDate);
}
