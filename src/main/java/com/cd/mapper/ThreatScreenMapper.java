package com.cd.mapper;

import com.cd.dto.ThreatScreenOverviewDTO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ThreatScreenMapper {

    List<ThreatScreenOverviewDTO.DistributionItemDTO> selectAssetCategoryDistribution(@Param("tenantId") Long tenantId);

    List<ThreatScreenOverviewDTO.DistributionItemDTO> selectPlatformAssetCategoryDistribution();

    List<ThreatScreenOverviewDTO.HostNodeDTO> selectHostRiskNodes(@Param("tenantId") Long tenantId);

    List<ThreatScreenOverviewDTO.HostNodeDTO> selectPlatformTenantRiskNodes();

    ThreatScreenOverviewDTO.BaselineStatusDTO selectBaselineStatusSummary(@Param("tenantId") Long tenantId);

    ThreatScreenOverviewDTO.BaselineStatusDTO selectPlatformBaselineStatusSummary();

    LocalDateTime selectLatestActivityTime(@Param("tenantId") Long tenantId);

    LocalDateTime selectPlatformLatestActivityTime();

    Long selectTodayVulnerabilityCount(@Param("tenantId") Long tenantId);

    Long selectPlatformTodayVulnerabilityCount();

    Long selectTodayAlertCount(@Param("tenantId") Long tenantId);

    Long selectPlatformTodayAlertCount();

    List<ThreatScreenOverviewDTO.TrendPointDTO> selectBaselineRiskTrend(@Param("tenantId") Long tenantId,
                                                                        @Param("startDate") String startDate);

    List<ThreatScreenOverviewDTO.TrendPointDTO> selectPlatformBaselineRiskTrend(@Param("startDate") String startDate);

    List<ThreatScreenOverviewDTO.TrendPointDTO> selectVulnerabilityTrend(@Param("tenantId") Long tenantId,
                                                                         @Param("startDate") String startDate);

    List<ThreatScreenOverviewDTO.TrendPointDTO> selectPlatformVulnerabilityTrend(@Param("startDate") String startDate);

    List<ThreatScreenOverviewDTO.TrendPointDTO> selectPatchRiskTrend(@Param("tenantId") Long tenantId,
                                                                     @Param("startDate") String startDate);

    List<ThreatScreenOverviewDTO.TrendPointDTO> selectPlatformPatchRiskTrend(@Param("startDate") String startDate);

    List<ThreatScreenOverviewDTO.TrendPointDTO> selectAlertTrend(@Param("tenantId") Long tenantId,
                                                                 @Param("startDate") String startDate);

    List<ThreatScreenOverviewDTO.TrendPointDTO> selectPlatformAlertTrend(@Param("startDate") String startDate);

    List<ThreatScreenOverviewDTO.EventFeedItemDTO> selectEventFeed(@Param("tenantId") Long tenantId,
                                                                   @Param("limit") int limit);

    List<ThreatScreenOverviewDTO.EventFeedItemDTO> selectPlatformEventFeed(@Param("limit") int limit);
}
