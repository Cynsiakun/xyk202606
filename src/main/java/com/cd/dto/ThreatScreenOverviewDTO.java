package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ThreatScreenOverviewDTO {

    private LocalDateTime generatedAt;
    private String viewMode = "tenant";
    private String lastUpdatedLabel;
    private LabelsDTO labels = new LabelsDTO();
    private List<MetricDTO> metrics = new ArrayList<>();
    private CoreDTO core = new CoreDTO();
    private List<DistributionItemDTO> assetCategories = new ArrayList<>();
    private List<TopItemDTO> topProducts = new ArrayList<>();
    private BaselineStatusDTO baselineStatus = new BaselineStatusDTO();
    private SeverityBreakdownDTO vulnSeverity = new SeverityBreakdownDTO();
    private List<FunnelStageDTO> vulnFunnel = new ArrayList<>();
    private List<TrendDTO> trends = new ArrayList<>();
    private List<EventFeedItemDTO> eventFeed = new ArrayList<>();

    @Data
    public static class MetricDTO {
        private String label;
        private String value;
        private String subText;
        private String tone;
    }

    @Data
    public static class CoreDTO {
        private Integer platformScore = 100;
        private Long totalTenants = 0L;
        private Long riskyTenantCount = 0L;
        private Long riskyHostCount = 0L;
        private Long totalHosts = 0L;
        private Long totalAssets = 0L;
        private Long todayVulnCount = 0L;
        private Long baselineAlertCount = 0L;
        private Long todayAlertCount = 0L;
        private String latestScanTime = "-";
        private List<HostNodeDTO> nodes = new ArrayList<>();
    }

    @Data
    public static class HostNodeDTO {
        private Long hostId;
        private Long tenantId;
        private String tenantName;
        private String nodeType;
        private String hostName;
        private String ipv4;
        private Long assetCount;
        private Long managedHostCount;
        private Long managedRiskyHostCount;
        private Long baselinePassCount;
        private Long baselineFailCount;
        private Long baselineErrorCount;
        private Long criticalVulnCount;
        private Long highVulnCount;
        private Long mediumVulnCount;
        private Long lowVulnCount;
        private Long patchRiskCount;
        private Long alertCount;
        private LocalDateTime latestActivityTime;
        private Integer riskScore;
        private String riskLevel;
        private String primarySignal;
        private Double complianceRate;
    }

    @Data
    public static class DistributionItemDTO {
        private String name;
        private Long count;
        private Integer percent;
    }

    @Data
    public static class TopItemDTO {
        private String name;
        private String secondary;
        private Long count;
    }

    @Data
    public static class BaselineStatusDTO {
        private Long passCount;
        private Long failCount;
        private Long errorCount;
        private Double complianceRate;
    }

    @Data
    public static class SeverityBreakdownDTO {
        private Long criticalCount;
        private Long highCount;
        private Long mediumCount;
        private Long lowCount;
    }

    @Data
    public static class FunnelStageDTO {
        private String name;
        private Long count;
    }

    @Data
    public static class TrendDTO {
        private String key;
        private String label;
        private String tone;
        private List<TrendPointDTO> points = new ArrayList<>();
    }

    @Data
    public static class TrendPointDTO {
        private String label;
        private Long count;
    }

    @Data
    public static class EventFeedItemDTO {
        private String sourceType;
        private String level;
        private String tenantName;
        private String title;
        private String hostName;
        private String ipv4;
        private String detail;
        private LocalDateTime eventTime;
    }

    @Data
    public static class LabelsDTO {
        private String title = "安全态势大屏";
        private String subtitle = "以全屏视角展示资产、基线、漏洞、补丁和日志五类态势信号。";
        private String assetCategoryTitle = "资产分类玫瑰图";
        private String topProductsTitle = "TOP 服务 / 产品";
        private String coreTitle = "主机态势星图";
        private String coreSubtitle = "聚焦高风险主机与四类风险信号";
    }
}
