package com.cd.service.impl;

import com.cd.common.security.TenantContextHolder;
import com.cd.dto.AssetStatisticsOverviewDTO;
import com.cd.mapper.AssetStatisticsMapper;
import com.cd.service.AssetStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetStatisticsServiceImpl implements AssetStatisticsService {

    private static final int TOP_IP_LIMIT = 10;
    private static final int TOP_PORT_LIMIT = 10;
    private static final int TOP_PRODUCT_LIMIT = 10;
    private static final int TREND_HOURS = 24;
    private static final int TREND_DAYS = 14;

    private final AssetStatisticsMapper assetStatisticsMapper;

    @Override
    public AssetStatisticsOverviewDTO overview() {
        Long tenantId = currentTenantId();
        AssetStatisticsOverviewDTO dto = new AssetStatisticsOverviewDTO();
        dto.setGeneratedAt(LocalDateTime.now());

        AssetStatisticsOverviewDTO.MetricsSnapshotDTO metrics = assetStatisticsMapper.selectMetricsSnapshot(tenantId);
        long totalAssets = metrics == null || metrics.getTotalAssets() == null ? 0L : metrics.getTotalAssets();
        long totalHosts = metrics == null || metrics.getTotalHosts() == null ? 0L : metrics.getTotalHosts();
        long distinctPorts = metrics == null || metrics.getDistinctPorts() == null ? 0L : metrics.getDistinctPorts();
        long identifiedAssets = metrics == null || metrics.getIdentifiedAssets() == null ? 0L : metrics.getIdentifiedAssets();
        long distinctProducts = metrics == null || metrics.getDistinctProducts() == null ? 0L : metrics.getDistinctProducts();
        int identifiedRate = totalAssets <= 0 ? 0 : (int) Math.round(identifiedAssets * 100.0 / totalAssets);

        dto.setTopHosts(defaultList(assetStatisticsMapper.selectTopHosts(tenantId, TOP_IP_LIMIT)));
        dto.setTopPorts(defaultList(assetStatisticsMapper.selectTopPorts(tenantId, TOP_PORT_LIMIT)));
        dto.setTopProducts(defaultList(assetStatisticsMapper.selectTopProducts(tenantId, TOP_PRODUCT_LIMIT)));

        List<AssetStatisticsOverviewDTO.DistributionItemDTO> typeItems = defaultList(assetStatisticsMapper.selectAssetTypeDistribution(tenantId));
        for (AssetStatisticsOverviewDTO.DistributionItemDTO item : typeItems) {
            long count = item.getCount() == null ? 0L : item.getCount();
            item.setPercent(totalAssets <= 0 ? 0 : (int) Math.round(count * 100.0 / totalAssets));
        }
        dto.setAssetTypes(typeItems);

        AssetStatisticsOverviewDTO.TrendDTO trend = new AssetStatisticsOverviewDTO.TrendDTO();
        List<AssetStatisticsOverviewDTO.TrendPointDTO> hourly = defaultList(assetStatisticsMapper.selectDiscoveryTrendHourly(tenantId, TREND_HOURS));
        if (!hourly.isEmpty()) {
            trend.setGranularity("hour");
            trend.setSubtitle("最近24小时新增资产");
            trend.setPoints(hourly);
        } else {
            trend.setGranularity("day");
            trend.setSubtitle("最近14天新增资产");
            trend.setPoints(defaultList(assetStatisticsMapper.selectDiscoveryTrendDaily(tenantId, TREND_DAYS)));
        }
        dto.setDiscoveryTrend(trend);

        dto.setTotalAssets(kpi("资产总量", String.valueOf(totalAssets), "当前库存资产记录"));
        dto.setTotalHosts(kpi("主机覆盖", String.valueOf(totalHosts), "涉及主机数"));
        dto.setPortKinds(kpi("开放端口种类", String.valueOf(distinctPorts), "Distinct port 数"));
        dto.setIdentifiedRate(kpi("识别率", identifiedRate + "%", "已识别 " + identifiedAssets + " 条 / 软件 " + distinctProducts + " 种"));

        log.info("资产统计概览生成完成: tenantId={}, totalAssets={}, totalHosts={}, distinctPorts={}, identifiedRate={}",
                tenantId, totalAssets, totalHosts, distinctPorts, identifiedRate);
        return dto;
    }

    private AssetStatisticsOverviewDTO.KpiCardDTO kpi(String label, String value, String subText) {
        AssetStatisticsOverviewDTO.KpiCardDTO dto = new AssetStatisticsOverviewDTO.KpiCardDTO();
        dto.setLabel(label);
        dto.setValue(value);
        dto.setSubText(subText);
        return dto;
    }

    private <T> List<T> defaultList(List<T> source) {
        return source == null ? List.of() : source;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }
}
