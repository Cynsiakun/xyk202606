package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.dto.BaselineHostResultItemDTO;
import com.cd.dto.BaselineProblemHostDTO;
import com.cd.dto.BaselineRuleOptionDTO;
import com.cd.dto.BaselineTaskExportRowDTO;
import com.cd.dto.BaselineTaskListItemDTO;
import com.cd.dto.BaselineTaskResultOverviewDTO;
import com.cd.mapper.BaselineQueryMapper;
import com.cd.service.BaselineQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BaselineQueryServiceImpl implements BaselineQueryService {

    private static final DateTimeFormatter REPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int PDF_PROBLEM_LIMIT = 120;

    private final BaselineQueryMapper baselineQueryMapper;

    @Override
    public PageResult<BaselineTaskListItemDTO> listTasks(Integer page, Integer size, String keyword,
                                                         String executeType, String taskType, String status) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        String safeKeyword = normalizeText(keyword);
        String safeExecuteType = normalizeEnum(executeType, List.of("MANUAL", "SCHEDULED"));
        String safeTaskType = normalizeEnum(taskType, List.of("SCAN", "RECHECK"));
        String safeStatus = normalizeStatus(status);
        long total = baselineQueryMapper.countTasks(safeKeyword, safeExecuteType, safeTaskType, safeStatus);
        List<BaselineTaskListItemDTO> list = baselineQueryMapper.selectTaskPage(
                safeKeyword, safeExecuteType, safeTaskType, safeStatus, (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(total, list);
    }

    @Override
    public BaselineTaskResultOverviewDTO getResultOverview(Long taskId) {
        BaselineTaskResultOverviewDTO overview = baselineQueryMapper.selectResultOverview(taskId);
        if (overview == null) {
            overview = new BaselineTaskResultOverviewDTO();
            overview.setTaskId(taskId);
            overview.setTotalHostCount(0);
            overview.setFinishedHostCount(0);
            overview.setFailHostCount(0);
            overview.setProblemRuleCount(0);
            overview.setPassRuleCount(0);
            overview.setFailRuleCount(0);
            overview.setErrorRuleCount(0);
        }
        return overview;
    }

    @Override
    public PageResult<BaselineProblemHostDTO> listProblemHosts(Long taskId, Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        long total = baselineQueryMapper.countProblemHosts(taskId);
        List<BaselineProblemHostDTO> list = baselineQueryMapper.selectProblemHostPage(
                taskId, (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(total, list);
    }

    @Override
    public List<BaselineHostResultItemDTO> listTaskHostResults(Long taskId, Long hostId) {
        return baselineQueryMapper.selectTaskHostResults(taskId, hostId);
    }

    @Override
    public List<BaselineRuleOptionDTO> listRuleOptions(String keyword) {
        String trimmed = keyword == null ? null : keyword.trim();
        return baselineQueryMapper.selectRuleOptions(trimmed);
    }

    @Override
    public byte[] exportTaskResultCsv(Long taskId) {
        List<BaselineTaskExportRowDTO> rows = baselineQueryMapper.selectTaskExportRows(taskId);
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("主机ID,主机名,IP,规则ID,规则名称,分类,检测项,检测状态,修复状态,期望值,实际值,证据\n");
        for (BaselineTaskExportRowDTO row : rows) {
            builder.append(csv(row.getHostId())).append(',')
                    .append(csv(row.getHostName())).append(',')
                    .append(csv(row.getIpv4())).append(',')
                    .append(csv(row.getRuleId())).append(',')
                    .append(csv(row.getRuleName())).append(',')
                    .append(csv(row.getCategory())).append(',')
                    .append(csv(row.getCheckKey())).append(',')
                    .append(csv(row.getStatus())).append(',')
                    .append(csv(row.getRemediationStatus())).append(',')
                    .append(csv(row.getExpectedValue())).append(',')
                    .append(csv(row.getActualValue())).append(',')
                    .append(csv(row.getEvidence())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] exportTaskResultHtml(Long taskId) {
        return renderBeautifulHtmlReport(buildTaskReport(taskId)).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] exportTaskResultPdf(Long taskId) {
        return renderBeautifulPdfReport(buildTaskReport(taskId));
    }

    @Override
    public byte[] exportHostResultCsv(Long hostId) {
        List<BaselineTaskExportRowDTO> rows = baselineQueryMapper.selectHostExportRows(hostId);
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("主机ID,主机名,IP,规则ID,规则名称,分类,检测项,检测状态,修复状态,期望值,实际值,证据\n");
        for (BaselineTaskExportRowDTO row : rows) {
            builder.append(csv(row.getHostId())).append(',')
                    .append(csv(row.getHostName())).append(',')
                    .append(csv(row.getIpv4())).append(',')
                    .append(csv(row.getRuleId())).append(',')
                    .append(csv(row.getRuleName())).append(',')
                    .append(csv(row.getCategory())).append(',')
                    .append(csv(row.getCheckKey())).append(',')
                    .append(csv(row.getStatus())).append(',')
                    .append(csv(row.getRemediationStatus())).append(',')
                    .append(csv(row.getExpectedValue())).append(',')
                    .append(csv(row.getActualValue())).append(',')
                    .append(csv(row.getEvidence())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] exportHostResultHtml(Long hostId) {
        return renderBeautifulHtmlReport(buildHostReport(hostId)).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] exportHostResultPdf(Long hostId) {
        return renderBeautifulPdfReport(buildHostReport(hostId));
    }

    private BaselineReport buildTaskReport(Long taskId) {
        BaselineTaskResultOverviewDTO overview = getResultOverview(taskId);
        List<BaselineTaskExportRowDTO> rows = baselineQueryMapper.selectTaskExportRows(taskId);
        BaselineReport report = buildReport("TASK", taskId, "基线任务检测报告", "任务 #" + taskId, rows);
        report.finishedHostCount = value(overview.getFinishedHostCount());
        report.hostCount = value(overview.getTotalHostCount());
        report.problemHostCount = value(overview.getFailHostCount());
        report.complianceRate = overview.getAvgComplianceRate();
        report.passCount = value(overview.getPassRuleCount());
        report.failCount = value(overview.getFailRuleCount());
        report.errorCount = value(overview.getErrorRuleCount());
        report.totalCount = report.passCount + report.failCount + report.errorCount;
        report.summary = "本报告汇总当前基线任务的检测结果，重点展示存在风险的主机、规则分类和未通过项。";
        return report;
    }

    private BaselineReport buildHostReport(Long hostId) {
        List<BaselineTaskExportRowDTO> rows = baselineQueryMapper.selectHostExportRows(hostId);
        String subject = "主机 #" + hostId;
        if (!rows.isEmpty()) {
            BaselineTaskExportRowDTO first = rows.get(0);
            subject = text(first.getHostName(), "主机 #" + hostId);
            if (hasText(first.getIpv4())) {
                subject += " / " + first.getIpv4();
            }
        }
        BaselineReport report = buildReport("HOST", hostId, "主机基线合规报告", subject, rows);
        report.hostCount = 1;
        report.finishedHostCount = rows.isEmpty() ? 0 : 1;
        report.problemHostCount = report.failCount + report.errorCount > 0 ? 1 : 0;
        report.summary = "本报告展示当前主机最新一次基线检测快照，用于快速判断该主机的合规状态和主要问题。";
        return report;
    }

    private BaselineReport buildReport(String scopeType, Long scopeId, String title, String subject,
                                       List<BaselineTaskExportRowDTO> rows) {
        BaselineReport report = new BaselineReport();
        report.scopeType = scopeType;
        report.scopeId = scopeId;
        report.title = title;
        report.subject = subject;
        report.generatedAt = LocalDateTime.now();
        report.rows = rows == null ? List.of() : rows;
        report.totalCount = report.rows.size();
        Set<Long> hosts = new LinkedHashSet<>();
        Set<Long> problemHosts = new LinkedHashSet<>();
        Map<String, CategoryStat> categoryStats = new LinkedHashMap<>();
        Map<String, List<BaselineTaskExportRowDTO>> problemRowsByCategory = new LinkedHashMap<>();
        LocalDateTime latestScanTime = null;
        for (BaselineTaskExportRowDTO row : report.rows) {
            String status = normalizeStatusValue(row.getStatus());
            if (row.getHostId() != null) {
                hosts.add(row.getHostId());
            }
            if (row.getScanTime() != null && (latestScanTime == null || row.getScanTime().isAfter(latestScanTime))) {
                latestScanTime = row.getScanTime();
            }
            if ("PASS".equals(status)) {
                report.passCount++;
            } else if ("ERROR".equals(status)) {
                report.errorCount++;
                if (row.getHostId() != null) {
                    problemHosts.add(row.getHostId());
                }
                report.problemRows.add(row);
                problemRowsByCategory.computeIfAbsent(text(row.getCategory(), "未分类"), ignored -> new ArrayList<>()).add(row);
                countRemediationAndTicket(report, row);
            } else {
                report.failCount++;
                if (row.getHostId() != null) {
                    problemHosts.add(row.getHostId());
                }
                report.problemRows.add(row);
                problemRowsByCategory.computeIfAbsent(text(row.getCategory(), "未分类"), ignored -> new ArrayList<>()).add(row);
                countRemediationAndTicket(report, row);
            }
            CategoryStat stat = categoryStats.computeIfAbsent(text(row.getCategory(), "未分类"), CategoryStat::new);
            stat.total++;
            if ("PASS".equals(status)) {
                stat.pass++;
            } else if ("ERROR".equals(status)) {
                stat.error++;
            } else {
                stat.fail++;
            }
        }
        if (report.hostCount == 0) {
            report.hostCount = hosts.size();
        }
        if (report.problemHostCount == 0) {
            report.problemHostCount = problemHosts.size();
        }
        if (report.complianceRate == null && report.totalCount > 0) {
            report.complianceRate = BigDecimal.valueOf(report.passCount * 100.0 / report.totalCount);
        }
        report.categoryStats = categoryStats.values().stream()
                .sorted(Comparator.comparingInt((CategoryStat stat) -> stat.fail + stat.error).reversed()
                        .thenComparing(stat -> stat.name))
                .toList();
        report.problemRowsByCategory = problemRowsByCategory;
        report.checkTime = latestScanTime;
        fillReportSubjectFields(report, hosts);
        report.recommendations = buildRecommendations(report);
        return report;
    }

    private void countRemediationAndTicket(BaselineReport report, BaselineTaskExportRowDTO row) {
        String remediation = normalizeTextValue(row.getRemediationStatus());
        if (isRollbackStatus(remediation)) {
            report.rolledBackCount++;
        } else if (isFixedStatus(remediation)) {
            report.fixedCount++;
        } else {
            report.unfixedCount++;
        }

        String workorder = normalizeTextValue(row.getWorkorderStatus());
        if ("OPEN".equals(workorder)) {
            report.workorderOpenCount++;
        } else if ("PROCESSING".equals(workorder)) {
            report.workorderProcessingCount++;
        } else if ("DONE".equals(workorder)) {
            report.workorderDoneCount++;
        }
    }

    private void fillReportSubjectFields(BaselineReport report, Set<Long> hosts) {
        if (report.rows.isEmpty()) {
            report.hostName = report.subject;
            report.ipv4 = "-";
            report.osName = "-";
            return;
        }
        BaselineTaskExportRowDTO first = report.rows.get(0);
        if (hosts.size() <= 1) {
            report.hostName = text(first.getHostName(), first.getHostId() == null ? report.subject : "主机 #" + first.getHostId());
            report.ipv4 = text(first.getIpv4(), "-");
            report.osName = text(first.getOsName(), "-");
            return;
        }
        report.hostName = "多主机任务（" + hosts.size() + " 台）";
        report.ipv4 = "详见明细";
        report.osName = "多系统";
    }

    private List<String> buildRecommendations(BaselineReport report) {
        List<String> recommendations = new ArrayList<>();
        Set<String> categories = report.problemRowsByCategory.keySet();
        if (categories.stream().anyMatch(category -> category.contains("账户") || category.contains("账号") || category.contains("密码"))) {
            recommendations.add("账户安全存在异常，建议优先检查密码策略、账户权限和共享账号使用情况。");
        }
        if (categories.stream().anyMatch(category -> category.contains("日志") || category.contains("审计"))) {
            recommendations.add("日志审计存在异常，建议检查安全日志、登录审计和关键操作审计策略。");
        }
        if (categories.stream().anyMatch(category -> category.contains("服务"))) {
            recommendations.add("服务安全存在异常，建议关闭不必要服务并核查关键服务启动策略。");
        }
        if (categories.stream().anyMatch(category -> category.contains("网络") || category.toLowerCase(Locale.ROOT).contains("tcp"))) {
            recommendations.add("网络安全配置存在异常，建议检查 TCP/IP 安全参数、远程访问和端口暴露情况。");
        }
        if (report.problemRows.stream().anyMatch(row -> isHighRisk(row.getSeverity()))) {
            recommendations.add("检测到 High 或 Critical 风险规则，建议优先安排整改并完成复检闭环。");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("当前范围内未发现明显异常，建议保持定期基线检查和变更后复检。");
        }
        return recommendations;
    }

    private boolean isFixedStatus(String status) {
        return "FIXED".equals(status) || "COMPLETED".equals(status) || "SUCCESS".equals(status);
    }

    private boolean isRollbackStatus(String status) {
        return "ROLLED_BACK".equals(status) || "ROLLBACK".equals(status);
    }

    private boolean isHighRisk(String severity) {
        String value = normalizeTextValue(severity);
        return "HIGH".equals(value) || "CRITICAL".equals(value);
    }

    private String renderBeautifulHtmlReport(BaselineReport report) {
        int issueCount = report.failCount + report.errorCount;
        String passPct = pct(report.passCount, Math.max(1, report.totalCount));
        String failPct = pct(report.failCount, Math.max(1, report.totalCount));
        String errorPct = pct(report.errorCount, Math.max(1, report.totalCount));
        String levelClass = reportLevelClass(report.complianceRate, issueCount);
        String levelText = reportLevelText(report.complianceRate, issueCount);
        StringBuilder html = new StringBuilder("\uFEFF");
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<title>").append(escapeHtml(report.title)).append("</title>")
                .append("<style>")
                .append(":root{--ink:#172033;--muted:#667085;--line:#d9e0ea;--soft:#f4f7fb;--brand:#176b87;--green:#159a69;--red:#d92d20;--amber:#d68a00;--blue:#2563eb;}")
                .append("*{box-sizing:border-box}body{margin:0;background:#eef2f6;color:var(--ink);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',Arial,sans-serif;}")
                .append(".report{max-width:1180px;margin:0 auto;padding:32px 28px 58px}.hero{position:relative;overflow:hidden;border-radius:8px;background:linear-gradient(135deg,#102a43,#155e75 58%,#0f766e);color:#fff;padding:34px 38px 32px;box-shadow:0 18px 40px rgba(16,42,67,.18)}")
                .append(".hero:after{content:'';position:absolute;right:-90px;top:-120px;width:320px;height:320px;border-radius:50%;border:44px solid rgba(255,255,255,.08)}")
                .append(".kicker{font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#b7f7df;font-weight:700}.hero h1{margin:10px 0 10px;font-size:34px;letter-spacing:0;font-weight:800}.hero p{max-width:760px;margin:0;color:#d8f1ea;line-height:1.7}")
                .append(".hero-meta{display:grid;grid-template-columns:1.4fr 1fr 1.2fr 1.2fr 1fr;gap:12px;margin-top:24px;position:relative;z-index:1}.hero-meta div{background:rgba(255,255,255,.11);border:1px solid rgba(255,255,255,.16);border-radius:8px;padding:12px 14px}.hero-meta label,.metric label,.metrics .card label{display:block;font-size:12px;color:#8b99aa;margin-bottom:7px}.hero-meta label{color:#c9e7df}.hero-meta strong{display:block;font-size:17px;color:#fff}")
                .append(".verdict{display:inline-flex;align-items:center;gap:8px;margin-top:18px;padding:8px 12px;border-radius:999px;font-weight:800;background:#fff;color:#102a43}.verdict.good{color:#087443}.verdict.warn{color:#9a5b00}.verdict.bad{color:#b42318}")
                .append(".metrics{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin:18px 0}.metrics .card,.metric{background:#fff;border:1px solid var(--line);border-radius:8px;padding:15px 16px;box-shadow:0 8px 22px rgba(16,24,40,.05)}.metrics .card strong,.metric strong{font-size:26px;line-height:1}.metrics .card .good,.metric .good{color:var(--green)}.metrics .card .bad,.metric .bad{color:var(--red)}.metrics .card .warn,.metric .warn{color:var(--amber)}")
                .append(".layout{display:grid;grid-template-columns:360px 1fr;gap:16px}.panel{background:#fff;border:1px solid var(--line);border-radius:8px;padding:20px;box-shadow:0 8px 22px rgba(16,24,40,.04);margin-bottom:16px}.panel h2{margin:0 0 14px;font-size:20px}.hint{color:var(--muted);font-size:13px;line-height:1.65;margin:8px 0 0}")
                .append(".score{display:grid;place-items:center;width:240px;height:240px;margin:6px auto 16px;border-radius:50%;background:conic-gradient(var(--green) 0 ")
                .append(passPct).append(",var(--red) ").append(passPct).append(" calc(").append(passPct).append(" + ")
                .append(failPct).append("),var(--amber) calc(").append(passPct).append(" + ").append(failPct).append(") 100%)}")
                .append(".score-inner{display:grid;place-items:center;width:156px;height:156px;border-radius:50%;background:#fff;text-align:center;box-shadow:inset 0 0 0 1px var(--line)}.score-inner strong{font-size:34px}.score-inner span{font-size:12px;color:var(--muted)}")
                .append(".legend{display:flex;gap:12px;justify-content:center;flex-wrap:wrap}.legend span{font-size:12px;color:var(--muted)}.dot{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:5px}.g{background:var(--green)}.r{background:var(--red)}.a{background:var(--amber)}")
                .append(".stack{height:16px;border-radius:999px;overflow:hidden;background:#edf1f6;display:flex;margin:12px 0}.stack span.pass{background:var(--green);width:")
                .append(passPct).append("}.stack span.fail{background:var(--red);width:").append(failPct)
                .append("}.stack span.error{background:var(--amber);width:").append(errorPct).append("}")
                .append(".cat{display:grid;grid-template-columns:155px 1fr 76px;gap:12px;align-items:center;margin:12px 0;font-size:13px}.cat strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.bar{height:12px;background:#eef2f7;border-radius:999px;overflow:hidden}.bar span{display:block;height:100%;background:linear-gradient(90deg,#ef4444,#f59e0b)}.cat small{color:var(--muted)}")
                .append(".summary{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.summary div{background:var(--soft);border-radius:8px;padding:12px}.summary b{display:block;font-size:18px}.summary span{font-size:12px;color:var(--muted)}")
                .append(".issue-grid{display:grid;gap:12px}.category-block{border:1px solid #e2e8f0;border-radius:8px;padding:16px;background:#fbfcfe;margin-bottom:14px}.category-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.category-head h3{margin:0;font-size:17px}.issue-card{border:1px solid #e2e8f0;border-left:5px solid var(--red);border-radius:8px;padding:14px;background:#fff;margin-top:10px}.issue-card.error{border-left-color:var(--amber)}.issue-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.issue-title{font-weight:800;font-size:15px}.issue-sub{font-size:12px;color:var(--muted);margin-top:5px}.kv{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:12px}.kv div{background:#f8fafc;border-radius:6px;padding:10px}.kv label{display:block;font-size:12px;color:var(--muted);margin-bottom:4px}.evidence{margin-top:10px;color:#475467;font-size:12px;line-height:1.6;background:#fbfcfe;border-radius:6px;padding:10px}")
                .append(".tag,.sev{display:inline-flex;align-items:center;border-radius:999px;padding:4px 9px;font-size:12px;font-weight:800}.tag.pass{background:#dcfce7;color:#087443}.tag.fail{background:#fee2e2;color:#b42318}.tag.error{background:#fef3c7;color:#92400e}.sev.low{background:#e0f2fe;color:#075985}.sev.medium{background:#fef3c7;color:#92400e}.sev.high{background:#ffedd5;color:#c2410c}.sev.critical{background:#fee2e2;color:#991b1b}.empty{padding:28px;text-align:center;color:var(--muted);background:#f8fafc;border-radius:8px}.advice{display:grid;gap:10px}.advice div{border-left:4px solid var(--brand);background:#f8fafc;border-radius:8px;padding:12px 14px;line-height:1.6}")
                .append(".table-wrap{display:none;overflow:auto}.rule-table-wrap{overflow:auto}table{width:100%;border-collapse:collapse}th,td{border-bottom:1px solid #e5e9f0;padding:11px 10px;text-align:left;vertical-align:top;font-size:13px}th{position:sticky;top:0;background:#f8fafc;color:#475467;font-weight:800}.print-tip{color:var(--muted);font-size:12px;margin-top:18px}")
                .append("@media(max-width:900px){.hero-meta,.metrics,.layout,.summary{grid-template-columns:1fr}.report{padding:18px 14px}.hero{padding:26px 22px}.hero h1{font-size:28px}}@media print{body{background:#fff}.report{padding:0}.hero,.panel,.metric,.issue-card{break-inside:avoid;box-shadow:none}.print-tip{display:none}}")
                .append("</style></head><body><main class=\"report\">");

        html.append("<section class=\"hero\"><div class=\"kicker\">Security Baseline Report</div><h1>")
                .append(escapeHtml(report.title)).append("</h1><p>").append(escapeHtml(report.summary))
                .append("</p><div class=\"verdict ").append(levelClass).append("\">").append(escapeHtml(levelText))
                .append("</div><div class=\"hero-meta\">")
                .append(meta("主机名称", text(report.hostName, "-")))
                .append(meta("IP 地址", text(report.ipv4, "-")))
                .append(meta("操作系统", text(report.osName, "-")))
                .append(meta("检查时间", formatDateTime(report.checkTime)))
                .append(meta("合规率", formatRate(report.complianceRate))).append("</div></section>");
        html.append("<section class=\"metrics\">")
                .append(card("检测项", report.totalCount, ""))
                .append(card("通过", report.passCount, "good"))
                .append(card("未通过", report.failCount, "bad"))
                .append(card("异常", report.errorCount, "warn"))
                .append(card("影响主机", report.problemHostCount, "bad"))
                .append("</section>");
        html.append("<section class=\"metrics\">")
                .append(card("已修复", report.fixedCount, "good"))
                .append(card("未修复", report.unfixedCount, "bad"))
                .append(card("已回滚", report.rolledBackCount, "warn"))
                .append(card("OPEN 工单", report.workorderOpenCount, "bad"))
                .append(card("PROCESSING", report.workorderProcessingCount, "warn"))
                .append("</section>");
        html.append("<section class=\"metrics\">")
                .append(card("DONE 工单", report.workorderDoneCount, "good"))
                .append(card("FAIL 数量", report.failCount, "bad"))
                .append(card("ERROR 数量", report.errorCount, "warn"))
                .append(card("PASS 数量", report.passCount, "good"))
                .append(card("问题分类", report.problemRowsByCategory.size(), "bad"))
                .append("</section>");
        html.append("<section class=\"layout\"><div><div class=\"panel\"><h2>整体合规状态</h2><div class=\"score\"><div class=\"score-inner\"><strong>")
                .append(escapeHtml(formatRate(report.complianceRate))).append("</strong><span>综合合规率</span></div></div><div class=\"legend\"><span><i class=\"dot g\"></i>通过 ")
                .append(report.passCount).append("</span><span><i class=\"dot r\"></i>未通过 ").append(report.failCount)
                .append("</span><span><i class=\"dot a\"></i>异常 ").append(report.errorCount).append("</span></div>")
                .append("<div class=\"stack\"><span class=\"pass\"></span><span class=\"fail\"></span><span class=\"error\"></span></div>")
                .append("<p class=\"hint\">图中比例直接来自当前导出范围内的检测结果，用于快速判断风险集中程度。</p></div>");
        html.append("<div class=\"panel\"><h2>报告解读</h2><div class=\"summary\"><div><b>")
                .append(issueCount).append("</b><span>需关注检测项</span></div><div><b>")
                .append(report.problemHostCount).append("</b><span>受影响主机</span></div><div><b>")
                .append(report.categoryStats.size()).append("</b><span>涉及分类</span></div></div><p class=\"hint\">优先处理未通过和异常项；证据摘要用于快速定位实际值与基线期望的差异。</p></div></div>");
        html.append("<div><div class=\"panel\"><h2>问题分类排行</h2>");
        if (report.categoryStats.isEmpty()) {
            html.append("<div class=\"empty\">暂无分类数据</div>");
        } else {
            for (CategoryStat stat : report.categoryStats) {
                int categoryIssueCount = stat.fail + stat.error;
                html.append("<div class=\"cat\"><strong title=\"").append(escapeHtml(stat.name)).append("\">")
                        .append(escapeHtml(stat.name)).append("</strong><div class=\"bar\"><span style=\"width:")
                        .append(pct(categoryIssueCount, Math.max(1, stat.total))).append("\"></span></div><small>")
                        .append(categoryIssueCount).append(" / ").append(stat.total).append("</small></div>");
            }
        }
        html.append("</div></div></section>");
        html.append("<section class=\"panel\" style=\"display:none\"><h2>重点问题清单</h2>");
        if (report.problemRows.isEmpty()) {
            html.append("<div class=\"empty\">当前范围内没有未通过或异常的检测项。</div>");
        } else {
            html.append("<div class=\"issue-grid\">");
            for (BaselineTaskExportRowDTO row : report.problemRows) {
                String status = normalizeStatusValue(row.getStatus());
                String issueClass = "ERROR".equals(status) ? " error" : "";
                html.append("<article class=\"issue-card").append(issueClass).append("\"><div class=\"issue-head\"><div><div class=\"issue-title\">")
                        .append(escapeHtml(text(row.getRuleName(), "未命名规则"))).append("</div><div class=\"issue-sub\">")
                        .append(escapeHtml(rowSubject(row))).append(" · ").append(escapeHtml(text(row.getCategory(), "未分类")))
                        .append(" · 检测项：").append(escapeHtml(text(row.getCheckKey(), "-"))).append("</div></div>")
                        .append(statusTag(status)).append("</div><div class=\"kv\"><div><label>基线期望</label>")
                        .append(escapeHtml(shortText(row.getExpectedValue(), 120))).append("</div><div><label>当前实际</label>")
                        .append(escapeHtml(shortText(row.getActualValue(), 120))).append("</div></div><div class=\"evidence\"><b>证据摘要：</b>")
                        .append(escapeHtml(shortText(row.getEvidence(), 180))).append("</div></article>");
            }
            html.append("</div>");
        }
        html.append("</section>");
        html.append(renderGroupedProblemHtml(report));
        html.append(renderProblemRuleTableHtml(report));
        html.append(renderAdviceHtml(report));
        html.append("<section class=\"panel\" style=\"display:none\"><h2>完整明细</h2><div class=\"table-wrap\"><table><thead><tr><th>对象</th><th>规则</th><th>分类</th><th>状态</th><th>期望值</th><th>实际值</th></tr></thead><tbody>");
        for (BaselineTaskExportRowDTO row : report.rows) {
            html.append("<tr><td>").append(escapeHtml(rowSubject(row))).append("</td><td>")
                    .append(escapeHtml(text(row.getRuleName(), "-"))).append("<div class=\"issue-sub\">")
                    .append(escapeHtml(text(row.getCheckKey(), "-"))).append("</div></td><td>")
                    .append(escapeHtml(text(row.getCategory(), "未分类"))).append("</td><td>")
                    .append(statusTag(row.getStatus())).append("</td><td>")
                    .append(escapeHtml(shortText(row.getExpectedValue(), 80))).append("</td><td>")
                    .append(escapeHtml(shortText(row.getActualValue(), 80))).append("</td></tr>");
        }
        html.append("</tbody></table></div></section><div class=\"print-tip\">HTML 报告可直接在浏览器中打印或投屏展示；PDF 与 HTML 使用同一份统计数据。</div></main></body></html>");
        return html.toString();

    }

    private String renderGroupedProblemHtml(BaselineReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<section class=\"panel\"><h2>问题项按分类展示</h2>");
        if (report.problemRowsByCategory.isEmpty()) {
            html.append("<div class=\"empty\">当前范围内没有 FAIL 或 ERROR 项。</div>");
        } else {
            for (Map.Entry<String, List<BaselineTaskExportRowDTO>> entry : report.problemRowsByCategory.entrySet()) {
                html.append("<div class=\"category-block\"><div class=\"category-head\"><h3>")
                        .append(escapeHtml(entry.getKey())).append("</h3><span class=\"tag fail\">异常 ")
                        .append(entry.getValue().size()).append("</span></div>");
                for (BaselineTaskExportRowDTO row : entry.getValue()) {
                    String status = normalizeStatusValue(row.getStatus());
                    String issueClass = "ERROR".equals(status) ? " error" : "";
                    html.append("<article class=\"issue-card").append(issueClass).append("\"><div class=\"issue-head\"><div><div class=\"issue-title\">")
                            .append(escapeHtml(text(row.getRuleName(), "未命名规则"))).append("</div><div class=\"issue-sub\">")
                            .append(escapeHtml(rowSubject(row))).append(" · 检测项：")
                            .append(escapeHtml(text(row.getCheckKey(), "-"))).append("</div></div><div>")
                            .append(severityTag(row.getSeverity())).append(" ").append(statusTag(status))
                            .append("</div></div><div class=\"kv\"><div><label>实际值</label>")
                            .append(escapeHtml(shortText(row.getActualValue(), 120))).append("</div><div><label>期望值</label>")
                            .append(escapeHtml(shortText(row.getExpectedValue(), 120))).append("</div></div></article>");
                }
                html.append("</div>");
            }
        }
        html.append("</section>");
        return html.toString();
    }

    private String renderProblemRuleTableHtml(BaselineReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<section class=\"panel\"><h2>问题规则列表</h2>");
        if (report.problemRows.isEmpty()) {
            html.append("<div class=\"empty\">暂无问题规则。</div>");
        } else {
            html.append("<div class=\"rule-table-wrap\"><table><thead><tr><th>分类</th><th>规则名称</th><th>风险等级</th><th>实际值</th><th>期望值</th><th>状态</th></tr></thead><tbody>");
            for (BaselineTaskExportRowDTO row : report.problemRows) {
                html.append("<tr><td>").append(escapeHtml(text(row.getCategory(), "未分类"))).append("</td><td>")
                        .append(escapeHtml(text(row.getRuleName(), "-"))).append("</td><td>")
                        .append(severityTag(row.getSeverity())).append("</td><td>")
                        .append(escapeHtml(shortText(row.getActualValue(), 90))).append("</td><td>")
                        .append(escapeHtml(shortText(row.getExpectedValue(), 90))).append("</td><td>")
                        .append(statusTag(row.getStatus())).append("</td></tr>");
            }
            html.append("</tbody></table></div>");
        }
        html.append("</section>");
        return html.toString();
    }

    private String renderAdviceHtml(BaselineReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<section class=\"panel\"><h2>安全建议</h2><div class=\"advice\">");
        for (String recommendation : report.recommendations) {
            html.append("<div>").append(escapeHtml(recommendation)).append("</div>");
        }
        html.append("</div></section>");
        return html.toString();
    }

    private byte[] renderBeautifulPdfReport(BaselineReport report) {
        int issueCount = report.failCount + report.errorCount;
        SimplePdf pdf = new SimplePdf();
        pdf.addCover(report.title, text(report.hostName, report.subject), formatDateTime(report.checkTime),
                reportLevelText(report.complianceRate, issueCount), formatRate(report.complianceRate));
        pdf.addLine("IP地址：" + text(report.ipv4, "-") + "    操作系统：" + text(report.osName, "-")
                + "    生成时间：" + report.generatedAt.format(REPORT_TIME_FORMATTER));
        pdf.addMetricStrip(report.totalCount, report.passCount, report.failCount, report.errorCount, report.problemHostCount);
        pdf.addRemediationStrip(report.fixedCount, report.unfixedCount, report.rolledBackCount);
        pdf.addWorkorderStrip(report.workorderOpenCount, report.workorderProcessingCount, report.workorderDoneCount);
        pdf.addGap(10);
        pdf.addSection("报告摘要");
        pdf.addLine(report.summary);
        pdf.addLine("本报告仅包含当前导出范围内的数据库检测结果，未引入外部推断数据。");
        pdf.addGap(8);
        pdf.addSection("合规分布");
        pdf.addDistribution(report.passCount, report.failCount, report.errorCount);
        pdf.addGap(8);
        pdf.addSection("问题分类排行");
        if (report.categoryStats.isEmpty()) {
            pdf.addLine("暂无分类数据");
        } else {
            for (CategoryStat stat : report.categoryStats) {
                pdf.addBar(stat.name, stat.fail + stat.error, stat.total);
            }
        }
        pdf.addGap(8);
        pdf.addSection("问题项按分类展示");
        if (report.problemRowsByCategory.isEmpty()) {
            pdf.addLine("当前范围内没有 FAIL 或 ERROR 项。");
        } else {
            for (Map.Entry<String, List<BaselineTaskExportRowDTO>> entry : report.problemRowsByCategory.entrySet()) {
                pdf.addLine(entry.getKey() + "：异常 " + entry.getValue().size() + " 项");
            }
        }
        pdf.addGap(8);
        pdf.addSection("问题规则列表");
        if (report.problemRows.isEmpty()) {
            pdf.addLine("当前范围内没有未通过或异常的检测项。");
        } else {
            int count = 0;
            for (BaselineTaskExportRowDTO row : report.problemRows) {
                count++;
                if (count > PDF_PROBLEM_LIMIT) {
                    pdf.addLine("其余 " + (report.problemRows.size() - PDF_PROBLEM_LIMIT) + " 项请查看 HTML 或 CSV 报告。");
                    break;
                }
                pdf.addProblemCard(count, rowSubject(row),
                        text(row.getRuleName(), "未命名规则") + " / " + normalizeTextValue(row.getSeverity()),
                        text(row.getCategory(), "未分类"), normalizeStatusValue(row.getStatus()),
                        text(row.getExpectedValue(), "-"), text(row.getActualValue(), "-"),
                        text(row.getEvidence(), "-"));
            }
        }
        pdf.addGap(8);
        pdf.addSection("安全建议");
        for (String recommendation : report.recommendations) {
            pdf.addLine("• " + recommendation);
        }
        return pdf.finish();
    }

    private String renderHtmlReport(BaselineReport report) {
        String passPct = pct(report.passCount, Math.max(1, report.totalCount));
        String issuePct = pct(report.failCount + report.errorCount, Math.max(1, report.totalCount));
        StringBuilder html = new StringBuilder("\uFEFF");
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<title>").append(escapeHtml(report.title)).append("</title>")
                .append("<style>")
                .append("body{margin:0;background:#eef2f7;color:#172033;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',Arial,sans-serif;}")
                .append(".report{max-width:1120px;margin:0 auto;padding:34px 28px 56px;}")
                .append(".hero{background:#101828;color:#fff;padding:30px;border-radius:8px;border-left:6px solid #35b37e;}")
                .append(".kicker{font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#9ad8bd;margin-bottom:8px;}")
                .append("h1{margin:0 0 12px;font-size:30px;font-weight:700;}h2{margin:0 0 14px;font-size:20px;}p{line-height:1.65;}")
                .append(".meta{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-top:18px}.meta div{background:rgba(255,255,255,.08);padding:12px;border-radius:6px;}")
                .append(".meta label,.card label{display:block;color:#7a879c;font-size:12px;margin-bottom:5px}.hero .meta label{color:#b8c6d8}.meta strong,.card strong{font-size:18px;}")
                .append(".cards{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:18px 0}.card{background:#fff;border:1px solid #d8dee9;border-radius:8px;padding:16px;}")
                .append(".card strong{color:#101828}.good{color:#14845d}.bad{color:#c92a2a}.warn{color:#b76e00}")
                .append(".grid{display:grid;grid-template-columns:320px 1fr;gap:16px}.panel{background:#fff;border:1px solid #d8dee9;border-radius:8px;padding:18px;margin-bottom:16px;}")
                .append(".donut{width:220px;height:220px;border-radius:50%;margin:12px auto;background:conic-gradient(#19a974 0 ")
                .append(passPct).append(",#e03131 ").append(passPct).append(" calc(").append(passPct).append(" + ")
                .append(issuePct).append("),#f59f00 calc(").append(passPct).append(" + ").append(issuePct)
                .append(") 100%);display:grid;place-items:center;}")
                .append(".donut span{background:#fff;border-radius:50%;width:138px;height:138px;display:grid;place-items:center;text-align:center;font-size:24px;font-weight:700;}")
                .append(".bar{height:12px;background:#edf1f7;border-radius:999px;overflow:hidden}.bar span{display:block;height:100%;background:#e03131;}")
                .append(".cat{display:grid;grid-template-columns:150px 1fr 80px;gap:10px;align-items:center;margin:10px 0;font-size:13px;}")
                .append("table{width:100%;border-collapse:collapse;background:#fff}th,td{border-bottom:1px solid #e5e9f0;padding:10px;text-align:left;vertical-align:top;font-size:13px;}th{background:#f7f9fc;color:#475467;font-weight:600;}")
                .append(".tag{display:inline-block;border-radius:999px;padding:3px 8px;font-size:12px;font-weight:700}.tag.pass{background:#def7ec;color:#087443}.tag.fail{background:#ffe3e3;color:#b42318}.tag.error{background:#fff3bf;color:#8f5b00}")
                .append(".muted{color:#667085}.issue{color:#101828;font-weight:600}.small{font-size:12px;color:#667085}.print{margin-top:20px;color:#667085;font-size:12px}")
                .append("@media print{body{background:#fff}.report{padding:0}.hero,.panel,.card{break-inside:avoid}.print{display:none}}")
                .append("</style></head><body><main class=\"report\">");
        html.append("<section class=\"hero\"><div class=\"kicker\">Security Compliance Report</div><h1>")
                .append(escapeHtml(report.title)).append("</h1><p>").append(escapeHtml(report.summary)).append("</p><div class=\"meta\">")
                .append(meta("报告对象", report.subject))
                .append(meta("生成时间", report.generatedAt.format(REPORT_TIME_FORMATTER)))
                .append(meta("合规率", formatRate(report.complianceRate)))
                .append("</div></section>");
        html.append("<section class=\"cards\">")
                .append(card("检测项总数", report.totalCount, ""))
                .append(card("通过项", report.passCount, "good"))
                .append(card("问题项", report.failCount + report.errorCount, "bad"))
                .append(card("影响主机", report.problemHostCount, "warn"))
                .append("</section>");
        html.append("<section class=\"grid\"><div class=\"panel\"><h2>整体状态</h2><div class=\"donut\"><span>")
                .append(escapeHtml(formatRate(report.complianceRate))).append("<br><small class=\"small\">合规率</small></span></div>")
                .append("<p class=\"small\">绿色为通过项，红色为未通过项，黄色为检测异常项。</p></div>");
        html.append("<div class=\"panel\"><h2>问题分类</h2>");
        if (report.categoryStats.isEmpty()) {
            html.append("<p class=\"muted\">暂无分类数据</p>");
        } else {
            for (CategoryStat stat : report.categoryStats) {
                int issueCount = stat.fail + stat.error;
                html.append("<div class=\"cat\"><strong>").append(escapeHtml(stat.name)).append("</strong><div class=\"bar\"><span style=\"width:")
                        .append(pct(issueCount, Math.max(1, stat.total))).append("\"></span></div><span>")
                        .append(issueCount).append(" / ").append(stat.total).append("</span></div>");
            }
        }
        html.append("</div></section>");
        html.append("<section class=\"panel\"><h2>需要关注的问题</h2>");
        if (report.problemRows.isEmpty()) {
            html.append("<p class=\"muted\">当前范围内没有未通过或异常的检测项。</p>");
        } else {
            html.append("<table><thead><tr><th>对象</th><th>问题说明</th><th>分类</th><th>状态</th><th>期望 / 实际</th><th>证据摘要</th></tr></thead><tbody>");
            for (BaselineTaskExportRowDTO row : report.problemRows) {
                html.append("<tr><td>").append(escapeHtml(rowSubject(row))).append("</td><td><div class=\"issue\">")
                        .append(escapeHtml(text(row.getRuleName(), "未命名规则"))).append("</div><div class=\"small\">检测项：")
                        .append(escapeHtml(text(row.getCheckKey(), "-"))).append("</div></td><td>")
                        .append(escapeHtml(text(row.getCategory(), "未分类"))).append("</td><td>")
                        .append(statusTag(row.getStatus())).append("</td><td><div>期望：")
                        .append(escapeHtml(shortText(row.getExpectedValue(), 90))).append("</div><div>实际：")
                        .append(escapeHtml(shortText(row.getActualValue(), 90))).append("</div></td><td>")
                        .append(escapeHtml(shortText(row.getEvidence(), 120))).append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</section><div class=\"print\">可直接使用浏览器打印；系统导出的 PDF 与本报告使用相同数据。</div></main></body></html>");
        return html.toString();
    }

    private byte[] renderPdfReport(BaselineReport report) {
        SimplePdf pdf = new SimplePdf();
        pdf.addTitle(report.title);
        pdf.addLine("报告对象：" + report.subject);
        pdf.addLine("生成时间：" + report.generatedAt.format(REPORT_TIME_FORMATTER));
        pdf.addGap(8);
        pdf.addSection("摘要");
        pdf.addLine(report.summary);
        pdf.addLine("检测项总数：" + report.totalCount + "    通过：" + report.passCount
                + "    未通过：" + report.failCount + "    异常：" + report.errorCount);
        pdf.addLine("合规率：" + formatRate(report.complianceRate) + "    问题主机：" + report.problemHostCount);
        pdf.addGap(8);
        pdf.addSection("问题分类");
        if (report.categoryStats.isEmpty()) {
            pdf.addLine("暂无分类数据");
        } else {
            for (CategoryStat stat : report.categoryStats) {
                pdf.addBar(stat.name, stat.fail + stat.error, stat.total);
            }
        }
        pdf.addGap(8);
        pdf.addSection("需要关注的问题");
        if (report.problemRows.isEmpty()) {
            pdf.addLine("当前范围内没有未通过或异常的检测项。");
        } else {
            int count = 0;
            for (BaselineTaskExportRowDTO row : report.problemRows) {
                count++;
                if (count > PDF_PROBLEM_LIMIT) {
                    pdf.addLine("其余 " + (report.problemRows.size() - PDF_PROBLEM_LIMIT) + " 项请查看 HTML 或 CSV 报告。");
                    break;
                }
                pdf.addProblem(count, rowSubject(row), text(row.getRuleName(), "未命名规则"),
                        text(row.getCategory(), "未分类"), text(row.getStatus(), "-"),
                        text(row.getExpectedValue(), "-"), text(row.getActualValue(), "-"),
                        text(row.getEvidence(), "-"));
            }
        }
        return pdf.finish();
    }

    private String meta(String label, String value) {
        return "<div><label>" + escapeHtml(label) + "</label><strong>" + escapeHtml(value) + "</strong></div>";
    }

    private String reportLevelClass(BigDecimal rate, int issueCount) {
        if (rate == null) {
            return "warn";
        }
        double value = rate.doubleValue();
        if (issueCount == 0 || value >= 90) {
            return "good";
        }
        if (value >= 60) {
            return "warn";
        }
        return "bad";
    }

    private String reportLevelText(BigDecimal rate, int issueCount) {
        if (rate == null) {
            return "暂无足够数据";
        }
        double value = rate.doubleValue();
        if (issueCount == 0) {
            return "结论：当前范围未发现不合规项";
        }
        if (value >= 90) {
            return "结论：整体良好，仍有少量项目需跟进";
        }
        if (value >= 60) {
            return "结论：存在明显合规短板，建议安排整改";
        }
        return "结论：高风险，建议优先整改";
    }

    private String card(String label, int value, String cls) {
        return "<div class=\"card\"><label>" + escapeHtml(label) + "</label><strong class=\"" + cls + "\">" + value + "</strong></div>";
    }

    private String statusTag(String status) {
        String normalized = normalizeStatusValue(status);
        String cls = "PASS".equals(normalized) ? "pass" : ("ERROR".equals(normalized) ? "error" : "fail");
        return "<span class=\"tag " + cls + "\">" + escapeHtml(normalized) + "</span>";
    }

    private String severityTag(String severity) {
        String normalized = normalizeTextValue(severity);
        String text = normalized.isEmpty() ? "UNKNOWN" : normalized;
        return "<span class=\"sev " + severityClass(text) + "\">" + escapeHtml(text) + "</span>";
    }

    private String severityClass(String severity) {
        String value = normalizeTextValue(severity);
        if ("CRITICAL".equals(value)) {
            return "critical";
        }
        if ("HIGH".equals(value)) {
            return "high";
        }
        if ("MEDIUM".equals(value)) {
            return "medium";
        }
        return "low";
    }

    private String rowSubject(BaselineTaskExportRowDTO row) {
        String subject = text(row.getHostName(), row.getHostId() == null ? "-" : "主机 #" + row.getHostId());
        return hasText(row.getIpv4()) ? subject + " / " + row.getIpv4() : subject;
    }

    private String normalizeStatusValue(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return value.isEmpty() ? "FAIL" : value;
    }

    private String normalizeTextValue(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String text(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String formatRate(BigDecimal rate) {
        if (rate == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f%%", rate.doubleValue());
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(REPORT_TIME_FORMATTER);
    }

    private String pct(int part, int total) {
        if (total <= 0) {
            return "0%";
        }
        double value = Math.max(0, Math.min(100, part * 100.0 / total));
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private String shortText(String value, int maxLength) {
        String text = value == null ? "-" : value.replace('\r', ' ').replace('\n', ' ').trim();
        if (text.isEmpty()) {
            return "-";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String escapeHtml(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 200);
    }

    private String normalizeStatus(String status) {
        return status == null || status.trim().isEmpty() ? null : status.trim();
    }

    private String normalizeText(String text) {
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    private String normalizeEnum(String value, List<String> allowed) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return allowed.contains(normalized) ? normalized : null;
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }

    private static class BaselineReport {
        private String scopeType;
        private Long scopeId;
        private String title;
        private String subject;
        private String summary;
        private String hostName;
        private String ipv4;
        private String osName;
        private LocalDateTime checkTime;
        private LocalDateTime generatedAt;
        private int hostCount;
        private int finishedHostCount;
        private int problemHostCount;
        private int totalCount;
        private int passCount;
        private int failCount;
        private int errorCount;
        private int fixedCount;
        private int unfixedCount;
        private int rolledBackCount;
        private int workorderOpenCount;
        private int workorderProcessingCount;
        private int workorderDoneCount;
        private BigDecimal complianceRate;
        private List<BaselineTaskExportRowDTO> rows = List.of();
        private List<BaselineTaskExportRowDTO> problemRows = new ArrayList<>();
        private Map<String, List<BaselineTaskExportRowDTO>> problemRowsByCategory = new LinkedHashMap<>();
        private List<CategoryStat> categoryStats = List.of();
        private List<String> recommendations = List.of();
    }

    private static class CategoryStat {
        private final String name;
        private int total;
        private int pass;
        private int fail;
        private int error;

        private CategoryStat(String name) {
            this.name = name;
        }
    }

    private static class SimplePdf {
        private static final float PAGE_WIDTH = 595F;
        private static final float PAGE_HEIGHT = 842F;
        private static final float LEFT = 50F;
        private static final float RIGHT = 545F;
        private static final float BOTTOM = 56F;

        private final List<StringBuilder> pages = new ArrayList<>();
        private StringBuilder content;
        private float y;

        private SimplePdf() {
            newPage();
        }

        private void addCover(String title, String subject, String generatedAt, String verdict, String rate) {
            ensureSpace(150);
            rect(0F, PAGE_HEIGHT - 178F, PAGE_WIDTH, 178F, 0.06F, 0.20F, 0.29F);
            rect(0F, PAGE_HEIGHT - 178F, 8F, 178F, 0.10F, 0.65F, 0.49F);
            textColor("SECURITY BASELINE REPORT", 10, LEFT, PAGE_HEIGHT - 64F, 0.71F, 0.94F, 0.86F);
            textColor(title, 24, LEFT, PAGE_HEIGHT - 96F, 1F, 1F, 1F);
            textColor("对象：" + subject, 11, LEFT, PAGE_HEIGHT - 124F, 0.86F, 0.93F, 0.96F);
            textColor("生成时间：" + generatedAt, 10, LEFT, PAGE_HEIGHT - 144F, 0.77F, 0.86F, 0.91F);
            rect(388F, PAGE_HEIGHT - 136F, 132F, 56F, 1F, 1F, 1F);
            textColor("综合合规率", 9, 410F, PAGE_HEIGHT - 98F, 0.39F, 0.45F, 0.54F);
            textColor(rate, 20, 410F, PAGE_HEIGHT - 122F, 0.08F, 0.45F, 0.34F);
            y = PAGE_HEIGHT - 205F;
            addCallout(verdict);
        }

        private void addCallout(String text) {
            ensureSpace(38);
            rect(LEFT, y - 20F, RIGHT - LEFT, 30F, 0.94F, 0.98F, 0.96F);
            textColor(text, 11, LEFT + 14F, y - 10F, 0.06F, 0.39F, 0.28F);
            y -= 44F;
        }

        private void addMetricStrip(int total, int pass, int fail, int error, int problemHost) {
            ensureSpace(82);
            float cardWidth = 90F;
            float gap = 11F;
            addMetricCard(LEFT, y, cardWidth, "检测项", total, 0.10F, 0.23F, 0.32F);
            addMetricCard(LEFT + (cardWidth + gap), y, cardWidth, "通过", pass, 0.08F, 0.60F, 0.41F);
            addMetricCard(LEFT + (cardWidth + gap) * 2, y, cardWidth, "未通过", fail, 0.85F, 0.17F, 0.13F);
            addMetricCard(LEFT + (cardWidth + gap) * 3, y, cardWidth, "异常", error, 0.82F, 0.52F, 0F);
            addMetricCard(LEFT + (cardWidth + gap) * 4, y, cardWidth, "影响主机", problemHost, 0.20F, 0.33F, 0.78F);
            y -= 72F;
        }

        private void addRemediationStrip(int fixed, int unfixed, int rolledBack) {
            ensureSpace(82);
            float cardWidth = 150F;
            float gap = 22F;
            addMetricCard(LEFT, y, cardWidth, "已修复", fixed, 0.08F, 0.60F, 0.41F);
            addMetricCard(LEFT + cardWidth + gap, y, cardWidth, "未修复", unfixed, 0.85F, 0.17F, 0.13F);
            addMetricCard(LEFT + (cardWidth + gap) * 2, y, cardWidth, "已回滚", rolledBack, 0.82F, 0.52F, 0F);
            y -= 72F;
        }

        private void addWorkorderStrip(int open, int processing, int done) {
            ensureSpace(82);
            float cardWidth = 150F;
            float gap = 22F;
            addMetricCard(LEFT, y, cardWidth, "OPEN", open, 0.85F, 0.17F, 0.13F);
            addMetricCard(LEFT + cardWidth + gap, y, cardWidth, "PROCESSING", processing, 0.82F, 0.52F, 0F);
            addMetricCard(LEFT + (cardWidth + gap) * 2, y, cardWidth, "DONE", done, 0.08F, 0.60F, 0.41F);
            y -= 72F;
        }

        private void addMetricCard(float x, float top, float width, String label, int value, float r, float g, float b) {
            rect(x, top - 52F, width, 52F, 0.96F, 0.98F, 1F);
            rect(x, top - 52F, 4F, 52F, r, g, b);
            textColor(label, 8, x + 12F, top - 19F, 0.39F, 0.45F, 0.54F);
            textColor(String.valueOf(value), 18, x + 12F, top - 42F, r, g, b);
        }

        private void addDistribution(int pass, int fail, int error) {
            ensureSpace(50);
            int total = Math.max(1, pass + fail + error);
            float width = RIGHT - LEFT;
            float passWidth = width * pass / total;
            float failWidth = width * fail / total;
            float errorWidth = width * error / total;
            rect(LEFT, y - 18F, width, 16F, 0.92F, 0.94F, 0.97F);
            rect(LEFT, y - 18F, passWidth, 16F, 0.08F, 0.60F, 0.41F);
            rect(LEFT + passWidth, y - 18F, failWidth, 16F, 0.85F, 0.17F, 0.13F);
            rect(LEFT + passWidth + failWidth, y - 18F, errorWidth, 16F, 0.82F, 0.52F, 0F);
            y -= 34F;
            text("通过 " + pass + "    未通过 " + fail + "    异常 " + error, 10, LEFT, y);
            y -= 20F;
        }

        private void addTitle(String text) {
            ensureSpace(44);
            text(text, 20, LEFT, y);
            y -= 30;
            line(LEFT, y, RIGHT, y, 0.2F, 0.28F, 0.38F);
            y -= 18;
        }

        private void addSection(String text) {
            ensureSpace(34);
            text(text, 15, LEFT, y);
            y -= 22;
        }

        private void addLine(String text) {
            for (String line : wrap(text, 46)) {
                ensureSpace(18);
                text(line, 10, LEFT, y);
                y -= 15;
            }
        }

        private void addGap(float gap) {
            y -= gap;
        }

        private void addBar(String label, int issueCount, int total) {
            ensureSpace(28);
            int safeTotal = Math.max(1, total);
            float width = 260F * Math.max(0F, Math.min(1F, issueCount / (float) safeTotal));
            text(label, 10, LEFT, y);
            rect(210F, y - 9F, 260F, 8F, 0.92F, 0.94F, 0.97F);
            rect(210F, y - 9F, width, 8F, 0.88F, 0.19F, 0.19F);
            text(issueCount + " / " + total, 10, 482F, y);
            y -= 22;
        }

        private void addProblem(int index, String subject, String rule, String category, String status,
                                String expected, String actual, String evidence) {
            ensureSpace(96);
            text(index + ". " + rule + " [" + status + "]", 11, LEFT, y);
            y -= 16;
            text("对象：" + subject + "    分类：" + category, 9, LEFT + 12, y);
            y -= 14;
            addIndented("检测发现：实际值未达到基线要求。期望值：" + expected + "；实际值：" + actual, 9, LEFT + 12, 58);
            addIndented("证据摘要：" + evidence, 9, LEFT + 12, 58);
            y -= 4;
        }

        private void addProblemCard(int index, String subject, String rule, String category, String status,
                                    String expected, String actual, String evidence) {
            ensureSpace(112);
            float top = y + 4F;
            float cardHeight = 96F;
            float colorR = "ERROR".equals(status) ? 0.82F : 0.85F;
            float colorG = "ERROR".equals(status) ? 0.52F : 0.17F;
            float colorB = "ERROR".equals(status) ? 0F : 0.13F;
            rect(LEFT, top - cardHeight, RIGHT - LEFT, cardHeight, 0.99F, 1F, 1F);
            rect(LEFT, top - cardHeight, 5F, cardHeight, colorR, colorG, colorB);
            textColor(index + ". " + trimForPdf(rule, 34), 11, LEFT + 14F, y, 0.09F, 0.13F, 0.20F);
            textColor(status, 9, RIGHT - 52F, y, colorR, colorG, colorB);
            y -= 15F;
            textColor("对象：" + trimForPdf(subject, 42) + "    分类：" + trimForPdf(category, 18), 9, LEFT + 14F, y, 0.39F, 0.45F, 0.54F);
            y -= 16F;
            addIndented("期望：" + expected, 9, LEFT + 14F, 58);
            addIndented("实际：" + actual, 9, LEFT + 14F, 58);
            addIndented("证据：" + evidence, 9, LEFT + 14F, 58);
            y = top - cardHeight - 12F;
        }

        private void addIndented(String text, int size, float x, int maxChars) {
            for (String line : wrap(text, maxChars)) {
                ensureSpace(14);
                text(line, size, x, y);
                y -= 13;
            }
        }

        private byte[] finish() {
            List<byte[]> objects = new ArrayList<>();
            objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
            StringBuilder kids = new StringBuilder();
            int firstPageObject = 5;
            for (int i = 0; i < pages.size(); i++) {
                kids.append(firstPageObject + i * 2).append(" 0 R ");
            }
            objects.add(bytes("<< /Type /Pages /Count " + pages.size() + " /Kids [" + kids + "] >>"));
            objects.add(bytes("<< /Type /Font /Subtype /Type0 /BaseFont /STSong-Light /Encoding /UniGB-UCS2-H /DescendantFonts [4 0 R] >>"));
            objects.add(bytes("<< /Type /Font /Subtype /CIDFontType0 /BaseFont /STSong-Light /CIDSystemInfo << /Registry (Adobe) /Ordering (GB1) /Supplement 2 >> >>"));
            for (StringBuilder page : pages) {
                int contentObject = objects.size() + 2;
                objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_WIDTH + " " + PAGE_HEIGHT
                        + "] /Resources << /Font << /F1 3 0 R >> >> /Contents " + contentObject + " 0 R >>"));
                byte[] stream = page.toString().getBytes(StandardCharsets.ISO_8859_1);
                objects.add(bytes("<< /Length " + stream.length + " >>\nstream\n" + page + "\nendstream"));
            }
            return writePdf(objects);
        }

        private byte[] writePdf(List<byte[]> objects) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            write(out, "%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n");
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            for (int i = 0; i < objects.size(); i++) {
                offsets.add(out.size());
                write(out, (i + 1) + " 0 obj\n");
                out.writeBytes(objects.get(i));
                write(out, "\nendobj\n");
            }
            int xref = out.size();
            write(out, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
            for (int i = 1; i < offsets.size(); i++) {
                write(out, String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
            }
            write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF");
            return out.toByteArray();
        }

        private void newPage() {
            content = new StringBuilder();
            pages.add(content);
            y = PAGE_HEIGHT - 56F;
        }

        private void ensureSpace(float height) {
            if (y - height < BOTTOM) {
                newPage();
            }
        }

        private void text(String value, int size, float x, float y) {
            textColor(value, size, x, y, 0.09F, 0.13F, 0.20F);
        }

        private void textColor(String value, int size, float x, float y, float r, float g, float b) {
            content.append("BT /F1 ").append(size).append(" Tf ")
                    .append(format(r)).append(' ').append(format(g)).append(' ').append(format(b)).append(" rg ")
                    .append(format(x)).append(' ').append(format(y)).append(" Td <")
                    .append(hex(value)).append("> Tj ET\n");
        }

        private void rect(float x, float y, float w, float h, float r, float g, float b) {
            content.append(format(r)).append(' ').append(format(g)).append(' ').append(format(b)).append(" rg ")
                    .append(format(x)).append(' ').append(format(y)).append(' ')
                    .append(format(w)).append(' ').append(format(h)).append(" re f\n");
        }

        private void line(float x1, float y1, float x2, float y2, float r, float g, float b) {
            content.append(format(r)).append(' ').append(format(g)).append(' ').append(format(b)).append(" RG 1 w ")
                    .append(format(x1)).append(' ').append(format(y1)).append(" m ")
                    .append(format(x2)).append(' ').append(format(y2)).append(" l S\n");
        }

        private List<String> wrap(String text, int maxChars) {
            String normalized = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
            if (normalized.isEmpty()) {
                return List.of("-");
            }
            List<String> lines = new ArrayList<>();
            int index = 0;
            while (index < normalized.length()) {
                int end = Math.min(normalized.length(), index + maxChars);
                lines.add(normalized.substring(index, end));
                index = end;
            }
            return lines;
        }

        private String hex(String value) {
            byte[] raw = (value == null ? "" : value).getBytes(StandardCharsets.UTF_16BE);
            StringBuilder builder = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                builder.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
            }
            return builder.toString();
        }

        private String format(float value) {
            return String.format(Locale.ROOT, "%.2f", value);
        }

        private String trimForPdf(String value, int maxChars) {
            String normalized = value == null ? "-" : value.replace('\r', ' ').replace('\n', ' ').trim();
            if (normalized.isEmpty()) {
                return "-";
            }
            return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
        }

        private byte[] bytes(String value) {
            return value.getBytes(StandardCharsets.ISO_8859_1);
        }

        private void write(ByteArrayOutputStream out, String value) {
            out.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
        }
    }
}



