package com.cd.mapper;

import com.cd.dto.AssetStatisticsOverviewDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AssetStatisticsMapper {

    AssetStatisticsOverviewDTO.MetricsSnapshotDTO selectMetricsSnapshot(@Param("tenantId") Long tenantId);

    List<AssetStatisticsOverviewDTO.TopItemDTO> selectTopHosts(@Param("tenantId") Long tenantId,
                                                               @Param("limit") int limit);

    List<AssetStatisticsOverviewDTO.TopItemDTO> selectTopPorts(@Param("tenantId") Long tenantId,
                                                               @Param("limit") int limit);

    List<AssetStatisticsOverviewDTO.DistributionItemDTO> selectAssetTypeDistribution(@Param("tenantId") Long tenantId);

    List<AssetStatisticsOverviewDTO.TopItemDTO> selectTopProducts(@Param("tenantId") Long tenantId,
                                                                  @Param("limit") int limit);

    List<AssetStatisticsOverviewDTO.TrendPointDTO> selectDiscoveryTrendDaily(@Param("tenantId") Long tenantId,
                                                                             @Param("days") int days);

    List<AssetStatisticsOverviewDTO.TrendPointDTO> selectDiscoveryTrendHourly(@Param("tenantId") Long tenantId,
                                                                              @Param("hours") int hours);
}
