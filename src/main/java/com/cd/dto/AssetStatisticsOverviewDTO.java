package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AssetStatisticsOverviewDTO {

    private LocalDateTime generatedAt;
    private KpiCardDTO totalAssets = new KpiCardDTO();
    private KpiCardDTO totalHosts = new KpiCardDTO();
    private KpiCardDTO portKinds = new KpiCardDTO();
    private KpiCardDTO identifiedRate = new KpiCardDTO();
    private List<TopItemDTO> topHosts = new ArrayList<>();
    private List<TopItemDTO> topPorts = new ArrayList<>();
    private List<DistributionItemDTO> assetTypes = new ArrayList<>();
    private List<TopItemDTO> topProducts = new ArrayList<>();
    private TrendDTO discoveryTrend = new TrendDTO();

    @Data
    public static class KpiCardDTO {
        private String label;
        private String value;
        private String subText;
    }

    @Data
    public static class MetricsSnapshotDTO {
        private Long totalAssets;
        private Long totalHosts;
        private Long distinctPorts;
        private Long identifiedAssets;
        private Long distinctProducts;
    }

    @Data
    public static class TopItemDTO {
        private String name;
        private String secondary;
        private Long count;
    }

    @Data
    public static class DistributionItemDTO {
        private String name;
        private Long count;
        private Integer percent;
    }

    @Data
    public static class TrendDTO {
        private String granularity = "day";
        private String subtitle = "-";
        private List<TrendPointDTO> points = new ArrayList<>();
    }

    @Data
    public static class TrendPointDTO {
        private String label;
        private Long count;
    }
}
