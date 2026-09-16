package com.cd.service.impl;

import com.cd.common.security.TenantContextHolder;
import com.cd.dto.AssetStatisticsOverviewDTO;
import com.cd.dto.DashboardOverviewDTO;
import com.cd.dto.DashboardStatisticsDTO;
import com.cd.dto.PatchSecuritySummaryDTO;
import com.cd.dto.VulnDetectionSummaryDTO;
import com.cd.mapper.AssetStatisticsMapper;
import com.cd.mapper.DashboardMapper;
import com.cd.mapper.HostVulnResultMapper;
import com.cd.mapper.PatchSecurityMapper;
import com.cd.mapper.UserMapper;
import com.cd.service.DashboardService;
import com.cd.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int TOP_PRODUCT_LIMIT = 8;
    private static final int TREND_DAYS = 7;
    private static final DateTimeFormatter DATE_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final UserMapper userMapper;
    private final LoginLogService loginLogService;
    private final AssetStatisticsMapper assetStatisticsMapper;
    private final HostVulnResultMapper hostVulnResultMapper;
    private final PatchSecurityMapper patchSecurityMapper;
    private final DashboardMapper dashboardMapper;

    @Override
    public DashboardStatisticsDTO statistics() {
        Long tenantId = currentTenantId();
        DashboardStatisticsDTO dto = new DashboardStatisticsDTO();
        dto.setTotalUsers(userMapper.countAllByTenant(null, tenantId));
        dto.setTodayLoginCount(loginLogService.countTodaySuccess());
        dto.setTodayNewUsers(userMapper.countCreatedTodayByTenant(tenantId));
        dto.setWeekActiveUsers(loginLogService.countWeekActiveUsers());
        dto.setTotalLogs(loginLogService.countTotalLogs());
        return dto;
    }

    @Override
    public DashboardOverviewDTO overview() {
        Long tenantId = currentTenantId();
        DashboardOverviewDTO dto = new DashboardOverviewDTO();
        dto.setGeneratedAt(LocalDateTime.now());

        AssetStatisticsOverviewDTO.MetricsSnapshotDTO assetMetrics = assetStatisticsMapper.selectMetricsSnapshot(tenantId);
        List<DashboardOverviewDTO.DistributionItemDTO> assetCategories = defaultList(dashboardMapper.selectAssetCategoryDistribution(tenantId));
        List<DashboardOverviewDTO.TopItemDTO> topProducts = mapTopProducts(assetStatisticsMapper.selectTopProducts(tenantId, TOP_PRODUCT_LIMIT));
        DashboardOverviewDTO.BaselineStatusDTO baselineStatus = ensureBaselineStatus(dashboardMapper.selectBaselineStatusSummary(tenantId));
        VulnDetectionSummaryDTO vulnerabilitySummary = ensureVulnerabilitySummary(hostVulnResultMapper.selectSummaryByTenant(tenantId));
        PatchSecuritySummaryDTO patchSummary = ensurePatchSummary(patchSecurityMapper.selectSummary(tenantId));
        List<DashboardOverviewDTO.HostNodeDTO> hostNodes = enrichHostNodes(defaultList(dashboardMapper.selectHostRiskNodes(tenantId)));
        LocalDateTime latestActivityTime = dashboardMapper.selectLatestActivityTime(tenantId);

        fillDistributionPercent(assetCategories, safeLong(assetMetrics == null ? null : assetMetrics.getTotalAssets()));
        dto.setAssetCategories(assetCategories);
        dto.setTopProducts(topProducts);
        dto.setBaselineStatus(baselineStatus);
        dto.setVulnerabilitySeverity(buildSeverityBreakdown(vulnerabilitySummary));
        dto.setVulnerabilityFunnel(buildVulnerabilityFunnel(vulnerabilitySummary));
        dto.setMetrics(buildMetrics(assetMetrics, hostNodes, baselineStatus, vulnerabilitySummary, patchSummary, latestActivityTime));
        dto.setStarRing(buildStarRing(hostNodes, latestActivityTime));
        dto.setLastUpdatedLabel(latestActivityTime == null ? "-" : DATE_TIME_FORMATTER.format(latestActivityTime));
        dto.setTrends(buildTrendCards(tenantId));
        return dto;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private List<DashboardOverviewDTO.MetricCardDTO> buildMetrics(AssetStatisticsOverviewDTO.MetricsSnapshotDTO assetMetrics,
                                                                  List<DashboardOverviewDTO.HostNodeDTO> hostNodes,
                                                                  DashboardOverviewDTO.BaselineStatusDTO baselineStatus,
                                                                  VulnDetectionSummaryDTO vulnerabilitySummary,
                                                                  PatchSecuritySummaryDTO patchSummary,
                                                                  LocalDateTime latestActivityTime) {
        List<DashboardOverviewDTO.MetricCardDTO> metrics = new ArrayList<>();
        long totalAssets = safeLong(assetMetrics == null ? null : assetMetrics.getTotalAssets());
        long totalHosts = safeLong(assetMetrics == null ? null : assetMetrics.getTotalHosts());
        long portKinds = safeLong(assetMetrics == null ? null : assetMetrics.getDistinctPorts());
        long riskyHosts = hostNodes.stream().filter(node -> safeInt(node.getRiskScore()) > 0).count();
        long baselineRiskCount = safeLong(baselineStatus.getFailCount()) + safeLong(baselineStatus.getErrorCount());
        long vulnerabilityRiskCount = safeLong(vulnerabilitySummary.getCriticalCount()) + safeLong(vulnerabilitySummary.getHighCount());
        long patchRiskCount = safeLong(patchSummary.getTotalRiskCount());
        long alertCount = hostNodes.stream().mapToLong(node -> safeLong(node.getAlertCount())).sum();

        metrics.add(metric("Asset Coverage", formatNumber(totalAssets),
                formatNumber(totalHosts) + " hosts / " + formatNumber(portKinds) + " open ports", "cyan"));
        metrics.add(metric("Risk Hosts", formatNumber(riskyHosts),
                formatNumber(baselineRiskCount) + " baseline issues / " + formatNumber(vulnerabilityRiskCount) + " high-risk vulns", "amber"));
        metrics.add(metric("Risk Events", formatNumber(baselineRiskCount + vulnerabilityRiskCount + patchRiskCount + alertCount),
                latestActivityTime == null ? "No recent activity" : "Last update " + DATE_TIME_FORMATTER.format(latestActivityTime), "red"));
        return metrics;
    }

    private DashboardOverviewDTO.MetricCardDTO metric(String label, String value, String subText, String tone) {
        DashboardOverviewDTO.MetricCardDTO card = new DashboardOverviewDTO.MetricCardDTO();
        card.setLabel(label);
        card.setValue(value);
        card.setSubText(subText);
        card.setTone(tone);
        return card;
    }

    private DashboardOverviewDTO.StarRingDTO buildStarRing(List<DashboardOverviewDTO.HostNodeDTO> hostNodes,
                                                           LocalDateTime latestActivityTime) {
        DashboardOverviewDTO.StarRingDTO starRing = new DashboardOverviewDTO.StarRingDTO();
        starRing.setNodes(hostNodes);
        starRing.setTotalHosts((long) hostNodes.size());
        starRing.setRiskyHostCount(hostNodes.stream().filter(node -> safeInt(node.getRiskScore()) > 0).count());
        starRing.setLatestScanTime(latestActivityTime == null ? "-" : DATE_TIME_FORMATTER.format(latestActivityTime));
        if (hostNodes.isEmpty()) {
            starRing.setPlatformScore(100);
            return starRing;
        }
        double avgRisk = hostNodes.stream().mapToInt(node -> safeInt(node.getRiskScore())).average().orElse(0D);
        int score = (int) Math.round(Math.max(18D, Math.min(100D, 100D - avgRisk * 0.72D)));
        starRing.setPlatformScore(score);
        return starRing;
    }

    private DashboardOverviewDTO.SeverityBreakdownDTO buildSeverityBreakdown(VulnDetectionSummaryDTO summary) {
        DashboardOverviewDTO.SeverityBreakdownDTO breakdown = new DashboardOverviewDTO.SeverityBreakdownDTO();
        breakdown.setCriticalCount(safeLong(summary.getCriticalCount()));
        breakdown.setHighCount(safeLong(summary.getHighCount()));
        breakdown.setMediumCount(safeLong(summary.getMediumCount()));
        breakdown.setLowCount(safeLong(summary.getLowCount()));
        return breakdown;
    }

    private List<DashboardOverviewDTO.FunnelStageDTO> buildVulnerabilityFunnel(VulnDetectionSummaryDTO summary) {
        List<DashboardOverviewDTO.FunnelStageDTO> stages = new ArrayList<>();
        stages.add(stage("Pending", safeLong(summary.getPendingCount())));
        stages.add(stage("Verifying", safeLong(summary.getVerifyingCount())));
        stages.add(stage("Verified", safeLong(summary.getVerifiedCount())));
        stages.add(stage("Repair", safeLong(summary.getRepairCount())));
        stages.add(stage("Fixed", safeLong(summary.getFixedCount())));
        return stages;
    }

    private DashboardOverviewDTO.FunnelStageDTO stage(String name, long count) {
        DashboardOverviewDTO.FunnelStageDTO stage = new DashboardOverviewDTO.FunnelStageDTO();
        stage.setName(name);
        stage.setCount(count);
        return stage;
    }

    private List<DashboardOverviewDTO.TrendCardDTO> buildTrendCards(Long tenantId) {
        LocalDate startDate = LocalDate.now().minusDays(TREND_DAYS - 1L);
        List<DashboardOverviewDTO.TrendCardDTO> cards = new ArrayList<>();
        cards.add(trendCard("vuln", "Vulnerability Trend", "New vulnerability findings in the last 7 days",
                fillTrendPoints(startDate, dashboardMapper.selectVulnerabilityTrend(tenantId, startDate.toString()))));
        cards.add(trendCard("patch", "Patch Risk Trend", "Open patch risks in the last 7 days",
                fillTrendPoints(startDate, dashboardMapper.selectPatchRiskTrend(tenantId, startDate.toString()))));
        cards.add(trendCard("log", "Alert Trend", "Security alerts in the last 7 days",
                fillTrendPoints(startDate, dashboardMapper.selectAlertTrend(tenantId, startDate.toString()))));
        cards.add(trendCard("baseline", "Baseline Risk Trend", "Baseline fail/error findings in the last 7 days",
                fillTrendPoints(startDate, dashboardMapper.selectBaselineRiskTrend(tenantId, startDate.toString()))));
        return cards;
    }

    private DashboardOverviewDTO.TrendCardDTO trendCard(String key,
                                                        String label,
                                                        String subtitle,
                                                        List<DashboardOverviewDTO.TrendPointDTO> points) {
        DashboardOverviewDTO.TrendCardDTO card = new DashboardOverviewDTO.TrendCardDTO();
        card.setKey(key);
        card.setLabel(label);
        card.setSubtitle(subtitle);
        card.setPoints(points);
        return card;
    }

    private List<DashboardOverviewDTO.TrendPointDTO> fillTrendPoints(LocalDate startDate,
                                                                     List<DashboardOverviewDTO.TrendPointDTO> source) {
        Map<String, Long> valueMap = new LinkedHashMap<>();
        for (DashboardOverviewDTO.TrendPointDTO item : defaultList(source)) {
            valueMap.put(item.getLabel(), safeLong(item.getCount()));
        }
        List<DashboardOverviewDTO.TrendPointDTO> points = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate date = startDate.plusDays(i);
            String rawLabel = date.toString();
            DashboardOverviewDTO.TrendPointDTO point = new DashboardOverviewDTO.TrendPointDTO();
            point.setLabel(DATE_LABEL_FORMATTER.format(date));
            point.setCount(valueMap.getOrDefault(rawLabel, 0L));
            points.add(point);
        }
        return points;
    }

    private List<DashboardOverviewDTO.HostNodeDTO> enrichHostNodes(List<DashboardOverviewDTO.HostNodeDTO> source) {
        List<DashboardOverviewDTO.HostNodeDTO> nodes = new ArrayList<>();
        for (DashboardOverviewDTO.HostNodeDTO node : source) {
            long passCount = safeLong(node.getBaselinePassCount());
            long failCount = safeLong(node.getBaselineFailCount());
            long errorCount = safeLong(node.getBaselineErrorCount());
            long totalBaseline = passCount + failCount + errorCount;
            double complianceRate = totalBaseline <= 0 ? 100D : passCount * 100D / totalBaseline;
            node.setComplianceRate(Math.round(complianceRate * 100D) / 100D);

            int rawRisk = (int) Math.min(100L,
                    safeLong(node.getCriticalVulnCount()) * 22L
                            + safeLong(node.getHighVulnCount()) * 12L
                            + safeLong(node.getMediumVulnCount()) * 6L
                            + safeLong(node.getLowVulnCount()) * 3L
                            + safeLong(node.getPatchRiskCount()) * 8L
                            + safeLong(node.getAlertCount()) * 3L
                            + failCount * 2L
                            + errorCount * 4L);
            node.setRiskScore(rawRisk);
            node.setRiskLevel(resolveRiskLevel(rawRisk));
            nodes.add(node);
        }
        nodes.sort((left, right) -> Integer.compare(safeInt(right.getRiskScore()), safeInt(left.getRiskScore())));
        return nodes;
    }

    private String resolveRiskLevel(int score) {
        if (score >= 75) {
            return "critical";
        }
        if (score >= 45) {
            return "high";
        }
        if (score > 0) {
            return "medium";
        }
        return "safe";
    }

    private DashboardOverviewDTO.BaselineStatusDTO ensureBaselineStatus(DashboardOverviewDTO.BaselineStatusDTO source) {
        DashboardOverviewDTO.BaselineStatusDTO dto = source == null ? new DashboardOverviewDTO.BaselineStatusDTO() : source;
        long passCount = safeLong(dto.getPassCount());
        long failCount = safeLong(dto.getFailCount());
        long errorCount = safeLong(dto.getErrorCount());
        long total = passCount + failCount + errorCount;
        dto.setPassCount(passCount);
        dto.setFailCount(failCount);
        dto.setErrorCount(errorCount);
        dto.setComplianceRate(total <= 0 ? 100D : Math.round(passCount * 10000D / total) / 100D);
        return dto;
    }

    private VulnDetectionSummaryDTO ensureVulnerabilitySummary(VulnDetectionSummaryDTO source) {
        return source == null ? new VulnDetectionSummaryDTO() : source;
    }

    private PatchSecuritySummaryDTO ensurePatchSummary(PatchSecuritySummaryDTO source) {
        return source == null ? new PatchSecuritySummaryDTO() : source;
    }

    private List<DashboardOverviewDTO.TopItemDTO> mapTopProducts(List<AssetStatisticsOverviewDTO.TopItemDTO> source) {
        List<DashboardOverviewDTO.TopItemDTO> items = new ArrayList<>();
        for (AssetStatisticsOverviewDTO.TopItemDTO item : defaultList(source)) {
            DashboardOverviewDTO.TopItemDTO mapped = new DashboardOverviewDTO.TopItemDTO();
            mapped.setName(item.getName());
            mapped.setSecondary(item.getSecondary());
            mapped.setCount(item.getCount());
            items.add(mapped);
        }
        return items;
    }

    private void fillDistributionPercent(List<DashboardOverviewDTO.DistributionItemDTO> items, long total) {
        for (DashboardOverviewDTO.DistributionItemDTO item : items) {
            long count = safeLong(item.getCount());
            item.setCount(count);
            item.setPercent(total <= 0 ? 0 : (int) Math.round(count * 100D / total));
        }
    }

    private String formatNumber(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> List<T> defaultList(List<T> source) {
        return source == null ? List.of() : source;
    }
}
