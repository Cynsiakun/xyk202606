package com.cd.service.impl;

import com.cd.dto.VulnOpsDashboardResponseDTO;
import com.cd.service.VulnOpsDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VulnOpsDashboardServiceImpl implements VulnOpsDashboardService {

    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MM月");
    private static final List<String> HIGH_RISK_COLORS = List.of(
            "#EF4444", "#F59E0B", "#3B82F6", "#10B981", "#F97316", "#0EA5E9"
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    public VulnOpsDashboardResponseDTO overview(String range, LocalDate startDate, LocalDate endDate) {
        RangeWindow window = resolveWindow(range, startDate, endDate);
        RangeWindow previousWindow = window.previousWindow();

        List<ResultRecord> records = loadResultRecords(window.end());
        List<HostRecord> hosts = loadHosts();

        VulnOpsDashboardResponseDTO response = new VulnOpsDashboardResponseDTO();
        fillKpis(response.getKpis(), records, hosts, window, previousWindow);
        fillTrend(response.getTrend(), records, window);
        fillHighRisk(response.getHighRisk(), records);
        fillSla(response.getSla(), records, window);
        fillVerifyTrend(response.getVerifyTrend(), records, window);
        fillClientTrend(response.getClientTrend(), hosts, window);
        return response;
    }

    @Override
    public String exportHtml(String range, LocalDate startDate, LocalDate endDate) {
        RangeWindow window = resolveWindow(range, startDate, endDate);
        VulnOpsDashboardResponseDTO data = overview(range, startDate, endDate);
        String title = "漏洞运营仪表盘";
        String exportedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>漏洞运营仪表盘报告</title>
                    <style>
                        body { font-family: "Microsoft YaHei", Arial, sans-serif; margin: 0; background: #f5f7fa; color: #162132; }
                        .shell { max-width: 1200px; margin: 0 auto; padding: 24px; }
                        .header, .section, .card { background: #fff; border: 1px solid #e7edf4; border-radius: 12px; box-shadow: 0 4px 14px rgba(15,23,42,.05); }
                        .header { padding: 24px; margin-bottom: 16px; }
                        h1, h2, h3, p { margin: 0; }
                        .meta { margin-top: 8px; color: #6d7a8c; font-size: 13px; }
                        .grid-5 { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 16px; }
                        .grid-2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 16px; }
                        .card { padding: 16px; }
                        .label { color: #6d7a8c; font-size: 13px; margin-bottom: 10px; }
                        .value { font-size: 30px; font-weight: 700; font-family: "JetBrains Mono", Consolas, monospace; }
                        .hint { margin-top: 8px; color: #6d7a8c; font-size: 12px; line-height: 1.6; }
                        .section { padding: 16px; }
                        .section + .section { margin-top: 16px; }
                        table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 13px; }
                        th, td { border-bottom: 1px solid #edf2f8; padding: 10px 8px; text-align: left; vertical-align: top; }
                        th { color: #6d7a8c; font-weight: 600; background: #f8fafc; }
                        .bar { height: 10px; border-radius: 999px; background: #edf2f8; overflow: hidden; }
                        .bar > span { display: block; height: 100%; }
                        .list { margin: 12px 0 0; padding: 0; list-style: none; }
                        .list li { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0; border-bottom: 1px solid #edf2f8; }
                        .severity-critical { color: #b91c1c; }
                        .severity-high { color: #c2410c; }
                        .severity-medium { color: #a16207; }
                        .severity-low { color: #166534; }
                        @media (max-width: 960px) {
                            .grid-5, .grid-2 { grid-template-columns: 1fr; }
                        }
                    </style>
                </head>
                <body>
                <div class="shell">
                """);

        html.append("<section class=\"header\">")
                .append("<h1>").append(escapeHtml(title)).append("</h1>")
                .append("<p class=\"meta\">统计窗口：").append(escapeHtml(window.metaLabel()))
                .append(" | 导出时间：").append(escapeHtml(exportedAt)).append("</p>")
                .append("</section>");

        VulnOpsDashboardResponseDTO.KpisDTO kpis = data.getKpis();
        html.append("<section class=\"grid-5\">")
                .append(kpiHtml("漏洞总量", valueOf(kpis.getTotal().getCount()), valueOf(kpis.getTotal().getTrendText())))
                .append(kpiHtml("待修复漏洞", valueOf(kpis.getToFix().getCount()), "高危占比 " + kpis.getToFix().getHighRiskRatio() + "%"))
                .append(kpiHtml("平均修复时效", valueOf(kpis.getAvgFixDays().getCount()), valueOf(kpis.getAvgFixDays().getGapText())))
                .append(kpiHtml("验证成功率", valueOf(kpis.getVerifyRate().getCount()) + "%", valueOf(kpis.getVerifyRate().getGapText())))
                .append(kpiHtml("Client 在线率", kpis.getClientOnline().getRate() + "%", "在线 " + kpis.getClientOnline().getOnline() + " / 总数 " + kpis.getClientOnline().getTotal()))
                .append("</section>");

        html.append("<section class=\"grid-2\">")
                .append(sectionTable("漏洞趋势", List.of("时间", "待验证", "验证中", "待修复", "已修复", "新增"), trendRows(data.getTrend().getPoints())))
                .append(sectionTable("高危未修复分布", List.of("分类", "漏洞数", "影响主机", "占比"), highRiskRows(data.getHighRisk())))
                .append("</section>");

        html.append("<section class=\"grid-2\">")
                .append(sectionTable("修复时效分布", List.of("区间", "数量", "占比条"), slaRows(data.getSla())))
                .append(sectionTable("验证成功率趋势", List.of("时间", "验证次数", "命中", "不影响", "成功率"), verifyRows(data.getVerifyTrend().getPoints())))
                .append("</section>");

        html.append("<section class=\"grid-2\">")
                .append(sectionTable("Client 在线率趋势", List.of("时间", "活跃主机数", "在线率"), clientTrendRows(data.getClientTrend().getPoints())))
                .append(sectionTable("当前离线主机", List.of("主机名", "IP", "离线时长"), offlineRows(data.getClientTrend().getOfflineHosts())))
                .append("</section>");

        html.append("</div></body></html>");
        return html.toString();
    }

    private void fillKpis(VulnOpsDashboardResponseDTO.KpisDTO kpis,
                          List<ResultRecord> records,
                          List<HostRecord> hosts,
                          RangeWindow window,
                          RangeWindow previousWindow) {
        long totalCount = records.stream()
                .filter(this::isRiskRecord)
                .count();
        long currentNewCount = records.stream()
                .filter(this::isRiskRecord)
                .filter(record -> inWindow(record.createdAt, window))
                .count();
        long previousNewCount = records.stream()
                .filter(this::isRiskRecord)
                .filter(record -> inWindow(record.createdAt, previousWindow))
                .count();
        double trendDelta = percentDelta(currentNewCount, previousNewCount);

        kpis.getTotal().setCount(String.valueOf(totalCount));
        kpis.getTotal().setTrendUp(trendDelta <= 0);
        kpis.getTotal().setTrendText((trendDelta <= 0 ? "下降 " : "上升 ") + formatPercent(Math.abs(trendDelta)));

        List<ResultRecord> toFixRecords = records.stream()
                .filter(this::isToFix)
                .toList();
        long highRiskToFixCount = toFixRecords.stream()
                .filter(record -> isHighRisk(record.severity))
                .count();
        kpis.getToFix().setCount(String.valueOf(toFixRecords.size()));
        kpis.getToFix().setHighRiskRatio(toFixRecords.isEmpty()
                ? 0
                : (int) Math.round(highRiskToFixCount * 100.0 / toFixRecords.size()));

        List<Double> fixedDays = records.stream()
                .filter(this::isFixed)
                .filter(record -> inWindow(record.updatedAt, window))
                .filter(record -> record.createdAt != null && record.updatedAt != null)
                .map(record -> hoursBetween(record.createdAt, record.updatedAt) / 24.0)
                .toList();
        double averageFixDays = fixedDays.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        kpis.getAvgFixDays().setCount(formatDays(averageFixDays));
        kpis.getAvgFixDays().setProgress(averageFixDays <= 0
                ? 0
                : (int) Math.max(0, Math.min(100, Math.round(2.0 / averageFixDays * 100))));
        kpis.getAvgFixDays().setGapText(fixedDays.isEmpty()
                ? "当前窗口暂无修复完成记录"
                : averageFixDays <= 2.0
                ? "较目标快 " + formatDays(2.0 - averageFixDays)
                : "距目标慢 " + formatDays(averageFixDays - 2.0));

        long verifySuccess = records.stream()
                .filter(record -> "VERIFIED".equals(record.verifyStatus))
                .filter(record -> inWindow(record.updatedAt, window))
                .count();
        long verifyNotAffected = records.stream()
                .filter(record -> "NOT_AFFECTED".equals(record.verifyStatus))
                .filter(record -> inWindow(record.updatedAt, window))
                .count();
        long verifyTotal = verifySuccess + verifyNotAffected;
        int verifyRate = verifyTotal == 0 ? 0 : (int) Math.round(verifySuccess * 100.0 / verifyTotal);
        kpis.getVerifyRate().setCount(String.valueOf(verifyRate));
        kpis.getVerifyRate().setProgress(verifyRate);
        kpis.getVerifyRate().setGapText(verifyTotal == 0
                ? "当前窗口暂无验证回传"
                : "命中 " + verifySuccess + " / 不影响 " + verifyNotAffected);

        long onlineCount = hosts.stream().filter(host -> host.status == 1).count();
        int totalHosts = hosts.size();
        int onlineRate = totalHosts == 0 ? 0 : (int) Math.round(onlineCount * 100.0 / totalHosts);
        kpis.getClientOnline().setOnline((int) onlineCount);
        kpis.getClientOnline().setOffline(Math.max(totalHosts - (int) onlineCount, 0));
        kpis.getClientOnline().setTotal(totalHosts);
        kpis.getClientOnline().setRate(onlineRate);
    }

    private void fillTrend(VulnOpsDashboardResponseDTO.TrendDTO trend,
                           List<ResultRecord> records,
                           RangeWindow window) {
        trend.setMeta(window.metaLabel());
        for (Bucket bucket : buildBuckets(window)) {
            VulnOpsDashboardResponseDTO.TrendPointDTO point = new VulnOpsDashboardResponseDTO.TrendPointDTO();
            point.setLabel(bucket.label());
            point.setPending((int) records.stream()
                    .filter(record -> "PENDING".equals(record.verifyStatus))
                    .filter(record -> inBucket(record.createdAt, bucket))
                    .count());
            point.setVerifying((int) records.stream()
                    .filter(record -> "VERIFYING".equals(record.verifyStatus))
                    .filter(record -> inBucket(eventTime(record), bucket))
                    .count());
            point.setRepair((int) records.stream()
                    .filter(this::isToFix)
                    .filter(record -> inBucket(eventTime(record), bucket))
                    .count());
            point.setFixed((int) records.stream()
                    .filter(this::isFixed)
                    .filter(record -> inBucket(record.updatedAt, bucket))
                    .count());
            point.setNewCount((int) records.stream()
                    .filter(this::isRiskRecord)
                    .filter(record -> inBucket(record.createdAt, bucket))
                    .count());
            trend.getPoints().add(point);
        }
    }

    private void fillHighRisk(List<VulnOpsDashboardResponseDTO.HighRiskItemDTO> output,
                              List<ResultRecord> records) {
        Map<String, Aggregate> grouped = new LinkedHashMap<>();
        records.stream()
                .filter(this::isHighRiskOpenRisk)
                .forEach(record -> {
                    String key = firstNonBlank(record.category, record.productType, "未分类");
                    Aggregate aggregate = grouped.computeIfAbsent(key, ignored -> new Aggregate());
                    aggregate.count++;
                    if (record.hostId != null) {
                        aggregate.hostIds.add(record.hostId);
                    }
                });

        int total = grouped.values().stream().mapToInt(item -> item.count).sum();
        List<Map.Entry<String, Aggregate>> entries = grouped.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue().count, left.getValue().count))
                .limit(6)
                .toList();
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, Aggregate> entry = entries.get(index);
            VulnOpsDashboardResponseDTO.HighRiskItemDTO item = new VulnOpsDashboardResponseDTO.HighRiskItemDTO();
            item.setName(entry.getKey());
            item.setCount(entry.getValue().count);
            item.setHosts(entry.getValue().hostIds.size());
            item.setRatio(total == 0 ? 0 : (int) Math.round(entry.getValue().count * 100.0 / total));
            item.setColor(HIGH_RISK_COLORS.get(index % HIGH_RISK_COLORS.size()));
            output.add(item);
        }
    }

    private void fillSla(VulnOpsDashboardResponseDTO.SlaDTO sla,
                         List<ResultRecord> records,
                         RangeWindow window) {
        List<Double> fixedDays = records.stream()
                .filter(this::isFixed)
                .filter(record -> inWindow(record.updatedAt, window))
                .filter(record -> record.createdAt != null && record.updatedAt != null)
                .map(record -> hoursBetween(record.createdAt, record.updatedAt) / 24.0)
                .toList();

        sla.setAverageDays(roundOne(fixedDays.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)));
        sla.setTargetDays(2);
        sla.setMaxDays(16);
        sla.getBuckets().add(bucket("0-1天", countBetween(fixedDays, 0.0, 1.0, true), false));
        sla.getBuckets().add(bucket("1-3天", countBetween(fixedDays, 1.0, 3.0, false), true));
        sla.getBuckets().add(bucket("3-7天", countBetween(fixedDays, 3.0, 7.0, false), true));
        sla.getBuckets().add(bucket("7-14天", countBetween(fixedDays, 7.0, 14.0, false), true));
        sla.getBuckets().add(bucket(">14天", (int) fixedDays.stream().filter(day -> day > 14.0).count(), true));
    }

    private void fillVerifyTrend(VulnOpsDashboardResponseDTO.VerifyTrendDTO verifyTrend,
                                 List<ResultRecord> records,
                                 RangeWindow window) {
        for (Bucket bucket : buildBuckets(window)) {
            VulnOpsDashboardResponseDTO.VerifyPointDTO point = new VulnOpsDashboardResponseDTO.VerifyPointDTO();
            point.setLabel(bucket.label());
            point.setSuccess((int) records.stream()
                    .filter(record -> "VERIFIED".equals(record.verifyStatus))
                    .filter(record -> inBucket(record.updatedAt, bucket))
                    .count());
            point.setFail((int) records.stream()
                    .filter(record -> "NOT_AFFECTED".equals(record.verifyStatus))
                    .filter(record -> inBucket(record.updatedAt, bucket))
                    .count());
            point.setTotal(point.getSuccess() + point.getFail());
            point.setRate(point.getTotal() == 0 ? 0 : (int) Math.round(point.getSuccess() * 100.0 / point.getTotal()));
            verifyTrend.getPoints().add(point);
        }
    }

    private void fillClientTrend(VulnOpsDashboardResponseDTO.ClientTrendDTO clientTrend,
                                 List<HostRecord> hosts,
                                 RangeWindow window) {
        int totalHosts = hosts.size();
        for (Bucket bucket : buildBuckets(window)) {
            int activeCount = (int) hosts.stream()
                    .filter(host -> host.updatedAt != null)
                    .filter(host -> inBucket(host.updatedAt, bucket))
                    .count();
            VulnOpsDashboardResponseDTO.ClientPointDTO point = new VulnOpsDashboardResponseDTO.ClientPointDTO();
            point.setLabel(bucket.label());
            point.setOnline(activeCount);
            point.setRate(totalHosts == 0 ? 0 : (int) Math.round(activeCount * 100.0 / totalHosts));
            clientTrend.getPoints().add(point);
        }

        hosts.stream()
                .filter(host -> host.status == 0)
                .sorted(Comparator.comparing((HostRecord host) -> host.updatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .limit(8)
                .forEach(host -> {
                    VulnOpsDashboardResponseDTO.OfflineHostDTO item = new VulnOpsDashboardResponseDTO.OfflineHostDTO();
                    item.setHostname(firstNonBlank(host.hostname, "Host #" + host.id));
                    item.setIp(firstNonBlank(host.ipv4, "-"));
                    item.setOfflineFor(formatOfflineFor(host.updatedAt));
                    clientTrend.getOfflineHosts().add(item);
                });
    }

    private List<ResultRecord> loadResultRecords(LocalDateTime endTime) {
        return jdbcTemplate.query("""
                        SELECT hvr.host_id,
                               hvr.rule_id,
                               hvr.severity,
                               hvr.verify_status,
                               hvr.created_at,
                               hvr.updated_at,
                               vr.category,
                               vr.title,
                               vr.product_type
                        FROM host_vuln_result hvr
                        LEFT JOIN vuln_rule vr ON vr.id = hvr.rule_id
                        WHERE hvr.status = 1
                          AND hvr.rule_id IS NOT NULL
                          AND hvr.rule_id > 0
                          AND hvr.created_at <= ?
                        """,
                (rs, rowNum) -> {
                    ResultRecord record = new ResultRecord();
                    record.hostId = rs.getLong("host_id");
                    record.ruleId = rs.getLong("rule_id");
                    record.severity = upper(rs.getString("severity"));
                    record.verifyStatus = upper(rs.getString("verify_status"));
                    record.createdAt = toLocalDateTime(rs.getTimestamp("created_at"));
                    record.updatedAt = toLocalDateTime(rs.getTimestamp("updated_at"));
                    record.category = rs.getString("category");
                    record.title = rs.getString("title");
                    record.productType = rs.getString("product_type");
                    return record;
                },
                Timestamp.valueOf(endTime));
    }

    private List<HostRecord> loadHosts() {
        return jdbcTemplate.query("""
                        SELECT id, hostname, ipv4, status, updated_at
                        FROM hosts
                        """,
                (rs, rowNum) -> {
                    HostRecord record = new HostRecord();
                    record.id = rs.getLong("id");
                    record.hostname = rs.getString("hostname");
                    record.ipv4 = rs.getString("ipv4");
                    record.status = rs.getInt("status");
                    record.updatedAt = toLocalDateTime(rs.getTimestamp("updated_at"));
                    return record;
                });
    }

    private RangeWindow resolveWindow(String range, LocalDate startDate, LocalDate endDate) {
        LocalDateTime now = LocalDateTime.now();
        if ("custom".equalsIgnoreCase(range)
                && startDate != null
                && endDate != null
                && !endDate.isBefore(startDate)) {
            return new RangeWindow(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), RangeType.CUSTOM);
        }
        if ("7d".equalsIgnoreCase(range)) {
            return new RangeWindow(now.minusDays(7), now, RangeType.D7);
        }
        if ("30d".equalsIgnoreCase(range)) {
            return new RangeWindow(now.minusDays(30), now, RangeType.D30);
        }
        if ("90d".equalsIgnoreCase(range)) {
            return new RangeWindow(now.minusDays(90), now, RangeType.D90);
        }
        return new RangeWindow(now.minusHours(24), now, RangeType.H24);
    }

    private List<Bucket> buildBuckets(RangeWindow window) {
        int count = switch (window.type()) {
            case H24 -> 8;
            case D7 -> 7;
            case D30 -> 10;
            case D90 -> 12;
            case CUSTOM -> 9;
        };
        Duration duration = Duration.between(window.start(), window.end());
        long stepSeconds = Math.max(1, duration.getSeconds() / count);

        List<Bucket> buckets = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            LocalDateTime bucketStart = window.start().plusSeconds(stepSeconds * index);
            LocalDateTime bucketEnd = index == count - 1
                    ? window.end()
                    : window.start().plusSeconds(stepSeconds * (index + 1));
            buckets.add(new Bucket(bucketStart, bucketEnd, formatBucketLabel(bucketStart, window.type(), index)));
        }
        return buckets;
    }

    private String formatBucketLabel(LocalDateTime time, RangeType type, int index) {
        return switch (type) {
            case H24 -> time.format(HOUR_LABEL);
            case D7, CUSTOM -> time.format(DAY_LABEL);
            case D30 -> "W" + (index + 1);
            case D90 -> time.format(MONTH_LABEL);
        };
    }

    private boolean isRiskRecord(ResultRecord record) {
        return record != null && !"NOT_AFFECTED".equals(record.verifyStatus);
    }

    private boolean isToFix(ResultRecord record) {
        return record != null && isToFixStatus(record.verifyStatus);
    }

    private boolean isToFixStatus(String verifyStatus) {
        return "VERIFIED".equals(verifyStatus)
                || "REPAIR_PENDING".equals(verifyStatus)
                || "TO_FIX".equals(verifyStatus);
    }

    private boolean isFixed(ResultRecord record) {
        return record != null && "FIXED".equals(record.verifyStatus);
    }

    private boolean isHighRiskOpenRisk(ResultRecord record) {
        return record != null
                && isHighRisk(record.severity)
                && !"FIXED".equals(record.verifyStatus)
                && !"NOT_AFFECTED".equals(record.verifyStatus);
    }

    private boolean isHighRisk(String severity) {
        return "CRITICAL".equals(severity) || "HIGH".equals(severity);
    }

    private LocalDateTime eventTime(ResultRecord record) {
        return record.updatedAt != null ? record.updatedAt : record.createdAt;
    }

    private boolean inWindow(LocalDateTime time, RangeWindow window) {
        return time != null && !time.isBefore(window.start()) && time.isBefore(window.end());
    }

    private boolean inBucket(LocalDateTime time, Bucket bucket) {
        return time != null && !time.isBefore(bucket.start()) && time.isBefore(bucket.end());
    }

    private int countBetween(List<Double> days, double startInclusive, double endInclusive, boolean includeStart) {
        return (int) days.stream()
                .filter(day -> includeStart ? day >= startInclusive : day > startInclusive)
                .filter(day -> day <= endInclusive)
                .count();
    }

    private VulnOpsDashboardResponseDTO.SlaBucketDTO bucket(String label, int count, boolean overTarget) {
        VulnOpsDashboardResponseDTO.SlaBucketDTO bucket = new VulnOpsDashboardResponseDTO.SlaBucketDTO();
        bucket.setLabel(label);
        bucket.setCount(count);
        bucket.setOverTarget(overTarget);
        return bucket;
    }

    private double percentDelta(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return ((double) current - previous) / previous * 100.0;
    }

    private long hoursBetween(LocalDateTime start, LocalDateTime end) {
        return Duration.between(start, end).toHours();
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private String formatDays(double value) {
        return String.format(Locale.ROOT, "%.1f天", Math.max(0.0, roundOne(value)));
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String formatOfflineFor(LocalDateTime updatedAt) {
        if (updatedAt == null) {
            return "未知";
        }
        Duration duration = Duration.between(updatedAt, LocalDateTime.now());
        long hours = Math.max(duration.toHours(), 0);
        long minutes = Math.max(duration.minusHours(hours).toMinutes(), 0);
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String kpiHtml(String label, String value, String hint) {
        return "<article class=\"card\">"
                + "<div class=\"label\">" + escapeHtml(label) + "</div>"
                + "<div class=\"value\">" + escapeHtml(value) + "</div>"
                + "<div class=\"hint\">" + escapeHtml(hint) + "</div>"
                + "</article>";
    }

    private String sectionTable(String title, List<String> headers, List<List<String>> rows) {
        StringBuilder html = new StringBuilder();
        html.append("<section class=\"section\">")
                .append("<h2>").append(escapeHtml(title)).append("</h2>")
                .append("<table><thead><tr>");
        for (String header : headers) {
            html.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        if (rows.isEmpty()) {
            html.append("<tr><td colspan=\"").append(headers.size()).append("\">暂无数据</td></tr>");
        } else {
            for (List<String> row : rows) {
                html.append("<tr>");
                for (String cell : row) {
                    html.append("<td>").append(cell).append("</td>");
                }
                html.append("</tr>");
            }
        }
        html.append("</tbody></table></section>");
        return html.toString();
    }

    private List<List<String>> trendRows(List<VulnOpsDashboardResponseDTO.TrendPointDTO> points) {
        return points.stream().map(point -> List.of(
                escapeHtml(valueOf(point.getLabel())),
                String.valueOf(point.getPending()),
                String.valueOf(point.getVerifying()),
                String.valueOf(point.getRepair()),
                String.valueOf(point.getFixed()),
                String.valueOf(point.getNewCount())
        )).toList();
    }

    private List<List<String>> highRiskRows(List<VulnOpsDashboardResponseDTO.HighRiskItemDTO> items) {
        return items.stream().map(item -> List.of(
                escapeHtml(valueOf(item.getName())),
                String.valueOf(item.getCount()),
                String.valueOf(item.getHosts()),
                item.getRatio() + "%"
        )).toList();
    }

    private List<List<String>> slaRows(VulnOpsDashboardResponseDTO.SlaDTO sla) {
        int total = sla.getBuckets().stream().mapToInt(VulnOpsDashboardResponseDTO.SlaBucketDTO::getCount).sum();
        return sla.getBuckets().stream().map(item -> List.of(
                escapeHtml(valueOf(item.getLabel())),
                String.valueOf(item.getCount()),
                progressBar(total == 0 ? 0 : (int) Math.round(item.getCount() * 100.0 / total),
                        item.isOverTarget() ? "#EF4444" : "#3B82F6")
        )).toList();
    }

    private List<List<String>> verifyRows(List<VulnOpsDashboardResponseDTO.VerifyPointDTO> points) {
        return points.stream().map(point -> List.of(
                escapeHtml(valueOf(point.getLabel())),
                String.valueOf(point.getTotal()),
                String.valueOf(point.getSuccess()),
                String.valueOf(point.getFail()),
                point.getRate() + "%"
        )).toList();
    }

    private List<List<String>> clientTrendRows(List<VulnOpsDashboardResponseDTO.ClientPointDTO> points) {
        return points.stream().map(point -> List.of(
                escapeHtml(valueOf(point.getLabel())),
                String.valueOf(point.getOnline()),
                point.getRate() + "%"
        )).toList();
    }

    private List<List<String>> offlineRows(List<VulnOpsDashboardResponseDTO.OfflineHostDTO> items) {
        return items.stream().map(item -> List.of(
                escapeHtml(valueOf(item.getHostname())),
                escapeHtml(valueOf(item.getIp())),
                escapeHtml(valueOf(item.getOfflineFor()))
        )).toList();
    }

    private String progressBar(int percent, String color) {
        return "<div class=\"bar\"><span style=\"width:" + Math.max(0, Math.min(100, percent))
                + "%;background:" + color + ";\"></span></div>";
    }

    private String valueOf(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static class Aggregate {
        private int count;
        private final Set<Long> hostIds = new HashSet<>();
    }

    private static class ResultRecord {
        private Long hostId;
        private Long ruleId;
        private String severity;
        private String verifyStatus;
        private String category;
        private String title;
        private String productType;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    private static class HostRecord {
        private Long id;
        private String hostname;
        private String ipv4;
        private Integer status;
        private LocalDateTime updatedAt;
    }

    private record Bucket(LocalDateTime start, LocalDateTime end, String label) {
    }

    private record RangeWindow(LocalDateTime start, LocalDateTime end, RangeType type) {
        private RangeWindow previousWindow() {
            Duration duration = Duration.between(start, end);
            return new RangeWindow(start.minus(duration), start, type);
        }

        private String metaLabel() {
            return switch (type) {
                case H24 -> "最近24小时";
                case D7 -> "最近7天";
                case D30 -> "最近30天";
                case D90 -> "最近90天";
                case CUSTOM -> start.toLocalDate() + " 至 " + end.minusSeconds(1).toLocalDate();
            };
        }
    }

    private enum RangeType {
        H24, D7, D30, D90, CUSTOM
    }
}
