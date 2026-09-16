package com.cd.service.impl;

import com.cd.common.security.TenantContextHolder;
import com.cd.common.security.PermissionChecker;
import com.cd.dto.AssetStatisticsOverviewDTO;
import com.cd.dto.PatchSecuritySummaryDTO;
import com.cd.dto.ThreatScreenOverviewDTO;
import com.cd.dto.VulnDetectionSummaryDTO;
import com.cd.mapper.AssetStatisticsMapper;
import com.cd.mapper.HostVulnResultMapper;
import com.cd.mapper.PatchSecurityMapper;
import com.cd.mapper.ThreatScreenMapper;
import com.cd.service.ThreatScreenService;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class ThreatScreenServiceImpl implements ThreatScreenService {

    private static final int TOP_PRODUCT_LIMIT = 8;
    private static final int TREND_DAYS = 7;
    private static final int EVENT_FEED_LIMIT = 18;
    private static final DateTimeFormatter DATE_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final ThreatScreenMapper threatScreenMapper;
    private final AssetStatisticsMapper assetStatisticsMapper;
    private final HostVulnResultMapper hostVulnResultMapper;
    private final PatchSecurityMapper patchSecurityMapper;
    private final PermissionChecker permissionChecker;

    @Override
    public ThreatScreenOverviewDTO overview() {
        return permissionChecker.isSuperAdmin() ? buildPlatformOverview() : buildTenantOverview();
    }

    private ThreatScreenOverviewDTO buildTenantOverview() {
        Long tenantId = currentTenantId();
        ThreatScreenOverviewDTO dto = new ThreatScreenOverviewDTO();
        dto.setGeneratedAt(LocalDateTime.now());
        dto.setViewMode("tenant");
        dto.setLabels(buildTenantLabels());

        AssetStatisticsOverviewDTO.MetricsSnapshotDTO assetMetrics = assetStatisticsMapper.selectMetricsSnapshot(tenantId);
        List<ThreatScreenOverviewDTO.DistributionItemDTO> assetCategories = defaultList(threatScreenMapper.selectAssetCategoryDistribution(tenantId));
        List<ThreatScreenOverviewDTO.TopItemDTO> topProducts = mapTopProducts(assetStatisticsMapper.selectTopProducts(tenantId, TOP_PRODUCT_LIMIT));
        ThreatScreenOverviewDTO.BaselineStatusDTO baselineStatus = ensureBaselineStatus(threatScreenMapper.selectBaselineStatusSummary(tenantId));
        VulnDetectionSummaryDTO vulnSummary = ensureVulnSummary(hostVulnResultMapper.selectSummaryByTenant(tenantId));
        PatchSecuritySummaryDTO patchSummary = ensurePatchSummary(patchSecurityMapper.selectSummary(tenantId));
        List<ThreatScreenOverviewDTO.HostNodeDTO> hostNodes = enrichNodes(defaultList(threatScreenMapper.selectHostRiskNodes(tenantId)));
        List<ThreatScreenOverviewDTO.EventFeedItemDTO> eventFeed = normalizeEventFeed(defaultList(threatScreenMapper.selectEventFeed(tenantId, EVENT_FEED_LIMIT)));
        LocalDateTime latestActivityTime = threatScreenMapper.selectLatestActivityTime(tenantId);
        long totalAssets = safeLong(assetMetrics == null ? null : assetMetrics.getTotalAssets());
        long totalHosts = safeLong(assetMetrics == null ? null : assetMetrics.getTotalHosts());
        long todayVulnCount = safeLong(threatScreenMapper.selectTodayVulnerabilityCount(tenantId));
        long todayAlertCount = safeLong(threatScreenMapper.selectTodayAlertCount(tenantId));

        fillDistributionPercent(assetCategories, totalAssets);
        dto.setAssetCategories(assetCategories);
        dto.setTopProducts(topProducts);
        dto.setBaselineStatus(baselineStatus);
        dto.setVulnSeverity(buildSeverity(vulnSummary));
        dto.setVulnFunnel(buildFunnel(vulnSummary));
        dto.setTrends(buildTrends(tenantId));
        dto.setEventFeed(eventFeed);
        dto.setMetrics(buildMetrics(hostNodes, todayVulnCount, baselineStatus, todayAlertCount));
        dto.setCore(buildCore(hostNodes, totalHosts, totalAssets, todayVulnCount, baselineStatus, todayAlertCount, latestActivityTime));
        dto.setLastUpdatedLabel(latestActivityTime == null ? "-" : DATE_TIME_FORMATTER.format(latestActivityTime));
        return dto;
    }

    private ThreatScreenOverviewDTO buildPlatformOverview() {
        LocalDate startDate = LocalDate.now().minusDays(TREND_DAYS - 1L);
        ThreatScreenOverviewDTO dto = new ThreatScreenOverviewDTO();
        dto.setGeneratedAt(LocalDateTime.now());
        dto.setViewMode("platform");
        dto.setLabels(buildPlatformLabels());

        List<ThreatScreenOverviewDTO.DistributionItemDTO> assetCategories = defaultList(threatScreenMapper.selectPlatformAssetCategoryDistribution());
        List<ThreatScreenOverviewDTO.HostNodeDTO> tenantNodes = enrichPlatformNodes(defaultList(threatScreenMapper.selectPlatformTenantRiskNodes()));
        ThreatScreenOverviewDTO.BaselineStatusDTO baselineStatus = ensureBaselineStatus(threatScreenMapper.selectPlatformBaselineStatusSummary());
        List<ThreatScreenOverviewDTO.EventFeedItemDTO> eventFeed = normalizeEventFeed(defaultList(threatScreenMapper.selectPlatformEventFeed(EVENT_FEED_LIMIT)));
        LocalDateTime latestActivityTime = threatScreenMapper.selectPlatformLatestActivityTime();
        long totalHosts = tenantNodes.stream().mapToLong(node -> safeLong(node.getManagedHostCount())).sum();
        long totalAssets = tenantNodes.stream().mapToLong(node -> safeLong(node.getAssetCount())).sum();
        long todayVulnCount = safeLong(threatScreenMapper.selectPlatformTodayVulnerabilityCount());
        long todayAlertCount = safeLong(threatScreenMapper.selectPlatformTodayAlertCount());
        long totalTenants = tenantNodes.size();
        long riskyTenants = tenantNodes.stream().filter(node -> safeInt(node.getRiskScore()) > 0).count();

        fillDistributionPercent(assetCategories, totalAssets);
        dto.setAssetCategories(assetCategories);
        dto.setTopProducts(buildPlatformTopTenants(tenantNodes));
        dto.setBaselineStatus(baselineStatus);
        dto.setVulnSeverity(buildPlatformSeverity(tenantNodes));
        dto.setVulnFunnel(buildPlatformFunnel(tenantNodes));
        dto.setTrends(buildPlatformTrends(startDate));
        dto.setEventFeed(eventFeed);
        dto.setMetrics(buildPlatformMetrics(tenantNodes, totalTenants, riskyTenants, totalHosts, todayVulnCount, baselineStatus, todayAlertCount));
        dto.setCore(buildPlatformCore(tenantNodes, totalTenants, riskyTenants, totalHosts, totalAssets, todayVulnCount, baselineStatus, todayAlertCount, latestActivityTime));
        dto.setLastUpdatedLabel(latestActivityTime == null ? "-" : DATE_TIME_FORMATTER.format(latestActivityTime));
        return dto;
    }

    private List<ThreatScreenOverviewDTO.MetricDTO> buildMetrics(List<ThreatScreenOverviewDTO.HostNodeDTO> hostNodes,
                                                                 long todayVulnCount,
                                                                 ThreatScreenOverviewDTO.BaselineStatusDTO baselineStatus,
                                                                 long todayAlertCount) {
        List<ThreatScreenOverviewDTO.MetricDTO> metrics = new ArrayList<>();
        long riskyHosts = hostNodes.stream().filter(node -> safeInt(node.getRiskScore()) > 0).count();
        int platformScore = resolvePlatformScore(hostNodes);
        long baselineAlerts = safeLong(baselineStatus.getFailCount()) + safeLong(baselineStatus.getErrorCount());

        metrics.add(metric("平台风险指数", String.valueOf(platformScore), "综合评估当前全网风险热度", "cyan"));
        metrics.add(metric("风险主机数", formatNumber(riskyHosts), "按漏洞、基线、补丁、日志联合评分", "amber"));
        metrics.add(metric("今日新增漏洞", formatNumber(todayVulnCount), "按 host_vuln_result 当日新增统计", "red"));
        metrics.add(metric("基线异常数", formatNumber(baselineAlerts), "当前 FAIL / ERROR 基线结果总量", "amber"));
        metrics.add(metric("日志告警数", formatNumber(todayAlertCount), "security_alerts 当日新增告警", "cyan"));
        return metrics;
    }

    private List<ThreatScreenOverviewDTO.MetricDTO> buildPlatformMetrics(List<ThreatScreenOverviewDTO.HostNodeDTO> tenantNodes,
                                                                         long totalTenants,
                                                                         long riskyTenants,
                                                                         long totalHosts,
                                                                         long todayVulnCount,
                                                                         ThreatScreenOverviewDTO.BaselineStatusDTO baselineStatus,
                                                                         long todayAlertCount) {
        List<ThreatScreenOverviewDTO.MetricDTO> metrics = new ArrayList<>();
        long baselineAlerts = safeLong(baselineStatus.getFailCount()) + safeLong(baselineStatus.getErrorCount());
        int platformScore = resolvePlatformScore(tenantNodes);

        metrics.add(metric("平台风险指数", String.valueOf(platformScore), "综合评估平台侧所有企业安全态势", "cyan"));
        metrics.add(metric("纳管企业数", formatNumber(totalTenants), "当前接入并统计中的企业数量", "cyan"));
        metrics.add(metric("风险企业数", formatNumber(riskyTenants), "存在风险主机或高风险信号的企业数量", "amber"));
        metrics.add(metric("纳管主机数", formatNumber(totalHosts), "平台端汇总的企业主机总数", "amber"));
        metrics.add(metric("今日新增漏洞", formatNumber(todayVulnCount), "平台侧全租户当日新增漏洞统计", "red"));
        metrics.add(metric("基线异常数", formatNumber(baselineAlerts), "平台侧 FAIL / ERROR 基线结果总量", "amber"));
        metrics.add(metric("日志告警数", formatNumber(todayAlertCount), "平台侧当日新增日志告警", "cyan"));
        return metrics;
    }

    private ThreatScreenOverviewDTO.MetricDTO metric(String label, String value, String subText, String tone) {
        ThreatScreenOverviewDTO.MetricDTO item = new ThreatScreenOverviewDTO.MetricDTO();
        item.setLabel(label);
        item.setValue(value);
        item.setSubText(subText);
        item.setTone(tone);
        return item;
    }

    private ThreatScreenOverviewDTO.CoreDTO buildCore(List<ThreatScreenOverviewDTO.HostNodeDTO> hostNodes,
                                                      long totalHosts,
                                                      long totalAssets,
                                                      long todayVulnCount,
                                                      ThreatScreenOverviewDTO.BaselineStatusDTO baselineStatus,
                                                      long todayAlertCount,
                                                      LocalDateTime latestActivityTime) {
        ThreatScreenOverviewDTO.CoreDTO core = new ThreatScreenOverviewDTO.CoreDTO();
        core.setNodes(hostNodes);
        core.setTotalHosts(totalHosts);
        core.setTotalAssets(totalAssets);
        core.setTodayVulnCount(todayVulnCount);
        core.setBaselineAlertCount(safeLong(baselineStatus.getFailCount()) + safeLong(baselineStatus.getErrorCount()));
        core.setTodayAlertCount(todayAlertCount);
        core.setRiskyHostCount(hostNodes.stream().filter(node -> safeInt(node.getRiskScore()) > 0).count());
        core.setPlatformScore(resolvePlatformScore(hostNodes));
        core.setLatestScanTime(latestActivityTime == null ? "-" : DATE_TIME_FORMATTER.format(latestActivityTime));
        return core;
    }

    private ThreatScreenOverviewDTO.CoreDTO buildPlatformCore(List<ThreatScreenOverviewDTO.HostNodeDTO> tenantNodes,
                                                              long totalTenants,
                                                              long riskyTenants,
                                                              long totalHosts,
                                                              long totalAssets,
                                                              long todayVulnCount,
                                                              ThreatScreenOverviewDTO.BaselineStatusDTO baselineStatus,
                                                              long todayAlertCount,
                                                              LocalDateTime latestActivityTime) {
        ThreatScreenOverviewDTO.CoreDTO core = new ThreatScreenOverviewDTO.CoreDTO();
        core.setNodes(tenantNodes);
        core.setTotalTenants(totalTenants);
        core.setRiskyTenantCount(riskyTenants);
        core.setTotalHosts(totalHosts);
        core.setTotalAssets(totalAssets);
        core.setTodayVulnCount(todayVulnCount);
        core.setBaselineAlertCount(safeLong(baselineStatus.getFailCount()) + safeLong(baselineStatus.getErrorCount()));
        core.setTodayAlertCount(todayAlertCount);
        core.setRiskyHostCount(tenantNodes.stream().mapToLong(node -> safeLong(node.getManagedRiskyHostCount())).sum());
        core.setPlatformScore(resolvePlatformScore(tenantNodes));
        core.setLatestScanTime(latestActivityTime == null ? "-" : DATE_TIME_FORMATTER.format(latestActivityTime));
        return core;
    }

    private int resolvePlatformScore(List<ThreatScreenOverviewDTO.HostNodeDTO> hostNodes) {
        if (hostNodes.isEmpty()) {
            return 100;
        }
        double avgRisk = hostNodes.stream().mapToInt(node -> safeInt(node.getRiskScore())).average().orElse(0D);
        return (int) Math.round(Math.max(16D, Math.min(100D, 100D - avgRisk * 0.7D)));
    }

    private ThreatScreenOverviewDTO.SeverityBreakdownDTO buildSeverity(VulnDetectionSummaryDTO summary) {
        ThreatScreenOverviewDTO.SeverityBreakdownDTO breakdown = new ThreatScreenOverviewDTO.SeverityBreakdownDTO();
        breakdown.setCriticalCount(safeLong(summary.getCriticalCount()));
        breakdown.setHighCount(safeLong(summary.getHighCount()));
        breakdown.setMediumCount(safeLong(summary.getMediumCount()));
        breakdown.setLowCount(safeLong(summary.getLowCount()));
        return breakdown;
    }

    private ThreatScreenOverviewDTO.SeverityBreakdownDTO buildPlatformSeverity(List<ThreatScreenOverviewDTO.HostNodeDTO> tenantNodes) {
        ThreatScreenOverviewDTO.SeverityBreakdownDTO breakdown = new ThreatScreenOverviewDTO.SeverityBreakdownDTO();
        breakdown.setCriticalCount(tenantNodes.stream().mapToLong(node -> safeLong(node.getCriticalVulnCount())).sum());
        breakdown.setHighCount(tenantNodes.stream().mapToLong(node -> safeLong(node.getHighVulnCount())).sum());
        breakdown.setMediumCount(tenantNodes.stream().mapToLong(node -> safeLong(node.getMediumVulnCount())).sum());
        breakdown.setLowCount(tenantNodes.stream().mapToLong(node -> safeLong(node.getLowVulnCount())).sum());
        return breakdown;
    }

    private List<ThreatScreenOverviewDTO.FunnelStageDTO> buildFunnel(VulnDetectionSummaryDTO summary) {
        List<ThreatScreenOverviewDTO.FunnelStageDTO> stages = new ArrayList<>();
        stages.add(funnelStage("待验证", safeLong(summary.getPendingCount())));
        stages.add(funnelStage("验证中", safeLong(summary.getVerifyingCount())));
        stages.add(funnelStage("已确认", safeLong(summary.getVerifiedCount())));
        stages.add(funnelStage("待修复", safeLong(summary.getRepairCount())));
        stages.add(funnelStage("已闭环", safeLong(summary.getFixedCount())));
        return stages;
    }

    private List<ThreatScreenOverviewDTO.FunnelStageDTO> buildPlatformFunnel(List<ThreatScreenOverviewDTO.HostNodeDTO> tenantNodes) {
        long criticalTenants = tenantNodes.stream().filter(node -> "critical".equals(node.getRiskLevel())).count();
        long highTenants = tenantNodes.stream().filter(node -> "high".equals(node.getRiskLevel())).count();
        long mediumTenants = tenantNodes.stream().filter(node -> "medium".equals(node.getRiskLevel())).count();
        long riskyHosts = tenantNodes.stream().mapToLong(node -> safeLong(node.getManagedRiskyHostCount())).sum();
        long patchTenants = tenantNodes.stream().filter(node -> safeLong(node.getPatchRiskCount()) > 0).count();

        List<ThreatScreenOverviewDTO.FunnelStageDTO> stages = new ArrayList<>();
        stages.add(funnelStage("高风险企业", criticalTenants));
        stages.add(funnelStage("重点企业", highTenants));
        stages.add(funnelStage("关注企业", mediumTenants));
        stages.add(funnelStage("风险主机", riskyHosts));
        stages.add(funnelStage("补丁待处置企业", patchTenants));
        return stages;
    }

    private ThreatScreenOverviewDTO.FunnelStageDTO funnelStage(String name, long count) {
        ThreatScreenOverviewDTO.FunnelStageDTO stage = new ThreatScreenOverviewDTO.FunnelStageDTO();
        stage.setName(name);
        stage.setCount(count);
        return stage;
    }

    private List<ThreatScreenOverviewDTO.TrendDTO> buildTrends(Long tenantId) {
        LocalDate startDate = LocalDate.now().minusDays(TREND_DAYS - 1L);
        List<ThreatScreenOverviewDTO.TrendDTO> trends = new ArrayList<>();
        trends.add(trend("vuln", "漏洞趋势", "red",
                fillTrendPoints(startDate, threatScreenMapper.selectVulnerabilityTrend(tenantId, startDate.toString()))));
        trends.add(trend("patch", "补丁风险趋势", "amber",
                fillTrendPoints(startDate, threatScreenMapper.selectPatchRiskTrend(tenantId, startDate.toString()))));
        trends.add(trend("log", "日志告警趋势", "cyan",
                fillTrendPoints(startDate, threatScreenMapper.selectAlertTrend(tenantId, startDate.toString()))));
        trends.add(trend("baseline", "基线风险趋势", "blue",
                fillTrendPoints(startDate, threatScreenMapper.selectBaselineRiskTrend(tenantId, startDate.toString()))));
        return trends;
    }

    private List<ThreatScreenOverviewDTO.TrendDTO> buildPlatformTrends(LocalDate startDate) {
        List<ThreatScreenOverviewDTO.TrendDTO> trends = new ArrayList<>();
        trends.add(trend("vuln", "漏洞趋势", "red",
                fillTrendPoints(startDate, threatScreenMapper.selectPlatformVulnerabilityTrend(startDate.toString()))));
        trends.add(trend("patch", "补丁风险趋势", "amber",
                fillTrendPoints(startDate, threatScreenMapper.selectPlatformPatchRiskTrend(startDate.toString()))));
        trends.add(trend("log", "日志告警趋势", "cyan",
                fillTrendPoints(startDate, threatScreenMapper.selectPlatformAlertTrend(startDate.toString()))));
        trends.add(trend("baseline", "基线风险趋势", "blue",
                fillTrendPoints(startDate, threatScreenMapper.selectPlatformBaselineRiskTrend(startDate.toString()))));
        return trends;
    }

    private ThreatScreenOverviewDTO.TrendDTO trend(String key,
                                                   String label,
                                                   String tone,
                                                   List<ThreatScreenOverviewDTO.TrendPointDTO> points) {
        ThreatScreenOverviewDTO.TrendDTO trend = new ThreatScreenOverviewDTO.TrendDTO();
        trend.setKey(key);
        trend.setLabel(label);
        trend.setTone(tone);
        trend.setPoints(points);
        return trend;
    }

    private List<ThreatScreenOverviewDTO.TrendPointDTO> fillTrendPoints(LocalDate startDate,
                                                                        List<ThreatScreenOverviewDTO.TrendPointDTO> source) {
        Map<String, Long> valueMap = new LinkedHashMap<>();
        for (ThreatScreenOverviewDTO.TrendPointDTO item : defaultList(source)) {
            valueMap.put(item.getLabel(), safeLong(item.getCount()));
        }
        List<ThreatScreenOverviewDTO.TrendPointDTO> points = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate date = startDate.plusDays(i);
            ThreatScreenOverviewDTO.TrendPointDTO point = new ThreatScreenOverviewDTO.TrendPointDTO();
            point.setLabel(DATE_LABEL_FORMATTER.format(date));
            point.setCount(valueMap.getOrDefault(date.toString(), 0L));
            points.add(point);
        }
        return points;
    }

    private List<ThreatScreenOverviewDTO.HostNodeDTO> enrichNodes(List<ThreatScreenOverviewDTO.HostNodeDTO> source) {
        List<ThreatScreenOverviewDTO.HostNodeDTO> nodes = new ArrayList<>();
        for (ThreatScreenOverviewDTO.HostNodeDTO node : source) {
            long passCount = safeLong(node.getBaselinePassCount());
            long failCount = safeLong(node.getBaselineFailCount());
            long errorCount = safeLong(node.getBaselineErrorCount());
            long totalBaseline = passCount + failCount + errorCount;
            double complianceRate = totalBaseline <= 0 ? 100D : passCount * 100D / totalBaseline;
            node.setComplianceRate(Math.round(complianceRate * 100D) / 100D);

            long vulnRisk = safeLong(node.getCriticalVulnCount()) * 22L
                    + safeLong(node.getHighVulnCount()) * 12L
                    + safeLong(node.getMediumVulnCount()) * 6L
                    + safeLong(node.getLowVulnCount()) * 3L;
            long patchRisk = safeLong(node.getPatchRiskCount()) * 8L;
            long alertRisk = safeLong(node.getAlertCount()) * 4L;
            long baselineRisk = failCount * 2L + errorCount * 5L;
            int riskScore = (int) Math.min(100L, vulnRisk + patchRisk + alertRisk + baselineRisk);
            node.setRiskScore(riskScore);
            node.setRiskLevel(resolveRiskLevel(riskScore));
            node.setPrimarySignal(resolvePrimarySignal(vulnRisk, patchRisk, alertRisk, baselineRisk));
            nodes.add(node);
        }
        nodes.sort((left, right) -> Integer.compare(safeInt(right.getRiskScore()), safeInt(left.getRiskScore())));
        return nodes;
    }

    private List<ThreatScreenOverviewDTO.HostNodeDTO> enrichPlatformNodes(List<ThreatScreenOverviewDTO.HostNodeDTO> source) {
        List<ThreatScreenOverviewDTO.HostNodeDTO> nodes = enrichNodes(source);
        for (ThreatScreenOverviewDTO.HostNodeDTO node : nodes) {
            node.setNodeType("tenant");
            node.setHostName(node.getTenantName());
        }
        return nodes;
    }

    private List<ThreatScreenOverviewDTO.TopItemDTO> buildPlatformTopTenants(List<ThreatScreenOverviewDTO.HostNodeDTO> tenantNodes) {
        List<ThreatScreenOverviewDTO.TopItemDTO> items = new ArrayList<>();
        for (ThreatScreenOverviewDTO.HostNodeDTO node : tenantNodes.stream().limit(TOP_PRODUCT_LIMIT).toList()) {
            ThreatScreenOverviewDTO.TopItemDTO item = new ThreatScreenOverviewDTO.TopItemDTO();
            item.setName(node.getTenantName());
            item.setSecondary("风险主机 " + formatNumber(safeLong(node.getManagedRiskyHostCount())));
            item.setCount(safeLong(node.getManagedHostCount()));
            items.add(item);
        }
        return items;
    }

    private String resolvePrimarySignal(long vulnRisk, long patchRisk, long alertRisk, long baselineRisk) {
        long max = Math.max(Math.max(vulnRisk, patchRisk), Math.max(alertRisk, baselineRisk));
        if (max <= 0) {
            return "safe";
        }
        if (max == vulnRisk) {
            return "vuln";
        }
        if (max == patchRisk) {
            return "patch";
        }
        if (max == alertRisk) {
            return "log";
        }
        return "baseline";
    }

    private String resolveRiskLevel(int score) {
        if (score >= 76) {
            return "critical";
        }
        if (score >= 48) {
            return "high";
        }
        if (score > 0) {
            return "medium";
        }
        return "safe";
    }

    private List<ThreatScreenOverviewDTO.EventFeedItemDTO> normalizeEventFeed(List<ThreatScreenOverviewDTO.EventFeedItemDTO> source) {
        List<ThreatScreenOverviewDTO.EventFeedItemDTO> items = new ArrayList<>();
        for (ThreatScreenOverviewDTO.EventFeedItemDTO item : source) {
            item.setSourceType(normalizeSource(item.getSourceType()));
            item.setLevel(normalizeLevel(item.getLevel()));
            items.add(item);
        }
        return items;
    }

    private String normalizeSource(String sourceType) {
        if (sourceType == null) {
            return "EVENT";
        }
        return sourceType.toUpperCase(Locale.ROOT);
    }

    private String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return "medium";
        }
        String normalized = level.toLowerCase(Locale.ROOT);
        if ("critical".equals(normalized) || "high".equals(normalized) || "medium".equals(normalized) || "low".equals(normalized)) {
            return normalized;
        }
        if ("error".equals(normalized)) {
            return "high";
        }
        if ("warning".equals(normalized) || "warn".equals(normalized)) {
            return "medium";
        }
        if ("info".equals(normalized) || "information".equals(normalized)) {
            return "low";
        }
        return "medium";
    }

    private ThreatScreenOverviewDTO.BaselineStatusDTO ensureBaselineStatus(ThreatScreenOverviewDTO.BaselineStatusDTO source) {
        ThreatScreenOverviewDTO.BaselineStatusDTO status = source == null ? new ThreatScreenOverviewDTO.BaselineStatusDTO() : source;
        long passCount = safeLong(status.getPassCount());
        long failCount = safeLong(status.getFailCount());
        long errorCount = safeLong(status.getErrorCount());
        long total = passCount + failCount + errorCount;
        status.setPassCount(passCount);
        status.setFailCount(failCount);
        status.setErrorCount(errorCount);
        status.setComplianceRate(total <= 0 ? 100D : Math.round(passCount * 10000D / total) / 100D);
        return status;
    }

    private VulnDetectionSummaryDTO ensureVulnSummary(VulnDetectionSummaryDTO source) {
        return source == null ? new VulnDetectionSummaryDTO() : source;
    }

    private PatchSecuritySummaryDTO ensurePatchSummary(PatchSecuritySummaryDTO source) {
        return source == null ? new PatchSecuritySummaryDTO() : source;
    }

    private List<ThreatScreenOverviewDTO.TopItemDTO> mapTopProducts(List<AssetStatisticsOverviewDTO.TopItemDTO> source) {
        List<ThreatScreenOverviewDTO.TopItemDTO> items = new ArrayList<>();
        for (AssetStatisticsOverviewDTO.TopItemDTO item : defaultList(source)) {
            ThreatScreenOverviewDTO.TopItemDTO mapped = new ThreatScreenOverviewDTO.TopItemDTO();
            mapped.setName(item.getName());
            mapped.setSecondary(item.getSecondary());
            mapped.setCount(item.getCount());
            items.add(mapped);
        }
        return items;
    }

    private void fillDistributionPercent(List<ThreatScreenOverviewDTO.DistributionItemDTO> items, long total) {
        for (ThreatScreenOverviewDTO.DistributionItemDTO item : items) {
            long count = safeLong(item.getCount());
            item.setCount(count);
            item.setPercent(total <= 0 ? 0 : (int) Math.round(count * 100D / total));
        }
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private ThreatScreenOverviewDTO.LabelsDTO buildTenantLabels() {
        ThreatScreenOverviewDTO.LabelsDTO labels = new ThreatScreenOverviewDTO.LabelsDTO();
        labels.setTitle("安全态势大屏");
        labels.setSubtitle("以全屏视角展示资产、基线、漏洞、补丁和日志五类态势信号。");
        labels.setAssetCategoryTitle("资产分类玫瑰图");
        labels.setTopProductsTitle("TOP 服务 / 产品");
        labels.setCoreTitle("主机态势星图");
        labels.setCoreSubtitle("聚焦高风险主机与四类风险信号");
        return labels;
    }

    private ThreatScreenOverviewDTO.LabelsDTO buildPlatformLabels() {
        ThreatScreenOverviewDTO.LabelsDTO labels = new ThreatScreenOverviewDTO.LabelsDTO();
        labels.setTitle("平台安全态势大屏");
        labels.setSubtitle("从平台管理员视角总览企业分布、企业风险、主机规模与跨租户风险信号。");
        labels.setAssetCategoryTitle("全平台资产分类");
        labels.setTopProductsTitle("TOP 风险企业 / 主机规模");
        labels.setCoreTitle("企业态势星图");
        labels.setCoreSubtitle("聚焦高风险企业与平台级四类风险汇聚");
        return labels;
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
