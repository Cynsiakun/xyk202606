package com.cd.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VulnOpsDashboardResponseDTO {

    private KpisDTO kpis = new KpisDTO();
    private TrendDTO trend = new TrendDTO();
    private List<HighRiskItemDTO> highRisk = new ArrayList<>();
    private SlaDTO sla = new SlaDTO();
    private VerifyTrendDTO verifyTrend = new VerifyTrendDTO();
    private ClientTrendDTO clientTrend = new ClientTrendDTO();

    @Data
    public static class KpisDTO {
        private MetricDTO total = new MetricDTO();
        private ToFixMetricDTO toFix = new ToFixMetricDTO();
        private ProgressMetricDTO avgFixDays = new ProgressMetricDTO();
        private ProgressMetricDTO verifyRate = new ProgressMetricDTO();
        private ClientMetricDTO clientOnline = new ClientMetricDTO();
    }

    @Data
    public static class MetricDTO {
        private String count = "0";
        private String trendText = "-";
        private Boolean trendUp;
    }

    @Data
    public static class ToFixMetricDTO {
        private String count = "0";
        private int highRiskRatio;
    }

    @Data
    public static class ProgressMetricDTO {
        private String count = "0";
        private int progress;
        private String gapText = "-";
    }

    @Data
    public static class ClientMetricDTO {
        private int rate;
        private int online;
        private int offline;
        private int total;
    }

    @Data
    public static class TrendDTO {
        private List<TrendPointDTO> points = new ArrayList<>();
        private String meta = "-";
    }

    @Data
    public static class TrendPointDTO {
        private String label;
        private int pending;
        private int verifying;
        private int repair;
        private int fixed;
        private int newCount;
    }

    @Data
    public static class HighRiskItemDTO {
        private String name;
        private int hosts;
        private int count;
        private int ratio;
        private String color;
    }

    @Data
    public static class SlaDTO {
        private double averageDays;
        private int targetDays = 2;
        private int maxDays = 16;
        private List<SlaBucketDTO> buckets = new ArrayList<>();
    }

    @Data
    public static class SlaBucketDTO {
        private String label;
        private int count;
        private boolean overTarget;
    }

    @Data
    public static class VerifyTrendDTO {
        private List<VerifyPointDTO> points = new ArrayList<>();
    }

    @Data
    public static class VerifyPointDTO {
        private String label;
        private int total;
        private int success;
        private int fail;
        private int rate;
    }

    @Data
    public static class ClientTrendDTO {
        private List<ClientPointDTO> points = new ArrayList<>();
        private List<OfflineHostDTO> offlineHosts = new ArrayList<>();
    }

    @Data
    public static class ClientPointDTO {
        private String label;
        private int online;
        private int rate;
    }

    @Data
    public static class OfflineHostDTO {
        private String hostname;
        private String ip;
        private String offlineFor;
    }
}
