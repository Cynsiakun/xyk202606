package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DashboardOverviewDTO {

    private LocalDateTime generatedAt;
    private String lastUpdatedLabel;
    private List<MetricCardDTO> metrics = new ArrayList<>();
    private StarRingDTO starRing = new StarRingDTO();
    private List<DistributionItemDTO> assetCategories = new ArrayList<>();
    private List<TopItemDTO> topProducts = new ArrayList<>();
    private BaselineStatusDTO baselineStatus = new BaselineStatusDTO();
    private SeverityBreakdownDTO vulnerabilitySeverity = new SeverityBreakdownDTO();
    private List<FunnelStageDTO> vulnerabilityFunnel = new ArrayList<>();
    private List<TrendCardDTO> trends = new ArrayList<>();

    @Data
    public static class MetricCardDTO {
        private String label;
        private String value;
        private String subText;
        private String tone;
    }

    @Data
    public static class StarRingDTO {
        private Integer platformScore = 100;
        private Long totalHosts = 0L;
        private Long riskyHostCount = 0L;
        private String latestScanTime = "-";
        private List<HostNodeDTO> nodes = new ArrayList<>();
    }

    @Data
    public static class HostNodeDTO {
        private Long hostId;
        private String hostName;
        private String ipv4;
        private Long assetCount;
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
    public static class TrendCardDTO {
        private String key;
        private String label;
        private String subtitle;
        private List<TrendPointDTO> points = new ArrayList<>();
    }

    @Data
    public static class TrendPointDTO {
        private String label;
        private Long count;
    }
}
